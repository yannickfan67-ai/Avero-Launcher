package io.yannickfan.avero.minecraft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class RuntimeManagerTest {
    @Test
    fun mapsSupportedAndroidAbisInPriorityOrder() {
        assertEquals(
            AndroidRuntimeAbi.ARM64_V8A,
            AndroidRuntimeAbi.select(listOf("armeabi-v7a", "arm64-v8a"))
        )
        assertEquals(
            AndroidRuntimeAbi.X86_64,
            AndroidRuntimeAbi.select(listOf("x86_64"))
        )
        assertNull(AndroidRuntimeAbi.select(listOf("armeabi-v7a", "x86")))
    }

    @Test
    fun missingManagedRuntimeNeedsInstall() {
        val root = Files.createTempDirectory("avero-runtime-test").toFile()
        try {
            val requirement = RuntimeManager().requirementFor(
                metadata = metadata(javaMajorVersion = 21),
                runtimeRoot = root,
                supportedAbis = listOf("arm64-v8a")
            )

            assertEquals(RuntimeState.NEEDS_INSTALL, requirement.state)
            assertEquals("arm64-v8a", requirement.architecture)
            assertNotNull(requirement.javaExecutable)
            assertTrue(
                requirement.javaExecutable!!.path.endsWith(
                    "runtimes/java-21/arm64-v8a/bin/java"
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun existingExecutableManagedRuntimeIsAvailable() {
        val root = Files.createTempDirectory("avero-runtime-test").toFile()
        try {
            val java = ManagedRuntimeLayout(root).javaExecutable(
                17,
                AndroidRuntimeAbi.X86_64
            )
            java.parentFile!!.mkdirs()
            java.writeText("#!/bin/sh\nexit 0\n")
            java.setExecutable(true, true)

            val requirement = RuntimeManager().requirementFor(
                metadata = metadata(javaMajorVersion = 17),
                runtimeRoot = root,
                supportedAbis = listOf("x86_64")
            )

            assertEquals(RuntimeState.AVAILABLE, requirement.state)
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
                    supportedAbis = listOf("armeabi-v7a")
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
