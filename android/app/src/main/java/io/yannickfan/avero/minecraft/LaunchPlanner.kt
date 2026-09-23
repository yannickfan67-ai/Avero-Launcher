package io.yannickfan.avero.minecraft

class LaunchPlanner(
    private val rules: RuleEvaluator = RuleEvaluator(),
    private val nativeResolver: NativeLibraryResolver = NativeLibraryResolver()
) {
    fun createVanillaPlan(
        metadata: MinecraftVersionMetadata,
        instance: LauncherInstance,
        context: RuleContext = MinecraftPlatform.androidRuleContext()
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

        val jvm = buildList {
            add("-Xms512M")
            add("-Xmx${instance.memoryMb}M")
            addAll(rules.resolveArguments(metadata.jvmArguments, context))
        }

        return LaunchPlan(
            versionId = metadata.id,
            mainClass = metadata.mainClass,
            javaMajorVersion = instance.javaMajorVersion ?: metadata.javaMajorVersion,
            classpathEntries = classpath,
            jvmArguments = jvm,
            gameArguments = rules.resolveArguments(metadata.gameArguments, context),
            nativeArchives = nativeResolver.resolve(allowedLibraries, context)
        )
    }
}
