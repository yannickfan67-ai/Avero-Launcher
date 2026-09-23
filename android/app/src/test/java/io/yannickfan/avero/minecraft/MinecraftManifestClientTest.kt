package io.yannickfan.avero.minecraft

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MinecraftManifestClientTest {
    @Test
    fun preservesQuotedLegacyArguments() {
        val metadata = MinecraftManifestClient.parseVersion(
            JSONObject(
                """
                {
                  "id": "legacy",
                  "type": "old_alpha",
                  "mainClass": "net.minecraft.client.Minecraft",
                  "minecraftArguments": "--username Steve --gameDir \"My World\" --demo",
                  "downloads": {
                    "client": {
                      "url": "https://example.invalid/client.jar",
                      "size": 1
                    }
                  },
                  "assetIndex": {
                    "id": "legacy",
                    "url": "https://example.invalid/assets.json",
                    "size": 1
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(
            listOf("--username", "Steve", "--gameDir", "My World", "--demo"),
            metadata.gameArguments.flatMap { it.values }
        )
    }

    @Test
    fun rejectsUnterminatedLegacyQuote() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            MinecraftManifestClient.parseVersion(
                JSONObject(
                    """
                    {
                      "id": "legacy",
                      "type": "old_alpha",
                      "mainClass": "net.minecraft.client.Minecraft",
                      "minecraftArguments": "--gameDir \"Broken World",
                      "downloads": {
                        "client": {
                          "url": "https://example.invalid/client.jar",
                          "size": 1
                        }
                      },
                      "assetIndex": {
                        "id": "legacy",
                        "url": "https://example.invalid/assets.json",
                        "size": 1
                      }
                    }
                    """.trimIndent()
                )
            )
        }

        assertEquals("Unterminated quote in legacy minecraftArguments", error.message)
    }
}
