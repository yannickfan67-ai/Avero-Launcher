package io.yannickfan.avero.minecraft

class LaunchPlanner(
    private val ruleEvaluator: MojangRuleEvaluator = MojangRuleEvaluator()
) {
    fun createVanillaPlan(
        metadata: MinecraftVersionMetadata,
        instance: LauncherInstance,
        context: LaunchContext,
        environment: LaunchEnvironment = LaunchEnvironment.android(context)
    ): LaunchPlan {
        require(instance.loader == Loader.VANILLA) {
            "Loader-specific metadata must be normalized before launch planning"
        }

        val classpath = buildList {
            metadata.libraries.mapNotNullTo(this) { it.artifact?.path }
            add("versions/" + metadata.id + "/" + metadata.id + ".jar")
        }

        val variables = buildMap {
            put("auth_player_name", context.playerName)
            put("version_name", metadata.id)
            put("game_directory", context.gameDirectory)
            put("assets_root", context.assetsRoot)
            put("assets_index_name", metadata.assetIndexId)
            put("auth_uuid", context.uuid)
            put("auth_access_token", context.accessToken)
            put("clientid", context.clientId)
            put("auth_xuid", context.xuid)
            put("user_type", context.userType)
            put("user_properties", context.userProperties)
            put("version_type", metadata.type)
            put("natives_directory", context.nativesDirectory)
            put("launcher_name", context.launcherName)
            put("launcher_version", context.launcherVersion)
            put("classpath", classpath.joinToString(":"))
            put("classpath_separator", ":")
            put("library_directory", context.librariesDirectory)
            context.resolutionWidth?.let { put("resolution_width", it.toString()) }
            context.resolutionHeight?.let { put("resolution_height", it.toString()) }
        }
        val resolver = PlaceholderResolver(variables)

        val jvm = buildList {
            add("-Xms512M")
            add("-Xmx" + instance.memoryMb + "M")
            addAll(resolveArguments(metadata.jvmArguments, environment, resolver))
        }
        val game = resolveArguments(metadata.gameArguments, environment, resolver)

        val plan = LaunchPlan(
            versionId = metadata.id,
            mainClass = metadata.mainClass,
            javaMajorVersion = instance.javaMajorVersion ?: metadata.javaMajorVersion,
            classpathEntries = classpath,
            jvmArguments = jvm,
            gameArguments = game
        )
        requireResolved(plan)
        return plan
    }

    private fun resolveArguments(
        arguments: List<MinecraftArgument>,
        environment: LaunchEnvironment,
        resolver: PlaceholderResolver
    ): List<String> = buildList {
        arguments.forEach { argument ->
            if (ruleEvaluator.isAllowed(argument.rules, environment)) {
                argument.values.mapTo(this) { resolver.resolve(it) }
            }
        }
    }

    private fun requireResolved(plan: LaunchPlan) {
        val unresolved = (plan.jvmArguments + plan.gameArguments)
            .firstOrNull { it.contains("${") }
        require(unresolved == null) {
            "Launch plan contains unresolved launcher variable: " + unresolved
        }
    }
}
