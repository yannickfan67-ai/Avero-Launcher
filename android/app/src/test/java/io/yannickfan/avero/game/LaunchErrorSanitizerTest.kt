package io.yannickfan.avero.game

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchErrorSanitizerTest {
    @Test
    fun redactsTokenFormsFromLaunchErrors() {
        val error = IllegalStateException(
            "access_token=abc123 refresh-token: def456 " +
                "Authorization: Bearer ghi789 client_secret=secret"
        )

        assertEquals(
            "accessToken=<redacted> refreshToken=<redacted> " +
                "Authorization=<redacted> clientSecret=<redacted>",
            sanitizeLaunchError(error)
        )
    }
}
