package io.yannickfan.avero.minecraft

object MinecraftPlatform {
    fun androidRuleContext(
        features: Map<String, Boolean> = emptyMap()
    ): RuleContext = RuleContext(
        // Mojang metadata has no Android OS token. Linux rules are the closest
        // metadata compatibility bucket for generic Java libraries. Desktop
        // native classifiers are planned separately and are not assumed to be
        // Android-compatible.
        osName = "linux",
        osVersion = System.getProperty("os.version") ?: "",
        osArch = System.getProperty("os.arch") ?: "unknown",
        features = features
    )

    fun classifierArchToken(arch: String): String =
        if (arch.contains("64")) "64" else "32"
}
