package io.yannickfan.avero.game

import io.yannickfan.avero.minecraft.ResolvedLaunchCommand
import java.io.File

data class AndroidJvmTuning(
    val javaHome: File,
    val tempDirectory: File,
    val userHome: File,
    val androidRelease: String,
    val width: Int,
    val height: Int
)

object AndroidGameJvmTuning {
    fun apply(
        command: ResolvedLaunchCommand,
        tuning: AndroidJvmTuning
    ): ResolvedLaunchCommand {
        require(tuning.width > 0 && tuning.height > 0) {
            "Game Surface dimensions must be positive"
        }

        val overrides = linkedMapOf(
            "java.home" to tuning.javaHome.absolutePath,
            "java.io.tmpdir" to tuning.tempDirectory.absolutePath,
            "user.home" to tuning.userHome.absolutePath,
            "os.name" to "Linux",
            "os.version" to "Android-${tuning.androidRelease}",
            "org.lwjgl.opengl.libname" to "libGLESv2.so",
            "org.lwjgl.vulkan.libname" to "libvulkan.so",
            "glfwstub.windowWidth" to tuning.width.toString(),
            "glfwstub.windowHeight" to tuning.height.toString(),
            "glfwstub.initEgl" to "false",
            "log4j2.formatMsgNoLookups" to "true",
            "jdk.lang.Process.launchMechanism" to "FORK"
        )

        val filtered = command.jvmArguments.filterNot { argument ->
            val key = systemPropertyKey(argument)
            key != null && key in overrides
        }

        return command.copy(
            jvmArguments = buildList {
                addAll(filtered)
                overrides.forEach { (key, value) ->
                    add("-D$key=$value")
                }
            }
        )
    }

    private fun systemPropertyKey(argument: String): String? {
        if (!argument.startsWith("-D")) return null
        val equals = argument.indexOf('=')
        if (equals <= 2) return null
        return argument.substring(2, equals)
    }
}
