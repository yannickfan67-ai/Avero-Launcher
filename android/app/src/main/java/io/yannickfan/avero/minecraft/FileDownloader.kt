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
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class FileDownloader(private val maxAttempts: Int = 4, private val initialBackoffMs: Long = 500L,
    private val allowLocalHttp: Boolean = false) {
    init { require(maxAttempts in 1..8 && initialBackoffMs in 0..10000) }
    suspend fun isValid(spec: DownloadSpec, file: File): Boolean = withContext(Dispatchers.IO) {
        validateSpec(spec)
        if (!file.isFile || Files.isSymbolicLink(file.toPath())) return@withContext false
        if (spec.size != null && file.length() != spec.size) return@withContext false
        if (file.length() > MAX_FILE_BYTES) return@withContext false
        hashes(spec).all { (algorithm, expected) -> digest(file, algorithm).equals(expected, true) } &&
            (spec.gitBlobSha1 == null || gitBlobSha1(file).equals(spec.gitBlobSha1, true))
    }
    suspend fun download(spec: DownloadSpec, destination: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> }): File = withContext(Dispatchers.IO) {
        validateSpec(spec); DownloadPolicy.validate(spec.url, allowLocalHttp); ensurePhysical(destination)
        destination.parentFile?.mkdirs(); ensurePhysical(destination)
        val temp = File(destination.parentFile, destination.name + ".part"); ensurePhysical(temp)
        if (temp.isFile && hasDigest(spec) && isValid(spec, temp)) return@withContext commit(temp, destination)
        for (attempt in 0 until maxAttempts) {
            currentCoroutineContext().ensureActive()
            try {
                downloadAttempt(spec, temp, onProgress)
                if (!isValid(spec, temp)) { temp.delete(); throw IntegrityException("Downloaded file did not match its digest or size") }
                currentCoroutineContext().ensureActive(); return@withContext commit(temp, destination)
            } catch (t: Exception) {
                if (t is CancellationException) throw t
                if (attempt + 1 >= maxAttempts || (t is HttpStatusException && !t.retryable) || t !is IOException) throw t
                delay(initialBackoffMs * (1L shl attempt.coerceAtMost(4)))
            }
        }
        error("Download attempts exhausted")
    }
    private suspend fun downloadAttempt(spec: DownloadSpec, temp: File, progress: (Long, Long?) -> Unit) {
        ensurePhysical(temp)
        var resume = if (hasDigest(spec) && temp.isFile) temp.length() else 0L
        if (resume > (spec.size ?: MAX_FILE_BYTES)) { temp.delete(); resume = 0 }
        val connection = DownloadPolicy.open(spec.url, allowLocalHttp, resume)
        try {
            val code = connection.responseCode
            if (code == 416 && resume > 0) { temp.delete(); throw IOException("Saved range was rejected; restarting") }
            if (code !in setOf(200,206)) throw HttpStatusException(code, code in setOf(408,429) || code in 500..599)
            val append = code == 206 && resume > 0
            if (code == 206) {
                val range = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(connection.getHeaderField("Content-Range").orEmpty())
                val start = range?.groupValues?.get(1)?.toLongOrNull()
                val end = range?.groupValues?.get(2)?.toLongOrNull()
                val total = range?.groupValues?.get(3)?.toLongOrNull()
                if (start != resume || end == null || total == null || end < resume || end >= total || total > MAX_FILE_BYTES || (spec.size != null && total != spec.size)) {
                    temp.delete(); throw IOException("Unexpected Content-Range; saved partial discarded")
                }
            }
            val offset = if (append) resume else 0L
            val length = connection.contentLengthLong.takeIf { it >= 0 }
            val total = spec.size ?: length?.plus(offset)
            if ((total ?: 0) > MAX_FILE_BYTES) { temp.delete(); throw IOException("Download exceeds file size limit") }
            if (spec.size != null && length != null && offset + length != spec.size) throw IOException("Response size differs from metadata")
            var downloaded = offset; var received = 0L
            progress(downloaded, total)
            connection.inputStream.use { input ->
                FileOutputStream(temp, append).buffered().use { output ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        currentCoroutineContext().ensureActive(); val read = input.read(buffer); if (read < 0) break
                        if (downloaded + read > (spec.size ?: MAX_FILE_BYTES)) { temp.delete(); throw IOException("Download exceeds expected size") }
                        output.write(buffer, 0, read); downloaded += read; received += read; progress(downloaded, total)
                    }
                }
            }
            if (length != null && received != length) throw IOException("Connection ended early")
            if (spec.size != null && downloaded < spec.size) throw IOException("Download is incomplete")
        } finally { connection.disconnect() }
    }
    private fun commit(temp: File, destination: File): File {
        ensurePhysical(temp); ensurePhysical(destination)
        try { Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        return destination
    }
    private fun ensurePhysical(file: File) {
        var current: File? = file.absoluteFile
        while (current != null) { require(!Files.isSymbolicLink(current.toPath())) { "Download path contains a symbolic link" }; current = current.parentFile }
    }
    private fun validateSpec(spec: DownloadSpec) {
        require(spec.size == null || spec.size in 0..MAX_FILE_BYTES) { "Invalid download size" }
        for ((algorithm, hash) in hashes(spec)) require(hash.matches(Regex(if (algorithm == "SHA-1") "[A-Fa-f0-9]{40}" else "[A-Fa-f0-9]{64}"))) { "Invalid digest" }
        require(spec.gitBlobSha1 == null || spec.gitBlobSha1.matches(Regex("[A-Fa-f0-9]{40}")))
    }
    private fun hashes(spec: DownloadSpec) = listOfNotNull(spec.sha1?.let { "SHA-1" to it }, spec.sha256?.let { "SHA-256" to it })
    private fun hasDigest(spec: DownloadSpec) = hashes(spec).isNotEmpty() || spec.gitBlobSha1 != null
    private suspend fun digest(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(32768)
            while (true) { currentCoroutineContext().ensureActive(); val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private suspend fun gitBlobSha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1"); digest.update("blob ${file.length()}\u0000".toByteArray())
        file.inputStream().use { input -> val buffer = ByteArray(32768); while (true) { currentCoroutineContext().ensureActive(); val n=input.read(buffer); if(n<0) break; digest.update(buffer,0,n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private class IntegrityException(message: String) : IOException(message)
    private class HttpStatusException(code: Int, val retryable: Boolean) : IOException("HTTP $code")
    companion object { const val MAX_FILE_BYTES = 512L * 1024 * 1024 }
}
