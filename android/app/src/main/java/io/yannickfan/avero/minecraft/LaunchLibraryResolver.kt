package io.yannickfan.avero.minecraft

data class LibrarySelection(
    val libraries: List<LibrarySpec>,
    val nativeArchives: List<NativeArchivePlan>,
    val nativeState: NativePlanState,
    val nativeProviderId: String?,
    val skippedByRule: Int,
    val strippedDesktopNativeArtifacts: Int
)

class LaunchLibraryResolver(
    private val rules: RuleEvaluator = RuleEvaluator(),
    private val nativeResolver: NativeLibraryResolver = NativeLibraryResolver()
) {
    fun resolve(
        libraries: List<LibrarySpec>,
        context: RuleContext,
        nativeClassifierPolicy: NativeClassifierPolicy = NativeClassifierPolicy.DISABLED,
        androidNativeProvider: AndroidNativeProvider? = null
    ): LibrarySelection {
        require(
            androidNativeProvider == null ||
                nativeClassifierPolicy == NativeClassifierPolicy.DISABLED
        ) {
            "Use either an Android native provider or Mojang desktop classifiers, not both"
        }

        val allowed = libraries.filter { rules.isAllowed(it.rules, context) }
        val desktopNativeArtifacts = allowed.filter(LibrarySpec::isDesktopNativeArtifact)
        val baseLibraries =
            if (
                androidNativeProvider != null ||
                nativeClassifierPolicy == NativeClassifierPolicy.DISABLED
            ) {
                allowed.filterNot(LibrarySpec::isDesktopNativeArtifact)
            } else {
                allowed
            }

        val legacyNativeLibraries = allowed.filter { it.natives.isNotEmpty() }
        val nativeSupportRequired =
            legacyNativeLibraries.isNotEmpty() || desktopNativeArtifacts.isNotEmpty()

        val providerResolution = androidNativeProvider?.resolve(baseLibraries, context)
        if (providerResolution != null) {
            return LibrarySelection(
                libraries = providerResolution.libraries,
                nativeArchives = providerResolution.nativeArchives,
                nativeState = NativePlanState.READY,
                nativeProviderId = providerResolution.providerId,
                skippedByRule = libraries.size - allowed.size,
                strippedDesktopNativeArtifacts = desktopNativeArtifacts.size
            )
        }

        if (!nativeSupportRequired) {
            return LibrarySelection(
                libraries = baseLibraries,
                nativeArchives = emptyList(),
                nativeState = NativePlanState.NOT_REQUIRED,
                nativeProviderId = null,
                skippedByRule = libraries.size - allowed.size,
                strippedDesktopNativeArtifacts = desktopNativeArtifacts.size
            )
        }

        if (nativeClassifierPolicy == NativeClassifierPolicy.MOJANG_DESKTOP) {
            val nativeArchives = nativeResolver.resolve(
                allowed,
                context,
                nativeClassifierPolicy
            )
            return LibrarySelection(
                libraries = allowed,
                nativeArchives = nativeArchives,
                nativeState =
                    if (nativeArchives.size == legacyNativeLibraries.size) {
                        NativePlanState.READY
                    } else {
                        NativePlanState.MISSING_COMPATIBLE_ARCHIVES
                    },
                nativeProviderId = "mojang-desktop",
                skippedByRule = libraries.size - allowed.size,
                strippedDesktopNativeArtifacts = 0
            )
        }

        return LibrarySelection(
            libraries = baseLibraries,
            nativeArchives = emptyList(),
            nativeState = NativePlanState.MISSING_ANDROID_PROVIDER,
            nativeProviderId = null,
            skippedByRule = libraries.size - allowed.size,
            strippedDesktopNativeArtifacts = desktopNativeArtifacts.size
        )
    }
}

internal fun LibrarySpec.isDesktopNativeArtifact(): Boolean {
    val parts = name.split(':')
    if (parts.size < 4) return false

    val artifact = parts[1].lowercase()
    val classifier = parts[3].substringBefore('@').lowercase()

    if (classifier.startsWith("natives-")) return true

    val platformClassifier =
        classifier == "linux" ||
            classifier.startsWith("linux-") ||
            classifier == "windows" ||
            classifier.startsWith("windows-") ||
            classifier == "osx" ||
            classifier.startsWith("osx-") ||
            classifier == "macos" ||
            classifier.startsWith("macos-")

    return platformClassifier && artifact.contains("native")
}
