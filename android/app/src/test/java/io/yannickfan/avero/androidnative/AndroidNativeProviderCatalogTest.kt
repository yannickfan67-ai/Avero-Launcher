package io.yannickfan.avero.androidnative

import io.yannickfan.avero.minecraft.ConditionalArgument
import io.yannickfan.avero.minecraft.DownloadSpec
import io.yannickfan.avero.minecraft.LibrarySpec
import io.yannickfan.avero.minecraft.MinecraftVersionMetadata
import io.yannickfan.avero.runtime.RuntimeArch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidNativeProviderCatalogTest {
    @Test
    fun detectsPinnedLwjgl341Provider() {
        val pkg = AndroidNativeProviderCatalog.detect(
            metadata("3.4.1"),
            RuntimeArch.ARM64
        )

        assertNotNull(pkg)
        assertEquals("3.4.1", pkg?.lwjglVersion)
        assertEquals(
            "523e976c9fa282a63407e39fbe8d7c846efe0778",
            pkg?.nativeArchive?.gitBlobSha1
        )
        assertEquals(
            AndroidNativeProviderPackage.UPSTREAM_COMMIT,
            pkg?.upstreamCommit
        )
        assertEquals(14, pkg?.javaComponents?.size)
        assertEquals(
            "f77c2f24ed0f8034d98195a3e2092dfc5e64d293",
            pkg?.javaComponents
                ?.firstOrNull { it.fileName == "lwjgl.jar" }
                ?.download
                ?.gitBlobSha1
        )
    }

    @Test
    fun unsupportedLwjglVersionIsRejected() {
        assertNull(
            AndroidNativeProviderCatalog.detect(
                metadata("3.5.0"),
                RuntimeArch.ARM64
            )
        )
    }

    private fun metadata(lwjglVersion: String): MinecraftVersionMetadata {
        val dummy = DownloadSpec(
            url = "https://example.invalid/file",
            sha1 = null,
            size = 1
        )
        return MinecraftVersionMetadata(
            id = "test",
            type = "release",
            mainClass = "net.minecraft.client.main.Main",
            javaMajorVersion = 21,
            client = dummy,
            assetIndexId = "test",
            assetIndex = dummy,
            libraries = listOf(
                LibrarySpec(
                    name = "org.lwjgl:lwjgl:$lwjglVersion",
                    artifact = dummy.copy(path = "libraries/lwjgl.jar")
                )
            ),
            logging = null,
            gameArguments = emptyList<ConditionalArgument>(),
            jvmArguments = emptyList<ConditionalArgument>()
        )
    }
    @Test
    fun pinnedGitBlobHashesAreFullSha1Values() {
        for (version in listOf("3.3.3", "3.4.1")) {
            val pkg = requireNotNull(
                AndroidNativeProviderCatalog.find(version, RuntimeArch.ARM64)
            )
            val pins = buildList {
                add(pkg.nativeArchive.gitBlobSha1)
                addAll(pkg.javaComponents.map { it.download.gitBlobSha1 })
            }

            assertEquals(true, pins.isNotEmpty())
            pins.forEach { pin ->
                assertNotNull(pin)
                assertEquals(40, pin!!.length)
                assertEquals(true, pin.matches(Regex("[0-9a-f]{40}")))
            }
        }
    }

}
