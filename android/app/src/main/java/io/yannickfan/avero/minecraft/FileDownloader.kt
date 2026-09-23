package io.yannickfan.avero.minecraft

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class FileDownloader(
    private val maxAttempts: Int = 4,
    private val initialBackoffMs: Long = 500L
) {
    suspend fun isValid(spec: DownloadSpec, file: File): Boolean = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext false
        spec.size?.let { expected ->
            if (expected > 0 && file.length() != expected) return@withContext false
        }
        spec.sha1?.let { expected ->
            if (!digest(file, "SHA-1").equals(expected, ignoreCase = true)) {
                return@withContext false
            }
        }
        spec.sha256?.let { expected ->
            if (!digest(file, "SHA-256").equals(expected, ignoreCase = true)) {
                return@withContext false
            }
        }
        true
    }

    suspend fun download(
        spec: DownloadSpec,
        destination: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, destination.name + ".part")

        if (temp.isFile && hasIntegrityMetadata(spec) && isValid(spec, temp)) {
            return@withContext commit(temp, destination)
        }

        var attempt = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                downloadAttempt(spec, temp, onProgress)
                validateComplete(spec, temp)
                return@withContext commit(temp, destination)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t

                attempt++
                if (attempt >= maxAttempts || !isRetryable(t)) {
                    throw t
                }

                val backoff = initialBackoffMs * (1L shl (attempt - 1).coerceAtMost(4))
                delay(backoff)
            }
        }
    }

    private suspend fun downloadAttempt(
        spec: DownloadSpec,
        temp: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit
    ) {
        val resumeFrom = temp.takeIf { it.isFile }?.length()?.takeIf { it > 0 } ?: 0L
        val connection = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Avero-Launcher/0.1")
            if (resumeFrom > 0L) {
                setRequestProperty("Range", "bytes=$resumeFrom-")
            }
        }

        try {
            val code = connection.responseCode
            if (code == HTTP_RANGE_NOT_SATISFIABLE && resumeFrom > 0L) {
                temp.delete()
                throw RetryableDownloadException("Server rejected the saved download range; restarting")
            }
            if (code !in 200..299) {
                throw HttpStatusException(
                    code = code,
                    retryable = code == 408 || code == 429 || code in 500..599
                )
            }

            val append = resumeFrom > 0L && code == HttpURLConnection.HTTP_PARTIAL
            if (code == HttpURLConnection.HTTP_PARTIAL && resumeFrom > 0L) {
                val contentRange = connection.getHeaderField("Content-Range").orEmpty()
                if (!contentRange.startsWith("bytes $resumeFrom-")) {
                    temp.delete()
                    throw RetryableDownloadException(
                        "Server returned an unexpected Content-Range: $contentRange"
                    )
                }
            }

            val responseLength = connection.contentLengthLong.takeIf { it >= 0L }
            val initialDownloaded = if (append) resumeFrom else 0L
            val total = spec.size ?: responseLength?.let { length ->
                if (append) initialDownloaded + length else length
            }

            var downloaded = initialDownloaded
            var receivedThisAttempt = 0L
            onProgress(downloaded, total)

            connection.inputStream.use { input ->
                FileOutputStream(temp, append).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        receivedThisAttempt += read
                        onProgress(downloaded, total)
                    }
                }
            }

            if (responseLength != null && receivedThisAttempt != responseLength) {
                throw RetryableDownloadException(
                    "Connection ended early: expected $responseLength response bytes, got $receivedThisAttempt"
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun validateComplete(spec: DownloadSpec, temp: File) {
        spec.size?.let { expected ->
            if (expected > 0 && temp.length() < expected) {
                throw RetryableDownloadException(
                    "Download incomplete: expected $expected bytes, got " + temp.length()
                )
            }
            if (expected > 0 && temp.length() > expected) {
                temp.delete()
                error("Size mismatch: expected $expected, got " + temp.length())
            }
        }

        spec.sha1?.let { expected ->
            val actual = digest(temp, "SHA-1")
            if (!actual.equals(expected, ignoreCase = true)) {
                temp.delete()
                error("SHA-1 mismatch: expected $expected, got $actual")
            }
        }

        spec.sha256?.let { expected ->
            val actual = digest(temp, "SHA-256")
            if (!actual.equals(expected, ignoreCase = true)) {
                temp.delete()
                error("SHA-256 mismatch: expected $expected, got $actual")
            }
        }
    }

    private fun commit(temp: File, destination: File): File {
        if (destination.exists()) {
            check(destination.delete()) { "Could not replace existing download" }
        }
        check(temp.renameTo(destination)) { "Could not move completed download" }
        return destination
    }

    private fun hasIntegrityMetadata(spec: DownloadSpec): Boolean =
        (spec.size ?: 0L) > 0L ||
            !spec.sha1.isNullOrBlank() ||
            !spec.sha256.isNullOrBlank()

    private fun isRetryable(t: Throwable): Boolean {
        if (t is HttpStatusException) return t.retryable
        return t is IOException
    }

    private fun digest(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private class RetryableDownloadException(message: String) : IOException(message)

    private class HttpStatusException(
        val code: Int,
        val retryable: Boolean
    ) : IOException("Download failed: HTTP $code")

    companion object {
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}
