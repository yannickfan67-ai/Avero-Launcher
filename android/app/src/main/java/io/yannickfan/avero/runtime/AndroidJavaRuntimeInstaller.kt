package io.yannickfan.avero.runtime

import io.yannickfan.avero.minecraft.FileDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class RuntimeInstallProgress(
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

data class InstalledJavaRuntime(
    val packageInfo: AndroidJavaRuntimePackage,
    val home: File,
    val javaExecutable: File,
    val jvmLibrary: File?
)

class AndroidJavaRuntimeInstaller(
    private val downloader: FileDownloader = FileDownloader(),
    private val extractor: TarXzExtractor = TarXzExtractor()
) {
    suspend fun install(
        runtimePackage: AndroidJavaRuntimePackage,
        root: File,
        onProgress: (RuntimeInstallProgress) -> Unit = {}
    ): InstalledJavaRuntime = withContext(Dispatchers.IO) {
        root.mkdirs()
        val runtimeHome = File(root, runtimePackage.id)
        findInstalled(runtimePackage, runtimeHome)?.let {
            onProgress(
                RuntimeInstallProgress(
                    RuntimeInstallProgress.Stage.COMPLETE,
                    "Java ${runtimePackage.majorVersion} is already installed"
                )
            )
            return@withContext it
        }

        val cacheDir = File(root, ".downloads").apply { mkdirs() }
        val archive = File(cacheDir, runtimePackage.fileName)

        onProgress(
            RuntimeInstallProgress(
                RuntimeInstallProgress.Stage.CHECKING,
                "Checking ${runtimePackage.fileName}"
            )
        )

        if (!downloader.isValid(runtimePackage.download, archive)) {
            onProgress(
                RuntimeInstallProgress(
                    RuntimeInstallProgress.Stage.DOWNLOADING,
                    "Downloading Java ${runtimePackage.majorVersion}"
                )
            )
            downloader.download(runtimePackage.download, archive) { done, total ->
                onProgress(
                    RuntimeInstallProgress(
                        stage = RuntimeInstallProgress.Stage.DOWNLOADING,
                        message = "Downloading Java ${runtimePackage.majorVersion}",
                        downloadedBytes = done,
                        totalBytes = total
                    )
                )
            }
        }

        val staging = File(root, ".installing-${runtimePackage.id}")
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()

        try {
            onProgress(
                RuntimeInstallProgress(
                    RuntimeInstallProgress.Stage.EXTRACTING,
                    "Extracting Java ${runtimePackage.majorVersion}"
                )
            )
            extractor.extract(archive, staging)

            onProgress(
                RuntimeInstallProgress(
                    RuntimeInstallProgress.Stage.VALIDATING,
                    "Validating runtime layout"
                )
            )
            val staged = requireValid(runtimePackage, staging)

            if (runtimeHome.exists()) runtimeHome.deleteRecursively()
            check(staging.renameTo(runtimeHome)) {
                "Could not move Java runtime into final location"
            }

            val installed = requireValid(runtimePackage, runtimeHome)
            check(
                installed.javaExecutable.canExecute() ||
                    installed.javaExecutable.setExecutable(true, false)
            ) {
                "Installed Java executable is not executable: ${installed.javaExecutable}"
            }

            onProgress(
                RuntimeInstallProgress(
                    RuntimeInstallProgress.Stage.COMPLETE,
                    "Java ${runtimePackage.majorVersion} installed"
                )
            )
            installed
        } catch (t: Throwable) {
            staging.deleteRecursively()
            throw t
        }
    }

    fun findInstalled(
        runtimePackage: AndroidJavaRuntimePackage,
        runtimeHome: File
    ): InstalledJavaRuntime? =
        runCatching { requireValid(runtimePackage, runtimeHome) }
            .getOrNull()
            ?.takeIf { it.javaExecutable.canExecute() }

    private fun requireValid(
        runtimePackage: AndroidJavaRuntimePackage,
        home: File
    ): InstalledJavaRuntime {
        require(home.isDirectory) { "Runtime home is missing: $home" }

        val java = File(home, "bin/java")
        require(java.isFile) { "Runtime does not contain bin/java" }

        val jvm = home.walkTopDown()
            .firstOrNull { it.isFile && it.name == "libjvm.so" }

        return InstalledJavaRuntime(
            packageInfo = runtimePackage,
            home = home,
            javaExecutable = java,
            jvmLibrary = jvm
        )
    }
}
