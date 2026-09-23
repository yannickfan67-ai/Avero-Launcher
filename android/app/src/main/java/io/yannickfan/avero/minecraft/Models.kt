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

enum class RuleAction {
    ALLOW, DISALLOW
}

data class RuleOs(
    val name: String? = null,
    val arch: String? = null,
    val version: String? = null
)

data class ArgumentRule(
    val action: RuleAction,
    val os: RuleOs? = null,
    val features: Map<String, Boolean> = emptyMap()
)

data class MinecraftArgument(
    val values: List<String>,
    val rules: List<ArgumentRule> = emptyList()
)

data class RuleContext(
    val osName: String,
    val osArch: String,
    val osVersion: String,
    val features: Map<String, Boolean> = emptyMap()
) {
    companion object {
        fun android(features: Map<String, Boolean> = emptyMap()) = RuleContext(
            osName = "linux",
            osArch = System.getProperty("os.arch") ?: "unknown",
            osVersion = System.getProperty("os.version") ?: "",
            features = features
        )
    }
}

data class LaunchContext(
    val rootDirectory: String,
    val gameDirectory: String,
    val nativesDirectory: String,
    val assetsRoot: String,
    val librariesDirectory: String,
    val username: String,
    val uuid: String,
    val accessToken: String,
    val userType: String = "msa",
    val launcherName: String = "Avero",
    val launcherVersion: String = "0.1.0",
    val clientId: String = "",
    val xuid: String = "",
    val resolutionWidth: Int? = null,
    val resolutionHeight: Int? = null,
    val quickPlayPath: String = "",
    val quickPlaySingleplayer: String = "",
    val quickPlayMultiplayer: String = "",
    val quickPlayRealms: String = "",
    val classpathSeparator: String = ":",
    val extraVariables: Map<String, String> = emptyMap(),
    val ruleContext: RuleContext = RuleContext.android()
) {
    companion object {
        fun preview(metadata: MinecraftVersionMetadata, instance: LauncherInstance) = LaunchContext(
            rootDirectory = ".",
            gameDirectory = "instances/\${instance.name}/game",
            nativesDirectory = "natives",
            assetsRoot = "assets",
            librariesDirectory = "libraries",
            username = "AveroPlayer",
            uuid = "00000000000000000000000000000000",
            accessToken = "preview",
            userType = "legacy",
            extraVariables = mapOf("version_name" to metadata.id)
        )
    }
}

data class MinecraftVersionMetadata(
    val id: String,
    val type: String,
    val mainClass: String,
    val javaMajorVersion: Int,
    val client: DownloadSpec,
    val assetIndexId: String,
    val assetIndex: DownloadSpec,
    val libraries: List<LibrarySpec>,
    val gameArguments: List<MinecraftArgument>,
    val jvmArguments: List<MinecraftArgument>
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
