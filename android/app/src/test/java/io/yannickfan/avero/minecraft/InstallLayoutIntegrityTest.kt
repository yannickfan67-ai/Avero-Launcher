package io.yannickfan.avero.minecraft

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class InstallLayoutIntegrityTest {
    @Test
    fun sameSizeCorruptedAssetIsRejected() = runBlocking {
        val root = Files.createTempDirectory("avero-asset-integrity-").toFile()
        try {
            val expected = "payload".toByteArray()
            val asset = AssetObjectSpec(
                logicalName = "minecraft/test.txt",
                hash = sha1(expected),
                size = expected.size.toLong()
            )
            val file = InstanceLayout(root).assetObject(asset.hash)
            file.parentFile?.mkdirs()
            file.writeBytes(expected)

            val installer = AssetInstaller()
            assertTrue(installer.isInstalled(asset, root))

            file.writeBytes(ByteArray(expected.size) { 0x42 })
            assertEquals(expected.size.toLong(), file.length())
            assertFalse(installer.isInstalled(asset, root))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun dotDotInstanceNameStaysInsideInstancesRoot() {
        val root = Files.createTempDirectory("avero-instance-layout-").toFile()
        try {
            val game = InstanceLayout(root).gameDirectory("..").canonicalFile
            val instances = File(root, "instances").canonicalFile

            assertTrue(game.path.startsWith(instances.path + File.separator))
            assertEquals(File(instances, "_/game").canonicalFile, game)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun libraryPathCannotEscapeManagedLibraryRoot() {
        val root = Files.createTempDirectory("avero-library-layout-").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                InstanceLayout(root).library("../outside.jar")
            }

            assertEquals(
                File(root, "libraries/org/example/demo.jar").canonicalFile,
                InstanceLayout(root)
                    .library("libraries/org/example/demo.jar")
                    .canonicalFile
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
