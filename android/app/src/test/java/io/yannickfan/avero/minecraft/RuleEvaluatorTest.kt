package io.yannickfan.avero.minecraft

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEvaluatorTest {
    private val evaluator = RuleEvaluator()
    private val linuxArm64 = RuleContext(
        osName = "linux",
        osVersion = "6.1",
        osArch = "aarch64"
    )

    @Test
    fun noRulesAreAllowed() {
        assertTrue(evaluator.isAllowed(emptyList(), linuxArm64))
    }

    @Test
    fun matchingAllowRuleIsAllowed() {
        val rules = listOf(
            RuleSpec(
                action = RuleAction.ALLOW,
                os = OsRule(name = "linux")
            )
        )
        assertTrue(evaluator.isAllowed(rules, linuxArm64))
    }

    @Test
    fun nonMatchingRuleLeavesRestrictedItemDisallowed() {
        val rules = listOf(
            RuleSpec(
                action = RuleAction.ALLOW,
                os = OsRule(name = "windows")
            )
        )
        assertFalse(evaluator.isAllowed(rules, linuxArm64))
    }

    @Test
    fun laterMatchingRuleOverridesEarlierRule() {
        val rules = listOf(
            RuleSpec(action = RuleAction.ALLOW),
            RuleSpec(
                action = RuleAction.DISALLOW,
                os = OsRule(archRegex = "aarch64")
            )
        )
        assertFalse(evaluator.isAllowed(rules, linuxArm64))
    }

    @Test
    fun featureRuleMustMatch() {
        val rules = listOf(
            RuleSpec(
                action = RuleAction.ALLOW,
                features = mapOf("is_demo_user" to true)
            )
        )
        assertFalse(evaluator.isAllowed(rules, linuxArm64))
        assertTrue(
            evaluator.isAllowed(
                rules,
                linuxArm64.copy(features = mapOf("is_demo_user" to true))
            )
        )
    }
}
