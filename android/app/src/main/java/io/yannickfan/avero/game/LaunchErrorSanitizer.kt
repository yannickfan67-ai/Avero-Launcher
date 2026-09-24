package io.yannickfan.avero.game

internal fun sanitizeLaunchError(t: Throwable): String {
    val raw = t.message?.takeIf { it.isNotBlank() }
        ?: t::class.java.simpleName

    return raw
        .replace(
            Regex("(?i)access[_ -]?token\\s*[:=]\\s*\\S+"),
            "accessToken=<redacted>"
        )
        .replace(
            Regex("(?i)refresh[_ -]?token\\s*[:=]\\s*\\S+"),
            "refreshToken=<redacted>"
        )
        .replace(
            Regex("(?i)client[_ -]?secret\\s*[:=]\\s*\\S+"),
            "clientSecret=<redacted>"
        )
        .replace(
            Regex("(?i)authorization\\s*[:=]\\s*\\S+"),
            "Authorization=<redacted>"
        )
        .replace(
            Regex("(?i)bearer\\s+\\S+"),
            "Bearer <redacted>"
        )
        .take(500)
}
