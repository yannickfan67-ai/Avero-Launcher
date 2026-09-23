package io.yannickfan.avero.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class GameLaunchRequestStoreTest {
    @Test
    fun requestStringNeverContainsAccessToken() {
        val token = "secret-minecraft-token"
        val request = GameLaunchRequest.create(
            versionId = "1.21.4",
            playerName = "Player",
            playerUuid = "1234",
            minecraftAccessToken = token,
            nowEpochMs = 1_000
        )

        assertFalse(request.toString().contains(token))
        assertTrue(request.toString().contains("<redacted>"))
    }

    @Test
    fun requestIsConsumedExactlyOnce() {
        val root = Files.createTempDirectory("avero-launch-request-").toFile()
        try {
            val store = GameLaunchRequestStore(root)
            val request = GameLaunchRequest.create(
                versionId = "1.21.4",
                playerName = "Player",
                playerUuid = "abcd",
                minecraftAccessToken = "token",
                nowEpochMs = 10_000
            )
            store.write(request)

            val consumed = store.consume(
                request.requestId,
                nowEpochMs = 10_001
            )
            assertEquals(request, consumed)

            var failed = false
            try {
                store.consume(
                    request.requestId,
                    nowEpochMs = 10_002
                )
            } catch (_: IllegalArgumentException) {
                failed = true
            }
            assertTrue(failed)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun expiredRequestIsDeletedAndRejected() {
        val root = Files.createTempDirectory("avero-launch-request-").toFile()
        try {
            val store = GameLaunchRequestStore(
                filesRoot = root,
                maxAgeMs = 100
            )
            val request = GameLaunchRequest.create(
                versionId = "1.21.4",
                playerName = "Player",
                playerUuid = "abcd",
                minecraftAccessToken = "token",
                nowEpochMs = 1_000
            )
            store.write(request)

            var failed = false
            try {
                store.consume(
                    request.requestId,
                    nowEpochMs = 1_101
                )
            } catch (_: IllegalArgumentException) {
                failed = true
            }
            assertTrue(failed)

            val remaining = root.resolve(
                GameLaunchRequestStore.DIRECTORY_NAME
            ).listFiles().orEmpty()
            assertTrue(remaining.none { it.name == "${request.requestId}.json" })
        } finally {
            root.deleteRecursively()
        }
    }
}
