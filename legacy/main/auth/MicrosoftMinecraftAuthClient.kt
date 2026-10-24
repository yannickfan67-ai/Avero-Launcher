package io.yannickfan.avero.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class DeviceCodeInfo(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
    val message: String
)

data class MicrosoftOAuthToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Int
)

data class MinecraftProfile(
    val id: String,
    val name: String
)

data class MinecraftEntitlements(
    val names: List<String>
) {
    val hasAnyEntitlement: Boolean get() = names.isNotEmpty()
}

data class AuthenticatedMinecraftAccount(
    val profile: MinecraftProfile,
    val minecraftAccessToken: String,
    val minecraftTokenExpiresInSeconds: Int,
    val microsoftRefreshToken: String?,
    val entitlements: MinecraftEntitlements
)

class AuthHttpException(
    val statusCode: Int,
    val responseBody: String
) : IllegalStateException("Authentication HTTP $statusCode")

class MicrosoftMinecraftAuthClient(
    private val clientId: String
) {
    init {
        require(clientId.isNotBlank()) {
            "Avero Microsoft client ID is not configured"
        }
    }

    suspend fun requestDeviceCode(): DeviceCodeInfo = withContext(Dispatchers.IO) {
        val response = postForm(
            DEVICE_CODE_ENDPOINT,
            mapOf(
                "client_id" to clientId,
                "scope" to "XboxLive.signin offline_access"
            )
        )
        val json = JSONObject(response)
        DeviceCodeInfo(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUri = json.getString("verification_uri"),
            expiresInSeconds = json.getInt("expires_in"),
            intervalSeconds = json.optInt("interval", 5).coerceAtLeast(1),
            message = json.optString("message")
        )
    }

    suspend fun awaitMicrosoftToken(info: DeviceCodeInfo): MicrosoftOAuthToken {
        val started = System.currentTimeMillis()
        var intervalSeconds = info.intervalSeconds

        while ((System.currentTimeMillis() - started) < info.expiresInSeconds * 1000L) {
            delay(intervalSeconds * 1000L)
            when (val result = pollDeviceToken(info.deviceCode)) {
                is DeviceTokenPoll.Authorized -> return result.token
                DeviceTokenPoll.Pending -> Unit
                DeviceTokenPoll.SlowDown -> intervalSeconds += 5
                is DeviceTokenPoll.Failed -> error(
                    buildString {
                        append("Microsoft authorization failed: ")
                        append(result.error)
                        result.description?.let { append(" — ").append(it) }
                    }
                )
            }
        }

        error("Microsoft device code expired")
    }

    suspend fun refreshMicrosoftToken(refreshToken: String): MicrosoftOAuthToken =
        withContext(Dispatchers.IO) {
            val json = JSONObject(
                postForm(
                    TOKEN_ENDPOINT,
                    mapOf(
                        "client_id" to clientId,
                        "grant_type" to "refresh_token",
                        "refresh_token" to refreshToken,
                        "scope" to "XboxLive.signin offline_access"
                    )
                )
            )
            parseMicrosoftToken(json)
        }

    suspend fun authenticateMinecraft(
        microsoftToken: MicrosoftOAuthToken
    ): AuthenticatedMinecraftAccount = withContext(Dispatchers.IO) {
        val xbl = authenticateXboxLive(microsoftToken.accessToken)
        val xsts = authorizeXsts(xbl.token)
        val minecraft = loginMinecraft(xsts.userHash, xsts.token)
        val entitlements = fetchEntitlements(minecraft.accessToken)
        val profile = fetchProfile(minecraft.accessToken)

        AuthenticatedMinecraftAccount(
            profile = profile,
            minecraftAccessToken = minecraft.accessToken,
            minecraftTokenExpiresInSeconds = minecraft.expiresInSeconds,
            microsoftRefreshToken = microsoftToken.refreshToken,
            entitlements = entitlements
        )
    }

    private suspend fun pollDeviceToken(deviceCode: String): DeviceTokenPoll =
        withContext(Dispatchers.IO) {
            val response = request(
                TOKEN_ENDPOINT,
                "POST",
                "application/x-www-form-urlencoded",
                formBody(
                    mapOf(
                        "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                        "client_id" to clientId,
                        "device_code" to deviceCode
                    )
                )
            )

            if (response.status in 200..299) {
                return@withContext DeviceTokenPoll.Authorized(
                    parseMicrosoftToken(JSONObject(response.body))
                )
            }

            val json = runCatching { JSONObject(response.body) }.getOrNull()
            val error = json?.optString("error").orEmpty()
            val description = json?.optString("error_description")?.takeIf { it.isNotBlank() }

            when (error) {
                "authorization_pending" -> DeviceTokenPoll.Pending
                "slow_down" -> DeviceTokenPoll.SlowDown
                "authorization_declined",
                "expired_token",
                "bad_verification_code" -> DeviceTokenPoll.Failed(error, description)
                else -> throw AuthHttpException(response.status, response.body)
            }
        }

    private fun authenticateXboxLive(msAccessToken: String): XboxToken {
        val payload = JSONObject()
            .put(
                "Properties",
                JSONObject()
                    .put("AuthMethod", "RPS")
                    .put("SiteName", "user.auth.xboxlive.com")
                    .put("RpsTicket", "d=$msAccessToken")
            )
            .put("RelyingParty", "http://auth.xboxlive.com")
            .put("TokenType", "JWT")

        return parseXboxToken(postJson(XBL_AUTH_ENDPOINT, payload))
    }

    private fun authorizeXsts(xblToken: String): XboxToken {
        val payload = JSONObject()
            .put(
                "Properties",
                JSONObject()
                    .put("SandboxId", "RETAIL")
                    .put("UserTokens", org.json.JSONArray().put(xblToken))
            )
            .put("RelyingParty", "rp://api.minecraftservices.com/")
            .put("TokenType", "JWT")

        return parseXboxToken(postJson(XSTS_AUTH_ENDPOINT, payload))
    }

    private fun loginMinecraft(userHash: String, xstsToken: String): MinecraftToken {
        val payload = JSONObject()
            .put("identityToken", "XBL3.0 x=$userHash;$xstsToken")

        val json = JSONObject(postJson(MC_LOGIN_ENDPOINT, payload))
        return MinecraftToken(
            accessToken = json.getString("access_token"),
            expiresInSeconds = json.optInt("expires_in", 0)
        )
    }

    private fun fetchEntitlements(mcAccessToken: String): MinecraftEntitlements {
        val json = JSONObject(
            getJson(
                MC_ENTITLEMENTS_ENDPOINT,
                mapOf("Authorization" to "Bearer $mcAccessToken")
            )
        )
        val items = json.optJSONArray("items")
        val names = buildList {
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    item.optString("name")
                        .takeIf { it.isNotBlank() }
                        ?.let(::add)
                }
            }
        }
        return MinecraftEntitlements(names)
    }

    private fun fetchProfile(mcAccessToken: String): MinecraftProfile {
        val json = JSONObject(
            getJson(
                MC_PROFILE_ENDPOINT,
                mapOf("Authorization" to "Bearer $mcAccessToken")
            )
        )
        return MinecraftProfile(
            id = json.getString("id"),
            name = json.getString("name")
        )
    }

    private fun parseMicrosoftToken(json: JSONObject): MicrosoftOAuthToken =
        MicrosoftOAuthToken(
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
            expiresInSeconds = json.optInt("expires_in", 0)
        )

    private fun parseXboxToken(body: String): XboxToken {
        val json = JSONObject(body)
        val claims = json.getJSONObject("DisplayClaims")
            .getJSONArray("xui")
            .getJSONObject(0)
        return XboxToken(
            token = json.getString("Token"),
            userHash = claims.getString("uhs")
        )
    }

    private fun postForm(url: String, fields: Map<String, String>): String {
        val response = request(
            url = url,
            method = "POST",
            contentType = "application/x-www-form-urlencoded",
            body = formBody(fields)
        )
        if (response.status !in 200..299) {
            throw AuthHttpException(response.status, response.body)
        }
        return response.body
    }

    private fun postJson(url: String, json: JSONObject): String {
        val response = request(
            url = url,
            method = "POST",
            contentType = "application/json",
            body = json.toString()
        )
        if (response.status !in 200..299) {
            throw AuthHttpException(response.status, response.body)
        }
        return response.body
    }

    private fun getJson(url: String, headers: Map<String, String>): String {
        val response = request(
            url = url,
            method = "GET",
            contentType = null,
            body = null,
            headers = headers
        )
        if (response.status !in 200..299) {
            throw AuthHttpException(response.status, response.body)
        }
        return response.body
    }

    private fun request(
        url: String,
        method: String,
        contentType: String?,
        body: String?,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Avero-Launcher/0.1")
            contentType?.let { setRequestProperty("Content-Type", it) }
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (body != null) doOutput = true
        }

        try {
            if (body != null) {
                connection.outputStream.use {
                    it.write(body.toByteArray(StandardCharsets.UTF_8))
                }
            }

            val status = connection.responseCode
            val input = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseBody = input?.bufferedReader()?.use { it.readText() }.orEmpty()
            return HttpResponse(status, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun formBody(fields: Map<String, String>): String =
        fields.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private sealed interface DeviceTokenPoll {
        data class Authorized(val token: MicrosoftOAuthToken) : DeviceTokenPoll
        data object Pending : DeviceTokenPoll
        data object SlowDown : DeviceTokenPoll
        data class Failed(
            val error: String,
            val description: String?
        ) : DeviceTokenPoll
    }

    private data class XboxToken(
        val token: String,
        val userHash: String
    )

    private data class MinecraftToken(
        val accessToken: String,
        val expiresInSeconds: Int
    )

    private data class HttpResponse(
        val status: Int,
        val body: String
    )

    companion object {
        private const val DEVICE_CODE_ENDPOINT =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
        private const val TOKEN_ENDPOINT =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
        private const val XBL_AUTH_ENDPOINT =
            "https://user.auth.xboxlive.com/user/authenticate"
        private const val XSTS_AUTH_ENDPOINT =
            "https://xsts.auth.xboxlive.com/xsts/authorize"
        private const val MC_LOGIN_ENDPOINT =
            "https://api.minecraftservices.com/authentication/login_with_xbox"
        private const val MC_ENTITLEMENTS_ENDPOINT =
            "https://api.minecraftservices.com/entitlements/mcstore"
        private const val MC_PROFILE_ENDPOINT =
            "https://api.minecraftservices.com/minecraft/profile"
    }
}
