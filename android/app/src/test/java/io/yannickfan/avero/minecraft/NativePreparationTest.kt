package io.yannickfan.avero.minecraft

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NativePreparationTest {
    @Test
    fun reusesVerifiedArchiveAndAtomicallyExtractsNatives() = runBlocking {
        val root = Files.createTempDirectory("avero-native-test-").toFile()
        try {
            val layout = InstanceLayout(root)
            val archivePath = "provider/test-native.jar"
            val archive = layout.library(archivePath)
            archive.parentFile!!.mkdirs()
            createNativeArchive(archive)

            val previous = layout.nativesDirectory("Test Instance")
            previous.mkdirs()
            File(previous, "stale.so").writeText("old")

            val spec = DownloadSpec(
                url = "https://example.invalid/test-native.jar",
                sha1 = sha1(archive),
                size = archive.length(),
                path = archivePath
            )
            val plan = LaunchPlan(
                versionId = "test",
                mainClass = "net.minecraft.client.main.Main",
                javaMajorVersion = 21,
                classpathEntries = emptyList(),
                jvmArguments = emptyList(),
                gameArguments = emptyList(),
                nativeArchives = listOf(
                    NativeArchivePlan(
                        libraryName = "provider:test-native:1",
                        classifier = "android-arm64",
                        download = spec,
                        extractExcludes = emptyList()
                    )
                ),
                nativeState = NativePlanState.READY,
                nativeProviderId = "test-provider"
            )

            val result = NativePreparation().prepare(
                plan = plan,
                root = root,
                instanceName = "Test Instance"
            )

            assertEquals(0, result.downloadedArchives)
            assertEquals(1, result.reusedArchives)
            assertEquals(1, result.extractedFiles)
            assertTrue(File(result.directory, "libtest.so").isFile)
            assertFalse(File(result.directory, "stale.so").exists())
            assertFalse(File(result.directory, "META-INF/MANIFEST.MF").exists())
            assertFalse(layout.nativesStagingDirectory("Test Instance").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun blockedPlanDoesNotReplaceExistingNatives() = runBlocking {
        val root = Files.createTempDirectory("avero-native-test-").toFile()
        try {
            val layout = InstanceLayout(root)
            val previous = layout.nativesDirectory("Test")
            previous.mkdirs()
            val marker = File(previous, "working.so").apply { writeText("ok") }

            var failed = false
            try {
                NativePreparation().prepare(
                    plan = LaunchPlan(
                        versionId = "test",
                        mainClass = "net.minecraft.client.main.Main",
                        javaMajorVersion = 21,
                        classpathEntries = emptyList(),
                        jvmArguments = emptyList(),
                        gameArguments = emptyList(),
                        nativeState = NativePlanState.MISSING_ANDROID_PROVIDER
                    ),
                    root = root,
                    instanceName = "Test"
                )
            } catch (_: IllegalArgumentException) {
                failed = true
            }

            assertTrue(failed)
            assertTrue(marker.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createNativeArchive(file: File) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("libtest.so"))
            zip.write("native".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zip.write("ignored".toByteArray())
            zip.closeEntry()
        }
    }

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
