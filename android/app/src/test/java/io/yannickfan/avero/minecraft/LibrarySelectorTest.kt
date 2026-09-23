package io.yannickfan.avero.minecraft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySelectorTest {
    private val context = RuleContext(osName = "linux", osArch = "aarch64")

    @Test
    fun androidSelectionStripsModernDesktopNativeArtifacts() {
        val selection = LibrarySelector().select(
            libraries = representativeModernLibraries(),
            context = context
        )

        val names = selection.effective.map { it.name }
        assertTrue(names.contains("org.lwjgl:lwjgl:3.3.3"))
        assertFalse(names.contains("org.lwjgl:lwjgl:3.3.3:natives-linux"))
        assertFalse(names.contains("com.mojang:jtracy:1.0.29:natives-linux"))
        assertFalse(
            names.contains(
                "io.netty:netty-transport-native-epoll:4.1.115.Final:linux-aarch_64"
            )
        )
        assertEquals(3, selection.strippedDesktopNativeArtifacts)
    }

    @Test
    fun explicitDesktopPolicyKeepsDesktopNativeArtifacts() {
        val selection = LibrarySelector().select(
            libraries = representativeModernLibraries(),
            context = context,
            nativeClassifierPolicy = NativeClassifierPolicy.MOJANG_DESKTOP
        )

        assertEquals(4, selection.effective.size)
        assertEquals(0, selection.strippedDesktopNativeArtifacts)
    }


    @Test
    fun nonDesktopNativeClassifierIsPreserved() {
        val selection = LibrarySelector().select(
            libraries = listOf(
                library(
                    "org.lwjgl:lwjgl:3.4.1:natives-android",
                    "org/lwjgl/lwjgl/3.4.1/lwjgl-3.4.1-natives-android.jar"
                )
            ),
            context = context
        )

        assertEquals(1, selection.effective.size)
        assertEquals(0, selection.strippedDesktopNativeArtifacts)
    }

    @Test(expected = IllegalArgumentException::class)
    fun AndroidProviderCannotBeCombinedWithDesktopNativePolicy() {
        LibrarySelector().select(
            libraries = representativeModernLibraries(),
            context = context,
            nativeClassifierPolicy = NativeClassifierPolicy.MOJANG_DESKTOP,
            hasAndroidNativeProvider = true
        )
    }

    private fun representativeModernLibraries(): List<LibrarySpec> {
        val linuxRule = listOf(
            RuleSpec(
                action = RuleAction.ALLOW,
                os = OsRule(name = "linux")
            )
        )

        return listOf(
            library(
                "org.lwjgl:lwjgl:3.3.3",
                "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar"
            ),
            library(
                "org.lwjgl:lwjgl:3.3.3:natives-linux",
                "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar",
                linuxRule
            ),
            library(
                "com.mojang:jtracy:1.0.29:natives-linux",
                "com/mojang/jtracy/1.0.29/jtracy-1.0.29-natives-linux.jar",
                linuxRule
            ),
            library(
                "io.netty:netty-transport-native-epoll:4.1.115.Final:linux-aarch_64",
                "io/netty/netty-transport-native-epoll/4.1.115.Final/" +
                    "netty-transport-native-epoll-4.1.115.Final-linux-aarch_64.jar",
                linuxRule
            )
        )
    }

    private fun library(
        name: String,
        path: String,
        rules: List<RuleSpec> = emptyList()
    ) = LibrarySpec(
        name = name,
        artifact = DownloadSpec(
            url = "https://example.invalid/$path",
            sha1 = null,
            size = 1,
            path = path
        ),
        rules = rules
    )
}
