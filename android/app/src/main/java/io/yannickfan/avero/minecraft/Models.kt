package io.yannickfan.avero.minecraft

import io.yannickfan.avero.androidnative.AndroidNativeProviderPlan

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
    val path: String? = null,
    val sha256: String? = null,
    val gitBlobSha1: String? = null
)

enum class RuleAction {
    ALLOW,
    DISALLOW
}

data class OsRule(
    val name: String? = null,
    val versionRegex: String? = null,
    val archRegex: String? = null
)

data class RuleSpec(
    val action: RuleAction,
    val os: OsRule? = null,
    val features: Map<String, Boolean> = emptyMap()
)

data class ConditionalArgument(
    val values: List<String>,
    val rules: List<RuleSpec> = emptyList()
)

data class LoggingSpec(
    val argument: String,
    val fileId: String,
    val file: DownloadSpec
)

data class LibrarySpec(
    val name: String,
    val artifact: DownloadSpec?,
    val classifiers: Map<String, DownloadSpec> = emptyMap(),
    val natives: Map<String, String> = emptyMap(),
    val rules: List<RuleSpec> = emptyList(),
    val extractExcludes: List<String> = emptyList()
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
    val logging: LoggingSpec?,
    val gameArguments: List<ConditionalArgument>,
    val jvmArguments: List<ConditionalArgument>
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

data class RuleContext(
    val osName: String,
    val osVersion: String = "",
    val osArch: String,
    val features: Map<String, Boolean> = emptyMap()
)

data class LaunchPlan(
    val versionId: String,
    val mainClass: String,
    val javaMajorVersion: Int,
    val classpathEntries: List<String>,
    val jvmArguments: List<String>,
    val gameArguments: List<String>,
    val nativeArchives: List<NativeArchivePlan> = emptyList(),
    val logging: LoggingSpec? = null,
    val androidNativeProvider: AndroidNativeProviderPlan? = null
)

data class NativeArchivePlan(
    val libraryName: String,
    val classifier: String,
    val download: DownloadSpec,
    val extractExcludes: List<String>
)
