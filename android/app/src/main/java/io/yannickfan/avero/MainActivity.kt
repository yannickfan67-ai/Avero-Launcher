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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.yannickfan.avero.ui.theme.AveroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AveroTheme { AveroApp() } }
    }
}

data class NavItem(val label: String, val icon: ImageVector)
data class VersionEntry(val version: String, val loader: String, val state: String)

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
            0 -> HomeScreen(padding) { selected = it }
            1 -> VersionsScreen(padding)
            2 -> DownloadsScreen(padding)
            else -> SettingsScreen(padding)
        }
    }
}

@Composable
private fun HomeScreen(padding: PaddingValues, navigate: (Int) -> Unit) {
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
                "Manage accounts, versions, loaders and runtime settings from one native launcher.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("Selected instance", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Text("Minecraft 1.21.4", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Fabric · Java 21 · 4096 MB", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { }) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Text(" Play")
                        }
                        OutlinedButton(onClick = { navigate(1) }) {
                            Text("Change version")
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
        items(
            listOf(
                "Microsoft account authentication",
                "Game metadata and asset download",
                "Java runtime selection and management",
                "Loader and mod installation",
                "Launch arguments, renderer and logs"
            )
        ) { StatusRow(it, "planned") }
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
private fun VersionsScreen(padding: PaddingValues) {
    val versions = listOf(
        VersionEntry("1.21.4", "Fabric", "Ready"),
        VersionEntry("1.20.1", "Forge", "Not installed"),
        VersionEntry("1.8.9", "Vanilla", "Not installed")
    )

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Versions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Installed and available Minecraft instances.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(versions) { entry ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Storage, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(entry.version, fontWeight = FontWeight.SemiBold)
                        Text(entry.loader, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(entry.state, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun DownloadsScreen(padding: PaddingValues) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Downloads", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Game files, assets, libraries, loaders and runtimes will appear here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { FeatureCard(Icons.Rounded.Download, "Version files", "Minecraft metadata, client jar, libraries and assets.") }
        item { FeatureCard(Icons.Rounded.Extension, "Loaders", "Fabric, Forge, NeoForge and Quilt installation tasks.") }
        item { FeatureCard(Icons.Rounded.Storage, "Java runtimes", "Managed Java runtimes for the selected Minecraft version.") }
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item { StatusRow("Memory allocation", "4096 MB") }
        item { StatusRow("Java runtime", "Automatic") }
        item { StatusRow("Renderer", "Automatic") }
        item { StatusRow("Game directory", "Avero managed") }
        item { StatusRow("Package", "io.yannickfan.avero") }
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
