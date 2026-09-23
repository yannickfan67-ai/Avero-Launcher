package io.yannickfan.avero.minecraft

import android.os.Build
import java.io.File

enum class AndroidRuntimeAbi(val id: String) {
    ARM64_V8A("arm64-v8a"),
    X86_64("x86_64");

    companion object {
        fun select(candidates: List<String>): AndroidRuntimeAbi? {
            for (candidate in candidates) {
                when (candidate.lowercase()) {
                    "arm64-v8a", "aarch64", "arm64" -> return ARM64_V8A
                    "x86_64", "amd64" -> return X86_64
                }
            }
            return null
        }
    }
}

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

class ManagedRuntimeLayout(private val root: File) {
    fun runtimeDirectory(
        javaMajorVersion: Int,
        abi: AndroidRuntimeAbi
    ): File =
        File(root, "runtimes/java-$javaMajorVersion/\${abi.id}")

    fun javaExecutable(
        javaMajorVersion: Int,
        abi: AndroidRuntimeAbi
    ): File =
        File(runtimeDirectory(javaMajorVersion, abi), "bin/java")
}

class RuntimeManager {
    fun requirementFor(
        metadata: MinecraftVersionMetadata,
        runtimeRoot: File? = null,
        supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList()
    ): RuntimeRequirement {
        val major = metadata.javaMajorVersion
        val abi = AndroidRuntimeAbi.select(supportedAbis)
        val supportedMajor = major in SUPPORTED_JAVA_MAJORS

        if (!supportedMajor || abi == null) {
            return RuntimeRequirement(
                javaMajorVersion = major,
                architecture = abi?.id ?: supportedAbis.firstOrNull().orEmpty().ifBlank { "unknown" },
                state = RuntimeState.UNSUPPORTED
            )
        }

        val javaExecutable = runtimeRoot?.let {
            ManagedRuntimeLayout(it).javaExecutable(major, abi)
        }
        val state = if (javaExecutable != null && isUsableJava(javaExecutable)) {
            RuntimeState.AVAILABLE
        } else {
            RuntimeState.NEEDS_INSTALL
        }

        return RuntimeRequirement(
            javaMajorVersion = major,
            architecture = abi.id,
            state = state,
            javaExecutable = javaExecutable
        )
    }

    private fun isUsableJava(file: File): Boolean =
        file.isFile && file.canExecute()

    companion object {
        private val SUPPORTED_JAVA_MAJORS = setOf(8, 17, 21)
    }
}
