package io.yannickfan.avero.minecraft

data class LaunchContext(
    val playerName: String,
    val uuid: String,
    val accessToken: String,
    val clientId: String = "",
    val xuid: String = "",
    val userType: String = "msa",
    val userProperties: String = "{}",
    val profileProperties: String = "{}",
    val gameDirectory: String,
    val assetsRoot: String,
    val nativesDirectory: String,
    val librariesDirectory: String,
    val launcherName: String = "Avero",
    val launcherVersion: String = "0.1.0",
    val resolutionWidth: Int? = null,
    val resolutionHeight: Int? = null
) {
    companion object {
        fun preview(
            metadata: MinecraftVersionMetadata,
            instance: LauncherInstance,
            root: String = "."
        ): LaunchContext {
            val safeName = instance.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            return LaunchContext(
                playerName = "Player",
                uuid = "00000000000000000000000000000000",
                accessToken = "0",
                gameDirectory = "$root/instances/$safeName/game",
                assetsRoot = "$root/assets",
                nativesDirectory = "$root/versions/${metadata.id}/natives",
                librariesDirectory = "$root/libraries"
            )
        }
    }
}

class PlaceholderResolver(
    private val values: Map<String, String>
) {
    private val token = Regex("""\$\{([^}]+)}""")

    fun resolve(value: String): String =
        token.replace(value) { match ->
            val key = match.groupValues[1]
            values[key] ?: throw IllegalArgumentException(
                "Unresolved launcher variable: $key"
            )
        }
}
