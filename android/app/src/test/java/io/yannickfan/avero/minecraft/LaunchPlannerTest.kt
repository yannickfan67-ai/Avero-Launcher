package io.yannickfan.avero.minecraft

import io.yannickfan.avero.androidnative.AndroidNativeProviderPlan
import org.junit.Assert.assertEquals
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
    }

    @Test
    fun androidProviderMakesPlanReadyAndPrependsClasspath() {
        val provider = AndroidNativeProviderPlan(
            id = "test-provider",
            lwjglVersion = "3.3.3",
            classpathEntry = "android-native/test-provider/classes.jar",
            nativeDirectory = "android-native/test-provider/natives"
        )

        val plan = LaunchPlanner().createVanillaPlan(
            metadata = metadata(nativeLibrary()),
            instance = instance(),
            androidNativeProvider = provider
        )

        assertEquals(NativePlanState.READY, plan.nativeState)
        assertEquals(provider, plan.androidNativeProvider)
        assertEquals(provider.classpathEntry, plan.classpathEntries.first())
        assertTrue(
            plan.classpathEntries.contains(
                "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar"
            )
        )
        assertTrue(plan.nativeArchives.isEmpty())
    }

    @Test
    fun providerAndDesktopClassifierPolicyCannotBeCombined() {
        val provider = AndroidNativeProviderPlan(
            id = "test-provider",
            lwjglVersion = "3.3.3",
            classpathEntry = "android-native/test-provider/classes.jar",
            nativeDirectory = "android-native/test-provider/natives"
        )

        var failed = false
        try {
            LaunchPlanner().createVanillaPlan(
                metadata = metadata(nativeLibrary()),
                instance = instance(),
                nativeClassifierPolicy = NativeClassifierPolicy.MOJANG_DESKTOP,
                androidNativeProvider = provider
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
