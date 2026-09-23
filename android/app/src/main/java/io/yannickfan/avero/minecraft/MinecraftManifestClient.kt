package io.yannickfan.avero.minecraft

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MinecraftManifestClient(
    private val manifestUrl: String = VERSION_MANIFEST_V2
) {
    suspend fun fetchManifest(): VersionManifest = withContext(Dispatchers.IO) {
        val root = JSONObject(getText(manifestUrl))
        parseManifest(root)
    }

    suspend fun fetchVersion(summary: MinecraftVersionSummary): MinecraftVersionMetadata =
        fetchVersion(summary.metadataUrl)

    suspend fun fetchVersion(metadataUrl: String): MinecraftVersionMetadata =
        withContext(Dispatchers.IO) {
            parseVersion(JSONObject(getText(metadataUrl)))
        }

    private fun getText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Avero-Launcher/0.1")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code from $url")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val VERSION_MANIFEST_V2 =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

        fun parseManifest(root: JSONObject): VersionManifest {
            val latest = root.getJSONObject("latest")
            val array = root.getJSONArray("versions")
            val versions = buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        MinecraftVersionSummary(
                            id = item.getString("id"),
                            type = item.optString("type", "release"),
                            metadataUrl = item.getString("url"),
                            sha1 = item.optString("sha1").takeIf { it.isNotBlank() },
                            releaseTime = item.optString("releaseTime").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            return VersionManifest(
                latestRelease = latest.getString("release"),
                latestSnapshot = latest.getString("snapshot"),
                versions = versions
            )
        }

        fun parseVersion(root: JSONObject): MinecraftVersionMetadata {
            val downloads = root.getJSONObject("downloads")
            val client = downloads.getJSONObject("client")
            val asset = root.getJSONObject("assetIndex")

            val librariesJson = root.optJSONArray("libraries") ?: JSONArray()
            val libraries = buildList {
                for (i in 0 until librariesJson.length()) {
                    val lib = librariesJson.getJSONObject(i)
                    val artifact = lib.optJSONObject("downloads")?.optJSONObject("artifact")
                    add(
                        LibrarySpec(
                            name = lib.getString("name"),
                            artifact = artifact?.let {
                                DownloadSpec(
                                    url = it.getString("url"),
                                    sha1 = it.optString("sha1").takeIf(String::isNotBlank),
                                    size = it.optLong("size").takeIf { size -> size > 0 },
                                    path = it.optString("path").takeIf(String::isNotBlank)
                                )
                            }
                        )
                    )
                }
            }

            val arguments = root.optJSONObject("arguments")
            val gameArguments = parseArguments(arguments?.optJSONArray("game"))
                .ifEmpty {
                    splitLegacyArguments(root.optString("minecraftArguments"))
                        .map { MinecraftArgument(values = listOf(it)) }
                }
            val jvmArguments = parseArguments(arguments?.optJSONArray("jvm"))

            return MinecraftVersionMetadata(
                id = root.getString("id"),
                type = root.optString("type", "release"),
                mainClass = root.getString("mainClass"),
                javaMajorVersion = root.optJSONObject("javaVersion")?.optInt("majorVersion", 8) ?: 8,
                client = DownloadSpec(
                    url = client.getString("url"),
                    sha1 = client.optString("sha1").takeIf(String::isNotBlank),
                    size = client.optLong("size").takeIf { it > 0 }
                ),
                assetIndexId = asset.optString("id", root.optString("assets", "legacy")),
                assetIndex = DownloadSpec(
                    url = asset.getString("url"),
                    sha1 = asset.optString("sha1").takeIf(String::isNotBlank),
                    size = asset.optLong("size").takeIf { it > 0 }
                ),
                libraries = libraries,
                gameArguments = gameArguments,
                jvmArguments = jvmArguments
            )
        }

        private fun parseArguments(array: JSONArray?): List<MinecraftArgument> {
            if (array == null) return emptyList()

            return buildList {
                for (i in 0 until array.length()) {
                    when (val value = array.get(i)) {
                        is String -> add(MinecraftArgument(values = listOf(value)))
                        is JSONObject -> {
                            val values = parseArgumentValue(value.opt("value"))
                            if (values.isNotEmpty()) {
                                add(
                                    MinecraftArgument(
                                        values = values,
                                        rules = parseRules(value.optJSONArray("rules"))
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        private fun parseArgumentValue(value: Any?): List<String> =
            when (value) {
                is String -> listOf(value)
                is JSONArray -> buildList {
                    for (i in 0 until value.length()) {
                        val item = value.opt(i)
                        if (item is String) add(item)
                    }
                }
                else -> emptyList()
            }

        private fun parseRules(array: JSONArray?): List<ArgumentRule> {
            if (array == null) return emptyList()

            return buildList {
                for (i in 0 until array.length()) {
                    val rule = array.getJSONObject(i)
                    val action = when (rule.optString("action", "allow").lowercase()) {
                        "allow" -> RuleAction.ALLOW
                        "disallow" -> RuleAction.DISALLOW
                        else -> error("Unsupported Mojang rule action: \${rule.optString("action")}")
                    }

                    val osJson = rule.optJSONObject("os")
                    val os = osJson?.let {
                        RuleOs(
                            name = it.optString("name").takeIf(String::isNotBlank),
                            arch = it.optString("arch").takeIf(String::isNotBlank),
                            version = it.optString("version").takeIf(String::isNotBlank)
                        )
                    }

                    val featuresJson = rule.optJSONObject("features")
                    val features = buildMap {
                        if (featuresJson != null) {
                            val keys = featuresJson.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                put(key, featuresJson.optBoolean(key, false))
                            }
                        }
                    }

                    add(ArgumentRule(action = action, os = os, features = features))
                }
            }
        }

        internal fun splitLegacyArguments(value: String): List<String> {
            if (value.isBlank()) return emptyList()

            val result = mutableListOf<String>()
            val current = StringBuilder()
            var quote: Char? = null
            var escaped = false

            fun flush() {
                if (current.isNotEmpty()) {
                    result += current.toString()
                    current.setLength(0)
                }
            }

            for (char in value) {
                if (escaped) {
                    current.append(char)
                    escaped = false
                    continue
                }

                if (char == '\\') {
                    escaped = true
                    continue
                }

                if (quote != null) {
                    if (char == quote) quote = null else current.append(char)
                    continue
                }

                when (char) {
                    '"', '\'' -> quote = char
                    ' ', '\t', '\n', '\r' -> flush()
                    else -> current.append(char)
                }
            }

            if (escaped) current.append('\\')
            require(quote == null) { "Unterminated quote in legacy minecraftArguments" }
            flush()
            return result
        }
    }
}
