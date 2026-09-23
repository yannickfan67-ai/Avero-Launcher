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
        parseManifest(JSONObject(getText(manifestUrl)))
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
            val client = parseDownload(downloads.getJSONObject("client"))
            val asset = root.getJSONObject("assetIndex")

            val librariesJson = root.optJSONArray("libraries") ?: JSONArray()
            val libraries = buildList {
                for (i in 0 until librariesJson.length()) {
                    val lib = librariesJson.getJSONObject(i)
                    val libDownloads = lib.optJSONObject("downloads")
                    val artifact = libDownloads?.optJSONObject("artifact")?.let(::parseDownload)

                    val classifiers = buildMap {
                        val classifiersJson = libDownloads?.optJSONObject("classifiers")
                        if (classifiersJson != null) {
                            val keys = classifiersJson.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                put(key, parseDownload(classifiersJson.getJSONObject(key)))
                            }
                        }
                    }

                    val natives = buildMap {
                        val nativeJson = lib.optJSONObject("natives")
                        if (nativeJson != null) {
                            val keys = nativeJson.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                put(key, nativeJson.getString(key))
                            }
                        }
                    }

                    val extractExcludes = buildList {
                        val excludes = lib.optJSONObject("extract")?.optJSONArray("exclude")
                        if (excludes != null) {
                            for (j in 0 until excludes.length()) {
                                add(excludes.getString(j))
                            }
                        }
                    }

                    add(
                        LibrarySpec(
                            name = lib.getString("name"),
                            artifact = artifact,
                            classifiers = classifiers,
                            natives = natives,
                            rules = parseRules(lib.optJSONArray("rules")),
                            extractExcludes = extractExcludes
                        )
                    )
                }
            }

            val logging = root.optJSONObject("logging")
                ?.optJSONObject("client")
                ?.let { clientLogging ->
                    val file = clientLogging.getJSONObject("file")
                    LoggingSpec(
                        argument = clientLogging.getString("argument"),
                        fileId = file.getString("id"),
                        file = parseDownload(file)
                    )
                }

            val arguments = root.optJSONObject("arguments")
            val gameArguments = parseArguments(arguments?.optJSONArray("game"))
                .ifEmpty {
                    root.optString("minecraftArguments")
                        .takeIf { it.isNotBlank() }
                        ?.split(' ')
                        ?.map { ConditionalArgument(listOf(it)) }
                        .orEmpty()
                }
            val jvmArguments = parseArguments(arguments?.optJSONArray("jvm"))

            return MinecraftVersionMetadata(
                id = root.getString("id"),
                type = root.optString("type", "release"),
                mainClass = root.getString("mainClass"),
                javaMajorVersion = root.optJSONObject("javaVersion")?.optInt("majorVersion", 8) ?: 8,
                client = client,
                assetIndexId = asset.optString("id", root.optString("assets", "legacy")),
                assetIndex = parseDownload(asset),
                libraries = libraries,
                logging = logging,
                gameArguments = gameArguments,
                jvmArguments = jvmArguments
            )
        }

        private fun parseDownload(value: JSONObject): DownloadSpec =
            DownloadSpec(
                url = value.getString("url"),
                sha1 = value.optString("sha1").takeIf(String::isNotBlank),
                size = value.optLong("size").takeIf { it > 0 },
                path = value.optString("path").takeIf(String::isNotBlank)
            )

        private fun parseArguments(array: JSONArray?): List<ConditionalArgument> {
            if (array == null) return emptyList()
            return buildList {
                for (i in 0 until array.length()) {
                    when (val value = array.get(i)) {
                        is String -> add(ConditionalArgument(listOf(value)))
                        is JSONObject -> {
                            val values = when (val raw = value.get("value")) {
                                is String -> listOf(raw)
                                is JSONArray -> buildList {
                                    for (j in 0 until raw.length()) add(raw.getString(j))
                                }
                                else -> emptyList()
                            }
                            add(
                                ConditionalArgument(
                                    values = values,
                                    rules = parseRules(value.optJSONArray("rules"))
                                )
                            )
                        }
                    }
                }
            }
        }

        private fun parseRules(array: JSONArray?): List<RuleSpec> {
            if (array == null) return emptyList()
            return buildList {
                for (i in 0 until array.length()) {
                    val raw = array.getJSONObject(i)
                    val osRaw = raw.optJSONObject("os")
                    val featuresRaw = raw.optJSONObject("features")

                    val features = buildMap {
                        if (featuresRaw != null) {
                            val keys = featuresRaw.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                put(key, featuresRaw.getBoolean(key))
                            }
                        }
                    }

                    add(
                        RuleSpec(
                            action = if (raw.optString("action", "allow") == "disallow") {
                                RuleAction.DISALLOW
                            } else {
                                RuleAction.ALLOW
                            },
                            os = osRaw?.let {
                                OsRule(
                                    name = it.optString("name").takeIf(String::isNotBlank),
                                    versionRegex = it.optString("version").takeIf(String::isNotBlank),
                                    archRegex = it.optString("arch").takeIf(String::isNotBlank)
                                )
                            },
                            features = features
                        )
                    )
                }
            }
        }
    }
}
