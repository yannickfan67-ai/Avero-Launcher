package io.yannickfan.avero.minecraft

import java.io.File

data class LaunchIdentity(
    val playerName: String,
    val uuid: String,
    val accessToken: String,
    val userType: String = "msa",
    val xuid: String = "",
    val clientId: String = "",
    val userProperties: String = "{}"
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
    val loggingConfigFile: File? = null,
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
        validateNativeReadiness(plan, environment)

        val classpathSeparator = File.pathSeparator
        val classpath = plan.classpathEntries
            .map { entry -> File(environment.minecraftRoot, entry).absolutePath }
            .joinToString(classpathSeparator)

        val values = mutableMapOf(
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
            "user_properties" to identity.userProperties,
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
        environment.loggingConfigFile?.let { values["path"] = it.absolutePath }

        val loggingArgument = plan.logging?.let { logging ->
            requireNotNull(environment.loggingConfigFile) {
                "Logging configuration file is required for ${logging.fileId}"
            }
            substitute(logging.argument, values)
        }

        return ResolvedLaunchCommand(
            javaMajorVersion = plan.javaMajorVersion,
            mainClass = plan.mainClass,
            jvmArguments = buildList {
                loggingArgument?.let(::add)
                addAll(plan.jvmArguments.map { substitute(it, values) })
            },
            gameArguments = plan.gameArguments.map { substitute(it, values) }
        )
    }

    private fun validateNativeReadiness(
        plan: LaunchPlan,
        environment: LaunchEnvironment
    ) {
        val provider = plan.androidNativeProvider
        if (!plan.requiresNatives && provider == null) return

        if (provider != null) {
            require(provider.classpathEntries.isNotEmpty()) {
                "Android native provider has no patched classpath entries"
            }

            provider.classpathEntries.forEach { relativePath ->
                val file = managedFile(environment.minecraftRoot, relativePath)
                require(file.isFile && file.length() > 0L) {
                    "Android native provider classpath entry is missing: " + file.absolutePath
                }
            }

            val providerNatives = managedFile(
                environment.minecraftRoot,
                provider.nativeDirectory
            )
            require(providerNatives.isDirectory) {
                "Android native provider directory is missing: " +
                    providerNatives.absolutePath
            }
            require(
                providerNatives.listFiles()
                    ?.any { it.isFile && it.name.endsWith(".so") } == true
            ) {
                "Android native provider contains no native libraries"
            }
            require(
                environment.nativesDirectory.canonicalFile ==
                    providerNatives.canonicalFile
            ) {
                "Launch natives_directory does not match the installed Android provider"
            }
            return
        }

        require(plan.nativeArchives.isNotEmpty()) {
            "Android native provider or compatible native archives are required before launch"
        }
        require(
            environment.nativesDirectory.isDirectory &&
                environment.nativesDirectory.listFiles()
                    ?.any { it.isFile && it.name.endsWith(".so") } == true
        ) {
            "Native archives have not been prepared into natives_directory"
        }
    }

    private fun managedFile(root: File, relativePath: String): File {
        require(relativePath.isNotBlank()) {
            "Managed launch path is blank"
        }
        require(!File(relativePath).isAbsolute) {
            "Managed launch path must be relative: " + relativePath
        }

        val canonicalRoot = root.canonicalFile
        val file = File(canonicalRoot, relativePath).canonicalFile
        require(file.path.startsWith(canonicalRoot.path + File.separator)) {
            "Managed launch path escapes the Minecraft root: " + relativePath
        }
        return file
    }

    private fun substitute(value: String, variables: Map<String, String>): String {
        var result = value
        for ((key, replacement) in variables) {
            val placeholder = "$" + "{" + key + "}"
            result = result.replace(placeholder, replacement)
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
