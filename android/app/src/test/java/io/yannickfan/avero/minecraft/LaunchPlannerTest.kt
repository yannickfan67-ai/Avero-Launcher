package io.yannickfan.avero.minecraft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchPlannerTest {
    private fun variable(name: String): String =
        36.toChar().toString() + "{" + name + "}"

    @Test
    fun nativeMetadataWithoutAndroidProviderIsBlocked() {
        val plan = LaunchPlanner().createVanillaPlan(
            metadata = metadata(nativeLibrary()),
            instance = instance()
        )

        assertEquals(
            NativePlanState.MISSING_ANDROID_PROVIDER,
            plan.nativeState
        )
        assertTrue(plan.nativeArchives.isEmpty())
        assertTrue(plan.classpathEntries.contains("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar"))
    }

    @Test
    fun androidProviderCanReplaceClasspathAndNativeArchives() {
        val replacement = LibrarySpec(
            name = "org.lwjgl:lwjgl:3.3.3",
            artifact = DownloadSpec(
                url = "https://example.invalid/android-lwjgl.jar",
                sha1 = "abc",
                size = 100,
                path = "android/lwjgl-3.3.3.jar"
            )
        )
        val archive = NativeArchivePlan(
            libraryName = replacement.name,
            classifier = "android-arm64",
            download = DownloadSpec(
                url = "https://example.invalid/android-native.jar",
                sha1 = "def",
                size = 200,
                path = "android/lwjgl-native-arm64.jar"
            ),
            extractExcludes = emptyList()
        )
        val provider = AndroidNativeProvider { _, _ ->
            AndroidNativeProviderResolution(
                providerId = "test-provider",
                libraries = listOf(replacement),
                nativeArchives = listOf(archive)
            )
        }

        val plan = LaunchPlanner().createVanillaPlan(
            metadata = metadata(nativeLibrary()),
            instance = instance(),
            androidNativeProvider = provider
        )

        assertEquals(NativePlanState.READY, plan.nativeState)
        assertEquals("test-provider", plan.nativeProviderId)
        assertEquals(listOf(archive), plan.nativeArchives)
        assertTrue(plan.classpathEntries.contains("android/lwjgl-3.3.3.jar"))
        assertFalse(
            plan.classpathEntries.contains(
                "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar"
            )
        )
    }

    @Test
    fun providerAndDesktopClassifierPolicyCannotBeCombined() {
        var failed = false
        try {
            LaunchPlanner().createVanillaPlan(
                metadata = metadata(nativeLibrary()),
                instance = instance(),
                nativeClassifierPolicy = NativeClassifierPolicy.MOJANG_DESKTOP,
                androidNativeProvider = AndroidNativeProvider { libraries, _ ->
                    AndroidNativeProviderResolution(
                        providerId = "test-provider",
                        libraries = libraries
                    )
                }
            )
        } catch (_: IllegalArgumentException) {
            failed = true
        }

        assertTrue(failed)
    }

    private fun nativeLibrary() = LibrarySpec(
        name = "org.lwjgl:lwjgl:3.3.3",
        artifact = DownloadSpec(
            url = "https://example.invalid/lwjgl.jar",
            sha1 = null,
            size = null,
            path = "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar"
        ),
        classifiers = mapOf(
            "natives-linux-arm64" to DownloadSpec(
                url = "https://example.invalid/lwjgl-native.jar",
                sha1 = null,
                size = null,
                path = "org/lwjgl/lwjgl/3.3.3/lwjgl-natives-linux-arm64.jar"
            )
        ),
        natives = mapOf("linux" to "natives-linux-" + variable("arch"))
    )

    private fun instance() = LauncherInstance(
        name = "Test",
        versionId = "test",
        memoryMb = 1024
    )

    private fun metadata(library: LibrarySpec) = MinecraftVersionMetadata(
        id = "test",
        type = "release",
        mainClass = "net.minecraft.client.main.Main",
        javaMajorVersion = 21,
        client = DownloadSpec(
            url = "https://example.invalid/client.jar",
            sha1 = null,
            size = null
        ),
        assetIndexId = "test",
        assetIndex = DownloadSpec(
            url = "https://example.invalid/assets.json",
            sha1 = null,
            size = null
        ),
        libraries = listOf(library),
        logging = null,
        gameArguments = emptyList(),
        jvmArguments = emptyList()
    )
}
