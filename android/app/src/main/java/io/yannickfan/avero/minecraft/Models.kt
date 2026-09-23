package io.yannickfan.avero.minecraft

data class MinecraftVersionSummary(
    val id: String,
    val type: String,
    val metadataUrl: String,
    val sha1: String?,
    val releaseTime: String?
)

data class VersionManifest(
    val latestRelease: String,
    val latestSnapshot: String,
    val versions: List<MinecraftVersionSummary>
)

data class DownloadSpec(
    val url: String,
    val sha1: String?,
    val size: Long?,
    val path: String? = null
)

data class LibrarySpec(
    val name: String,
    val artifact: DownloadSpec?
)

data class MinecraftVersionMetadata(
    val id: String,
    val type: String,
    val mainClass: String,
    val javaMajorVersion: Int,
    val client: DownloadSpec,
    val assetIndexId: String,
    val assetIndex: DownloadSpec,
    val libraries: List<LibrarySpec>,
    val gameArguments: List<String>,
    val jvmArguments: List<String>
)

data class LauncherInstance(
    val name: String,
    val versionId: String,
    val loader: Loader = Loader.VANILLA,
    val memoryMb: Int = 4096,
    val javaMajorVersion: Int? = null
)

enum class Loader {
    VANILLA, FABRIC, FORGE, NEOFORGE, QUILT
}

data class LaunchPlan(
    val versionId: String,
    val mainClass: String,
    val javaMajorVersion: Int,
    val classpathEntries: List<String>,
    val jvmArguments: List<String>,
    val gameArguments: List<String>
)
