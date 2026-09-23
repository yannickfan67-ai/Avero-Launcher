package io.yannickfan.avero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.yannickfan.avero.minecraft.AssetInstaller
import io.yannickfan.avero.minecraft.GameInstaller
import io.yannickfan.avero.minecraft.InstanceLayout
import io.yannickfan.avero.minecraft.LaunchPlanner
import io.yannickfan.avero.minecraft.LauncherInstance
import io.yannickfan.avero.minecraft.MinecraftManifestClient
import io.yannickfan.avero.minecraft.MinecraftVersionMetadata
import io.yannickfan.avero.minecraft.RuntimeManager
import io.yannickfan.avero.minecraft.VersionManifest
import io.yannickfan.avero.ui.theme.AveroTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AveroTheme { AveroApp() } }
    }
}

data class NavItem(val label: String, val icon: ImageVector)

private sealed interface ManifestState {
    data object Loading : ManifestState
    data class Ready(
        val manifest: VersionManifest,
        val latestMetadata: MinecraftVersionMetadata
    ) : ManifestState
    data class Failed(val message: String) : ManifestState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AveroApp() {
    val nav = listOf(
        NavItem("Home", Icons.Rounded.Home),
        NavItem("Versions", Icons.Rounded.Storage),
        NavItem("Downloads", Icons.Rounded.Download),
        NavItem("Settings", Icons.Rounded.Settings)
    )
    var selected by remember { mutableIntStateOf(0) }
    var manifestState by remember { mutableStateOf<ManifestState>(ManifestState.Loading) }
    val client = remember { MinecraftManifestClient() }
    val scope = rememberCoroutineScope()

    fun refreshManifest() {
        manifestState = ManifestState.Loading
        scope.launch {
            manifestState = try {
                val manifest = client.fetchManifest()
                val latest = manifest.versions.firstOrNull { it.id == manifest.latestRelease }
                    ?: error("Latest release not present in manifest")
                val metadata = client.fetchVersion(latest)
                ManifestState.Ready(manifest, metadata)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                ManifestState.Failed(t.message ?: t::class.java.simpleName)
            }
        }
    }

    LaunchedEffect(Unit) {
        val manifest = try {
            client.fetchManifest()
        } catch (t: Throwable) {
                if (t is CancellationException) throw t
            manifestState = ManifestState.Failed(t.message ?: t::class.java.simpleName)
            return@LaunchedEffect
        }

        manifestState = try {
            val latest = manifest.versions.firstOrNull { it.id == manifest.latestRelease }
                ?: error("Latest release not present in manifest")
            ManifestState.Ready(manifest, client.fetchVersion(latest))
        } catch (t: Throwable) {
                if (t is CancellationException) throw t
            ManifestState.Failed(t.message ?: t::class.java.simpleName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(7.dp))
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text("A", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
                        }
                        Text("  Avero Launcher", fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                nav.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (selected) {
            0 -> HomeScreen(padding, manifestState, onRefresh = ::refreshManifest) { selected = it }
            1 -> VersionsScreen(padding, manifestState)
            2 -> DownloadsScreen(padding, manifestState)
            else -> SettingsScreen(padding, manifestState)
        }
    }
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    state: ManifestState,
    onRefresh: () -> Unit,
    navigate: (Int) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Minecraft: Java Edition on Android.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Avero now reads Mojang's official version manifest and version metadata directly.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            when (state) {
                ManifestState.Loading -> StatusCard(
                    "Connecting to Mojang metadata",
                    "Loading version_manifest_v2.json…"
                )
                is ManifestState.Failed -> {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text("Manifest request failed", fontWeight = FontWeight.Bold)
                            Text(state.message, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = onRefresh) {
                                Icon(Icons.Rounded.Refresh, null)
                                Text(" Retry")
                            }
                        }
                    }
                }
                is ManifestState.Ready -> {
                    val metadata = state.latestMetadata
                    val runtime = RuntimeManager().requirementFor(metadata)
                    val plan = LaunchPlanner().createVanillaPlan(
                        metadata = metadata,
                        instance = LauncherInstance(
                            name = "Latest release",
                            versionId = metadata.id,
                            javaMajorVersion = metadata.javaMajorVersion
                        )
                    )

                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text("Latest official release", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Minecraft ${metadata.id}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Java ${metadata.javaMajorVersion} · ${metadata.libraries.size} libraries",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Main class: ${metadata.mainClass}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            StatusRow("Runtime", "${runtime.state} · ${runtime.architecture}")
                            Spacer(Modifier.height(8.dp))
                            StatusRow("Classpath entries", plan.classpathEntries.size.toString())
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { navigate(2) }) {
                                    Icon(Icons.Rounded.Download, null)
                                    Text(" Prepare files")
                                }
                                OutlinedButton(onClick = { navigate(1) }) {
                                    Text("Versions")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile(
                    "Accounts",
                    "Microsoft sign-in",
                    Icons.Rounded.AccountCircle,
                    Modifier.weight(1f)
                ) { }
                QuickTile(
                    "Mods & loaders",
                    "Fabric / Forge / NeoForge",
                    Icons.Rounded.Extension,
                    Modifier.weight(1f)
                ) { }
            }
        }

