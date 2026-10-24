package io.yannickfan.avero.minecraft

import io.yannickfan.avero.runtime.AndroidJavaRuntimeCatalog
import io.yannickfan.avero.runtime.RuntimeArch
import java.io.File

data class RuntimeRequirement(
    val javaMajorVersion: Int,
    val architecture: String,
    val state: RuntimeState,
    val javaExecutable: File? = null
)

enum class RuntimeState {
    AVAILABLE,
    NEEDS_INSTALL,
    UNSUPPORTED
}

class RuntimeManager {
    fun requirementFor(
        metadata: MinecraftVersionMetadata,
        runtimeRoot: File? = null,
        arch: RuntimeArch? = RuntimeArch.current()
    ): RuntimeRequirement {
        val major = metadata.javaMajorVersion
        if (arch == null) {
            return RuntimeRequirement(
                javaMajorVersion = major,
                architecture = "unknown",
                state = RuntimeState.UNSUPPORTED
            )
        }

        val runtimePackage = AndroidJavaRuntimeCatalog.find(major, arch)
            ?: return RuntimeRequirement(
                javaMajorVersion = major,
                architecture = arch.assetToken,
                state = RuntimeState.UNSUPPORTED
            )

        val javaExecutable = runtimeRoot?.let { root ->
            File(File(root, runtimePackage.id), "bin/java")
        }
        val available = javaExecutable?.let {
            it.isFile && it.canExecute()
        } == true

        return RuntimeRequirement(
            javaMajorVersion = major,
            architecture = arch.assetToken,
            state = if (available) RuntimeState.AVAILABLE else RuntimeState.NEEDS_INSTALL,
            javaExecutable = javaExecutable
        )
    }
}
