package io.yannickfan.avero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.yannickfan.avero.downloads.*
import io.yannickfan.avero.minecraft.*
import io.yannickfan.avero.ui.theme.AveroTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val model: DownloadsViewModel by viewModels()
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) lifecycleScope.launch {
            try {
                val report=model.exportReport()
                withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri,"wt")?.use { it.write(report.toByteArray()) } ?: error("無法開啟輸出檔案") }
                model.reportExport("下載報告已匯出。")
            } catch(e: Exception) { model.reportExport("匯出失敗：${e.message}") }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AveroTheme { DownloadApp(model) { export.launch("Avero-download-report.json") } } }
    }
}

@Composable private fun DownloadApp(model: DownloadsViewModel, exportReport: () -> Unit) {
    val tabs=listOf("探索","下載","本機","關於")
    Scaffold(containerColor=MaterialTheme.colorScheme.background,
        bottomBar={ NavigationBar { tabs.forEachIndexed { i,label ->
            NavigationBarItem(selected=model.selectedTab==i,onClick={model.selectedTab=i},icon={Text(listOf("↓","≡","▣","i")[i],fontSize=22.sp)},label={Text(label)})
        } } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=20.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Column { Text("Avero",fontSize=30.sp,fontWeight=FontWeight.Bold); Text("下載限定版",color=MaterialTheme.colorScheme.primary) }
                Text("0.2.0",modifier=Modifier.padding(top=8.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Text(model.status,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=3)
            Spacer(Modifier.height(12.dp))
            when(model.selectedTab) {
                0 -> Catalog(model)
                1 -> Queue(model,exportReport)
                2 -> LocalFiles(model)
                else -> About()
            }
        }
    }
}

@Composable private fun Catalog(model: DownloadsViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var channel by rememberSaveable { mutableIntStateOf(0) }
    var naming by remember { mutableStateOf<MinecraftVersionSummary?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    val channels=listOf("正式版","快照","歷史","全部")
    val versions=model.manifest?.versions.orEmpty().filter { v ->
        (when(channel) { 0 -> v.type=="release"; 1 -> v.type=="snapshot"; 2 -> v.type in setOf("old_alpha","old_beta"); else -> true }) && v.id.contains(query.trim(),true)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            StatTile("正式版",model.manifest?.latestRelease ?: "—",Modifier.weight(1f))
            StatTile("快照",model.manifest?.latestSnapshot ?: "—",Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(query,{query=it},label={Text("搜尋版本，例如 1.21.4")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) { channels.forEachIndexed { i,label -> FilterChip(selected=channel==i,onClick={channel=i},label={Text(label)}) } }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(model.catalogStatus,style=MaterialTheme.typography.labelSmall,modifier=Modifier.weight(1f).padding(top=12.dp))
            TextButton(onClick=model::refresh,enabled=!model.loading) { Text(if(model.loading) "更新中…" else "更新") }
        }
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=16.dp)) {
            if(versions.isEmpty()) item { Text(if(model.loading) "正在取得官方目錄…" else "沒有符合條件的版本。",modifier=Modifier.padding(20.dp)) }
            items(versions,key={it.id}) { version ->
                Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text(version.id,fontSize=19.sp,fontWeight=FontWeight.SemiBold); Text(version.releaseTime?.take(10) ?: version.type,style=MaterialTheme.typography.bodySmall) }
                    FilledTonalButton(onClick={naming=version;name=""},enabled=!model.loading) { Text("下載") }
                } }
            }
        }
    }
    naming?.let { version -> AlertDialog(onDismissRequest={naming=null},title={Text("下載 ${version.id}")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("從官方來源取得客戶端、程式庫及資源，並校驗 SHA-1。此版本不提供遊戲啟動。")
            OutlinedTextField(name,{name=it.take(128)},label={Text("名稱（選填）")},singleLine=true)
            Text("下載不代表取得遊戲授權。遊玩仍需合法持有 Java Edition。",style=MaterialTheme.typography.bodySmall)
        }},confirmButton={TextButton(onClick={model.enqueue(version,name);naming=null}) {Text("加入佇列")}},dismissButton={TextButton(onClick={naming=null}) {Text("取消")}}) }
}

