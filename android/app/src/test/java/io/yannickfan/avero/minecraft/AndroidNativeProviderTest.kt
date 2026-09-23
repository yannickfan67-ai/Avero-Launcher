package io.yannickfan.avero.minecraft

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AndroidNativeProviderTest {
    @Test
    fun rejectsArtifactWithoutSha256() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidNativeArtifact(
                id = "lwjgl",
                abi = "arm64-v8a",
                target = AndroidNativeArtifactTarget.NATIVE_FILE,
                fileName = "liblwjgl.so",
                download = DownloadSpec(
                    url = "https://example.invalid/liblwjgl.so",
                    sha1 = null,
                    size = 1
                )
            )
        }
    }

    @Test
    fun rejectsMixedAbiBundle() {
        val artifact = artifact(
            id = "lwjgl",
            abi = "x86_64",
            target = AndroidNativeArtifactTarget.NATIVE_FILE,
            fileName = "liblwjgl.so"
        )

        assertThrows(IllegalArgumentException::class.java) {
            AndroidNativeBundle(
                providerId = "test-provider",
                minecraftVersion = "1.21.4",
                abi = "arm64-v8a",
                artifacts = listOf(artifact)
            )
        }
    }

    @Test
    fun preparesPerInstanceNativesAndKeepsClasspathSeparate() = runBlocking {
        val root = Files.createTempDirectory("avero-native-test").toFile()
        try {
            val layout = InstanceLayout(root)
            val oldDirectory = layout.nativesDirectory("Latest release")
            oldDirectory.mkdirs()
            File(oldDirectory, "stale.so").writeText("stale")

            val bundle = AndroidNativeBundle(
                providerId = "test-provider",
                minecraftVersion = "1.21.4",
                abi = "arm64-v8a",
                artifacts = listOf(
                    artifact(
                        id = "lwjgl-native",
                        abi = "arm64-v8a",
                        target = AndroidNativeArtifactTarget.NATIVE_FILE,
                        fileName = "liblwjgl.so"
                    ),
                    artifact(
                        id = "lwjgl-classes",
                        abi = "arm64-v8a",
                        target = AndroidNativeArtifactTarget.CLASSPATH,
                        fileName = "lwjgl-android.jar"
                    )
                )
            )

            val result = AndroidNativePreparer(
                artifactStore = FakeArtifactStore()
            ).prepare(
                bundle = bundle,
                root = root,
                instanceName = "Latest release"
            )

            assertEquals(layout.nativesDirectory("Latest release"), result.nativesDirectory)
            assertEquals(1, result.preparedFiles)
            assertTrue(File(result.nativesDirectory, "liblwjgl.so").isFile)
            assertFalse(File(result.nativesDirectory, "stale.so").exists())
            assertFalse(File(result.nativesDirectory, "lwjgl-android.jar").exists())

            val classpath = result.classpathEntries.single()
                .replace(File.separatorChar, '/')
            assertEquals(
                "native-providers/test-provider/arm64-v8a/lwjgl-android.jar",
                classpath
            )
        } finally {
            root.deleteRecursively()
        }
    }


    @Test
    fun dotDotInstanceNameCannotEscapeManagedDirectory() {
        val root = Files.createTempDirectory("avero-layout-test").toFile()
        try {
            val layout = InstanceLayout(root)
            val game = layout.gameDirectory("..").canonicalFile
            val natives = layout.nativesDirectory("..").canonicalFile
            val instancesRoot = File(root, "instances").canonicalFile

            assertTrue(game.path.startsWith(instancesRoot.path + File.separator))
            assertTrue(natives.path.startsWith(instancesRoot.path + File.separator))
            assertEquals(File(instancesRoot, "_/game").canonicalFile, game)
            assertEquals(File(instancesRoot, "_/natives").canonicalFile, natives)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun artifact(
        id: String,
        abi: String,
        target: AndroidNativeArtifactTarget,
        fileName: String
    ) = AndroidNativeArtifact(
        id = id,
        abi = abi,
        target = target,
        fileName = fileName,
        download = DownloadSpec(
            url = "memory://$id",
            sha1 = null,
            size = 7,
            sha256 = "a".repeat(64)
        )
    )

    private class FakeArtifactStore : AndroidNativeArtifactStore {
        override suspend fun ensure(
            spec: DownloadSpec,
            destination: File
        ): File {
            destination.parentFile?.mkdirs()
            destination.writeText("payload")
            return destination
        }
    }
}
