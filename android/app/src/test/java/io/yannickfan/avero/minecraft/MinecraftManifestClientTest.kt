package io.yannickfan.avero.minecraft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MinecraftManifestClientTest {
    @Test
    fun legacyArgumentsKeepQuotedValuesTogether() {
        assertEquals(
            listOf("--username", "Legacy Player", "--gameDir", "My World"),
            MinecraftManifestClient.tokenizeLegacyArguments(
                "--username \"Legacy Player\" --gameDir 'My World'"
            )
        )
    }

    @Test
    fun legacyArgumentsSupportEscapedWhitespace() {
        assertEquals(
            listOf("--gameDir", "My World", "--version", "1.12.2"),
            MinecraftManifestClient.tokenizeLegacyArguments(
                "--gameDir My\\ World --version 1.12.2"
            )
        )
    }

    @Test
    fun legacyArgumentsRejectUnterminatedQuotes() {
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftManifestClient.tokenizeLegacyArguments(
                "--gameDir \"Broken World"
            )
        }
    }
}
