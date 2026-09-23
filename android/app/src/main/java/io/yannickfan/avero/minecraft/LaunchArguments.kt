package io.yannickfan.avero.minecraft

enum class RuleAction {
    ALLOW,
    DISALLOW
}

data class OperatingSystemRule(
    val name: String? = null,
    val version: String? = null,
    val arch: String? = null
)

data class MinecraftArgumentRule(
    val action: RuleAction,
    val os: OperatingSystemRule? = null,
    val features: Map<String, Boolean> = emptyMap()
)

data class MinecraftArgument(
    val values: List<String>,
    val rules: List<MinecraftArgumentRule> = emptyList()
) {
    init {
        require(values.isNotEmpty()) { "Minecraft argument must contain at least one value" }
    }
}

data class LaunchContext(
    val playerName: String,
    val uuid: String,
    val accessToken: String,
    val clientId: String = "",
    val xuid: String = "",
    val userType: String = "msa",
    val userProperties: String = "{}",
    val gameDirectory: String,
    val assetsRoot: String,
    val nativesDirectory: String,
    val librariesDirectory: String,
    val launcherName: String = "Avero",
    val launcherVersion: String = "0.1.0",
    val resolutionWidth: Int? = null,
    val resolutionHeight: Int? = null,
    val features: Map<String, Boolean> = emptyMap()
) {
    companion object {
        fun preview(
            metadata: MinecraftVersionMetadata,
            instance: LauncherInstance,
            root: String = "."
        ): LaunchContext {
            val safeName = instance.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            return LaunchContext(
                playerName = "Player",
                uuid = "00000000000000000000000000000000",
                accessToken = "0",
                gameDirectory = root + "/instances/" + safeName + "/game",
                assetsRoot = root + "/assets",
                nativesDirectory = root + "/versions/" + metadata.id + "/natives",
                librariesDirectory = root + "/libraries"
            )
        }
    }
}

data class LaunchEnvironment(
    val osName: String,
    val osVersion: String,
    val architecture: String,
    val features: Map<String, Boolean>
) {
    companion object {
        fun android(
            context: LaunchContext,
            architecture: String = System.getProperty("os.arch") ?: "unknown",
            osVersion: String = System.getProperty("os.version") ?: ""
        ): LaunchEnvironment {
            val normalizedArch = when {
                architecture.contains("aarch64", ignoreCase = true) ||
                    architecture.contains("arm64", ignoreCase = true) -> "arm64"
                architecture.contains("x86_64", ignoreCase = true) ||
                    architecture.contains("amd64", ignoreCase = true) -> "x86_64"
                architecture.contains("x86", ignoreCase = true) -> "x86"
                else -> architecture.lowercase()
            }

            val featureMap = buildMap {
                putAll(context.features)
                if (context.resolutionWidth != null && context.resolutionHeight != null) {
                    put("has_custom_resolution", true)
                }
            }

            return LaunchEnvironment(
                osName = "linux",
                osVersion = osVersion,
                architecture = normalizedArch,
                features = featureMap
            )
        }
    }
}

class MojangRuleEvaluator {
    fun isAllowed(
        rules: List<MinecraftArgumentRule>,
        environment: LaunchEnvironment
    ): Boolean {
        if (rules.isEmpty()) return true

        var allowed = false
        rules.forEach { rule ->
            if (matches(rule, environment)) {
                allowed = rule.action == RuleAction.ALLOW
            }
        }
        return allowed
    }

    private fun matches(
        rule: MinecraftArgumentRule,
        environment: LaunchEnvironment
    ): Boolean {
        rule.os?.let { os ->
            if (os.name != null && !os.name.equals(environment.osName, ignoreCase = true)) {
                return false
            }
            if (os.version != null && !matchesPattern(os.version, environment.osVersion)) {
                return false
            }
            if (os.arch != null && !matchesPattern(os.arch, environment.architecture)) {
                return false
            }
        }

        return rule.features.all { (feature, expected) ->
            (environment.features[feature] ?: false) == expected
        }
    }

    private fun matchesPattern(pattern: String, value: String): Boolean =
        runCatching { Regex(pattern).containsMatchIn(value) }
            .getOrElse { pattern.equals(value, ignoreCase = true) }
}

class PlaceholderResolver(
    private val values: Map<String, String>
) {
    private val pattern = Regex("""\$\{([^}]+)}""")

    fun resolve(value: String): String =
        pattern.replace(value) { match ->
            val key = match.groupValues[1]
            values[key] ?: throw IllegalArgumentException(
                "Unresolved launcher variable: " + key
            )
        }
}
