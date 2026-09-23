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
    suspend fun parseIndex(indexFile: File): List<AssetObjectSpec> = withContext(Dispatchers.IO) {
        val root = JSONObject(indexFile.readText())
        val objects = root.getJSONObject("objects")
        buildList {
            val keys = objects.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val entry = objects.getJSONObject(name)
                add(
                    AssetObjectSpec(
                        logicalName = name,
                        hash = entry.getString("hash"),
                        size = entry.getLong("size")
                    )
                )
            }
        }
    }

    suspend fun install(
        indexFile: File,
        root: File,
        onProgress: (AssetInstallProgress) -> Unit = {}
    ): Int = coroutineScope {
        val assets = parseIndex(indexFile)
        val layout = InstanceLayout(root)
        val semaphore = Semaphore(parallelism)
        val completed = AtomicInteger(0)

        assets.map { asset ->
            async {
                semaphore.withPermit {
                    val destination = layout.assetObject(asset.hash)
                    if (!downloader.isValid(asset.downloadSpec, destination)) {
                        downloader.download(asset.downloadSpec, destination)
                    }
                    val done = completed.incrementAndGet()
                    onProgress(AssetInstallProgress(done, assets.size, asset.logicalName))
                }
            }
        }.awaitAll()

        assets.size
    }
}
