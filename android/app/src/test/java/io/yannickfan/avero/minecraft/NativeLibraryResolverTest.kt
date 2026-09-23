package io.yannickfan.avero.minecraft

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeLibraryResolverTest {
    private fun variable(name: String): String =
        36.toChar().toString() + "{" + name + "}"

    @Test
    fun resolvesArchPlaceholderInClassifier() {
        val download = DownloadSpec(
            url = "https://example.invalid/native.jar",
            sha1 = "abc",
            size = 123,
            path = "native.jar"
        )
        val library = LibrarySpec(
            name = "example:native:1",
            artifact = null,
            classifiers = mapOf("natives-linux-64" to download),
            natives = mapOf("linux" to "natives-linux-" + variable("arch"))
        )

        val result = NativeLibraryResolver().resolve(
            listOf(library),
            RuleContext(osName = "linux", osArch = "aarch64"),
            NativeClassifierPolicy.MOJANG_DESKTOP
        )

        assertEquals(1, result.size)
        assertEquals("natives-linux-64", result.single().classifier)
    }

    @Test
    fun desktopClassifiersAreDisabledByDefaultForAndroidPlanning() {
        val download = DownloadSpec(
            url = "https://example.invalid/native.jar",
            sha1 = "abc",
            size = 123,
            path = "native.jar"
        )
        val library = LibrarySpec(
            name = "example:native:1",
            artifact = null,
            classifiers = mapOf("natives-linux-64" to download),
            natives = mapOf("linux" to "natives-linux-" + variable("arch"))
        )

        val result = NativeLibraryResolver().resolve(
            listOf(library),
            MinecraftPlatform.androidRuleContext()
        )

        assertEquals(emptyList<NativeArchivePlan>(), result)
    }
}
