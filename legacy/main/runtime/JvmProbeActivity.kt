package io.yannickfan.avero.runtime

import android.os.Bundle
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.yannickfan.avero.ui.theme.AveroTheme
import kotlinx.coroutines.CancellationException
import java.io.File

class JvmProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val majorVersion = intent.getIntExtra(EXTRA_JAVA_MAJOR, -1)

        setContent {
            AveroTheme {
                JvmProbeScreen(
                    majorVersion = majorVersion,
                    filesRoot = filesDir,
                    cacheRoot = cacheDir,
                    onClose = ::finish
                )
            }
        }
    }

    companion object {
        const val EXTRA_JAVA_MAJOR = "java_major"
    }
}

@Composable
private fun JvmProbeScreen(
    majorVersion: Int,
    filesRoot: File,
    cacheRoot: File,
    onClose: () -> Unit
) {
    var status by remember { mutableStateOf("Preparing Java runtime probe…") }

    LaunchedEffect(majorVersion) {
        status = try {
            require(majorVersion > 0) { "Invalid Java major version" }
            val arch = requireNotNull(RuntimeArch.current()) {
                "Unsupported Android ABI"
            }
            val pkg = requireNotNull(AndroidJavaRuntimeCatalog.find(majorVersion, arch)) {
                "No Java $majorVersion package for ${arch.assetToken}"
            }

            val runtimeHome = File(File(filesRoot, "runtimes"), pkg.id)
            val runtime = requireNotNull(
                AndroidJavaRuntimeInstaller().findInstalled(pkg, runtimeHome)
            ) {
                "Java $majorVersion is not installed"
            }

            val exitCode = NativeJvmBridge().launchRaw(
                runtime = runtime,
                args = listOf(runtime.javaExecutable.absolutePath, "-version"),
                environment = JvmLaunchEnvironment(
                    javaHome = runtime.home,
                    gameDirectory = File(cacheRoot, "jvm-probe-work"),
                    tempDirectory = File(cacheRoot, "jvm-probe-tmp"),
                    homeDirectory = filesRoot
                )
            )

            "JLI_Launch returned exit code $exitCode"
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            "Probe failed: ${t.message ?: t::class.java.simpleName}"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Avero JVM Probe",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(status)
        Text(
            "This probe runs in an isolated Android process. If the Java launcher exits the process, the main Avero launcher remains alive.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onClose) {
            Text("Close")
        }
    }
}
