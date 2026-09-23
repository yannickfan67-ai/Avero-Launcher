package io.yannickfan.avero.minecraft

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class FileDownloaderTest {
    @Test
    fun resumesInterruptedDownloadWithRange() = withTempDir { root ->
        val payload = payload()
        val half = payload.size / 2
        val first = ScriptedConnection(
            code = 200,
            body = payload,
            declaredLength = payload.size.toLong(),
            failAfter = half
        )
        val second = ScriptedConnection(
            code = 206,
            body = payload.copyOfRange(half, payload.size),
            declaredLength = (payload.size - half).toLong(),
            contentRange = "bytes $half-${payload.lastIndex}/${payload.size}"
        )
        val connections = ArrayDeque(listOf(first, second))
        val downloader = downloader(connections)
        val destination = root.resolve("client.jar")

        runBlocking {
            downloader.download(spec(payload), destination)
        }

        assertArrayEquals(payload, destination.readBytes())
        assertEquals("bytes=$half-", second.requestHeaders["Range"])
        assertFalse(root.resolve("client.jar.part").exists())
        assertEquals(0, connections.size)
    }

    @Test
    fun restartsWhenServerIgnoresRangeRequest() = withTempDir { root ->
        val payload = payload()
        val half = payload.size / 2
        val destination = root.resolve("client.jar")
        root.resolve("client.jar.part").writeBytes(payload.copyOfRange(0, half))

        val connection = ScriptedConnection(
            code = 200,
            body = payload,
            declaredLength = payload.size.toLong()
        )
        val connections = ArrayDeque(listOf(connection))

        runBlocking {
            downloader(connections, maxAttempts = 1).download(spec(payload), destination)
        }

        assertEquals("bytes=$half-", connection.requestHeaders["Range"])
        assertArrayEquals(payload, destination.readBytes())
    }

    @Test
    fun invalidResumeContentRangeRestartsFromZero() = withTempDir { root ->
        val payload = payload()
        val half = payload.size / 2
        val destination = root.resolve("client.jar")
        root.resolve("client.jar.part").writeBytes(payload.copyOfRange(0, half))

        val invalidResume = ScriptedConnection(
            code = 206,
            body = payload.copyOfRange(half, payload.size),
            declaredLength = (payload.size - half).toLong(),
            contentRange = null
        )
        val restart = ScriptedConnection(
            code = 200,
            body = payload,
            declaredLength = payload.size.toLong()
        )
        val connections = ArrayDeque(listOf(invalidResume, restart))

        runBlocking {
            downloader(connections, maxAttempts = 2).download(spec(payload), destination)
        }

        assertEquals("bytes=$half-", invalidResume.requestHeaders["Range"])
        assertEquals(null, restart.requestHeaders["Range"])
        assertArrayEquals(payload, destination.readBytes())
    }

    @Test
    fun retriesTransientHttpFailure() = withTempDir { root ->
        val payload = payload()
        val unavailable = ScriptedConnection(code = 503)
        val success = ScriptedConnection(
            code = 200,
            body = payload,
            declaredLength = payload.size.toLong()
        )
        val connections = ArrayDeque(listOf(unavailable, success))
        val destination = root.resolve("client.jar")

        runBlocking {
            downloader(connections).download(spec(payload), destination)
        }

        assertArrayEquals(payload, destination.readBytes())
        assertEquals(0, connections.size)
    }

    @Test
    fun permanentHttpFailureIsNotRetried() = withTempDir { root ->
        val payload = payload()
        val notFound = ScriptedConnection(code = 404)
        val shouldNotBeUsed = ScriptedConnection(
            code = 200,
            body = payload,
            declaredLength = payload.size.toLong()
        )
        val connections = ArrayDeque(listOf(notFound, shouldNotBeUsed))
        val destination = root.resolve("client.jar")

        assertThrows(IOException::class.java) {
            runBlocking {
                downloader(connections).download(spec(payload), destination)
            }
        }

        assertEquals(1, connections.size)
        assertFalse(destination.exists())
    }

    @Test
    fun completeVerifiedPartIsReusedWithoutNetwork() = withTempDir { root ->
        val payload = payload()
        val destination = root.resolve("client.jar")
        root.resolve("client.jar.part").writeBytes(payload)
        var connectionCalls = 0
        val downloader = FileDownloader(
            maxAttempts = 1,
            initialRetryDelayMillis = 0
        ) {
            connectionCalls++
            error("Network should not be used for a verified complete partial")
        }

        runBlocking {
            downloader.download(spec(payload), destination)
        }

        assertEquals(0, connectionCalls)
        assertArrayEquals(payload, destination.readBytes())
    }

    @Test
    fun digestMismatchNeverReplacesExistingDestination() = withTempDir { root ->
        val expected = payload()
        val corrupt = expected.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        val destination = root.resolve("client.jar")
        destination.writeText("known-good-existing-file")
        val connection = ScriptedConnection(
            code = 200,
            body = corrupt,
            declaredLength = corrupt.size.toLong()
        )
        val connections = ArrayDeque(listOf(connection))

        assertThrows(IOException::class.java) {
            runBlocking {
                downloader(connections, maxAttempts = 1).download(
                    spec(expected),
                    destination
                )
            }
        }

        assertEquals("known-good-existing-file", destination.readText())
        assertFalse(root.resolve("client.jar.part").exists())
    }

    private fun downloader(
        connections: ArrayDeque<ScriptedConnection>,
        maxAttempts: Int = 3
    ) = FileDownloader(
        maxAttempts = maxAttempts,
        initialRetryDelayMillis = 0
    ) {
        check(connections.isNotEmpty()) { "Unexpected extra HTTP attempt" }
        connections.removeFirst().also { connection -> connection.requestUrl = it }
    }

    private fun spec(payload: ByteArray) = DownloadSpec(
        url = "https://example.invalid/file",
        sha1 = digest(payload, "SHA-1"),
        size = payload.size.toLong(),
        sha256 = digest(payload, "SHA-256")
    )

    private fun payload(): ByteArray =
        ByteArray(64 * 1024) { index -> (index * 31).toByte() }

    private fun digest(bytes: ByteArray, algorithm: String): String =
        MessageDigest.getInstance(algorithm)
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun withTempDir(block: (File) -> Unit) {
        val root = Files.createTempDirectory("avero-download-test").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private class ScriptedConnection(
        private val code: Int,
        private val body: ByteArray = byteArrayOf(),
        private val declaredLength: Long = -1,
        private val contentRange: String? = null,
        private val failAfter: Int? = null
    ) : HttpURLConnection(URL("https://example.invalid/scripted")) {
        val requestHeaders = mutableMapOf<String, String>()
        var requestUrl: URL? = null

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false

        override fun setRequestProperty(key: String, value: String) {
            requestHeaders[key] = value
        }

        override fun getRequestProperty(key: String): String? = requestHeaders[key]
        override fun getResponseCode(): Int = code
        override fun getContentLengthLong(): Long = declaredLength

        override fun getHeaderField(name: String?): String? =
            if (name.equals("Content-Range", ignoreCase = true)) contentRange else null

        override fun getInputStream(): InputStream =
            FailingByteArrayInputStream(body, failAfter)
    }

    private class FailingByteArrayInputStream(
        private val bytes: ByteArray,
        private val failAfter: Int?
    ) : InputStream() {
        private var position = 0

        override fun read(): Int {
            if (failAfter != null && position >= failAfter) {
                throw IOException("Simulated connection drop")
            }
            if (position >= bytes.size) return -1
            return bytes[position++].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (failAfter != null && position >= failAfter) {
                throw IOException("Simulated connection drop")
            }
            if (position >= bytes.size) return -1

            val beforeFailure = failAfter?.minus(position) ?: Int.MAX_VALUE
            val count = minOf(length, bytes.size - position, beforeFailure)
            if (count <= 0) throw IOException("Simulated connection drop")

            bytes.copyInto(
                destination = buffer,
                destinationOffset = offset,
                startIndex = position,
                endIndex = position + count
            )
            position += count
            return count
        }
    }
}
