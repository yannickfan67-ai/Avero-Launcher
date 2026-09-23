package io.yannickfan.avero

import android.content.Intent
import android.net.Uri
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
import io.yannickfan.avero.androidnative.AndroidNativeProviderCatalog
import io.yannickfan.avero.androidnative.AndroidNativeProviderInstaller
import io.yannickfan.avero.auth.AuthenticatedMinecraftAccount
import io.yannickfan.avero.auth.DeviceCodeInfo
import io.yannickfan.avero.auth.MicrosoftMinecraftAuthClient
import io.yannickfan.avero.minecraft.AssetInstaller
import io.yannickfan.avero.minecraft.GameInstaller
import io.yannickfan.avero.minecraft.InstanceLayout
import io.yannickfan.avero.minecraft.LaunchPlanner
import io.yannickfan.avero.minecraft.LauncherInstance
import io.yannickfan.avero.minecraft.MinecraftManifestClient
import io.yannickfan.avero.minecraft.MinecraftVersionMetadata
import io.yannickfan.avero.minecraft.RuntimeManager
import io.yannickfan.avero.minecraft.RuntimeState
import io.yannickfan.avero.minecraft.VersionManifest
import io.yannickfan.avero.runtime.AndroidJavaRuntimeCatalog
import io.yannickfan.avero.runtime.AndroidJavaRuntimeInstaller
import io.yannickfan.avero.runtime.JvmProbeActivity
import io.yannickfan.avero.runtime.RuntimeArch
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

private sealed interface AccountState {
    data object Unconfigured : AccountState
    data object SignedOut : AccountState
    data class AwaitingCode(val info: DeviceCodeInfo) : AccountState
    data object Completing : AccountState
    data class SignedIn(val account: AuthenticatedMinecraftAccount) : AccountState
    data class Failed(val message: String) : AccountState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AveroApp() {
    val nav = listOf(
        NavItem("Home", Icons.Rounded.Home),
        NavItem("Versions", Icons.Rounded.Storage),
        NavItem("Downloads", Icons.Rounded.Download),
        NavItem("Accounts", Icons.Rounded.AccountCircle),
        NavItem("Settings", Icons.Rounded.Settings)
    )
    var selected by remember { mutableIntStateOf(0) }
    var manifestState by remember { mutableStateOf<ManifestState>(ManifestState.Loading) }
    val client = remember { MinecraftManifestClient() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val authClient = remember {
        BuildConfig.MICROSOFT_CLIENT_ID
            .takeIf { it.isNotBlank() }
            ?.let(::MicrosoftMinecraftAuthClient)
    }
    var accountState by remember {
        mutableStateOf<AccountState>(
            if (authClient == null) AccountState.Unconfigured else AccountState.SignedOut
        )
    }

    fun startMicrosoftSignIn() {
        val auth = authClient
        if (auth == null) {
            accountState = AccountState.Unconfigured
            return
        }

        scope.launch {
            accountState = try {
                val info = auth.requestDeviceCode()
                accountState = AccountState.AwaitingCode(info)
                val microsoftToken = auth.awaitMicrosoftToken(info)
                accountState = AccountState.Completing
                AccountState.SignedIn(auth.authenticateMinecraft(microsoftToken))
            } catch (t: Throwable) {
                t.rethrowIfCancellation()
                AccountState.Failed(t.message ?: t::class.java.simpleName)
            }
        }
    }

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
                t.rethrowIfCancellation()
                ManifestState.Failed(t.message ?: t::class.java.simpleName)
            }
        }
    }

    LaunchedEffect(Unit) {
        val manifest = try {
            client.fetchManifest()
        } catch (t: Throwable) {
                t.rethrowIfCancellation()
            manifestState = ManifestState.Failed(t.message ?: t::class.java.simpleName)
            return@LaunchedEffect
        }

        manifestState = try {
            val latest = manifest.versions.firstOrNull { it.id == manifest.latestRelease }
                ?: error("Latest release not present in manifest")
            ManifestState.Ready(manifest, client.fetchVersion(latest))
        } catch (t: Throwable) {
                t.rethrowIfCancellation()
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
                        label = { Text(item.label) },
                        alwaysShowLabel = selected == index
                    )
                }
            }
        }
    ) { padding ->
        when (selected) {
            0 -> HomeScreen(
                padding,
                manifestState,
                accountState,
                onRefresh = ::refreshManifest
            ) { selected = it }
            1 -> VersionsScreen(padding, manifestState)
            2 -> DownloadsScreen(padding, manifestState)
            3 -> AccountsScreen(
                padding = padding,
                state = accountState,
                onSignIn = ::startMicrosoftSignIn,
                onOpenVerification = { uri ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                }
            )
            else -> SettingsScreen(padding, manifestState)
        }
    }
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    state: ManifestState,
    accountState: AccountState,
    onRefresh: () -> Unit,
    navigate: (Int) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(
                        "AVERO · JAVA EDITION",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Minecraft Java,\nmade for Android.",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Official metadata, verified installs, Microsoft sign-in and managed Java runtimes in one native launcher.",
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroBadge("Native UI")
                        HeroBadge("Verified installs")
                    }
                }
            }
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
                    val runtime = RuntimeManager().requirementFor(
                        metadata = metadata,
                        runtimeRoot = File(context.filesDir, "runtimes")
                    )
                    val nativeProviderPlan = RuntimeArch.current()
                        ?.let { arch ->
                            AndroidNativeProviderCatalog.detect(metadata, arch)
                        }
                        ?.let(AndroidNativeProviderCatalog::plan)
                    val plan = LaunchPlanner().createVanillaPlan(
                        metadata = metadata,
                        instance = LauncherInstance(
                            name = "Latest release",
                            versionId = metadata.id,
                            javaMajorVersion = metadata.javaMajorVersion
                        ),
                        androidNativeProvider = nativeProviderPlan
                    )

                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
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
                ) { navigate(3) }
                QuickTile(
                    "Java runtime",
                    "Install & test Java",
                    Icons.Rounded.Storage,
                    Modifier.weight(1f)
                ) { navigate(2) }
            }
        }

        item { SectionTitle("Launcher pipeline") }
        item { StatusRow("Official version manifest", if (state is ManifestState.Ready) "working" else "pending") }
        item { StatusRow("Version metadata parser", if (state is ManifestState.Ready) "working" else "pending") }
        item { StatusRow("Verified + resumable downloads", "implemented") }
        item { StatusRow("Vanilla launch-plan builder", "implemented") }
        item {
            StatusRow(
                "Microsoft authentication",
                when (accountState) {
                    is AccountState.SignedIn -> "signed in"
                    AccountState.Unconfigured -> "client ID required"
                    else -> "implemented"
                }
            )
        }
        item { StatusRow("Android Java runtime install", "implemented") }
        item { StatusRow("Isolated JVM probe", "implemented") }
        item { StatusRow("Minecraft + LWJGL launch", "in progress") }
    }
}

