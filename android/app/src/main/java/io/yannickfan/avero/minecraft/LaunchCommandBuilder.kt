package io.yannickfan.avero.minecraft

import java.io.File

data class LaunchIdentity(
    val playerName: String,
    val uuid: String,
    val accessToken: String,
    val userType: String = "msa",
    val xuid: String = "",
    val clientId: String = ""
)

data class LaunchEnvironment(
    val versionName: String,
    val versionType: String,
    val minecraftRoot: File,
    val gameDirectory: File,
    val assetsRoot: File,
    val assetsIndexName: String,
    val nativesDirectory: File,
    val libraryDirectory: File,
    val launcherName: String = "Avero",
    val launcherVersion: String = "0.1.0",
    val resolutionWidth: Int = 1280,
    val resolutionHeight: Int = 720
)

data class ResolvedLaunchCommand(
    val javaMajorVersion: Int,
    val mainClass: String,
    val jvmArguments: List<String>,
    val gameArguments: List<String>
) {
    fun asArgumentList(javaExecutable: String): List<String> =
        buildList {
            add(javaExecutable)
            addAll(jvmArguments)
            add(mainClass)
            addAll(gameArguments)
        }
}

class LaunchCommandBuilder {
    fun resolve(
        plan: LaunchPlan,
        identity: LaunchIdentity,
        environment: LaunchEnvironment
    ): ResolvedLaunchCommand {
        val classpathSeparator = File.pathSeparator
        val classpath = plan.classpathEntries
            .map { entry -> File(environment.minecraftRoot, entry).absolutePath }
            .joinToString(classpathSeparator)

        val values = mapOf(
            "auth_player_name" to identity.playerName,
            "version_name" to environment.versionName,
            "game_directory" to environment.gameDirectory.absolutePath,
            "assets_root" to environment.assetsRoot.absolutePath,
            "assets_index_name" to environment.assetsIndexName,
            "auth_uuid" to identity.uuid,
            "auth_access_token" to identity.accessToken,
            "clientid" to identity.clientId,
            "auth_xuid" to identity.xuid,
            "user_type" to identity.userType,
            "version_type" to environment.versionType,
            "natives_directory" to environment.nativesDirectory.absolutePath,
            "launcher_name" to environment.launcherName,
            "launcher_version" to environment.launcherVersion,
            "classpath" to classpath,
            "classpath_separator" to classpathSeparator,
            "library_directory" to environment.libraryDirectory.absolutePath,
            "resolution_width" to environment.resolutionWidth.toString(),
            "resolution_height" to environment.resolutionHeight.toString()
        )

        return ResolvedLaunchCommand(
            javaMajorVersion = plan.javaMajorVersion,
            mainClass = plan.mainClass,
            jvmArguments = plan.jvmArguments.map { substitute(it, values) },
            gameArguments = plan.gameArguments.map { substitute(it, values) }
        )
    }

    private fun substitute(value: String, variables: Map<String, String>): String {
        var result = value
        for ((key, replacement) in variables) {
            result = result.replace("${$key}", replacement)
        }

        val unresolved = PLACEHOLDER.findAll(result).map { it.value }.toList()
        require(unresolved.isEmpty()) {
            "Unresolved Minecraft launch variables in '$value': ${unresolved.joinToString()}"
        }
        return result
    }

    companion object {
        private val PLACEHOLDER = Regex("""\$\{[^}]+}""")
    }
}
