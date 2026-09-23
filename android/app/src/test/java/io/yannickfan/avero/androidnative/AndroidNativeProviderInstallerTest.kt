package io.yannickfan.avero.androidnative

import com.sun.net.httpserver.HttpServer
import io.yannickfan.avero.minecraft.DownloadSpec
import io.yannickfan.avero.runtime.RuntimeArch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AndroidNativeProviderInstallerTest {
    @Test
    fun downloadsVerifiesAndInstallsJavaComponentsAndNativeAar() {
        val component = "patched-lwjgl-classes".toByteArray()
        val native = byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 1, 2, 3)
        val aar = nativeAar(native)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/provider.aar") { exchange ->
            exchange.sendResponseHeaders(200, aar.size.toLong())
            exchange.responseBody.use { it.write(aar) }
            exchange.close()
        }
        server.createContext("/component.jar") { exchange ->
            exchange.sendResponseHeaders(200, component.size.toLong())
            exchange.responseBody.use { it.write(component) }
            exchange.close()
        }

        val root = Files.createTempDirectory("avero-native-provider-test-").toFile()
        server.start()
        try {
            val pkg = AndroidNativeProviderPackage(
                id = "test-lwjgl-arm64",
                lwjglVersion = "3.4.1",
                arch = RuntimeArch.ARM64,
                nativeArchive = DownloadSpec(
                    url = "http://127.0.0.1:" + server.address.port + "/provider.aar",
                    sha1 = null,
                    size = aar.size.toLong(),
                    gitBlobSha1 = gitBlobSha1(aar)
                ),
                javaComponents = listOf(
                    AndroidNativeProviderComponent(
                        fileName = "patched.jar",
                        download = DownloadSpec(
                            url =
                                "http://127.0.0.1:" + server.address.port +
                                    "/component.jar",
                            sha1 = null,
                            size = component.size.toLong(),
                            gitBlobSha1 = gitBlobSha1(component)
                        )
                    )
                )
            )

            val installed = runBlocking {
                AndroidNativeProviderInstaller().install(pkg, root)
            }

            assertEquals(1, installed.classpathJars.size)
            assertTrue(installed.classpathJars.single().isFile)
            assertArrayEquals(component, installed.classpathJars.single().readBytes())

            assertEquals(1, installed.nativeLibraries.size)
            assertEquals("liblwjgl.so", installed.nativeLibraries.single().name)
            assertArrayEquals(native, installed.nativeLibraries.single().readBytes())
        } finally {
            server.stop(0)
            root.deleteRecursively()
        }
    }

    private fun nativeAar(native: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("classes.jar"))
            zip.write(byteArrayOf())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("jni/arm64-v8a/liblwjgl.so"))
            zip.write(native)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("jni/x86_64/liblwjgl.so"))
            zip.write(byteArrayOf(9, 9, 9))
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun gitBlobSha1(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(("blob " + data.size + "\u0000").toByteArray())
        digest.update(data)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
