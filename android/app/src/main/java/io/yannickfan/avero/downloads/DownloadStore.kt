package io.yannickfan.avero.downloads

import io.yannickfan.avero.minecraft.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

data class DownloadTask(val id: String, val version: String, val name: String, val state: String,
    val updated: Long = System.currentTimeMillis(), val message: String = "")

data class LocalVersion(val version: String, val name: String, val installedAt: Long)

class DownloadStore(private val root: File) {
    private val mutex = Mutex()
    private fun file(name: String): File {
        root.mkdirs()
        require(!Files.isSymbolicLink(root.toPath()))
        return File(root, name).also { require(!Files.isSymbolicLink(it.toPath())) }
    }
    private fun read(name: String, limit: Long = 16L * 1024 * 1024): JSONObject? {
        val f = file(name); if (!f.exists()) return null
        require(f.isFile && f.length() <= limit) { "Invalid saved download data" }
        return JSONObject(f.readText())
    }
    private fun write(name: String, json: JSONObject): File {
        val target = file(name); val temp = File.createTempFile("avero-state-", ".tmp", root)
        try {
            FileOutputStream(temp).use { stream -> stream.write(json.toString(2).toByteArray()); stream.fd.sync() }
            try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        } finally { temp.delete() }
        return target
    }
    suspend fun loadTasks(): List<DownloadTask> = withContext(Dispatchers.IO) { mutex.withLock {
        val array = read("downloads.json")?.getJSONArray("tasks") ?: return@withLock emptyList()
        require(array.length() <= 100)
        val result = (0 until array.length()).map { i ->
            val x = array.getJSONObject(i); UUID.fromString(x.getString("id")); validVersion(x.getString("version"))
            val state = x.getString("state"); require(state in setOf("Queued","Downloading","Paused","Failed","Complete"))
            val name = x.getString("name"); val message=x.optString("message"); require(name.length in 1..128 && message.length <= 2048)
            DownloadTask(x.getString("id"),x.getString("version"),name,
                if (state in setOf("Downloading","Queued")) "Paused" else state,x.getLong("updated"),message)
        }
        require(result.map { it.id }.distinct().size == result.size); result
    } }
    suspend fun saveTasks(tasks: List<DownloadTask>) = withContext(Dispatchers.IO) { mutex.withLock {
        require(tasks.size <= 100)
        write("downloads.json", JSONObject().put("tasks", JSONArray().apply { tasks.forEach { x -> put(JSONObject()
            .put("id",x.id).put("version",x.version).put("name",x.name).put("state",x.state).put("updated",x.updated).put("message",x.message)) } }))
    } }
    suspend fun saveCatalog(manifest: VersionManifest) = withContext(Dispatchers.IO) { mutex.withLock {
        val versions = JSONArray(); manifest.versions.forEach { x -> versions.put(JSONObject().put("id",x.id).put("type",x.type)
            .put("url",x.metadataUrl).put("sha1",x.sha1).put("releaseTime",x.releaseTime)) }
        write("catalog.json",JSONObject().put("fetchedAt",System.currentTimeMillis()).put("latest",JSONObject().put("release",manifest.latestRelease).put("snapshot",manifest.latestSnapshot)).put("versions",versions))
    } }
    suspend fun loadCatalog(): VersionManifest? = withContext(Dispatchers.IO) { mutex.withLock {
        val json = read("catalog.json") ?: return@withLock null
        MinecraftManifestClient.parseManifest(json).also { manifest ->
            require(manifest.versions.size <= 10000)
            manifest.versions.forEach { validVersion(it.id); DownloadPolicy.validate(it.metadataUrl); require(it.sha1?.matches(Regex("[a-fA-F0-9]{40}")) == true) }
        }
    } }
    suspend fun localVersions(): List<LocalVersion> = withContext(Dispatchers.IO) { mutex.withLock {
        val array = read("installed.json")?.getJSONArray("versions") ?: return@withLock emptyList()
        require(array.length() <= 10000)
        (0 until array.length()).map { val x=array.getJSONObject(it); validVersion(x.getString("version")); LocalVersion(x.getString("version"),x.getString("name"),x.getLong("installedAt")) }
    } }
    suspend fun recordComplete(version: String, name: String) {
        val existing = localVersions().filter { it.version != version } + LocalVersion(version,name,System.currentTimeMillis())
        withContext(Dispatchers.IO) { mutex.withLock { write("installed.json",JSONObject().put("versions",JSONArray().apply {
            existing.forEach { put(JSONObject().put("version",it.version).put("name",it.name).put("installedAt",it.installedAt)) }
        })) } }
    }
    suspend fun report(tasks: List<DownloadTask>): String = withContext(Dispatchers.IO) { mutex.withLock {
        JSONObject().put("product","Avero").put("version","0.2.0-download-only").put("authentication",false).put("gameplay",false)
            .put("createdAt",System.currentTimeMillis()).put("jobs",JSONArray().apply { tasks.forEach {
                put(JSONObject().put("version",it.version).put("state",it.state).put("updated",it.updated))
            } }).toString(2)
    } }
    private fun validVersion(value: String) { require(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,159}"))) { "Unsafe version ID" } }
}
