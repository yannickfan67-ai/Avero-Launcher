package io.yannickfan.avero.game

import org.json.JSONObject
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
            temp.writeText(encode(request))
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

        val payload = try {
            file.readText()
        } finally {
            // One-shot by design: even malformed/expired requests are destroyed.
            file.delete()
        }

        val request = decode(payload)
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
                val request = decode(file.readText())
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
        val file = File(dir, "$requestId.json").canonicalFile
        require(file.parentFile == dir) {
            "Launch request path escaped private directory"
        }
        return file
    }

    private fun encode(request: GameLaunchRequest): String =
        JSONObject()
            .put("requestId", request.requestId)
            .put("versionId", request.versionId)
            .put("playerName", request.playerName)
            .put("playerUuid", request.playerUuid)
            .put("minecraftAccessToken", request.minecraftAccessToken)
            .put("memoryMb", request.memoryMb)
            .put("createdAtEpochMs", request.createdAtEpochMs)
            .toString()

    private fun decode(value: String): GameLaunchRequest {
        val json = JSONObject(value)
        return GameLaunchRequest(
            requestId = json.getString("requestId"),
            versionId = json.getString("versionId"),
            playerName = json.getString("playerName"),
            playerUuid = json.getString("playerUuid"),
            minecraftAccessToken = json.getString("minecraftAccessToken"),
            memoryMb = json.optInt("memoryMb", 4096),
            createdAtEpochMs = json.getLong("createdAtEpochMs")
        )
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
    }
}
