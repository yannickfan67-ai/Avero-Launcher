package io.yannickfan.avero.minecraft

import io.yannickfan.avero.runtime.AndroidJavaRuntimeCatalog
import io.yannickfan.avero.runtime.RuntimeArch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RuntimeManagerTest {
    @Test
    fun mapsSupportedAndroidAbis() {
        assertEquals(RuntimeArch.ARM64, RuntimeArch.fromAbi("arm64-v8a"))
        assertEquals(RuntimeArch.X86_64, RuntimeArch.fromAbi("x86_64"))
        assertNull(RuntimeArch.fromAbi("mips64"))
    }

    @Test
    fun missingManagedRuntimeNeedsInstallAtInstallerPath() {
        val root = Files.createTempDirectory("avero-runtime-test").toFile()
        try {
            val requirement = RuntimeManager().requirementFor(
                metadata = metadata(javaMajorVersion = 21),
                runtimeRoot = root,
                supportedAbis = listOf("arm64-v8a")
            )

            assertEquals(RuntimeState.NEEDS_INSTALL, requirement.state)
            assertEquals("arm64", requirement.architecture)
            assertNotNull(requirement.javaExecutable)
            assertTrue(
                requirement.javaExecutable!!.path.endsWith(
                    "java21-arm64/bin/java"
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun installedRuntimeFromCatalogIsAvailable() {
        val root = Files.createTempDirectory("avero-runtime-test").toFile()
        try {
            val pkg = requireNotNull(
                AndroidJavaRuntimeCatalog.find(17, RuntimeArch.X86_64)
            )
            val java = File(File(root, pkg.id), "bin/java")
            java.parentFile!!.mkdirs()
            java.writeText("#!/bin/sh\nexit 0\n")
            java.setExecutable(true, true)

            val requirement = RuntimeManager().requirementFor(
                metadata = metadata(javaMajorVersion = 17),
                runtimeRoot = root,
                supportedAbis = listOf("x86_64")
            )

            assertEquals(RuntimeState.AVAILABLE, requirement.state)
            assertEquals("x86_64", requirement.architecture)
            assertEquals(java.canonicalFile, requirement.javaExecutable!!.canonicalFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun unsupportedJavaOrAbiIsReportedClearly() {
        val root = Files.createTempDirectory("avero-runtime-test").toFile()
        try {
            assertEquals(
                RuntimeState.UNSUPPORTED,
                RuntimeManager().requirementFor(
                    metadata = metadata(javaMajorVersion = 11),
                    runtimeRoot = root,
                    supportedAbis = listOf("arm64-v8a")
                ).state
            )
            assertEquals(
                RuntimeState.UNSUPPORTED,
                RuntimeManager().requirementFor(
                    metadata = metadata(javaMajorVersion = 21),
                    runtimeRoot = root,
                    supportedAbis = listOf("mips64")
                ).state
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun metadata(javaMajorVersion: Int) = MinecraftVersionMetadata(
        id = "test",
        type = "release",
        mainClass = "net.minecraft.client.main.Main",
        javaMajorVersion = javaMajorVersion,
        client = DownloadSpec(
            url = "https://example.invalid/client.jar",
            sha1 = null,
            size = null
        ),
        assetIndexId = "test",
        assetIndex = DownloadSpec(
            url = "https://example.invalid/assets.json",
            sha1 = null,
            size = null
        ),
        libraries = emptyList(),
        logging = null,
        gameArguments = emptyList(),
        jvmArguments = emptyList()
    )
}
