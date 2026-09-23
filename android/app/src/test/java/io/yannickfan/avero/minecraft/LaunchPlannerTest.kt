package io.yannickfan.avero.minecraft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchPlannerTest {
    @Test
    fun ruleEvaluatorUsesLastMatchingRule() {
        val argument = MinecraftArgument(
            values = listOf("--demo"),
            rules = listOf(
                ArgumentRule(
                    action = RuleAction.ALLOW,
                    os = RuleOs(name = "linux")
                ),
                ArgumentRule(
                    action = RuleAction.DISALLOW,
                    features = mapOf("is_demo_user" to true)
                )
            )
        )

        assertFalse(
            MojangRuleEvaluator.isAllowed(
                argument,
                RuleContext(
                    osName = "linux",
                    osArch = "aarch64",
                    osVersion = "6.1",
                    features = mapOf("is_demo_user" to true)
                )
            )
        )

        assertTrue(
            MojangRuleEvaluator.isAllowed(
                argument,
                RuleContext(
                    osName = "linux",
                    osArch = "aarch64",
                    osVersion = "6.1",
                    features = mapOf("is_demo_user" to false)
                )
            )
        )
    }

    @Test
    fun plannerResolvesModernConditionalArgumentsAndPlaceholders() {
        val conditionalResolution = MinecraftArgument(
            values = listOf(
                "--width",
                "\${resolution_width}",
                "--height",
                "\${resolution_height}"
            ),
            rules = listOf(
                ArgumentRule(
                    action = RuleAction.ALLOW,
                    features = mapOf("has_custom_resolution" to true)
                )
            )
        )

        val metadata = MinecraftVersionMetadata(
            id = "1.21.4",
            type = "release",
            mainClass = "net.minecraft.client.main.Main",
            javaMajorVersion = 21,
            client = DownloadSpec(
                url = "https://example.invalid/client.jar",
                sha1 = null,
                size = null
            ),
            assetIndexId = "19",
            assetIndex = DownloadSpec(
                url = "https://example.invalid/assets.json",
                sha1 = null,
                size = null
            ),
            libraries = listOf(
                LibrarySpec(
                    name = "example:library:1.0",
                    artifact = DownloadSpec(
                        url = "https://example.invalid/library.jar",
                        sha1 = null,
                        size = null,
                        path = "example/library/1.0/library-1.0.jar"
                    )
                )
            ),
            gameArguments = listOf(
                MinecraftArgument(listOf("--username")),
                MinecraftArgument(listOf("\${auth_player_name}")),
                conditionalResolution
            ),
            jvmArguments = listOf(
                MinecraftArgument(listOf("-Djava.library.path=\${natives_directory}")),
                MinecraftArgument(listOf("-cp")),
                MinecraftArgument(listOf("\${classpath}"))
            )
        )

        val plan = LaunchPlanner().createVanillaPlan(
            metadata = metadata,
            instance = LauncherInstance(
                name = "Test",
                versionId = metadata.id,
                memoryMb = 2048
            ),
            context = LaunchContext(
                rootDirectory = "/data/minecraft",
                gameDirectory = "/data/minecraft/instances/test/game",
                nativesDirectory = "/data/minecraft/natives",
                assetsRoot = "/data/minecraft/assets",
                librariesDirectory = "/data/minecraft/libraries",
                username = "Alex",
                uuid = "0123456789abcdef0123456789abcdef",
                accessToken = "token",
                resolutionWidth = 1280,
                resolutionHeight = 720,
                ruleContext = RuleContext(
                    osName = "linux",
                    osArch = "aarch64",
                    osVersion = "6.1"
                )
            )
        )

        assertEquals(
            listOf("--username", "Alex", "--width", "1280", "--height", "720"),
            plan.gameArguments
        )
        assertTrue(plan.jvmArguments.contains("-Djava.library.path=/data/minecraft/natives"))
        assertTrue(
            plan.jvmArguments.any {
                it.contains("/data/minecraft/libraries/example/library/1.0/library-1.0.jar")
            }
        )
        assertTrue(
            LauncherPlaceholderResolver.unresolved(
                plan.jvmArguments + plan.gameArguments
            ).isEmpty()
        )
    }

    @Test
    fun legacyArgumentsKeepQuotedValuesTogether() {
        assertEquals(
            listOf("--username", "Legacy Player", "--version", "1.12.2"),
            MinecraftManifestClient.splitLegacyArguments(
                "--username \"Legacy Player\" --version 1.12.2"
            )
        )
    }
}
