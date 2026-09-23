package io.yannickfan.avero.minecraft

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class FileDownloaderTest {
    @Test
    fun resumesInterruptedDownloadWithRange() {
        val payload = ByteArray(128 * 1024) { index -> (index % 251).toByte() }
        val half = payload.size / 2
        val requests = AtomicInteger(0)
        val sawRange = AtomicBoolean(false)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        server.createContext("/file") { exchange ->
            val requestNumber = requests.incrementAndGet()
            val range = exchange.requestHeaders.getFirst("Range")

            if (requestNumber == 1 && range == null) {
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { out ->
                    out.write(payload, 0, half)
                }
                exchange.close()
            } else {
                val start = range
                    ?.removePrefix("bytes=")
                    ?.substringBefore('-')
                    ?.toIntOrNull()
                    ?: 0
                sawRange.set(range != null)
                val remaining = payload.copyOfRange(start, payload.size)
                exchange.responseHeaders.add(
                    "Content-Range",
                    "bytes $start-" + payload.lastIndex + "/" + payload.size
                )
                exchange.sendResponseHeaders(206, remaining.size.toLong())
                exchange.responseBody.use { it.write(remaining) }
                exchange.close()
            }
        }

        val root = Files.createTempDirectory("avero-download-test-").toFile()
        server.start()
        try {
            val destination = File(root, "payload.bin")
            val spec = DownloadSpec(
                url = "http://127.0.0.1:" + server.address.port + "/file",
                sha1 = sha1(payload),
                size = payload.size.toLong()
            )

            runBlocking {
                FileDownloader(
                    maxAttempts = 3,
                    initialBackoffMs = 1
                ).download(spec, destination)
            }

            assertTrue(sawRange.get())
            assertTrue(requests.get() >= 2)
            assertArrayEquals(payload, destination.readBytes())
            assertTrue(!File(root, "payload.bin.part").exists())
        } finally {
            server.stop(0)
            root.deleteRecursively()
        }
    }

    @Test
    fun retriesTransientHttpFailureThenSucceeds() {
        val payload = "runtime-data".toByteArray()
        val requests = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        server.createContext("/file") { exchange ->
            if (requests.incrementAndGet() == 1) {
                exchange.sendResponseHeaders(503, -1)
            } else {
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
            }
            exchange.close()
        }

        val root = Files.createTempDirectory("avero-download-test-").toFile()
        server.start()
        try {
            val destination = File(root, "payload.bin")
            runBlocking {
                FileDownloader(
                    maxAttempts = 3,
                    initialBackoffMs = 1
                ).download(
                    DownloadSpec(
                        url = "http://127.0.0.1:" + server.address.port + "/file",
                        sha1 = sha1(payload),
                        size = payload.size.toLong()
                    ),
                    destination
                )
            }

            assertEquals(2, requests.get())
            assertArrayEquals(payload, destination.readBytes())
        } finally {
            server.stop(0)
            root.deleteRecursively()
        }
    }

    @Test
    fun permanentHttpFailureIsNotRetried() {
        val requests = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/missing") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }

        val root = Files.createTempDirectory("avero-download-test-").toFile()
        server.start()
        try {
            var failed = false
            runBlocking {
                try {
                    FileDownloader(
                        maxAttempts = 4,
                        initialBackoffMs = 1
                    ).download(
                        DownloadSpec(
                            url = "http://127.0.0.1:" + server.address.port + "/missing",
                            sha1 = null,
                            size = 10
                        ),
                        File(root, "missing.bin")
                    )
                } catch (_: Throwable) {
                    failed = true
                }
            }

            assertTrue(failed)
            assertEquals(1, requests.get())
        } finally {
            server.stop(0)
            root.deleteRecursively()
        }
    }

    @Test
    fun interruptedFinalFailureKeepsPartialFile() {
        val payload = ByteArray(64 * 1024) { 7 }
        val half = payload.size / 2
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/file") { exchange ->
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload, 0, half) }
            exchange.close()
        }

        val root = Files.createTempDirectory("avero-download-test-").toFile()
        server.start()
        try {
            val destination = File(root, "payload.bin")
            var failed = false
            runBlocking {
                try {
                    FileDownloader(
                        maxAttempts = 1,
                        initialBackoffMs = 1
                    ).download(
                        DownloadSpec(
                            url = "http://127.0.0.1:" + server.address.port + "/file",
                            sha1 = sha1(payload),
                            size = payload.size.toLong()
                        ),
                        destination
                    )
                } catch (_: Throwable) {
                    failed = true
                }
            }

            val partial = File(root, "payload.bin.part")
            assertTrue(failed)
            assertTrue(partial.isFile)
            assertTrue(partial.length() in 1 until payload.size.toLong())
        } finally {
            server.stop(0)
            root.deleteRecursively()
        }
    }

    private fun sha1(data: ByteArray): String =
        MessageDigest.getInstance("SHA-1")
            .digest(data)
            .joinToString("") { "%02x".format(it) }
}
