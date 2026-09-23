package io.yannickfan.avero.androidnative

import io.yannickfan.avero.minecraft.FileDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

data class InstalledAndroidNativeProvider(
    val packageInfo: AndroidNativeProviderPackage,
    val root: File,
    val classpathJars: List<File>,
    val nativeDirectory: File,
    val nativeLibraries: List<File>
)

data class NativeProviderProgress(
    val stage: Stage,
    val message: String,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null
) {
    enum class Stage {
        CHECKING,
        DOWNLOADING,
        EXTRACTING,
        VALIDATING,
        COMPLETE
    }
}

class AndroidNativeProviderInstaller(
    private val downloader: FileDownloader = FileDownloader()
) {
    suspend fun install(
        pkg: AndroidNativeProviderPackage,
        minecraftRoot: File,
        onProgress: (NativeProviderProgress) -> Unit = {}
    ): InstalledAndroidNativeProvider = withContext(Dispatchers.IO) {
        val providerRoot = File(minecraftRoot, "android-native/${pkg.id}")
        findInstalled(pkg, providerRoot)?.let { installed ->
            normalizePermissions(installed)
            onProgress(
                NativeProviderProgress(
                    NativeProviderProgress.Stage.COMPLETE,
                    "Android LWJGL ${pkg.lwjglVersion} is already installed"
                )
            )
            return@withContext installed
        }

        val cache = File(minecraftRoot, "android-native/.downloads/${pkg.id}")
            .apply { mkdirs() }
        val nativeArchive = File(cache, "natives.aar")

        onProgress(
            NativeProviderProgress(
                NativeProviderProgress.Stage.CHECKING,
                "Checking Android LWJGL provider"
            )
        )

        if (!downloader.isValid(pkg.nativeArchive, nativeArchive)) {
            downloader.download(pkg.nativeArchive, nativeArchive) { done, total ->
                onProgress(
                    NativeProviderProgress(
                        stage = NativeProviderProgress.Stage.DOWNLOADING,
                        message = "Downloading Android LWJGL native AAR",
                        downloadedBytes = done,
                        totalBytes = total
                    )
                )
            }
        }

        val componentFiles = linkedMapOf<AndroidNativeProviderComponent, File>()
        for ((index, component) in pkg.javaComponents.withIndex()) {
            val cached = File(cache, component.fileName)
            if (!downloader.isValid(component.download, cached)) {
                downloader.download(component.download, cached) { done, total ->
                    onProgress(
                        NativeProviderProgress(
                            stage = NativeProviderProgress.Stage.DOWNLOADING,
                            message =
                                "LWJGL component ${index + 1}/${pkg.javaComponents.size} · " +
                                    component.fileName,
                            downloadedBytes = done,
                            totalBytes = total
                        )
                    )
                }
            }
            componentFiles[component] = cached
        }

        val staging = File(minecraftRoot, "android-native/.installing-${pkg.id}")
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()

        try {
            onProgress(
                NativeProviderProgress(
                    NativeProviderProgress.Stage.EXTRACTING,
                    "Preparing patched LWJGL Java components"
                )
            )
            val jarsDir = File(staging, "jars").apply { mkdirs() }
            for ((component, cached) in componentFiles) {
                cached.copyTo(
                    target = File(jarsDir, component.fileName),
                    overwrite = true
                )
            }

            onProgress(
                NativeProviderProgress(
                    NativeProviderProgress.Stage.EXTRACTING,
                    "Extracting native provider for ${pkg.arch.androidAbi}"
                )
            )
            extractNativeAar(
                archive = nativeArchive,
                nativeDirectory = File(staging, "natives"),
                androidAbi = pkg.arch.androidAbi
            )

            onProgress(
                NativeProviderProgress(
                    NativeProviderProgress.Stage.VALIDATING,
                    "Validating Android LWJGL provider"
                )
            )
            val staged = requireValid(pkg, staging)
            normalizePermissions(staged)

            if (providerRoot.exists()) providerRoot.deleteRecursively()
            providerRoot.parentFile?.mkdirs()
            check(staging.renameTo(providerRoot)) {
                "Could not move Android native provider into final location"
            }

            val installed = requireValid(pkg, providerRoot)
            normalizePermissions(installed)

            onProgress(
                NativeProviderProgress(
                    NativeProviderProgress.Stage.COMPLETE,
                    "Android LWJGL ${pkg.lwjglVersion} installed"
                )
            )
            installed
        } catch (t: Throwable) {
            staging.deleteRecursively()
            throw t
        }
    }

    fun findInstalled(
        pkg: AndroidNativeProviderPackage,
        providerRoot: File
    ): InstalledAndroidNativeProvider? =
        runCatching { requireValid(pkg, providerRoot) }.getOrNull()

    private fun extractNativeAar(
        archive: File,
        nativeDirectory: File,
        androidAbi: String
    ) {
        nativeDirectory.mkdirs()
        var nativeCount = 0

        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || !isAbiNative(entry.name, androidAbi)) {
                    continue
                }

                val fileName = File(entry.name).name
                require(fileName.endsWith(".so") && !fileName.contains('/')) {
                    "Invalid native library name: ${entry.name}"
                }

                val output = File(nativeDirectory, fileName)
                require(output.canonicalFile.parentFile == nativeDirectory.canonicalFile) {
                    "Invalid native library path: ${entry.name}"
                }

                zip.getInputStream(entry).use { input ->
                    output.outputStream().use(input::copyTo)
                }
                nativeCount++
            }
        }

        require(nativeCount > 0) {
            "Provider AAR has no native libraries for $androidAbi"
        }
    }

    private fun isAbiNative(path: String, abi: String): Boolean {
        val jniPrefix = "jni/$abi/"
        val libPrefix = "lib/$abi/"
        return path.endsWith(".so") &&
            (path.startsWith(jniPrefix) || path.startsWith(libPrefix))
    }

    private fun requireValid(
        pkg: AndroidNativeProviderPackage,
        root: File
    ): InstalledAndroidNativeProvider {
        require(root.isDirectory) { "Provider root is missing: $root" }

        val jarsDir = File(root, "jars")
        val classpathJars = pkg.javaComponents.map { component ->
            File(jarsDir, component.fileName).also { jar ->
                require(jar.isFile && jar.length() > 0) {
                    "Provider Java component is missing: ${component.fileName}"
                }
            }
        }

        val natives = File(root, "natives")
        val libraries = natives.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".so") }
            ?.sortedBy { it.name }
            .orEmpty()

        require(libraries.isNotEmpty()) {
            "Provider native directory is empty for ${pkg.arch.androidAbi}"
        }

        return InstalledAndroidNativeProvider(
            packageInfo = pkg,
            root = root,
            classpathJars = classpathJars,
            nativeDirectory = natives,
            nativeLibraries = libraries
        )
    }

    private fun normalizePermissions(installed: InstalledAndroidNativeProvider) {
        installed.classpathJars.forEach { jar ->
            jar.setReadable(true, false)
            jar.setWritable(false, false)
        }
        installed.nativeLibraries.forEach { lib ->
            lib.setReadable(true, false)
            lib.setWritable(false, false)
        }
    }
}
