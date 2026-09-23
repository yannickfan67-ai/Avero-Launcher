package io.yannickfan.avero.game

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.yannickfan.avero.minecraft.LaunchCommandBuilder
import io.yannickfan.avero.minecraft.LaunchIdentity
import io.yannickfan.avero.minecraft.MinecraftManifestClient
import io.yannickfan.avero.runtime.RuntimeArch
import io.yannickfan.avero.ui.theme.AveroTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class GameLaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        setContent {
            AveroTheme {
                GameLaunchScreen(
                    requestId = requestId,
                    filesRoot = filesDir
                )
            }
        }
    }

    companion object {
        const val EXTRA_REQUEST_ID = "launch_request_id"
    }
}

private data class ResolvedGameLaunch(
    val versionId: String,
    val javaMajor: Int,
    val classpathEntries: Int,
    val playerName: String
)

@Composable
private fun GameLaunchScreen(
    requestId: String?,
    filesRoot: File
) {
    var status by remember { mutableStateOf("Validating one-time launch request…") }
    var resolved by remember { mutableStateOf<ResolvedGameLaunch?>(null) }
    var surfaceReady by remember { mutableStateOf(false) }

    LaunchedEffect(requestId) {
        status = try {
            require(!requestId.isNullOrBlank()) {
                "Launch request ID is missing"
            }

            val result = withContext(Dispatchers.IO) {
                val request = GameLaunchRequestStore(filesRoot).consume(requestId)
                val minecraftRoot = File(filesRoot, "minecraft")
                val versionJson = File(
                    minecraftRoot,
                    "versions/${request.versionId}/${request.versionId}.json"
                )
                require(versionJson.isFile) {
                    "Installed Minecraft version metadata is missing"
                }

                val metadata = MinecraftManifestClient.parseVersion(
                    JSONObject(versionJson.readText())
                )
                val arch = requireNotNull(RuntimeArch.current()) {
                    "Unsupported Android ABI"
                }
                val ready = GameLaunchReadinessChecker().prepareOrThrow(
                    metadata = metadata,
                    request = request,
                    minecraftRoot = minecraftRoot,
                    runtimeRoot = File(filesRoot, "runtimes"),
                    gameDirectory = File(
                        minecraftRoot,
                        "instances/default/game"
                    ),
                    arch = arch
                )
                val command = LaunchCommandBuilder().resolve(
                    plan = ready.prepared.plan,
                    identity = LaunchIdentity(
                        playerName = request.playerName,
                        uuid = request.playerUuid,
                        accessToken = request.minecraftAccessToken
                    ),
                    environment = ready.prepared.environment
                )

                ResolvedGameLaunch(
                    versionId = metadata.id,
                    javaMajor = command.javaMajorVersion,
                    classpathEntries = ready.prepared.plan.classpathEntries.size,
                    playerName = request.playerName
                )
            }

            resolved = result
            "Launch context validated. Waiting for renderer bridge."
        } catch (t: Throwable) {
            "Launch preparation failed: ${safeError(t)}"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Avero Game Process",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(status)
        resolved?.let { launch ->
            Text(
                "Minecraft ${launch.versionId} · Java ${launch.javaMajor} · " +
                    "${launch.classpathEntries} classpath entries"
            )
            Text("Player: ${launch.playerName}")
        }
        Text(
            if (surfaceReady) {
                "Android Surface ready"
            } else {
                "Waiting for Android Surface"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                SurfaceView(context).also { view ->
                    view.holder.addCallback(
                        object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                surfaceReady = true
                            }

                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int
                            ) {
                                surfaceReady = true
                            }

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                surfaceReady = false
                            }
                        }
                    )
                }
            }
        )
    }
}

private fun safeError(t: Throwable): String {
    val raw = t.message?.takeIf { it.isNotBlank() }
        ?: t::class.java.simpleName
    return raw
        .replace(Regex("(?i)access[_ -]?token\\s*[:=]\\s*\\S+"), "accessToken=<redacted>")
        .take(500)
}
