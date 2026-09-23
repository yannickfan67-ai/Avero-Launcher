package io.yannickfan.avero.minecraft

import java.io.File

data class RuntimeInstallation(
    val javaMajorVersion: Int,
    val architecture: String,
    val home: File,
    val javaExecutable: File
)

data class RuntimeRequirement(
    val javaMajorVersion: Int,
    val architecture: String,
    val state: RuntimeState,
    val installation: RuntimeInstallation? = null
)

enum class RuntimeState {
    AVAILABLE,
    NEEDS_INSTALL,
    UNSUPPORTED
}

class RuntimeManager(
    private val managedRoot: File? = null
) {
    fun requirementFor(
        metadata: MinecraftVersionMetadata,
        architecture: String = System.getProperty("os.arch") ?: "unknown"
    ): RuntimeRequirement {
        val major = metadata.javaMajorVersion
        val normalizedAbi = normalizeAndroidAbi(architecture)

        if (major !in SUPPORTED_JAVA_MAJORS || normalizedAbi == null) {
            return RuntimeRequirement(
                javaMajorVersion = major,
                architecture = architecture,
                state = RuntimeState.UNSUPPORTED
            )
        }

        val installation = discover(major, normalizedAbi)
        return RuntimeRequirement(
            javaMajorVersion = major,
            architecture = normalizedAbi,
            state = if (installation != null) {
                RuntimeState.AVAILABLE
            } else {
                RuntimeState.NEEDS_INSTALL
            },
            installation = installation
        )
    }

    fun discover(
        javaMajorVersion: Int,
        architecture: String
    ): RuntimeInstallation? {
        val root = managedRoot ?: return null
        if (javaMajorVersion !in SUPPORTED_JAVA_MAJORS) return null

        val normalizedAbi = normalizeAndroidAbi(architecture) ?: return null
        val home = File(root, "$javaMajorVersion/$normalizedAbi")
        val executable = File(home, "bin/java")
        val release = File(home, "release")

        if (!executable.isFile || !executable.canExecute() || !release.isFile) return null
        if (readJavaMajor(release) != javaMajorVersion) return null

        return RuntimeInstallation(
            javaMajorVersion = javaMajorVersion,
            architecture = normalizedAbi,
            home = home,
            javaExecutable = executable
        )
    }

    companion object {
        private val SUPPORTED_JAVA_MAJORS = setOf(8, 17, 21)

        fun normalizeAndroidAbi(architecture: String): String? {
            val value = architecture.lowercase()
            return when {
                value == "arm64-v8a" ||
                    value.contains("aarch64") ||
                    value == "arm64" -> "arm64-v8a"
                value == "x86_64" ||
                    value == "amd64" -> "x86_64"
                else -> null
            }
        }

        private fun readJavaMajor(release: File): Int? {
            val version = release.useLines { lines ->
                lines.firstOrNull { it.startsWith("JAVA_VERSION=") }
            }?.substringAfter('=')
                ?.trim()
                ?.trim('"')
                ?.takeIf { it.isNotBlank() }
                ?: return null

            return if (version.startsWith("1.")) {
                version.substringAfter("1.").substringBefore('.').toIntOrNull()
            } else {
                version.substringBefore('.').substringBefore('-').toIntOrNull()
            }
        }
    }
}
