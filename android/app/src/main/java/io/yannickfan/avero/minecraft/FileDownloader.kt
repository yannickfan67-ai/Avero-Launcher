package io.yannickfan.avero.minecraft

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class FileDownloader {
    suspend fun isValid(spec: DownloadSpec, file: File): Boolean = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext false
        spec.size?.let { expected ->
            if (expected > 0 && file.length() != expected) return@withContext false
        }
        spec.sha1?.let { expected ->
            if (!sha1(file).equals(expected, ignoreCase = true)) return@withContext false
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
        if (temp.exists()) temp.delete()

        val connection = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "Avero-Launcher/0.1")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Download failed: HTTP $code")

            val total = connection.contentLengthLong.takeIf { it > 0 } ?: spec.size
            var downloaded = 0L
            connection.inputStream.use { input ->
                temp.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }

            spec.size?.let {
                if (it > 0 && temp.length() != it) {
                    error("Size mismatch: expected $it, got ${temp.length()}")
                }
            }

            spec.sha1?.let { expected ->
                val actual = sha1(temp)
                if (!actual.equals(expected, ignoreCase = true)) {
                    error("SHA-1 mismatch: expected $expected, got $actual")
                }
            }

            if (destination.exists()) destination.delete()
            check(temp.renameTo(destination)) { "Could not move completed download" }
            destination
        } catch (t: Throwable) {
            temp.delete()
            throw t
        } finally {
            connection.disconnect()
        }
    }

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
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
}
