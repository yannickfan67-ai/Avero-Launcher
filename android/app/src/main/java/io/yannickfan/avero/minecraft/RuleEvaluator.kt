package io.yannickfan.avero.minecraft

class RuleEvaluator {
    fun isAllowed(rules: List<RuleSpec>, context: RuleContext): Boolean {
        if (rules.isEmpty()) return true

        var allowed = false
        for (rule in rules) {
            if (matches(rule, context)) {
                allowed = rule.action == RuleAction.ALLOW
            }
        }
        return allowed
    }

    fun resolveArguments(
        entries: List<ConditionalArgument>,
        context: RuleContext
    ): List<String> = buildList {
        for (entry in entries) {
            if (isAllowed(entry.rules, context)) {
                addAll(entry.values)
            }
        }
    }

    private fun matches(rule: RuleSpec, context: RuleContext): Boolean {
        val os = rule.os
        if (os != null) {
            if (os.name != null && os.name != context.osName) return false
            if (os.versionRegex != null && !regexMatches(os.versionRegex, context.osVersion)) return false
            if (os.archRegex != null && !regexMatches(os.archRegex, context.osArch)) return false
        }

        for ((feature, expected) in rule.features) {
            if ((context.features[feature] ?: false) != expected) return false
        }

        return true
    }

    private fun regexMatches(pattern: String, value: String): Boolean =
        runCatching { Regex(pattern).containsMatchIn(value) }.getOrDefault(false)
}
