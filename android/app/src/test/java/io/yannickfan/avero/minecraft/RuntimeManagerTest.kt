package io.yannickfan.avero.minecraft

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeManagerTest {
    @Test
    fun discoversArm64RuntimeWithMatchingRelease() = withRuntimeRoot { root ->
        installFakeRuntime(root, 21, "arm64-v8a", "21.0.7")

        val requirement = RuntimeManager(root).requirementFor(
            metadata(javaMajor = 21),
            architecture = "aarch64"
        )

        assertEquals(RuntimeState.AVAILABLE, requirement.state)
        assertEquals("arm64-v8a", requirement.architecture)
        assertEquals(21, requirement.installation?.javaMajorVersion)
        assertEquals(
            root.resolve("21/arm64-v8a/bin/java").canonicalFile,
            requirement.installation?.javaExecutable?.canonicalFile
        )
    }

    @Test
    fun discoversX8664RuntimeAndLegacyJavaVersionSyntax() = withRuntimeRoot { root ->
        installFakeRuntime(root, 8, "x86_64", "1.8.0_452")

        val requirement = RuntimeManager(root).requirementFor(
            metadata(javaMajor = 8),
            architecture = "amd64"
        )

        assertEquals(RuntimeState.AVAILABLE, requirement.state)
        assertEquals("x86_64", requirement.architecture)
        assertNotNull(requirement.installation)
    }

    @Test
    fun wrongRuntimeMajorIsNotAccepted() = withRuntimeRoot { root ->
        installFakeRuntime(root, 21, "arm64-v8a", "17.0.13")

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
            metadata(javaMajor = 17),
            architecture = "armeabi-v7a"
        )

        assertEquals(RuntimeState.UNSUPPORTED, requirement.state)
        assertNull(requirement.installation)
    }

    @Test
    fun supportedAbiWithoutRuntimeNeedsInstall() = withRuntimeRoot { root ->
        val requirement = RuntimeManager(root).requirementFor(
            metadata(javaMajor = 17),
            architecture = "x86_64"
        )

        assertEquals(RuntimeState.NEEDS_INSTALL, requirement.state)
        assertEquals("x86_64", requirement.architecture)
        assertNull(requirement.installation)
    }

    private fun installFakeRuntime(
        root: java.io.File,
        major: Int,
        abi: String,
        releaseVersion: String
    ) {
        val home = root.resolve("$major/$abi")
        val java = home.resolve("bin/java")
        java.parentFile.mkdirs()
        java.writeText("#!/bin/sh\nexit 0\n")
        check(java.setExecutable(true, false)) { "Could not mark fake java executable" }
        home.resolve("release").writeText("JAVA_VERSION=\"$releaseVersion\"\n")
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
