package io.yannickfan.avero.androidnative

import io.yannickfan.avero.minecraft.DownloadSpec
import io.yannickfan.avero.minecraft.LauncherInstance
import io.yannickfan.avero.minecraft.LibrarySpec
import io.yannickfan.avero.minecraft.MinecraftVersionMetadata
import io.yannickfan.avero.minecraft.RuleAction
import io.yannickfan.avero.minecraft.RuleSpec
import io.yannickfan.avero.runtime.RuntimeArch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AndroidVanillaLaunchPreparerTest {
    @Test
    fun producesApplicableClasspathAndAndroidNativeDirectory() {
        val root = Files.createTempDirectory("avero-launch-prep-").toFile()
        try {
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
                assetIndexId = "test-assets",
                assetIndex = dummy,
                libraries = listOf(
                    LibrarySpec(
                        name = "org.lwjgl:lwjgl:3.4.1",
                        artifact = dummy.copy(
                            path = "libraries/org/lwjgl/lwjgl/3.4.1/lwjgl.jar"
                        )
                    ),
                    LibrarySpec(
                        name = "example:allowed:1",
                        artifact = dummy.copy(
                            path = "libraries/example/allowed.jar"
                        )
                    ),
                    LibrarySpec(
                        name = "example:blocked:1",
                        artifact = dummy.copy(
                            path = "libraries/example/blocked.jar"
                        ),
                        rules = listOf(
                            RuleSpec(action = RuleAction.DISALLOW)
                        )
                    )
                ),
                logging = null,
                gameArguments = emptyList(),
                jvmArguments = emptyList()
            )

            val pkg = requireNotNull(
                AndroidNativeProviderCatalog.detect(
                    metadata,
                    RuntimeArch.ARM64
                )
            )
            installFixture(pkg, root)

            val prepared = AndroidVanillaLaunchPreparer().prepareInstalled(
                metadata = metadata,
                instance = LauncherInstance(
                    name = "test",
                    versionId = metadata.id
                ),
                minecraftRoot = root,
                gameDirectory = File(root, "instances/test/game"),
                arch = RuntimeArch.ARM64
            )
            val providerPlan = requireNotNull(
                prepared.plan.androidNativeProvider
            )

            assertEquals(
                providerPlan.classpathEntries,
                prepared.plan.classpathEntries.take(
                    providerPlan.classpathEntries.size
                )
            )
            assertTrue(
                prepared.plan.classpathEntries.contains(
                    "libraries/example/allowed.jar"
                )
            )
            assertFalse(
                prepared.plan.classpathEntries.contains(
                    "libraries/example/blocked.jar"
                )
            )
            assertTrue(prepared.plan.nativeArchives.isEmpty())
            assertEquals(
                File(root, providerPlan.nativeDirectory).canonicalFile,
                prepared.environment.nativesDirectory.canonicalFile
            )
            assertTrue(prepared.nativeProvider.nativeLibraries.isNotEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun installFixture(
        pkg: AndroidNativeProviderPackage,
        minecraftRoot: File
    ) {
        val providerRoot = File(
            minecraftRoot,
            "android-native/${pkg.id}"
        )
        val jars = File(providerRoot, "jars").apply { mkdirs() }
        pkg.javaComponents.forEach { component ->
            File(jars, component.fileName).writeText("fixture")
        }

        val natives = File(providerRoot, "natives").apply { mkdirs() }
        File(natives, "liblwjgl.so").writeBytes(
            byteArrayOf(0x7f, 0x45, 0x4c, 0x46)
        )
    }
}
