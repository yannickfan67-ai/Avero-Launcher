package io.yannickfan.avero.minecraft

class LaunchPlanner {
    fun createPreviewVanillaPlan(
        metadata: MinecraftVersionMetadata,
        instance: LauncherInstance
    ): LaunchPlan =
        createPlan(
            metadata = metadata,
            instance = instance,
            context = LaunchContext.preview(metadata, instance),
            validateResolved = false
        )

    fun createVanillaPlan(
        metadata: MinecraftVersionMetadata,
        instance: LauncherInstance,
        context: LaunchContext
    ): LaunchPlan =
        createPlan(
            metadata = metadata,
            instance = instance,
            context = context,
            validateResolved = true
        )

    private fun createPlan(
        metadata: MinecraftVersionMetadata,
        instance: LauncherInstance,
        context: LaunchContext,
        validateResolved: Boolean
    ): LaunchPlan {
        require(instance.loader == Loader.VANILLA) {
            "Loader-specific metadata must be normalized before launch planning"
        }

        val root = context.rootDirectory.trimEnd('/')
        val librariesRoot = context.librariesDirectory.trimEnd('/')
        val classpath = buildList {
            metadata.libraries.mapNotNullTo(this) { library ->
                library.artifact?.path?.let { path -> "$librariesRoot/$path" }
            }
            add("$root/versions/\${metadata.id}/\${metadata.id}.jar")
        }

        val features = context.ruleContext.features.toMutableMap()
        if (context.resolutionWidth != null && context.resolutionHeight != null) {
            features["has_custom_resolution"] = true
        }
        val ruleContext = context.ruleContext.copy(features = features)

        val variables = linkedMapOf(
            "natives_directory" to context.nativesDirectory,
            "launcher_name" to context.launcherName,
            "launcher_version" to context.launcherVersion,
            "classpath" to classpath.joinToString(context.classpathSeparator),
            "classpath_separator" to context.classpathSeparator,
            "library_directory" to context.librariesDirectory,
            "auth_player_name" to context.username,
            "version_name" to metadata.id,
            "game_directory" to context.gameDirectory,
            "assets_root" to context.assetsRoot,
            "assets_index_name" to metadata.assetIndexId,
            "auth_uuid" to context.uuid,
            "auth_access_token" to context.accessToken,
            "clientid" to context.clientId,
            "xuid" to context.xuid,
            "user_type" to context.userType,
            "version_type" to metadata.type,
            "user_properties" to "{}",
            "auth_session" to context.accessToken,
            "game_assets" to context.assetsRoot,
            "quickPlayPath" to context.quickPlayPath,
            "quickPlaySingleplayer" to context.quickPlaySingleplayer,
            "quickPlayMultiplayer" to context.quickPlayMultiplayer,
            "quickPlayRealms" to context.quickPlayRealms
        ).apply {
            context.resolutionWidth?.let { put("resolution_width", it.toString()) }
            context.resolutionHeight?.let { put("resolution_height", it.toString()) }
            putAll(context.extraVariables)
        }

        val rawJvm = selectArguments(metadata.jvmArguments, ruleContext)
        val rawGame = selectArguments(metadata.gameArguments, ruleContext)
        val resolvedJvm = LauncherPlaceholderResolver.resolveAll(rawJvm, variables)
        val resolvedGame = LauncherPlaceholderResolver.resolveAll(rawGame, variables)

        if (validateResolved) {
            val unresolved = LauncherPlaceholderResolver.unresolved(resolvedJvm + resolvedGame)
            require(unresolved.isEmpty()) {
                "Unresolved launcher variables: \${unresolved.joinToString(", ")}"
            }
        }

        val jvm = buildList {
            add("-Xms512M")
            add("-Xmx\${instance.memoryMb}M")
            addAll(resolvedJvm)
        }

        return LaunchPlan(
            versionId = metadata.id,
            mainClass = metadata.mainClass,
            javaMajorVersion = instance.javaMajorVersion ?: metadata.javaMajorVersion,
            classpathEntries = classpath,
            jvmArguments = jvm,
            gameArguments = resolvedGame
        )
    }

    private fun selectArguments(
        arguments: List<MinecraftArgument>,
        context: RuleContext
    ): List<String> =
        arguments
            .filter { MojangRuleEvaluator.isAllowed(it, context) }
            .flatMap { it.values }
}
