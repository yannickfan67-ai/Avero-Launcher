package io.yannickfan.avero.minecraft
import java.net.URI
import java.net.HttpURLConnection
import java.net.URL

object DownloadPolicy {
    private val hosts = setOf("piston-meta.mojang.com", "piston-data.mojang.com", "launchermeta.mojang.com", "launcher.mojang.com", "libraries.minecraft.net", "resources.download.minecraft.net")
    fun validate(url: String, allowLocalHttp: Boolean = false): URL {
        val uri = URI(url)
        val local = allowLocalHttp && uri.scheme == "http" && uri.host in setOf("127.0.0.1", "localhost", "[::1]")
        require(local || (uri.scheme == "https" && uri.host?.lowercase() in hosts && uri.port in setOf(-1,443))) { "Untrusted download address" }
        require(uri.rawUserInfo == null && uri.rawFragment == null) { "Download URL contains credentials or a fragment" }
        return uri.toURL()
    }
    fun open(url: String, allowLocalHttp: Boolean = false, range: Long = 0): HttpURLConnection {
        var current = validate(url, allowLocalHttp)
        repeat(6) { redirect ->
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000; readTimeout = 30000; instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Avero-Launcher/0.2.0-download-only")
                if (range > 0) setRequestProperty("Range", "bytes=$range-")
            }
            try {
                val code = connection.responseCode
                if (code !in setOf(301,302,303,307,308)) return connection
                require(redirect < 5) { "Too many download redirects" }
                val location = connection.getHeaderField("Location") ?: error("Redirect without Location")
                current = validate(URL(current, location).toString(), allowLocalHttp)
            } catch (t: Throwable) { connection.disconnect(); throw t }
            connection.disconnect()
        }
        error("Download redirect loop")
    }
}
