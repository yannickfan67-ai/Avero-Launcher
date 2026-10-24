package io.yannickfan.avero.downloads

import android.app.Application
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import io.yannickfan.avero.minecraft.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val root = File(application.filesDir, "minecraft").apply { mkdirs() }
    private val store = DownloadStore(File(application.filesDir, "download-workspace"))
    private val client = MinecraftManifestClient()
    private var worker: Job? = null
    private var active: Deferred<Unit>? = null
    private var paused = false
    private val lastProgress = AtomicLong(0)
    var manifest by mutableStateOf<VersionManifest?>(null); private set
    var status by mutableStateOf("正在載入工作區…"); private set
    var catalogStatus by mutableStateOf("官方版本目錄"); private set
    var busy by mutableStateOf(false); private set
    var loading by mutableStateOf(true); private set
    var activeName by mutableStateOf("尚無下載任務"); private set
    var detail by mutableStateOf("選擇版本後即可加入下載佇列。"); private set
    var progress by mutableFloatStateOf(0f); private set
    var selectedTab by mutableIntStateOf(0)
    val tasks = mutableStateListOf<DownloadTask>()
    val installed = mutableStateListOf<LocalVersion>()

    init { scope.launch {
        try {
            tasks.addAll(store.loadTasks()); paused = tasks.any { it.state == "Paused" }
            installed.addAll(store.localVersions())
            manifest = store.loadCatalog()
            refreshInternal()
            if (paused) status = "已保留未完成任務；選擇繼續佇列即可恢復。"
        } catch (e: Exception) { status = "載入失敗：${e.message}" }
        finally { loading = false }
    } }
    fun refresh() { if (loading) return; scope.launch { loading = true; try { refreshInternal() } finally { loading = false } } }
    private suspend fun refreshInternal() {
        try {
            val value = client.fetchManifest(); store.saveCatalog(value); manifest = value
            catalogStatus = "官方目錄 · ${value.versions.size} 個版本"; status = "下載限定版 · 檔案下載、校驗與修復。"
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            catalogStatus = if (manifest != null) "離線快取目錄；下載仍需要網路" else "版本目錄無法載入"
            status = "目錄更新失敗：${e.message}"
        }
    }
    fun enqueue(version: MinecraftVersionSummary, name: String = "") {
        if (loading) return
        scope.launch {
            try {
                if (tasks.any { it.version == version.id && it.state in setOf("Queued","Downloading","Paused") }) { status = "此版本已在佇列中。"; selectedTab = 1; return@launch }
                if (tasks.size >= 100) {
                    val oldest = tasks.firstOrNull { it.state == "Complete" } ?: error("佇列已滿，請先完成現有任務。")
                    tasks.remove(oldest)
                }
                val title = name.trim().ifBlank { "Minecraft ${version.id}" }; require(title.length <= 128) { "名稱最多 128 字元" }
                tasks.add(DownloadTask(UUID.randomUUID().toString(),version.id,title,if(paused) "Paused" else "Queued"))
                store.saveTasks(tasks.toList()); selectedTab=1; status="$title 已加入佇列。"; pump()
            } catch (e: Exception) { status=e.message ?: "無法加入任務" }
        }
    }
    fun repair(version: LocalVersion) {
        val summary=manifest?.versions?.firstOrNull { it.id == version.version }
        if(summary==null) { status="請更新版本目錄後再試。"; return }
        enqueue(summary,version.name)
    }
    private fun update(id: String, state: String, message: String = "") {
        val index=tasks.indexOfFirst { it.id == id }; if(index>=0) tasks[index]=tasks[index].copy(state=state,message=message.take(2048),updated=System.currentTimeMillis())
    }
    private fun pump() {
        if (paused || worker?.isActive == true) return
        worker=scope.launch {
            busy=true
            try {
                while(!paused) {
                    val task=tasks.firstOrNull { it.state == "Queued" } ?: break
                    update(task.id,"Downloading"); activeName=task.name; progress=0f; store.saveTasks(tasks.toList())
                    try {
                        val summary=manifest?.versions?.firstOrNull { it.id==task.version } ?: error("請先更新目錄。")
                        val work=scope.async<Unit>(Dispatchers.IO) {
                            val metadata=client.fetchVersion(summary)
                            require(StatFs(root.absolutePath).availableBytes >= (metadata.client.size ?: 0L) + 64L * 1024 * 1024) { "儲存空間不足" }
                            GameInstaller().installVanillaCore(summary,metadata,root) { p ->
                                report("核心檔案 ${p.completedFiles}/${p.totalFiles} · ${p.currentFile}",
                                    p.completedFiles.toFloat()/p.totalFiles.coerceAtLeast(1),p.downloadedBytes,p.totalBytes)
                            }
                            AssetInstaller().install(InstanceLayout(root).assetIndex(metadata.assetIndexId),root) { p ->
                                report("資源 ${p.completed}/${p.total} · ${p.current}",p.completed.toFloat()/p.total.coerceAtLeast(1),0,null)
                            }
                        }
                        active=work; work.await()
                        store.recordComplete(task.version,task.name)
                        installed.clear(); installed.addAll(store.localVersions())
                        update(task.id,"Complete","已完成核心與資源 SHA-1 校驗。"); progress=1f
                        status="${task.name} 下載完成。此版本不提供遊戲啟動。"
                    } catch (e: CancellationException) {
                        if(!scope.isActive) throw e
                        update(task.id,"Paused","保留已校驗檔案和可續傳的部分檔案。")
                    } catch(e: Exception) { update(task.id,"Failed",e.message ?: "下載失敗"); status="下載失敗；可選擇重試。" }
                    finally { active=null; store.saveTasks(tasks.toList()) }
                }
            } catch(e: Exception) { if(e is CancellationException) throw e; status="佇列錯誤：${e.message}"; paused=true }
            finally { busy=false }
        }
    }
    private fun report(text: String, value: Float, bytes: Long, total: Long?) {
        val now=System.currentTimeMillis(); if(now-lastProgress.get()<150 && value<1f) return; lastProgress.set(now)
        scope.launch { detail=text + if(total!=null) "\n${bytes/1024} / ${total/1024} KiB" else ""; progress=value.coerceIn(0f,1f) }
    }
    fun pause() {
        paused=true; active?.cancel()
        tasks.filter { it.state=="Queued" }.forEach { update(it.id,"Paused","等待繼續。") }
        scope.launch { try { store.saveTasks(tasks.toList()); status="佇列已暫停。" } catch(e: Exception) { status=e.message ?: "無法儲存" } }
    }
    fun resume() {
        if(busy) { status="請等待目前任務完成暫停。"; return }
        tasks.filter { it.state=="Paused" }.forEach { update(it.id,"Queued") }; paused=false
        scope.launch { try { store.saveTasks(tasks.toList()); pump() } catch(e: Exception) { status=e.message ?: "無法儲存" } }
    }
    fun retry(task: DownloadTask) {
        if(task.state in setOf("Queued","Downloading")) return
        if(tasks.any { it.id!=task.id && it.version==task.version && it.state in setOf("Queued","Downloading","Paused") }) { status="此版本已有未完成任務。"; return }
        update(task.id,if(paused) "Paused" else "Queued")
        scope.launch { try { store.saveTasks(tasks.toList()); pump() } catch(e: Exception) { status=e.message ?: "無法儲存" } }
    }
    suspend fun exportReport() = store.report(tasks.toList())
    fun reportExport(message: String) { status=message }
    override fun onCleared() { scope.cancel() }
}
