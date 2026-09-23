package io.yannickfan.avero.minecraft

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchPlannerTest {
    private val variablePrefix = 36.toChar().toString() + "{"

    @Test
    fun resolvesRulesAndPlaceholdersForAndroid() {
        val metadata = baseMetadata(
            gameArguments = listOf(
                MinecraftArgument(listOf("--username", variablePrefix + "auth_player_name}")),
                MinecraftArgument(
                    values = listOf("--width", variablePrefix + "resolution_width}"),
                    rules = listOf(
                        MinecraftArgumentRule(
                            action = RuleAction.ALLOW,
                            features = mapOf("has_custom_resolution" to true)
                        )
                    )
                )
            ),
            jvmArguments = listOf(
                MinecraftArgument(listOf("-Djava.library.path=" + variablePrefix + "natives_directory}")),
                MinecraftArgument(
                    values = listOf("-Dos=windows"),
                    rules = listOf(
                        MinecraftArgumentRule(
                            action = RuleAction.ALLOW,
                            os = OperatingSystemRule(name = "windows")
                        )
                    )
                ),
                MinecraftArgument(
                    values = listOf("-Dos=linux"),
                    rules = listOf(
                        MinecraftArgumentRule(
                            action = RuleAction.ALLOW,
                            os = OperatingSystemRule(name = "linux", arch = "arm64")
                        )
                    )
                )
            )
        )
        val instance = LauncherInstance("Test instance", "1.21.4", memoryMb = 2048)
        val context = LaunchContext(
            playerName = "Alex",
            uuid = "0123456789abcdef0123456789abcdef",
            accessToken = "token",
            gameDirectory = "/data/game",
            assetsRoot = "/data/assets",
            nativesDirectory = "/data/natives",
            librariesDirectory = "/data/libraries",
            resolutionWidth = 1280,
            resolutionHeight = 720
        )
        val environment = LaunchEnvironment(
            osName = "linux",
            osVersion = "6.1",
            architecture = "arm64",
            features = mapOf("has_custom_resolution" to true)
        )

        val plan = LaunchPlanner().createVanillaPlan(metadata, instance, context, environment)

        assertTrue(plan.gameArguments.contains("Alex"))
        assertTrue(plan.gameArguments.contains("1280"))
        assertTrue(plan.jvmArguments.contains("-Djava.library.path=/data/natives"))
        assertTrue(plan.jvmArguments.contains("-Dos=linux"))
        assertFalse(plan.jvmArguments.contains("-Dos=windows"))
        assertFalse((plan.jvmArguments + plan.gameArguments).any { it.contains(variablePrefix) })
    }

    @Test
    fun parserRetainsConditionalArrayValues() {
        val root = JSONObject(
            """
            {
              "id": "test-modern",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "downloads": {
                "client": {"url": "https://example.invalid/client.jar", "size": 1}
              },
              "assetIndex": {
                "id": "test-assets",
                "url": "https://example.invalid/assets.json",
                "size": 1
              },
              "arguments": {
                "game": [
                  "--demo",
                  {
                    "rules": [{"action": "allow", "features": {"has_custom_resolution": true}}],
                    "value": ["--width", "1280"]
                  }
                ],
                "jvm": []
              }
            }
            """.trimIndent()
        )

        val metadata = MinecraftManifestClient.parseVersion(root)

        assertEquals(2, metadata.gameArguments.size)
        assertEquals(listOf("--width", "1280"), metadata.gameArguments[1].values)
        assertEquals(RuleAction.ALLOW, metadata.gameArguments[1].rules.single().action)
        assertEquals(true, metadata.gameArguments[1].rules.single().features["has_custom_resolution"])
    }

    @Test
    fun parsesLegacyQuotedArguments() {
        val root = JSONObject(
            """
            {
              "id": "legacy",
              "type": "old_alpha",
              "mainClass": "net.minecraft.client.Minecraft",
              "minecraftArguments": "--username Steve --gameDir \"My World\"",
              "downloads": {
                "client": {"url": "https://example.invalid/client.jar", "size": 1}
              },
              "assetIndex": {
                "id": "legacy",
                "url": "https://example.invalid/assets.json",
                "size": 1
              }
            }
            """.trimIndent()
        )

        val metadata = MinecraftManifestClient.parseVersion(root)

        assertEquals(
            listOf("--username", "Steve", "--gameDir", "My World"),
            metadata.gameArguments.flatMap { it.values }
        )
    }

    @Test
    fun failsClearlyOnUnknownLauncherVariable() {
        val metadata = baseMetadata(
            gameArguments = listOf(
                MinecraftArgument(listOf(variablePrefix + "missing_variable}"))
            )
        )
        val instance = LauncherInstance("Test", metadata.id)
        val context = LaunchContext.preview(metadata, instance)

        val error = assertThrows(IllegalArgumentException::class.java) {
            LaunchPlanner().createVanillaPlan(metadata, instance, context)
        }

        assertTrue(error.message.orEmpty().contains("missing_variable"))
    }

    private fun baseMetadata(
        gameArguments: List<MinecraftArgument> = emptyList(),
        jvmArguments: List<MinecraftArgument> = emptyList()
    ) = MinecraftVersionMetadata(
        id = "1.21.4",
        type = "release",
        mainClass = "net.minecraft.client.main.Main",
        javaMajorVersion = 21,
        client = DownloadSpec("https://example.invalid/client.jar", null, 1),
        assetIndexId = "17",
        assetIndex = DownloadSpec("https://example.invalid/assets.json", null, 1),
        libraries = listOf(
            LibrarySpec(
                "example:library:1.0",
                DownloadSpec(
                    "https://example.invalid/library.jar",
                    null,
                    1,
                    "example/library/1.0/library-1.0.jar"
                )
            )
        ),
        gameArguments = gameArguments,
        jvmArguments = jvmArguments
    )
}
