package io.yannickfan.avero.game

import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.yannickfan.avero.ui.theme.AveroTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

class GameLaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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

@Composable
private fun GameLaunchScreen(
    requestId: String?,
    filesRoot: File
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember {
        mutableStateOf("Validating one-time launch request…")
    }
    var execution by remember {
        mutableStateOf<PreparedGameExecution?>(null)
    }
    var currentSurface by remember {
        mutableStateOf<Surface?>(null)
    }
    var bridgeReady by remember { mutableStateOf(false) }
    var launching by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        org.lwjgl.glfw.CallbackBridge.initialize(context)
    }

    LaunchedEffect(requestId) {
        if (requestId.isNullOrBlank()) {
            status = "Launch preparation failed: Launch request ID is missing"
            return@LaunchedEffect
        }

        status = try {
            val prepared = GameExecutionSession(filesRoot).prepare(requestId)
            execution = prepared
            "Launch context validated. Waiting for Android Surface."
        } catch (t: Throwable) {
            "Launch preparation failed: ${safeError(t)}"
        }
    }

    LaunchedEffect(execution, currentSurface) {
        val prepared = execution
        val surface = currentSurface
        if (prepared != null && surface != null && surface.isValid) {
            bridgeReady = false
            status = try {
                AndroidLwjglBridge.prepare(
                    nativeDirectory = prepared.providerNativeDirectory,
                    surface = surface
                )
                bridgeReady = true
                "Android LWJGL bridge ready. Minecraft JVM can start."
            } catch (t: Throwable) {
                "Renderer bridge failed: ${safeError(t)}"
            }
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

        execution?.summary?.let { summary ->
            Text(
                "Minecraft ${summary.versionId} · " +
                    "Java ${summary.javaMajor} · " +
                    "${summary.classpathEntries} classpath entries"
            )
            Text("Player: ${summary.playerName}")
        }

        val size = if (currentSurface?.isValid == true) {
            GameSurfaceBridge.size()
        } else {
            0 to 0
        }
        Text(
            if (size.first > 0 && size.second > 0) {
                "Android Surface: ${size.first}×${size.second}"
            } else {
                "Waiting for Android Surface"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            enabled =
                bridgeReady &&
                    !launching &&
                    execution != null &&
                    size.first > 0 &&
                    size.second > 0,
            onClick = {
                val prepared = execution ?: return@Button
                launching = true
                status = "Starting Minecraft JVM…"
                scope.launch {
                    try {
                        val exitCode = prepared.launch(
                            surfaceWidth = size.first,
                            surfaceHeight = size.second
                        )
                        status = "Minecraft JVM exited with code $exitCode"
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        status = "JVM launch failed: ${safeError(t)}"
                    } finally {
                        launching = false
                    }
                }
            }
        ) {
            Text(
                if (launching) "Minecraft is running…"
                else "Start Minecraft JVM"
            )
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                SurfaceView(viewContext).also { view ->
                    view.holder.addCallback(
                        object : SurfaceHolder.Callback {
                            override fun surfaceCreated(
                                holder: SurfaceHolder
                            ) {
                                GameSurfaceBridge.attach(holder.surface)
                                currentSurface = holder.surface
                            }

                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int
                            ) {
                                GameSurfaceBridge.attach(holder.surface)
                                currentSurface = holder.surface
                            }

                            override fun surfaceDestroyed(
                                holder: SurfaceHolder
                            ) {
                                bridgeReady = false
                                AndroidLwjglBridge.release()
                                GameSurfaceBridge.detach()
                                currentSurface = null
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
        .replace(
            Regex("(?i)access[_ -]?token\\s*[:=]\\s*\\S+"),
            "accessToken=<redacted>"
        )
        .take(500)
}
