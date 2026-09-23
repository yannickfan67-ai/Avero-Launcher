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
    private val maxAttempts: Int = 3,
    private val initialRetryDelayMillis: Long = 500,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(initialRetryDelayMillis >= 0) { "initialRetryDelayMillis cannot be negative" }
    }

    suspend fun isValid(spec: DownloadSpec, file: File): Boolean = withContext(Dispatchers.IO) {
        validationState(spec, file, observedTotal = null) == ValidationState.VALID
    }

    suspend fun download(
        spec: DownloadSpec,
        destination: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, destination.name + ".part")

        if (prepareExistingPartial(spec, temp)) {
            return@withContext moveCompleted(temp, destination)
        }

        var lastFailure: Throwable? = null
        for (attempt in 1..maxAttempts) {
            currentCoroutineContext().ensureActive()

            try {
                val observedTotal = transferAttempt(spec, temp, onProgress)
                when (validationState(spec, temp, observedTotal)) {
                    ValidationState.VALID -> {
                        return@withContext moveCompleted(temp, destination)
                    }

                    ValidationState.INCOMPLETE -> {
                        lastFailure = IOException(
                            "Incomplete download: got ${temp.length()} bytes" +
                                (observedTotal?.let { ", expected $it" } ?: "")
                        )
                    }

                    ValidationState.CORRUPT -> {
                        temp.delete()
                        lastFailure = IOException(
                            "Downloaded file failed size or digest validation"
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (!isRetryable(failure)) throw failure
                lastFailure = failure
            }

            if (attempt == maxAttempts) {
                throw lastFailure ?: IOException("Download failed after $maxAttempts attempts")
            }

            val retryDelay = retryDelayMillis(attempt)
            if (retryDelay > 0) delay(retryDelay)
        }

        throw lastFailure ?: IOException("Download failed")
    }

    private suspend fun transferAttempt(
        spec: DownloadSpec,
        temp: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit
    ): Long? {
        var offset = temp.takeIf(File::isFile)?.length() ?: 0L
        spec.size?.takeIf { it > 0 }?.let { expected ->
            if (offset > expected) {
                temp.delete()
                offset = 0
            }
        }

        val connection = connectionFactory(URL(spec.url)).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Avero-Launcher/0.1")
            if (offset > 0) {
                setRequestProperty("Range", "bytes=$offset-")
            }
        }

        try {
            val code = connection.responseCode
            if (code == HTTP_REQUESTED_RANGE_NOT_SATISFIABLE && offset > 0) {
                if (spec.size?.takeIf { it > 0 } == offset) {
                    return spec.size
                }
                temp.delete()
                throw RestartDownloadException("Server rejected the saved partial range")
            }

            if (code !in 200..299) {
                throw HttpStatusException(
                    statusCode = code,
                    retryable = code == 408 || code == 429 || code in 500..599
                )
            }

            val contentRange = parseContentRange(connection.getHeaderField("Content-Range"))
            val append = offset > 0 && code == HttpURLConnection.HTTP_PARTIAL
            if (append && contentRange?.start != offset) {
                temp.delete()
                throw RestartDownloadException(
                    "Invalid Content-Range for resume: expected start $offset, got " +
                        (contentRange?.start?.toString() ?: "missing")
                )
            }

            if (!append && offset > 0) {
                temp.delete()
                offset = 0
            }

            val responseLength = connection.contentLengthLong.takeIf { it > 0 }
            val total = spec.size?.takeIf { it > 0 }
                ?: contentRange?.total
                ?: responseLength?.let { length ->
                    if (append) offset + length else length
                }

            var downloaded = offset
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
                        onProgress(downloaded, total)
                    }
                }
            }

            return total
        } finally {
            connection.disconnect()
        }
    }

    private fun prepareExistingPartial(spec: DownloadSpec, temp: File): Boolean {
        if (!temp.isFile) return false
        val expectedSize = spec.size?.takeIf { it > 0 } ?: return false

        return when {
            temp.length() > expectedSize -> {
                temp.delete()
                false
            }

            temp.length() == expectedSize -> {
                if (validationState(spec, temp, expectedSize) == ValidationState.VALID) {
                    true
                } else {
                    temp.delete()
                    false
                }
            }

            else -> false
        }
    }

    private fun validationState(
        spec: DownloadSpec,
        file: File,
        observedTotal: Long?
    ): ValidationState {
        if (!file.isFile) return ValidationState.INCOMPLETE

        val expectedSize = spec.size?.takeIf { it > 0 }
            ?: observedTotal?.takeIf { it > 0 }
        expectedSize?.let { expected ->
            if (file.length() < expected) return ValidationState.INCOMPLETE
            if (file.length() > expected) return ValidationState.CORRUPT
        }

        spec.sha1?.let { expected ->
            if (!digest(file, "SHA-1").equals(expected, ignoreCase = true)) {
                return ValidationState.CORRUPT
            }
        }
        spec.sha256?.let { expected ->
            if (!digest(file, "SHA-256").equals(expected, ignoreCase = true)) {
                return ValidationState.CORRUPT
            }
        }

        return ValidationState.VALID
    }

    private fun moveCompleted(temp: File, destination: File): File {
        if (destination.exists()) {
            check(destination.delete()) { "Could not replace existing destination" }
        }
        check(temp.renameTo(destination)) { "Could not move completed download" }
        return destination
    }

    private fun isRetryable(failure: Throwable): Boolean = when (failure) {
        is HttpStatusException -> failure.retryable
        is IOException -> true
        else -> false
    }

    private fun retryDelayMillis(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, 4)
        return initialRetryDelayMillis * (1L shl shift)
    }

    private fun parseContentRange(value: String?): ContentRange? {
        if (value.isNullOrBlank()) return null
        val match = CONTENT_RANGE.matchEntire(value.trim()) ?: return null
        return ContentRange(
            start = match.groupValues[1].toLongOrNull(),
            total = match.groupValues[2].takeUnless { it == "*" }?.toLongOrNull()
        )
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

    private enum class ValidationState {
        VALID,
        INCOMPLETE,
        CORRUPT
    }

    private data class ContentRange(
        val start: Long?,
        val total: Long?
    )

    private class HttpStatusException(
        val statusCode: Int,
        val retryable: Boolean
    ) : IOException("Download failed: HTTP $statusCode")

    private class RestartDownloadException(message: String) : IOException(message)

    companion object {
        private val CONTENT_RANGE = Regex("""bytes\s+(\d+)-\d+/(\d+|\*)""")
        private const val HTTP_REQUESTED_RANGE_NOT_SATISFIABLE = 416
    }
}