        item { SectionTitle("Launcher pipeline") }
        item { StatusRow("Official version manifest", if (state is ManifestState.Ready) "working" else "pending") }
        item { StatusRow("Version metadata parser", if (state is ManifestState.Ready) "working" else "pending") }
        item { StatusRow("Verified file downloader", "implemented") }
        item { StatusRow("Vanilla launch-plan builder", "implemented") }
        item { StatusRow("Microsoft authentication", "next") }
        item { StatusRow("Android Java runtime install", "next") }
        item { StatusRow("Actual Java process launch", "next") }
    }
}

@Composable
private fun QuickTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(18.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VersionsScreen(padding: PaddingValues, state: ManifestState) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Versions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Recent versions from Mojang's official manifest.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when (state) {
            ManifestState.Loading -> item { StatusCard("Loading versions", "Waiting for Mojang metadata…") }
            is ManifestState.Failed -> item { StatusCard("Unavailable", state.message) }
            is ManifestState.Ready -> {
                item { StatusRow("Latest release", state.manifest.latestRelease) }
                item { StatusRow("Latest snapshot", state.manifest.latestSnapshot) }
                items(state.manifest.versions.take(30)) { entry ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Storage, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                Text(entry.id, fontWeight = FontWeight.SemiBold)
                                Text(entry.type, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                entry.releaseTime?.take(10) ?: "",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsScreen(padding: PaddingValues, state: ManifestState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installing by remember { mutableStateOf(false) }
    var installStatus by remember { mutableStateOf("Idle") }

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Downloads", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Install the official version metadata, client jar, asset index and Java libraries with size/SHA-1 verification.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state is ManifestState.Ready) {
            val m = state.latestMetadata
            val summary = state.manifest.versions.firstOrNull { it.id == m.id }

            item { StatusRow("Target version", m.id) }
            item { StatusRow("Client jar", formatBytes(m.client.size)) }
            item { StatusRow("Asset index", m.assetIndexId) }
            item { StatusRow("Libraries", m.libraries.size.toString()) }
            item { StatusRow("Install state", installStatus) }

            item {
                Button(
                    enabled = !installing && summary != null,
                    onClick = {
                        if (summary == null) return@Button
                        scope.launch {
                            installing = true
                            installStatus = "Starting…"
                            try {
                                val result = GameInstaller().installVanillaCore(
                                    summary = summary,
                                    metadata = m,
                                    root = File(context.filesDir, "minecraft")
                                ) { progress ->
                                    installStatus =
                                        "${progress.completedFiles}/${progress.totalFiles} · ${progress.currentFile}"
                                }
                                val indexFile = InstanceLayout(result.root).assetIndex(m.assetIndexId)
                                val assetCount = AssetInstaller().install(
                                    indexFile = indexFile,
                                    root = result.root
                                ) { progress ->
                                    installStatus =
                                        "Assets ${progress.completed}/${progress.total} · ${progress.current}"
                                }
                                installStatus =
                                    "Ready · ${result.downloadedFiles} core files + $assetCount assets"
                            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                                installStatus = "Failed: ${t.message ?: t::class.java.simpleName}"
                            } finally {
                                installing = false
                            }
                        }
                    }
                ) {
                    Icon(Icons.Rounded.Download, null)
                    Text(if (installing) " Installing…" else " Install game files")
                }
            }
        } else {
            item { StatusCard("Version metadata required", "Wait for the official manifest to load first.") }
        }

        item {
            FeatureCard(
                Icons.Rounded.Download,
                "Verified downloads",
                "Downloads use temporary .part files and validate declared size and SHA-1 before installation."
            )
        }
        item {
            FeatureCard(
                Icons.Rounded.Extension,
                "Hashed assets",
                "The installer now reads the asset index and downloads every required Mojang asset object with SHA-1 verification."
            )
        }
        item {
            FeatureCard(
                Icons.Rounded.Storage,
                "Java runtimes",
                "Runtime requirements are detected; Android-compatible runtime packs still need installation support."
            )
        }
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues, state: ManifestState) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item { StatusRow("Memory allocation", "4096 MB") }
        item {
            StatusRow(
                "Required Java",
                if (state is ManifestState.Ready) "Java ${state.latestMetadata.javaMajorVersion}" else "Automatic"
            )
        }
        item { StatusRow("Renderer", "Automatic") }
        item { StatusRow("Game directory", "Avero managed") }
        item { StatusRow("Package", "io.yannickfan.avero") }
        item {
            FeatureCard(
                Icons.Rounded.AccountCircle,
                "Independent third-party launcher",
                "NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT."
            )
        }
    }
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, description: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusCard(title: String, description: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusRow(name: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

private fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "unknown"
    val mib = bytes.toDouble() / 1024.0 / 1024.0
    return "%.1f MiB".format(mib)
}
