package io.yannickfan.avero.minecraft

data class RuntimeRequirement(
    val javaMajorVersion: Int,
    val architecture: String,
    val state: RuntimeState
)

enum class RuntimeState {
    AVAILABLE,
    NEEDS_INSTALL,
    UNSUPPORTED
}

class RuntimeManager {
    fun requirementFor(
        metadata: MinecraftVersionMetadata,
        architecture: String = System.getProperty("os.arch") ?: "unknown"
    ): RuntimeRequirement {
        val major = metadata.javaMajorVersion
        val supportedMajor = major in setOf(8, 17, 21)
        val supportedArch = architecture.contains("aarch64", ignoreCase = true) ||
            architecture.contains("arm64", ignoreCase = true) ||
            architecture.contains("x86_64", ignoreCase = true) ||
            architecture.contains("amd64", ignoreCase = true)

        return RuntimeRequirement(
            javaMajorVersion = major,
            architecture = architecture,
            state = if (supportedMajor && supportedArch) RuntimeState.NEEDS_INSTALL else RuntimeState.UNSUPPORTED
        )
    }
}
