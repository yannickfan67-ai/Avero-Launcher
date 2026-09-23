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
            val gameArguments = flattenSimpleArguments(arguments?.optJSONArray("game"))
                .ifEmpty {
                    root.optString("minecraftArguments")
                        .takeIf { it.isNotBlank() }
                        ?.split(' ')
                        .orEmpty()
                }
            val jvmArguments = flattenSimpleArguments(arguments?.optJSONArray("jvm"))

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

        private fun flattenSimpleArguments(array: JSONArray?): List<String> {
            if (array == null) return emptyList()
            return buildList {
                for (i in 0 until array.length()) {
                    when (val value = array.get(i)) {
                        is String -> add(value)
                        // Rule-controlled objects are intentionally deferred to the rule evaluator.
                        is JSONObject -> Unit
                    }
                }
            }
        }
    }
}
