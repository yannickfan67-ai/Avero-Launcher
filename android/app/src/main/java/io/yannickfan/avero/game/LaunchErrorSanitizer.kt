package io.yannickfan.avero.game

internal fun sanitizeLaunchError(t: Throwable): String {
    val raw = t.message?.takeIf { it.isNotBlank() }
        ?: t::class.java.simpleName

    return raw
        .replace(
            Regex("(?i)(access[_ -]?token|refresh[_ -]?token|client[_ -]?secret|authorization|bearer)\\s*[:=]\\s*\\S+"),
            "$1=<redacted>"
        )
        .replace(
            Regex("(?i)bearer\\s+\\S+"),
            "Bearer <redacted>"
        )
        .take(500)
}