@Composable
private fun HeroBadge(text: String) {
    Box(
        Modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
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
        shape = RoundedCornerShape(20.dp),
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
    var runtimeInstalling by remember { mutableStateOf(false) }
    var runtimeStatus by remember { mutableStateOf("Not checked") }
    var nativeInstalling by remember { mutableStateOf(false) }
    var nativeStatus by remember { mutableStateOf("Not checked") }

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
            val runtimePackage = AndroidJavaRuntimeCatalog.find(m.javaMajorVersion)
            val currentArch = RuntimeArch.current()
            val nativeProviderPackage = currentArch?.let { arch ->
                AndroidNativeProviderCatalog.detect(m, arch)
            }
            val nativeProviderPlan = nativeProviderPackage
                ?.let(AndroidNativeProviderCatalog::plan)
            val nativeProviderInstalled = nativeProviderPackage?.let { pkg ->
                AndroidNativeProviderInstaller().findInstalled(
                    pkg = pkg,
                    providerRoot = File(
                        context.filesDir,
                        "minecraft/${nativeProviderPlan?.nativeDirectory?.substringBeforeLast('/')}"
                    )
                )
            }
            val runtimeRequirement = RuntimeManager().requirementFor(
                metadata = m,
                runtimeRoot = File(context.filesDir, "runtimes")
            )

            item { StatusRow("Libraries", m.libraries.size.toString()) }
            item { StatusRow("Game install", installStatus) }
            item {
                StatusRow(
                    "Java runtime",
                    runtimePackage?.let { "Java ${it.majorVersion} · ${it.arch.assetToken}" }
                        ?: "No package for Java ${m.javaMajorVersion} / ${currentArch?.assetToken ?: "unknown ABI"}"
                )
            }
            item { StatusRow("Runtime install", runtimeStatus) }
            item {
                StatusRow(
                    "Android LWJGL",
                    when {
                        nativeProviderPackage == null -> "No pinned provider for this LWJGL"
                        nativeProviderInstalled != null ->
                            "Ready · ${nativeProviderPackage.lwjglVersion} · ${nativeProviderPackage.arch.assetToken}"
                        else ->
                            "Available · ${nativeProviderPackage.lwjglVersion} · ${nativeProviderPackage.arch.assetToken}"
                    }
                )
            }
            item { StatusRow("Native provider install", nativeStatus) }

            item {
                Button(
                    enabled = !runtimeInstalling && runtimePackage != null,
                    onClick = {
                        val pkg = runtimePackage ?: return@Button
                        scope.launch {
                            runtimeInstalling = true
                            runtimeStatus = "Starting…"
                            try {
                                val installed = AndroidJavaRuntimeInstaller().install(
                                    runtimePackage = pkg,
                                    root = File(context.filesDir, "runtimes")
                                ) { progress ->
                                    runtimeStatus = when {
                                        progress.totalBytes != null && progress.totalBytes > 0 ->
                                            "${progress.stage} · ${formatBytes(progress.downloadedBytes)}/${formatBytes(progress.totalBytes)}"
                                        else -> "${progress.stage} · ${progress.message}"
                                    }
                                }
                                runtimeStatus =
                                    "Ready · ${installed.javaExecutable.absolutePath}"
                            } catch (t: Throwable) {
                                t.rethrowIfCancellation()
                                runtimeStatus = "Failed: ${t.message ?: t::class.java.simpleName}"
                            } finally {
                                runtimeInstalling = false
                            }
                        }
                    }
                ) {
                    Icon(Icons.Rounded.Storage, null)
                    Text(
                        if (runtimeInstalling) " Installing Java…"
                        else " Install Java ${runtimePackage?.majorVersion ?: m.javaMajorVersion}"
                    )
                }
            }

            item {
                OutlinedButton(
                    enabled = runtimeRequirement.state == RuntimeState.AVAILABLE &&
                        !runtimeInstalling,
                    onClick = {
                        context.startActivity(
                            Intent(context, JvmProbeActivity::class.java)
                                .putExtra(
                                    JvmProbeActivity.EXTRA_JAVA_MAJOR,
                                    m.javaMajorVersion
                                )
                        )
                    }
                ) {
                    Text("Test Java runtime")
                }
            }

            item {
                Button(
                    enabled = !nativeInstalling && nativeProviderPackage != null,
                    onClick = {
                        val pkg = nativeProviderPackage ?: return@Button
                        scope.launch {
                            nativeInstalling = true
                            nativeStatus = "Starting…"
                            try {
                                val installed = AndroidNativeProviderInstaller().install(
                                    pkg = pkg,
                                    minecraftRoot = File(context.filesDir, "minecraft")
                                ) { progress ->
                                    nativeStatus = when {
                                        progress.totalBytes != null && progress.totalBytes > 0 ->
                                            "${progress.stage} · ${formatBytes(progress.downloadedBytes)}/${formatBytes(progress.totalBytes)}"
                                        else -> "${progress.stage} · ${progress.message}"
                                    }
                                }
                                nativeStatus =
                                    "Ready · ${installed.nativeLibraries.size} native libraries"
                            } catch (t: Throwable) {
                                t.rethrowIfCancellation()
                                nativeStatus =
                                    "Failed: ${t.message ?: t::class.java.simpleName}"
                            } finally {
                                nativeInstalling = false
                            }
                        }
                    }
                ) {
                    Icon(Icons.Rounded.Extension, null)
                    Text(
                        if (nativeInstalling) " Installing Android LWJGL…"
                        else " Install Android LWJGL provider"
                    )
                }
            }

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
                                t.rethrowIfCancellation()
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
                "Interrupted transfers keep .part files, resume with HTTP Range when possible, retry transient failures, and validate size plus SHA digests before installation."
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
                "Android OpenJDK runtime packages can now be downloaded, SHA-256 verified and extracted for the selected Minecraft version."
            )
        }
        item {
            FeatureCard(
                Icons.Rounded.Extension,
                "Android LWJGL provider",
                "Pinned third-party LWJGL bridge AARs are verified as Git blobs, then only the provider classes and current-ABI native libraries are installed."
            )
        }
    }
}

