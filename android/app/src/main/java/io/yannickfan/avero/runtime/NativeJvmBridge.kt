package io.yannickfan.avero.runtime

import io.yannickfan.avero.minecraft.ResolvedLaunchCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class JvmLaunchEnvironment(
    val javaHome: File,
    val gameDirectory: File,
    val tempDirectory: File,
    val homeDirectory: File,
    val extraLibraryDirectories: List<File> = emptyList()
)

class NativeJvmBridge {
    init {
        System.loadLibrary("avero_jvm")
    }

    suspend fun launch(
        runtime: InstalledJavaRuntime,
        command: ResolvedLaunchCommand,
        environment: JvmLaunchEnvironment
    ): Int = withContext(Dispatchers.IO) {
        require(runtime.home.canonicalFile == environment.javaHome.canonicalFile) {
            "Launch environment Java home does not match installed runtime"
        }
        require(environment.gameDirectory.isDirectory || environment.gameDirectory.mkdirs()) {
            "Could not create game directory"
        }
        require(environment.tempDirectory.isDirectory || environment.tempDirectory.mkdirs()) {
            "Could not create runtime temp directory"
        }

        val jli = requireNotNull(runtime.jliLibrary) {
            "Installed runtime does not contain libjli.so"
        }
        require(jli.isFile) { "libjli.so is missing: $jli" }
        require(!jli.canWrite()) {
            "libjli.so must be read-only before dynamic loading on modern Android"
        }

        val libraryDirectories = buildList {
            addAll(
                runtime.home.walkTopDown()
                    .filter { dir ->
                        dir.isDirectory &&
                            dir.listFiles()?.any { child -> child.isFile && child.name.endsWith(".so") } == true
                    }
                    .toList()
            )
            addAll(environment.extraLibraryDirectories.filter(File::isDirectory))
        }.distinctBy { it.absolutePath }

        val env = linkedMapOf(
            "JAVA_HOME" to runtime.home.absolutePath,
            "HOME" to environment.homeDirectory.absolutePath,
            "TMPDIR" to environment.tempDirectory.absolutePath,
            "LD_LIBRARY_PATH" to libraryDirectories.joinToString(File.pathSeparator) {
                it.absolutePath
            }
        )

        val args = command.asArgumentList(runtime.javaExecutable.absolutePath)

        nativeLaunch(
            jliPath = jli.absolutePath,
            args = args.toTypedArray(),
            envKeys = env.keys.toTypedArray(),
            envValues = env.values.toTypedArray(),
            workingDirectory = environment.gameDirectory.absolutePath
        )
    }

    private external fun nativeLaunch(
        jliPath: String,
        args: Array<String>,
        envKeys: Array<String>,
        envValues: Array<String>,
        workingDirectory: String
    ): Int
}
