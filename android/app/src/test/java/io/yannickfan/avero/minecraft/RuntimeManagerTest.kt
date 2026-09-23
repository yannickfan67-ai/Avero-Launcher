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
    fun runtimeArchMapsSupportedAndroidAbis() {
        assertEquals(RuntimeArch.ARM64, RuntimeArch.fromAbi("arm64-v8a"))
        assertEquals(RuntimeArch.X86_64, RuntimeArch.fromAbi("x86_64"))
        assertNull(RuntimeArch.fromAbi("mips64"))
    }

    @Test
    fun missingRuntimeNeedsInstallAndUsesCatalogLayout() {
        val root = Files.createTempDirectory("avero-runtime-test").toFile()
        try {
            val requirement = RuntimeManager().requirementFor(
                metadata = metadata(21),
                runtimeRoot = root,
                arch = RuntimeArch.ARM64
            )

            assertEquals(RuntimeState.NEEDS_INSTALL, requirement.state)
            assertEquals("arm64", requirement.architecture)
            assertNotNull(requirement.javaExecutable)
            val suffix = listOf("java21-arm64", "bin", "java")
                .joinToString(File.separator)
            assertTrue(requirement.javaExecutable!!.path.endsWith(suffix))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun installedExecutableRuntimeIsAvailable() {
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
                metadata = metadata(17),
                runtimeRoot = root,
                arch = RuntimeArch.X86_64
            )

            assertEquals(RuntimeState.AVAILABLE, requirement.state)
            assertEquals(java.canonicalFile, requirement.javaExecutable!!.canonicalFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingCatalogPackageIsUnsupported() {
        val root = Files.createTempDirectory("avero-runtime-test").toFile()
        try {
            val requirement = RuntimeManager().requirementFor(
                metadata = metadata(11),
                runtimeRoot = root,
                arch = RuntimeArch.ARM64
            )
            assertEquals(RuntimeState.UNSUPPORTED, requirement.state)
            assertNull(requirement.javaExecutable)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun metadata(javaMajorVersion: Int) = MinecraftVersionMetadata(
        id = "test",
        type = "release",
        mainClass = "net.minecraft.client.main.Main",
        javaMajorVersion = javaMajorVersion,
        client = DownloadSpec("https://example.invalid/client.jar", null, null),
        assetIndexId = "test",
        assetIndex = DownloadSpec("https://example.invalid/assets.json", null, null),
        libraries = emptyList(),
        logging = null,
        gameArguments = emptyList(),
        jvmArguments = emptyList()
    )
}
