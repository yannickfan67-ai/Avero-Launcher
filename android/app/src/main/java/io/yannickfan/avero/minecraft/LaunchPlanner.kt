package io.yannickfan.avero.minecraft

import io.yannickfan.avero.androidnative.AndroidNativeProviderPlan

class LaunchPlanner(
    private val rules: RuleEvaluator = RuleEvaluator(),
    private val nativeResolver: NativeLibraryResolver = NativeLibraryResolver()
) {
    fun createVanillaPlan(
        metadata: MinecraftVersionMetadata,
        instance: LauncherInstance,
        context: RuleContext = MinecraftPlatform.androidRuleContext(),
        nativeClassifierPolicy: NativeClassifierPolicy = NativeClassifierPolicy.DISABLED,
        androidNativeProvider: AndroidNativeProviderPlan? = null
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

        val nativeArchives =
            if (
                androidNativeProvider == null &&
                nativeClassifierPolicy == NativeClassifierPolicy.MOJANG_DESKTOP
            ) {
                nativeResolver.resolve(
                    allowedLibraries,
                    context,
                    nativeClassifierPolicy
                )
            } else {
                emptyList()
            }

        val nativeState = when {
            androidNativeProvider != null ->
                NativePlanState.READY
            nativeLibraries.isEmpty() ->
                NativePlanState.NOT_REQUIRED
            nativeClassifierPolicy == NativeClassifierPolicy.MOJANG_DESKTOP &&
                nativeArchives.size == nativeLibraries.size ->
                NativePlanState.READY
            nativeClassifierPolicy == NativeClassifierPolicy.MOJANG_DESKTOP ->
                NativePlanState.MISSING_COMPATIBLE_ARCHIVES
            else ->
                NativePlanState.MISSING_ANDROID_PROVIDER
        }

        val classpath = buildList {
            androidNativeProvider?.classpathEntry?.let(::add)
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
            nativeArchives = nativeArchives,
            nativeState = nativeState,
            logging = metadata.logging,
            androidNativeProvider = androidNativeProvider
        )
    }
}
