package io.yannickfan.avero.game

import java.util.UUID

data class GameLaunchRequest(
    val requestId: String,
    val versionId: String,
    val playerName: String,
    val playerUuid: String,
    val minecraftAccessToken: String,
    val memoryMb: Int,
    val createdAtEpochMs: Long
) {
    init {
        require(REQUEST_ID.matches(requestId)) { "Invalid launch request ID" }
        require(versionId.isNotBlank()) { "Minecraft version is required" }
        require(playerName.isNotBlank()) { "Player name is required" }
        require(playerUuid.isNotBlank()) { "Player UUID is required" }
        require(minecraftAccessToken.isNotBlank()) {
            "Minecraft access token is required"
        }
        require(memoryMb >= 512) { "Memory allocation is too small" }
    }

    override fun toString(): String =
        "GameLaunchRequest(" +
            "requestId=$requestId, " +
            "versionId=$versionId, " +
            "playerName=$playerName, " +
            "playerUuid=$playerUuid, " +
            "minecraftAccessToken=<redacted>, " +
            "memoryMb=$memoryMb, " +
            "createdAtEpochMs=$createdAtEpochMs)"

    companion object {
        private val REQUEST_ID =
            Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
                "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        fun create(
            versionId: String,
            playerName: String,
            playerUuid: String,
            minecraftAccessToken: String,
            memoryMb: Int = 4096,
            nowEpochMs: Long = System.currentTimeMillis()
        ): GameLaunchRequest =
            GameLaunchRequest(
                requestId = UUID.randomUUID().toString(),
                versionId = versionId,
                playerName = playerName,
                playerUuid = playerUuid,
                minecraftAccessToken = minecraftAccessToken,
                memoryMb = memoryMb,
                createdAtEpochMs = nowEpochMs
            )

        fun isValidRequestId(value: String): Boolean =
            REQUEST_ID.matches(value)
    }
}
