package io.yannickfan.avero.runtime

import java.nio.file.Files
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidJavaRuntimeInstallerTest {
    @Test
    fun executableJavaIsRecognizedAsInstalled() = withRuntimeHome { root ->
        val runtimePackage = requireNotNull(
            AndroidJavaRuntimeCatalog.find(21, RuntimeArch.ARM64)
        )
        val home = root.resolve(runtimePackage.id)
        val java = home.resolve("bin/java")
        java.parentFile.mkdirs()
        java.writeText("#!/bin/sh\nexit 0\n")
        check(java.setExecutable(true, false))

        val installed = AndroidJavaRuntimeInstaller().findInstalled(
            runtimePackage,
            home
        )

        assertNotNull(installed)
    }

    @Test
    fun nonExecutableJavaIsNotRecognizedAsInstalled() = withRuntimeHome { root ->
        val runtimePackage = requireNotNull(
            AndroidJavaRuntimeCatalog.find(21, RuntimeArch.ARM64)
        )
        val home = root.resolve(runtimePackage.id)
        val java = home.resolve("bin/java")
        java.parentFile.mkdirs()
        java.writeText("#!/bin/sh\nexit 0\n")
        java.setExecutable(false, false)

        val installed = AndroidJavaRuntimeInstaller().findInstalled(
            runtimePackage,
            home
        )

        assertNull(installed)
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
