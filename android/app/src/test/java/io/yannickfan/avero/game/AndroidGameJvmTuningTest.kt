package io.yannickfan.avero.game

import io.yannickfan.avero.minecraft.ResolvedLaunchCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidGameJvmTuningTest {
    @Test
    fun androidPropertiesOverrideConflictingJvmProperties() {
        val command = ResolvedLaunchCommand(
            javaMajorVersion = 21,
            mainClass = "net.minecraft.client.main.Main",
            jvmArguments = listOf(
                "-Xmx4096M",
                "-Dos.name=Windows 11",
                "-Dglfwstub.windowWidth=1"
            ),
            gameArguments = listOf(
                "--accessToken",
                "secret-token"
            )
        )

        val tuned = AndroidGameJvmTuning.apply(
            command,
            AndroidJvmTuning(
                javaHome = File("/runtime"),
                tempDirectory = File("/tmp"),
                userHome = File("/home"),
                androidRelease = "14",
                width = 1920,
                height = 1080
            )
        )

        assertTrue(tuned.jvmArguments.contains("-Xmx4096M"))
        assertTrue(tuned.jvmArguments.contains("-Dos.name=Linux"))
        assertTrue(
            tuned.jvmArguments.contains(
                "-Dglfwstub.windowWidth=1920"
            )
        )
        assertTrue(
            tuned.jvmArguments.contains(
                "-Dglfwstub.windowHeight=1080"
            )
        )
        assertTrue(
            tuned.jvmArguments.contains(
                "-Dorg.lwjgl.opengl.libname=libGLESv2.so"
            )
        )
        assertFalse(tuned.jvmArguments.contains("-Dos.name=Windows 11"))
        assertEquals(command.gameArguments, tuned.gameArguments)
    }
}
