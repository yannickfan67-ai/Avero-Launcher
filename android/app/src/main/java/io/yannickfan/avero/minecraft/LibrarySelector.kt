package io.yannickfan.avero.minecraft

data class LibrarySelection(
    val ruleAllowed: List<LibrarySpec>,
    val effective: List<LibrarySpec>,
    val skippedByRule: Int,
    val strippedDesktopNativeArtifacts: Int
)

class LibrarySelector(
    private val rules: RuleEvaluator = RuleEvaluator()
) {
    fun select(
        libraries: List<LibrarySpec>,
        context: RuleContext,
        nativeClassifierPolicy: NativeClassifierPolicy = NativeClassifierPolicy.DISABLED,
        hasAndroidNativeProvider: Boolean = false
    ): LibrarySelection {
        require(
            !hasAndroidNativeProvider ||
                nativeClassifierPolicy == NativeClassifierPolicy.DISABLED
        ) {
            "Use either an Android native provider or Mojang desktop classifiers, not both"
        }

        val ruleAllowed = libraries.filter { rules.isAllowed(it.rules, context) }
        val keepDesktopNativeArtifacts =
            nativeClassifierPolicy == NativeClassifierPolicy.MOJANG_DESKTOP &&
                !hasAndroidNativeProvider

        val effective =
            if (keepDesktopNativeArtifacts) {
                ruleAllowed
            } else {
                ruleAllowed.filterNot { it.isDesktopNativeArtifact() }
            }

        return LibrarySelection(
            ruleAllowed = ruleAllowed,
            effective = effective,
            skippedByRule = libraries.size - ruleAllowed.size,
            strippedDesktopNativeArtifacts = ruleAllowed.size - effective.size
        )
    }
}

internal fun LibrarySpec.isDesktopNativeArtifact(): Boolean {
    val parts = name.split(':')
    if (parts.size < 4) return false

    val artifact = parts[1].lowercase()
    val classifier = parts[3].substringBefore('@').lowercase()

    fun isDesktopPlatform(value: String): Boolean =
        value == "linux" ||
            value.startsWith("linux-") ||
            value == "windows" ||
            value.startsWith("windows-") ||
            value == "osx" ||
            value.startsWith("osx-") ||
            value == "macos" ||
            value.startsWith("macos-")

    if (classifier.startsWith("natives-")) {
        return isDesktopPlatform(classifier.removePrefix("natives-"))
    }

    return isDesktopPlatform(classifier) && artifact.contains("native")
}
