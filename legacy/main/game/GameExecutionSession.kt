package io.yannickfan.avero.game

import android.os.Build
import io.yannickfan.avero.minecraft.LaunchCommandBuilder
import io.yannickfan.avero.minecraft.LaunchIdentity
import io.yannickfan.avero.minecraft.MinecraftManifestClient
import io.yannickfan.avero.minecraft.ResolvedLaunchCommand
import io.yannickfan.avero.runtime.InstalledJavaRuntime
import io.yannickfan.avero.runtime.JvmLaunchEnvironment
import io.yannickfan.avero.runtime.NativeJvmBridge
import io.yannickfan.avero.runtime.RuntimeArch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class GameExecutionSummary(
    val versionId: String,
    val javaMajor: Int,
    val classpathEntries: Int,
    val playerName: String
)

class PreparedGameExecution internal constructor(
    val summary: GameExecutionSummary,
    val providerNativeDirectory: File,
    private val filesRoot: File,
    private val runtime: InstalledJavaRuntime,
    private val command: ResolvedLaunchCommand,
    private val gameDirectory: File
) {
    suspend fun launch(
        surfaceWidth: Int,
        surfaceHeight: Int
    ): Int {
        val tempDirectory = File(filesRoot, "game-runtime/tmp")
        val userHome = File(filesRoot, "minecraft/home")
        tempDirectory.mkdirs()
        userHome.mkdirs()

        val tuned = AndroidGameJvmTuning.apply(
            command = command,
            tuning = AndroidJvmTuning(
                javaHome = runtime.home,
                tempDirectory = tempDirectory,
                userHome = userHome,
                androidRelease = Build.VERSION.RELEASE ?: "unknown",
                width = surfaceWidth,
                height = surfaceHeight
            )
        )

        return NativeJvmBridge().launch(
            runtime = runtime,
            command = tuned,
            environment = JvmLaunchEnvironment(
                javaHome = runtime.home,
                gameDirectory = gameDirectory,
                tempDirectory = tempDirectory,
                homeDirectory = userHome,
                extraLibraryDirectories =
                    listOf(providerNativeDirectory)
            )
        )
    }

    override fun toString(): String =
        "PreparedGameExecution(" +
            "versionId=${summary.versionId}, " +
            "javaMajor=${summary.javaMajor}, " +
            "classpathEntries=${summary.classpathEntries}, " +
            "playerName=${summary.playerName}, " +
            "credentials=<redacted>)"
}

class GameExecutionSession(
    private val filesRoot: File
) {
    suspend fun prepare(requestId: String): PreparedGameExecution =
        withContext(Dispatchers.IO) {
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

            PreparedGameExecution(
                summary = GameExecutionSummary(
                    versionId = metadata.id,
                    javaMajor = command.javaMajorVersion,
                    classpathEntries =
                        ready.prepared.plan.classpathEntries.size,
                    playerName = request.playerName
                ),
                providerNativeDirectory =
                    ready.prepared.nativeProvider.nativeDirectory,
                filesRoot = filesRoot,
                runtime = ready.runtime,
                command = command,
                gameDirectory = ready.prepared.environment.gameDirectory
            )
        }
}
