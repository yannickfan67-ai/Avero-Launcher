package io.yannickfan.avero.minecraft

import java.io.File

data class NativePreparationResult(
    val directory: File,
    val downloadedArchives: Int,
    val reusedArchives: Int,
    val extractedFiles: Int
)

class NativePreparation(
    private val downloader: FileDownloader = FileDownloader(),
    private val extractor: NativeArchiveExtractor = NativeArchiveExtractor()
) {
    suspend fun prepare(
        plan: LaunchPlan,
        root: File,
        instanceName: String
    ): NativePreparationResult {
        require(plan.nativeState.isLaunchable) {
            when (plan.nativeState) {
                NativePlanState.MISSING_ANDROID_PROVIDER ->
                    "Android native provider is required before native preparation"
                NativePlanState.MISSING_COMPATIBLE_ARCHIVES ->
                    "Compatible native archives are missing for this launch target"
                else ->
                    "Native plan is not preparable"
            }
        }

        plan.androidNativeProvider?.let { provider ->
            val directory = resolveProviderDirectory(
                root = root,
                relativePath = provider.nativeDirectory
            )
            val libraries = directory.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".so") }
                .orEmpty()
            require(libraries.isNotEmpty()) {
                "Android native provider is not installed or contains no native libraries: " +
                    provider.id
            }

            return NativePreparationResult(
                directory = directory,
                downloadedArchives = 0,
                reusedArchives = 0,
                extractedFiles = libraries.size
            )
        }

        val layout = InstanceLayout(root)
        val finalDirectory = layout.nativesDirectory(instanceName)
        val stagingDirectory = layout.nativesStagingDirectory(instanceName)

        if (stagingDirectory.exists()) {
            stagingDirectory.deleteRecursively()
        }
        check(stagingDirectory.mkdirs() || stagingDirectory.isDirectory) {
            "Could not create native staging directory"
        }

        var downloaded = 0
        var reused = 0
        var extracted = 0

        try {
            for (archivePlan in plan.nativeArchives) {
                val relativePath = requireNotNull(archivePlan.download.path) {
                    "Native archive download is missing a library path: " +
                        archivePlan.libraryName
                }
                val archive = layout.library(relativePath)

                if (downloader.isValid(archivePlan.download, archive)) {
                    reused++
                } else {
                    downloader.download(archivePlan.download, archive)
                    downloaded++
                }

                extracted += extractor.extract(
                    archive = archive,
                    destination = stagingDirectory,
                    excludes = archivePlan.extractExcludes
                )
            }

            if (finalDirectory.exists()) {
                check(finalDirectory.deleteRecursively()) {
                    "Could not replace previous native directory"
                }
            }
            finalDirectory.parentFile?.mkdirs()
            check(stagingDirectory.renameTo(finalDirectory)) {
                "Could not move prepared natives into final location"
            }

            return NativePreparationResult(
                directory = finalDirectory,
                downloadedArchives = downloaded,
                reusedArchives = reused,
                extractedFiles = extracted
            )
        } catch (t: Throwable) {
            stagingDirectory.deleteRecursively()
            throw t
        }
    }

    private fun resolveProviderDirectory(
        root: File,
        relativePath: String
    ): File {
        require(relativePath.isNotBlank()) {
            "Android native provider directory is blank"
        }
        require(!File(relativePath).isAbsolute) {
            "Android native provider directory must be relative to the Minecraft root"
        }

        val canonicalRoot = root.canonicalFile
        val directory = File(canonicalRoot, relativePath).canonicalFile
        require(
            directory.path.startsWith(canonicalRoot.path + File.separator)
        ) {
            "Android native provider directory escapes the Minecraft root"
        }
        require(directory.isDirectory) {
            "Android native provider directory is missing: " + directory.absolutePath
        }
        return directory
    }
}
