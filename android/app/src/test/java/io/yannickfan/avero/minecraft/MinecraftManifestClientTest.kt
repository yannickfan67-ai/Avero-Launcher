package io.yannickfan.avero.minecraft

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MinecraftManifestClientTest {
    @Test
    fun preservesQuotedLegacyArguments() {
        val root = JSONObject(
            """
            {
              "id": "legacy",
              "type": "old_alpha",
              "mainClass": "net.minecraft.client.Minecraft",
              "minecraftArguments": "--username Steve --gameDir \"My World\" --server 'example server'",
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
            listOf(
                "--username",
                "Steve",
                "--gameDir",
                "My World",
                "--server",
                "example server"
            ),
            metadata.gameArguments.flatMap { it.values }
        )
    }

    @Test
    fun rejectsUnterminatedLegacyQuote() {
        val root = JSONObject(
            """
            {
              "id": "legacy",
              "type": "old_alpha",
              "mainClass": "net.minecraft.client.Minecraft",
              "minecraftArguments": "--gameDir \"Broken World",
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

        assertThrows(IllegalArgumentException::class.java) {
            MinecraftManifestClient.parseVersion(root)
        }
    }
}
