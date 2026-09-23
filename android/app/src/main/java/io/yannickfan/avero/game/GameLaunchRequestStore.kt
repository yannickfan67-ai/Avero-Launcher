package io.yannickfan.avero.game

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File

class GameLaunchRequestStore(
    private val filesRoot: File,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS
) {
    private val directory: File
        get() = File(filesRoot, DIRECTORY_NAME)

    fun write(request: GameLaunchRequest): String {
        val dir = directory
        require(dir.isDirectory || dir.mkdirs()) {
            "Could not create launch request directory"
        }

        val destination = requestFile(request.requestId)
        val temp = File(dir, ".${request.requestId}.tmp")
        if (temp.exists()) temp.delete()

        try {
            DataOutputStream(
                BufferedOutputStream(temp.outputStream())
            ).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(FORMAT_VERSION)
                writeString(output, request.requestId)
                writeString(output, request.versionId)
                writeString(output, request.playerName)
                writeString(output, request.playerUuid)
                writeString(output, request.minecraftAccessToken)
                output.writeInt(request.memoryMb)
                output.writeLong(request.createdAtEpochMs)
            }
            makeOwnerOnly(temp)

            if (destination.exists()) {
                check(destination.delete()) {
                    "Could not replace launch request"
                }
            }
            check(temp.renameTo(destination)) {
                "Could not commit launch request"
            }
            makeOwnerOnly(destination)
            return request.requestId
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    fun consume(
        requestId: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): GameLaunchRequest {
        val file = requestFile(requestId)
        require(file.isFile) { "Launch request not found" }

        val request = try {
            decode(file)
        } finally {
            // One-shot by design: malformed/expired requests are destroyed too.
            file.delete()
        }

        require(request.requestId == requestId) {
            "Launch request identity mismatch"
        }

        val age = nowEpochMs - request.createdAtEpochMs
        require(age >= 0L && age <= maxAgeMs) {
            "Launch request expired"
        }
        return request
    }

    fun purgeExpired(
        nowEpochMs: Long = System.currentTimeMillis()
    ): Int {
        val files = directory.listFiles().orEmpty()
        var removed = 0
        for (file in files) {
            if (!file.isFile || file.name.startsWith(".")) continue

            val expired = runCatching {
                val request = decode(file)
                val age = nowEpochMs - request.createdAtEpochMs
                age < 0L || age > maxAgeMs
            }.getOrDefault(true)

            if (expired && file.delete()) removed++
        }
        return removed
    }

    private fun requestFile(requestId: String): File {
        require(GameLaunchRequest.isValidRequestId(requestId)) {
            "Invalid launch request ID"
        }
        val dir = directory.canonicalFile
        val file = File(dir, "$requestId.launch").canonicalFile
        require(file.parentFile == dir) {
            "Launch request path escaped private directory"
        }
        return file
    }

    private fun decode(file: File): GameLaunchRequest {
        try {
            DataInputStream(
                BufferedInputStream(file.inputStream())
            ).use { input ->
                require(input.readInt() == MAGIC) {
                    "Invalid launch request format"
                }
                require(input.readInt() == FORMAT_VERSION) {
                    "Unsupported launch request format"
                }

                return GameLaunchRequest(
                    requestId = readString(input),
                    versionId = readString(input),
                    playerName = readString(input),
                    playerUuid = readString(input),
                    minecraftAccessToken = readString(input),
                    memoryMb = input.readInt(),
                    createdAtEpochMs = input.readLong()
                )
            }
        } catch (e: EOFException) {
            throw IllegalArgumentException(
                "Truncated launch request",
                e
            )
        }
    }

    private fun writeString(
        output: DataOutputStream,
        value: String
    ) {
        val data = value.toByteArray(Charsets.UTF_8)
        require(data.size <= MAX_STRING_BYTES) {
            "Launch request field is too large"
        }
        output.writeInt(data.size)
        output.write(data)
    }

    private fun readString(input: DataInputStream): String {
        val size = input.readInt()
        require(size in 0..MAX_STRING_BYTES) {
            "Invalid launch request field length"
        }
        val data = ByteArray(size)
        input.readFully(data)
        return data.toString(Charsets.UTF_8)
    }

    private fun makeOwnerOnly(file: File) {
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
    }

    companion object {
        const val DIRECTORY_NAME = "launch-requests"
        const val DEFAULT_MAX_AGE_MS = 2 * 60 * 1000L

        private const val MAGIC = 0x4156524F // "AVRO"
        private const val FORMAT_VERSION = 1
        private const val MAX_STRING_BYTES = 128 * 1024
    }
}
