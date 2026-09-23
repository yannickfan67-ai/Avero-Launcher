package io.yannickfan.avero.minecraft

import java.io.File

enum class AndroidNativeArtifactTarget {
    NATIVE_FILE,
    NATIVE_ARCHIVE,
    CLASSPATH
}

data class AndroidNativeArtifact(
    val id: String,
    val abi: String,
    val target: AndroidNativeArtifactTarget,
    val fileName: String,
    val download: DownloadSpec
) {
    init {
        require(id.matches(SAFE_ID)) { "Invalid Android native artifact id: $id" }
        require(abi.matches(SAFE_ID)) { "Invalid Android native ABI: $abi" }
        require(fileName.matches(SAFE_FILE_NAME)) {
            "Android native artifact fileName must be a simple file name: $fileName"
        }
        require(!download.sha256.isNullOrBlank()) {
            "Android native artifacts require SHA-256 verification: $id"
        }
    }

    private companion object {
        val SAFE_ID = Regex("[A-Za-z0-9._-]+")
        val SAFE_FILE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
    }
}

data class AndroidNativeBundle(
    val providerId: String,
    val minecraftVersion: String,
    val abi: String,
    val artifacts: List<AndroidNativeArtifact>
) {
    init {
        require(providerId.matches(Regex("[A-Za-z0-9._-]+"))) {
            "Invalid Android native provider id: $providerId"
        }
        require(minecraftVersion.isNotBlank()) { "Minecraft version is required" }
        require(abi.matches(Regex("[A-Za-z0-9._-]+"))) {
            "Invalid Android native ABI: $abi"
        }
        require(artifacts.isNotEmpty()) { "Android native bundle must contain artifacts" }
        require(artifacts.all { it.abi == abi }) {
            "Android native bundle contains artifacts for a different ABI"
        }
        require(artifacts.map { it.fileName }.distinct().size == artifacts.size) {
            "Android native bundle contains duplicate file names"
        }
    }
}

interface AndroidNativeProvider {
    val id: String

    fun resolve(
        minecraftVersion: String,
        abi: String
    ): AndroidNativeBundle?
}

interface AndroidNativeArtifactStore {
    suspend fun ensure(
        spec: DownloadSpec,
        destination: File
    ): File
}

class VerifiedAndroidNativeArtifactStore(
    private val downloader: FileDownloader = FileDownloader()
) : AndroidNativeArtifactStore {
    override suspend fun ensure(
        spec: DownloadSpec,
        destination: File
    ): File {
        require(!spec.sha256.isNullOrBlank()) {
            "Android native artifact is missing SHA-256"
        }

        if (!downloader.isValid(spec, destination)) {
            downloader.download(spec, destination)
        }

        check(downloader.isValid(spec, destination)) {
            "Android native artifact failed verification: ${destination.name}"
        }
        return destination
    }
}

data class AndroidNativePreparationResult(
    val providerId: String,
    val abi: String,
    val nativesDirectory: File,
    val classpathEntries: List<String>,
    val preparedFiles: Int
)

class AndroidNativePreparer(
    private val artifactStore: AndroidNativeArtifactStore =
        VerifiedAndroidNativeArtifactStore(),
    private val extractor: NativeArchiveExtractor = NativeArchiveExtractor()
) {
    suspend fun prepare(
        bundle: AndroidNativeBundle,
        root: File,
        instanceName: String
    ): AndroidNativePreparationResult {
        val layout = InstanceLayout(root)
        val destination = layout.nativesDirectory(instanceName)
        val staging = File(destination.parentFile, destination.name + ".staging")
        val backup = File(destination.parentFile, destination.name + ".backup")

        staging.deleteRecursively()
        backup.deleteRecursively()
        check(staging.mkdirs() || staging.isDirectory) {
            "Could not create Android native staging directory"
        }

        val classpathEntries = mutableListOf<String>()
        var preparedFiles = 0

        try {
            for (artifact in bundle.artifacts) {
                val cached = layout.androidNativeArtifact(
                    providerId = bundle.providerId,
                    abi = bundle.abi,
                    fileName = artifact.fileName
                )
                artifactStore.ensure(artifact.download, cached)

                when (artifact.target) {
                    AndroidNativeArtifactTarget.NATIVE_FILE -> {
                        cached.copyTo(File(staging, artifact.fileName), overwrite = true)
                        preparedFiles++
                    }

                    AndroidNativeArtifactTarget.NATIVE_ARCHIVE -> {
                        preparedFiles += extractor.extract(cached, staging)
                    }

                    AndroidNativeArtifactTarget.CLASSPATH -> {
                        classpathEntries += cached.relativeTo(root).path
                    }
                }
            }

            destination.parentFile?.mkdirs()
            if (destination.exists()) {
                check(destination.renameTo(backup)) {
                    "Could not preserve previous natives directory"
                }
            }

            if (!staging.renameTo(destination)) {
                if (backup.exists()) {
                    backup.renameTo(destination)
                }
                error("Could not activate prepared Android natives")
            }

            backup.deleteRecursively()

            return AndroidNativePreparationResult(
                providerId = bundle.providerId,
                abi = bundle.abi,
                nativesDirectory = destination,
                classpathEntries = classpathEntries,
                preparedFiles = preparedFiles
            )
        } catch (t: Throwable) {
            staging.deleteRecursively()
            if (!destination.exists() && backup.exists()) {
                backup.renameTo(destination)
            }
            throw t
        }
    }
}

fun LaunchPlan.withAndroidNativePreparation(
    preparation: AndroidNativePreparationResult
): LaunchPlan = copy(
    classpathEntries = (classpathEntries + preparation.classpathEntries).distinct()
)
