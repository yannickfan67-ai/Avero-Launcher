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

        val librarySelection = LibrarySelector(rules).select(
            libraries = metadata.libraries,
            context = context,
            nativeClassifierPolicy = nativeClassifierPolicy,
            hasAndroidNativeProvider = androidNativeProvider != null
        )
        val effectiveLibraries = librarySelection.effective

        val classpath = buildList {
            addAll(androidNativeProvider?.classpathEntries.orEmpty())
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
            nativeArchives = nativeResolver.resolve(
                librarySelection.ruleAllowed,
                context,
                nativeClassifierPolicy
            ),
            logging = metadata.logging,
            androidNativeProvider = androidNativeProvider
        )
    }
}
