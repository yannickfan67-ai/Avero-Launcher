package io.yannickfan.avero.minecraft

import android.os.Build
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
        supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList()
    ): RuntimeRequirement {
        val major = metadata.javaMajorVersion
        val arch = supportedAbis.firstNotNullOfOrNull { RuntimeArch.fromAbi(it) }

        if (arch == null) {
            return RuntimeRequirement(
                javaMajorVersion = major,
                architecture = supportedAbis.firstOrNull().orEmpty().ifBlank { "unknown" },
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
        val state = if (javaExecutable != null && isUsableJava(javaExecutable)) {
            RuntimeState.AVAILABLE
        } else {
            RuntimeState.NEEDS_INSTALL
        }

        return RuntimeRequirement(
            javaMajorVersion = major,
            architecture = arch.assetToken,
            state = state,
            javaExecutable = javaExecutable
        )
    }

    private fun isUsableJava(file: File): Boolean =
        file.isFile && file.canExecute()
}
