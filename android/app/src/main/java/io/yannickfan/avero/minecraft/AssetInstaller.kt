package io.yannickfan.avero.minecraft

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class AssetObjectSpec(
    val logicalName: String,
    val hash: String,
    val size: Long
) {
    val downloadSpec: DownloadSpec
        get() = DownloadSpec(
            url = "https://resources.download.minecraft.net/${hash.take(2)}/$hash",
            sha1 = hash,
            size = size
        )
}

data class AssetInstallProgress(
    val completed: Int,
    val total: Int,
    val current: String
)

class AssetInstaller(
    private val downloader: FileDownloader = FileDownloader(),
    private val parallelism: Int = 4
) {
    init { require(parallelism in 1..8) }

    suspend fun parseIndex(indexFile: File): List<AssetObjectSpec> = withContext(Dispatchers.IO) {
        require(indexFile.length() <= 64L * 1024 * 1024) { "Asset index is too large" }
        val root = JSONObject(indexFile.readText())
        val objects = root.getJSONObject("objects")
        require(objects.length() <= 100000) { "Too many asset entries" }
        buildList {
            val keys = objects.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val entry = objects.getJSONObject(name)
                val hash = entry.getString("hash")
                require(hash.matches(Regex("[a-fA-F0-9]{40}"))) { "Invalid asset hash" }
                require(entry.getLong("size") in 0..FileDownloader.MAX_FILE_BYTES) { "Invalid asset size" }
                add(
                    AssetObjectSpec(
                        logicalName = name,
                        hash = hash,
                        size = entry.getLong("size")
                    )
                )
            }
        }
    }

    suspend fun isInstalled(
        asset: AssetObjectSpec,
        root: File
    ): Boolean =
        downloader.isValid(
            asset.downloadSpec,
            InstanceLayout(root).assetObject(asset.hash)
        )

    suspend fun install(
        indexFile: File,
        root: File,
        onProgress: (AssetInstallProgress) -> Unit = {}
    ): Int = coroutineScope {
        val assets = parseIndex(indexFile).distinctBy { it.hash }
        val layout = InstanceLayout(root)
        val next = AtomicInteger(0)
        val completed = AtomicInteger(0)

        List(parallelism) {
            async {
                while (true) {
                    val index = next.getAndIncrement()
                    if (index >= assets.size) break
                    val asset = assets[index]
                    val destination = layout.assetObject(asset.hash)
                    if (!downloader.isValid(asset.downloadSpec, destination)) downloader.download(asset.downloadSpec, destination)
                    val done = completed.incrementAndGet()
                    onProgress(AssetInstallProgress(done, assets.size, asset.logicalName))
                }
            }
        }.awaitAll()

        assets.size
    }
}
