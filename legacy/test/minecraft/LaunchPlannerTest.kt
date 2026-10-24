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
                    ),
                    natives = mapOf("linux" to "natives-linux")
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

        assertEquals(
            provider.classpathEntries,
            plan.classpathEntries.take(provider.classpathEntries.size)
        )
        assertEquals(provider, plan.androidNativeProvider)
        assertTrue(plan.requiresNatives)
        assertTrue(plan.nativeArchives.isEmpty())
    }
    @Test
    fun androidPlanDoesNotPutSeparateDesktopNativeJarOnClasspath() {
        val dummy = DownloadSpec(
            url = "https://example.invalid/file",
            sha1 = null,
            size = 1
        )
        val linuxRule = listOf(
            RuleSpec(
                action = RuleAction.ALLOW,
                os = OsRule(name = "linux")
            )
        )
        val metadata = MinecraftVersionMetadata(
            id = "1.21.4",
            type = "release",
            mainClass = "net.minecraft.client.main.Main",
            javaMajorVersion = 21,
            client = dummy,
            assetIndexId = "19",
            assetIndex = dummy,
            libraries = listOf(
                LibrarySpec(
                    name = "org.lwjgl:lwjgl:3.3.3",
                    artifact = dummy.copy(
                        path = "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar"
                    )
                ),
                LibrarySpec(
                    name = "org.lwjgl:lwjgl:3.3.3:natives-linux",
                    artifact = dummy.copy(
                        path = "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar"
                    ),
                    rules = linuxRule
                )
            ),
            logging = null,
            gameArguments = emptyList(),
            jvmArguments = emptyList()
        )

        val plan = LaunchPlanner().createVanillaPlan(
            metadata = metadata,
            instance = LauncherInstance("test", metadata.id),
            context = RuleContext(osName = "linux", osArch = "aarch64")
        )

        assertTrue(
            plan.classpathEntries.contains(
                "libraries/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar"
            )
        )
        assertTrue(plan.classpathEntries.none { it.contains("natives-linux") })
        assertTrue(plan.requiresNatives)
    }

}
