package io.yannickfan.avero.minecraft

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchPlannerTest {
    private val tokenPrefix = 36.toChar().toString() + "{"

    @Test
    fun resolvesConditionalArgumentsAndPlaceholders() {
        val metadata = baseMetadata(
            gameArguments = listOf(
                ConditionalArgument(listOf("--username", tokenPrefix + "auth_player_name}")),
                ConditionalArgument(
                    values = listOf("--width", tokenPrefix + "resolution_width}"),
                    rules = listOf(
                        RuleSpec(
                            action = RuleAction.ALLOW,
                            features = mapOf("has_custom_resolution" to true)
                        )
                    )
                )
            ),
            jvmArguments = listOf(
                ConditionalArgument(
                    listOf("-Djava.library.path=" + tokenPrefix + "natives_directory}")
                ),
                ConditionalArgument(
                    values = listOf("-Dos=windows"),
                    rules = listOf(
                        RuleSpec(
                            action = RuleAction.ALLOW,
                            os = OsRule(name = "windows")
                        )
                    )
                ),
                ConditionalArgument(
                    values = listOf("-Dos=linux"),
                    rules = listOf(
                        RuleSpec(
                            action = RuleAction.ALLOW,
                            os = OsRule(name = "linux")
                        )
                    )
                )
            )
        )
        val instance = LauncherInstance("Test", metadata.id, memoryMb = 2048)
        val launchContext = LaunchContext(
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
        val ruleContext = RuleContext(
            osName = "linux",
            osVersion = "6.1",
            osArch = "aarch64",
            features = launchContext.ruleFeatures()
        )

        val plan = LaunchPlanner().createVanillaPlan(
            metadata = metadata,
            instance = instance,
            launchContext = launchContext,
            context = ruleContext
        )

        assertTrue(plan.gameArguments.contains("Alex"))
        assertTrue(plan.gameArguments.contains("1280"))
        assertTrue(plan.jvmArguments.contains("-Djava.library.path=/data/natives"))
        assertTrue(plan.jvmArguments.contains("-Dos=linux"))
        assertFalse(plan.jvmArguments.contains("-Dos=windows"))
        assertFalse((plan.jvmArguments + plan.gameArguments).any { it.contains(tokenPrefix) })
    }

    @Test
    fun rejectsUnknownLauncherVariable() {
        val metadata = baseMetadata(
            gameArguments = listOf(
                ConditionalArgument(listOf(tokenPrefix + "unknown_variable}"))
            )
        )
        val instance = LauncherInstance("Test", metadata.id)

        val error = assertThrows(IllegalArgumentException::class.java) {
            LaunchPlanner().createVanillaPlan(metadata, instance)
        }

        assertTrue(error.message.orEmpty().contains("unknown_variable"))
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
    fun resolvesNativeClassifierArchToken() {
        val native = DownloadSpec(
            url = "https://example.invalid/native.jar",
            sha1 = null,
            size = 1,
            path = "native.jar"
        )
        val library = LibrarySpec(
            name = "org.lwjgl:lwjgl:3",
            artifact = null,
            classifiers = mapOf("natives-linux-64" to native),
            natives = mapOf("linux" to "natives-linux-" + tokenPrefix + "arch}")
        )

        val resolved = NativeLibraryResolver().resolve(
            listOf(library),
            RuleContext("linux", osArch = "aarch64")
        )

        assertEquals("natives-linux-64", resolved.single().classifier)
    }

    private fun baseMetadata(
        gameArguments: List<ConditionalArgument> = emptyList(),
        jvmArguments: List<ConditionalArgument> = emptyList()
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
                name = "example:library:1.0",
                artifact = DownloadSpec(
                    url = "https://example.invalid/library.jar",
                    sha1 = null,
                    size = 1,
                    path = "example/library/1.0/library-1.0.jar"
                )
            )
        ),
        gameArguments = gameArguments,
        jvmArguments = jvmArguments
    )
}