@Composable private fun StatTile(label: String,value: String,modifier: Modifier) {
    Surface(modifier,shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(16.dp)) { Text(label,style=MaterialTheme.typography.labelMedium); Text(value,fontSize=20.sp,fontWeight=FontWeight.Bold,maxLines=1) }
    }
}
@Composable private fun Queue(model: DownloadsViewModel,exportReport: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(model.activeName,fontWeight=FontWeight.Bold,fontSize=21.sp)
            LinearProgressIndicator(progress={model.progress},modifier=Modifier.fillMaxWidth())
            Text(model.detail,style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick=model::pause) {Text("暫停")}
                Button(onClick=model::resume,enabled=!model.busy) {Text("繼續佇列")}
            }
        } }
        TextButton(onClick=exportReport) {Text("匯出下載報告")}
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=16.dp)) {
            if(model.tasks.isEmpty()) item { Text("還沒有任務。從探索頁加入想要下載的版本。",modifier=Modifier.padding(20.dp)) }
            items(model.tasks,key={it.id}) { task -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(task.name,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                    Text(stateLabel(task.state),color=MaterialTheme.colorScheme.primary)
                }
                Text(task.version,style=MaterialTheme.typography.labelSmall)
                if(task.message.isNotBlank()) Text(task.message,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=6.dp))
                if(task.state in setOf("Failed","Complete")) TextButton(onClick={model.retry(task)}) {Text("重新校驗／重試")}
            } } }
        }
    }
}
@Composable private fun LocalFiles(model: DownloadsViewModel) {
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=16.dp)) {
        item { Text("已完成的版本",fontSize=24.sp,fontWeight=FontWeight.Bold); Text("修復會保留已校驗內容，只補齊缺失或損壞檔案。",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=8.dp)) }
        if(model.installed.isEmpty()) item {Text("完成下載後，版本會出現在這裡。")}
        items(model.installed,key={it.version}) { version -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text(version.name,fontSize=20.sp,fontWeight=FontWeight.SemiBold); Text(version.version)
            FilledTonalButton(onClick={model.repair(version)},enabled=!model.loading) {Text("校驗並修復")}
        } } }
    }
}
@Composable private fun About() {
    LazyColumn(verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item {Text("Avero 0.2.0",fontSize=26.sp,fontWeight=FontWeight.Bold)}
        item {InfoCard("檔案下載工作區","官方版本目錄、版本搜尋與分類、下載佇列、可續傳下載、SHA-1 校驗、修復與本機報告。")}
        item {InfoCard("登入與遊玩狀態","此發行版不包含登入、帳號儲存、原生 JVM 或遊戲執行 Activity。未來遊玩功能需先完成應用程式審核，並強制登入及驗證正版所有權。")}
        item {InfoCard("隱私與資料","目錄、佇列及遊戲檔案儲存在應用程式私有目錄。重新開啟後，未完成任務會暫停，等待手動恢復。匯出的下載報告不包含帳號、憑證或本機檔案路徑。Android 備份已停用。")}
        item {InfoCard("專案資訊","維護者：Yannick Fan / yannickfan67-ai\n聯絡：GitHub Issues\n原始碼：github.com/yannickfan67-ai/Avero-Launcher\n授權：MIT（僅限本專案程式碼）")}
        item {Text("NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.",style=MaterialTheme.typography.bodySmall)}
    }
}
@Composable private fun InfoCard(title: String,text: String) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(title,fontWeight=FontWeight.Bold); Text(text,style=MaterialTheme.typography.bodyMedium) } } }
private fun stateLabel(state: String) = when(state) { "Queued"->"等待中";"Downloading"->"下載中";"Paused"->"已暫停";"Failed"->"失敗";"Complete"->"完成";else->state }
