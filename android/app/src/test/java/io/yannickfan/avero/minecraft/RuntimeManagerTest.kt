package io.yannickfan.avero.minecraft

import io.yannickfan.avero.runtime.AndroidJavaRuntimeCatalog
import io.yannickfan.avero.runtime.RuntimeArch
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeManagerTest {
    @Test
    fun discoversArm64RuntimeUsingInstallerCatalogLayout() = withRuntimeRoot { root ->
        installFakeRuntime(root, 21, RuntimeArch.ARM64)

        val requirement = RuntimeManager(root).requirementFor(
            metadata(javaMajor = 21),
            architecture = "arm64-v8a"
        )

        assertEquals(RuntimeState.AVAILABLE, requirement.state)
        assertEquals("arm64", requirement.architecture)
        assertEquals(21, requirement.installation?.javaMajorVersion)
        assertEquals(
            AndroidJavaRuntimeCatalog.find(21, RuntimeArch.ARM64)?.id,
            requirement.installation?.home?.name
        )
    }

    @Test
    fun discoversX8664RuntimeFromJvmArchitectureAlias() = withRuntimeRoot { root ->
        installFakeRuntime(root, 17, RuntimeArch.X86_64)

        val requirement = RuntimeManager(root).requirementFor(
            metadata(javaMajor = 17),
            architecture = "amd64"
        )

        assertEquals(RuntimeState.AVAILABLE, requirement.state)
        assertEquals("x86_64", requirement.architecture)
        assertNotNull(requirement.installation)
    }

    @Test
    fun supportsCatalogBacked32BitArmRuntime() = withRuntimeRoot { root ->
        installFakeRuntime(root, 8, RuntimeArch.ARM)

        val requirement = RuntimeManager(root).requirementFor(
            metadata(javaMajor = 8),
            architecture = "armeabi-v7a"
        )

        assertEquals(RuntimeState.AVAILABLE, requirement.state)
        assertEquals("arm", requirement.architecture)
    }

    @Test
    fun differentInstalledMajorDoesNotSatisfyRequirement() = withRuntimeRoot { root ->
        installFakeRuntime(root, 17, RuntimeArch.ARM64)

        val requirement = RuntimeManager(root).requirementFor(
            metadata(javaMajor = 21),
            architecture = "arm64-v8a"
        )

        assertEquals(RuntimeState.NEEDS_INSTALL, requirement.state)
        assertNull(requirement.installation)
    }

    @Test
    fun nonExecutableJavaIsNotReportedAvailable() = withRuntimeRoot { root ->
        installFakeRuntime(root, 21, RuntimeArch.ARM64, executable = false)

        val requirement = RuntimeManager(root).requirementFor(
            metadata(javaMajor = 21),
            architecture = "arm64-v8a"
        )

        assertEquals(RuntimeState.NEEDS_INSTALL, requirement.state)
        assertNull(requirement.installation)
    }

    @Test
    fun unsupportedAbiIsReportedAsUnsupported() = withRuntimeRoot { root ->
        val requirement = RuntimeManager(root).requirementFor(
            metadata(javaMajor = 21),
            architecture = "riscv64"
        )

        assertEquals(RuntimeState.UNSUPPORTED, requirement.state)
        assertNull(requirement.installation)
    }

    @Test
    fun javaMajorWithoutCatalogPackageIsUnsupported() = withRuntimeRoot { root ->
        val requirement = RuntimeManager(root).requirementFor(
            metadata(javaMajor = 22),
            architecture = "arm64-v8a"
        )

        assertEquals(RuntimeState.UNSUPPORTED, requirement.state)
        assertNull(requirement.installation)
    }

    private fun installFakeRuntime(
        root: java.io.File,
        major: Int,
        arch: RuntimeArch,
        executable: Boolean = true
    ) {
        val runtimePackage = requireNotNull(AndroidJavaRuntimeCatalog.find(major, arch))
        val home = root.resolve(runtimePackage.id)
        val java = home.resolve("bin/java")
        java.parentFile.mkdirs()
        java.writeText("#!/bin/sh\nexit 0\n")
        if (executable) {
            check(java.setExecutable(true, false)) { "Could not mark fake java executable" }
        } else {
            java.setExecutable(false, false)
        }
    }

    private fun metadata(javaMajor: Int) = MinecraftVersionMetadata(
        id = "test",
        type = "release",
        mainClass = "example.Main",
        javaMajorVersion = javaMajor,
        client = DownloadSpec("https://example.invalid/client.jar", null, 1),
        assetIndexId = "test",
        assetIndex = DownloadSpec("https://example.invalid/assets.json", null, 1),
        libraries = emptyList(),
        logging = null,
        gameArguments = emptyList(),
        jvmArguments = emptyList()
    )

    private fun withRuntimeRoot(block: (java.io.File) -> Unit) {
        val root = Files.createTempDirectory("avero-runtime-test").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