@Composable
private fun AccountsScreen(
    padding: PaddingValues,
    state: AccountState,
    onSignIn: () -> Unit,
    onOpenVerification: (String) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Accounts",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Microsoft public-client sign-in → Xbox Live → XSTS → Minecraft Services.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when (state) {
            AccountState.Unconfigured -> {
                item {
                    FeatureCard(
                        Icons.Rounded.AccountCircle,
                        "Microsoft client ID required",
                        "Build Avero with AVERO_MS_CLIENT_ID set to the Application (client) ID of Avero's Microsoft public-client app registration. No client secret is embedded."
                    )
                }
            }

            AccountState.SignedOut -> {
                item {
                    Button(onClick = onSignIn) {
                        Icon(Icons.Rounded.AccountCircle, null)
                        Text(" Sign in with Microsoft")
                    }
                }
            }

            is AccountState.AwaitingCode -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text("Enter this Microsoft code", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                state.info.userCode,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Avero is waiting for Microsoft authorization. The code expires automatically.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    onOpenVerification(state.info.verificationUri)
                                }
                            ) {
                                Text("Open Microsoft sign-in")
                            }
                        }
                    }
                }
            }

            AccountState.Completing -> {
                item {
                    StatusCard(
                        "Finishing Minecraft sign-in",
                        "Exchanging Xbox Live / XSTS tokens and checking the Minecraft profile…"
                    )
                }
            }

            is AccountState.SignedIn -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text("Signed in", style = MaterialTheme.typography.labelLarge)
                            Text(
                                state.account.profile.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                state.account.profile.id,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            StatusRow(
                                "Minecraft entitlements",
                                state.account.entitlements.names.size.toString()
                            )
                        }
                    }
                }
            }

            is AccountState.Failed -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text("Sign-in failed", fontWeight = FontWeight.Bold)
                            Text(
                                state.message,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = onSignIn) {
                                Text("Try again")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Avero never asks for your Microsoft password directly. Authentication happens through Microsoft's device-code flow.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
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


private fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}
