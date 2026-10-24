package io.yannickfan.avero.runtime

import java.nio.file.Files
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidJavaRuntimeInstallerTest {
    @Test
    fun executableJavaAndJliAreRecognizedAsInstalled() = withRuntimeHome { root ->
        val runtimePackage = requireNotNull(
            AndroidJavaRuntimeCatalog.find(21, RuntimeArch.ARM64)
        )
        val home = root.resolve(runtimePackage.id)
        createJava(home, executable = true)
        createJli(home)

        val installed = AndroidJavaRuntimeInstaller().findInstalled(
            runtimePackage,
            home
        )

        assertNotNull(installed)
        assertNotNull(installed?.jliLibrary)
    }

    @Test
    fun nonExecutableJavaIsNotRecognizedAsInstalled() = withRuntimeHome { root ->
        val runtimePackage = requireNotNull(
            AndroidJavaRuntimeCatalog.find(21, RuntimeArch.ARM64)
        )
        val home = root.resolve(runtimePackage.id)
        createJava(home, executable = false)
        createJli(home)

        val installed = AndroidJavaRuntimeInstaller().findInstalled(
            runtimePackage,
            home
        )

        assertNull(installed)
    }

    @Test
    fun missingJliIsNotRecognizedAsInstalled() = withRuntimeHome { root ->
        val runtimePackage = requireNotNull(
            AndroidJavaRuntimeCatalog.find(21, RuntimeArch.ARM64)
        )
        val home = root.resolve(runtimePackage.id)
        createJava(home, executable = true)

        val installed = AndroidJavaRuntimeInstaller().findInstalled(
            runtimePackage,
            home
        )

        assertNull(installed)
    }

    private fun createJava(home: java.io.File, executable: Boolean) {
        val java = home.resolve("bin/java")
        java.parentFile.mkdirs()
        java.writeText("#!/bin/sh\nexit 0\n")
        java.setExecutable(executable, false)
    }

    private fun createJli(home: java.io.File) {
        val jli = home.resolve("lib/jli/libjli.so")
        jli.parentFile.mkdirs()
        jli.writeBytes(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))
        jli.setWritable(false, false)
    }

    private fun withRuntimeHome(block: (java.io.File) -> Unit) {
        val root = Files.createTempDirectory("avero-runtime-installer-test").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
