package io.yannickfan.avero.minecraft

class LaunchPlanner(
    private val rules: RuleEvaluator = RuleEvaluator(),
    private val nativeResolver: NativeLibraryResolver = NativeLibraryResolver()
) {
    fun createVanillaPlan(
        metadata: MinecraftVersionMetadata,
        instance: LauncherInstance,
        context: RuleContext = MinecraftPlatform.androidRuleContext(),
        nativeClassifierPolicy: NativeClassifierPolicy = NativeClassifierPolicy.DISABLED,
        androidNativeProvider: AndroidNativeProvider? = null
    ): LaunchPlan {
        require(instance.loader == Loader.VANILLA) {
            "Loader-specific metadata must be normalized before launch planning"
        }
        require(
            androidNativeProvider == null ||
                nativeClassifierPolicy == NativeClassifierPolicy.DISABLED
        ) {
            "Use either an Android native provider or Mojang desktop classifiers, not both"
        }

        val allowedLibraries = metadata.libraries.filter {
            rules.isAllowed(it.rules, context)
        }
        val nativeLibraries = allowedLibraries.filter { it.natives.isNotEmpty() }

        val providerResolution = androidNativeProvider?.resolve(
            allowedLibraries,
            context
        )

        val effectiveLibraries = providerResolution?.libraries ?: allowedLibraries
        val nativeArchives: List<NativeArchivePlan>
        val nativeState: NativePlanState
        val nativeProviderId: String?

        when {
            providerResolution != null -> {
                nativeArchives = providerResolution.nativeArchives
                nativeState = NativePlanState.READY
                nativeProviderId = providerResolution.providerId
            }

            nativeLibraries.isEmpty() -> {
                nativeArchives = emptyList()
                nativeState = NativePlanState.NOT_REQUIRED
                nativeProviderId = null
            }

            nativeClassifierPolicy == NativeClassifierPolicy.MOJANG_DESKTOP -> {
                nativeArchives = nativeResolver.resolve(
                    allowedLibraries,
                    context,
                    nativeClassifierPolicy
                )
                nativeState =
                    if (nativeArchives.size == nativeLibraries.size) {
                        NativePlanState.READY
                    } else {
                        NativePlanState.MISSING_COMPATIBLE_ARCHIVES
                    }
                nativeProviderId = "mojang-desktop"
            }

            else -> {
                nativeArchives = emptyList()
                nativeState = NativePlanState.MISSING_ANDROID_PROVIDER
                nativeProviderId = null
            }
        }

        val classpath = buildList {
            effectiveLibraries.mapNotNullTo(this) { it.artifact?.path }
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
            nativeArchives = nativeArchives,
            nativeState = nativeState,
            nativeProviderId = nativeProviderId,
            logging = metadata.logging
        )
    }
}
