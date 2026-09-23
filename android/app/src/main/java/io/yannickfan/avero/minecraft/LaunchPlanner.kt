package io.yannickfan.avero.minecraft

class LaunchPlanner(
    private val rules: RuleEvaluator = RuleEvaluator(),
    private val nativeResolver: NativeLibraryResolver = NativeLibraryResolver()
) {
    fun createVanillaPlan(
        metadata: MinecraftVersionMetadata,
        instance: LauncherInstance,
        launchContext: LaunchContext = LaunchContext.preview(metadata, instance),
        context: RuleContext = MinecraftPlatform.androidRuleContext(launchContext.ruleFeatures())
    ): LaunchPlan {
        require(instance.loader == Loader.VANILLA) {
            "Loader-specific metadata must be normalized before launch planning"
        }

        val allowedLibraries = metadata.libraries.filter {
            rules.isAllowed(it.rules, context)
        }

        val classpath = buildList {
            allowedLibraries.mapNotNullTo(this) { it.artifact?.path }
            add("versions/${metadata.id}/${metadata.id}.jar")
        }

        val variables = buildMap {
            put("auth_player_name", launchContext.playerName)
            put("version_name", metadata.id)
            put("game_directory", launchContext.gameDirectory)
            put("assets_root", launchContext.assetsRoot)
            put("game_assets", launchContext.assetsRoot)
            put("assets_index_name", metadata.assetIndexId)
            put("auth_uuid", launchContext.uuid)
            put("auth_access_token", launchContext.accessToken)
            put("auth_session", launchContext.accessToken)
            put("clientid", launchContext.clientId)
            put("auth_xuid", launchContext.xuid)
            put("user_type", launchContext.userType)
            put("user_properties", launchContext.userProperties)
            put("profile_properties", launchContext.profileProperties)
            put("version_type", metadata.type)
            put("natives_directory", launchContext.nativesDirectory)
            put("launcher_name", launchContext.launcherName)
            put("launcher_version", launchContext.launcherVersion)
            put("classpath", classpath.joinToString(":"))
            put("classpath_separator", ":")
            put("library_directory", launchContext.librariesDirectory)
            launchContext.resolutionWidth?.let { put("resolution_width", it.toString()) }
            launchContext.resolutionHeight?.let { put("resolution_height", it.toString()) }
        }
        val resolver = PlaceholderResolver(variables)

        val jvm = buildList {
            add("-Xms512M")
            add("-Xmx${instance.memoryMb}M")
            addAll(
                rules.resolveArguments(metadata.jvmArguments, context)
                    .map(resolver::resolve)
            )
        }
        val game = rules.resolveArguments(metadata.gameArguments, context)
            .map(resolver::resolve)

        val plan = LaunchPlan(
            versionId = metadata.id,
            mainClass = metadata.mainClass,
            javaMajorVersion = instance.javaMajorVersion ?: metadata.javaMajorVersion,
            classpathEntries = classpath,
            jvmArguments = jvm,
            gameArguments = game,
            nativeArchives = nativeResolver.resolve(allowedLibraries, context)
        )

        requireNoUnresolvedVariables(plan)
        return plan
    }

    private fun requireNoUnresolvedVariables(plan: LaunchPlan) {
        val unresolvedPrefix = 36.toChar().toString() + "{"
        val unresolved = (plan.jvmArguments + plan.gameArguments)
            .firstOrNull { it.contains(unresolvedPrefix) }

        require(unresolved == null) {
            "Launch plan contains unresolved launcher variable: $unresolved"
        }
    }
}
