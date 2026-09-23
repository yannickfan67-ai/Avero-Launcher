package io.yannickfan.avero.minecraft

import io.yannickfan.avero.androidnative.AndroidNativeProviderCatalog
import io.yannickfan.avero.runtime.RuntimeArch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchPlannerTest {
    @Test
    fun androidProviderClasspathComesBeforeMojangLibraries() {
        val dummy = DownloadSpec(
            url = "https://example.invalid/file",
            sha1 = null,
            size = 1
        )
        val metadata = MinecraftVersionMetadata(
            id = "1.test",
            type = "release",
            mainClass = "net.minecraft.client.main.Main",
            javaMajorVersion = 21,
            client = dummy,
            assetIndexId = "test",
            assetIndex = dummy,
            libraries = listOf(
                LibrarySpec(
                    name = "org.lwjgl:lwjgl:3.4.1",
                    artifact = dummy.copy(
                        path = "libraries/org/lwjgl/lwjgl/3.4.1/lwjgl-3.4.1.jar"
                    )
                )
            ),
            logging = null,
            gameArguments = emptyList(),
            jvmArguments = emptyList()
        )
        val providerPackage = requireNotNull(
            AndroidNativeProviderCatalog.detect(metadata, RuntimeArch.ARM64)
        )
        val provider = AndroidNativeProviderCatalog.plan(providerPackage)

        val plan = LaunchPlanner().createVanillaPlan(
            metadata = metadata,
            instance = LauncherInstance(
                name = "test",
                versionId = metadata.id
            ),
            context = RuleContext(
                osName = "linux",
                osArch = "aarch64"
            ),
            androidNativeProvider = provider
        )

        assertEquals(provider.classpathEntry, plan.classpathEntries.first())
        assertEquals(provider, plan.androidNativeProvider)
        assertTrue(plan.nativeArchives.isEmpty())
    }
}
