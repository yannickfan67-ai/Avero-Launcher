package io.yannickfan.avero.minecraft

object MojangRuleEvaluator {
    fun isAllowed(argument: MinecraftArgument, context: RuleContext): Boolean =
        isAllowed(argument.rules, context)

    fun isAllowed(rules: List<ArgumentRule>, context: RuleContext): Boolean {
        if (rules.isEmpty()) return true

        var allowed = false
        for (rule in rules) {
            if (matches(rule, context)) {
                allowed = rule.action == RuleAction.ALLOW
            }
        }
        return allowed
    }

    private fun matches(rule: ArgumentRule, context: RuleContext): Boolean {
        val osMatches = rule.os?.let { os ->
            (os.name == null || os.name.equals(context.osName, ignoreCase = true)) &&
                (os.arch == null || patternMatches(os.arch, context.osArch)) &&
                (os.version == null || patternMatches(os.version, context.osVersion))
        } ?: true

        if (!osMatches) return false

        return rule.features.all { (name, expected) ->
            (context.features[name] ?: false) == expected
        }
    }

    private fun patternMatches(pattern: String, value: String): Boolean =
        try {
            Regex(pattern).matches(value)
        } catch (_: IllegalArgumentException) {
            pattern.equals(value, ignoreCase = true)
        }
}
