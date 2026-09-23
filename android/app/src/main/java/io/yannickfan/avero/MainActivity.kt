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
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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

data class Project(val name: String, val description: String, val language: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AveroApp() {
    val nav = listOf(
        NavItem("Home", Icons.Rounded.Home),
        NavItem("Projects", Icons.Rounded.FolderOpen),
        NavItem("Workspace", Icons.Rounded.Terminal),
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
                        Text("  Avero", fontWeight = FontWeight.Bold)
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
            1 -> ProjectsScreen(padding)
            2 -> WorkspaceScreen(padding)
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
                "Mobile workspace, without the desktop baggage.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "A fresh Android client for projects, repository browsing and future AI-assisted coding tools.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile(
                    "Projects",
                    "Browse local work",
                    Icons.Rounded.FolderOpen,
                    Modifier.weight(1f)
                ) { navigate(1) }
                QuickTile(
                    "Workspace",
                    "Draft & inspect",
                    Icons.Rounded.Code,
                    Modifier.weight(1f)
                ) { navigate(2) }
            }
        }
        item { SectionTitle("What this build establishes") }
        items(
            listOf(
                "Native Compose UI — no WebView shell",
                "Adaptive layout baseline for Android 17",
                "Independent package and codebase",
                "Room for repo, Git and AI integrations later"
            )
        ) { FeatureRow(it) }
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
private fun ProjectsScreen(padding: PaddingValues) {
    val projects = listOf(
        Project("Android sandbox", "New native mobile experiments", "Kotlin"),
        Project("Website", "Product site and release landing page", "HTML / CSS"),
        Project("Scratchpad", "Temporary notes and snippets", "Text")
    )
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Projects",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        items(projects) { project ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.FolderOpen,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(project.name, fontWeight = FontWeight.SemiBold)
                        Text(project.description, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(project.language, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun WorkspaceScreen(padding: PaddingValues) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Workspace",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            FeatureCard(
                Icons.Rounded.Code,
                "Code workspace",
                "A mobile-first surface for snippets, diffs and repository context."
            )
        }
        item {
            FeatureCard(
                Icons.Rounded.Memory,
                "AI slot",
                "Reserved for coding assistance and repository analysis. No provider is hard-wired yet."
            )
        }
        item {
            FeatureCard(
                Icons.Rounded.Terminal,
                "Local tools",
                "Designed so Git, terminal bridges or device-side utilities can be added cleanly."
            )
        }
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        FeatureRow("Avero Android 0.1.0")
        FeatureRow("Package: io.yannickfan.avero")
        FeatureRow("Theme follows the system")
        FeatureRow("No account or telemetry layer yet")
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
private fun FeatureRow(text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(99.dp))
                .padding(4.dp)
        )
        Text(
            text,
            Modifier.padding(start = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}
