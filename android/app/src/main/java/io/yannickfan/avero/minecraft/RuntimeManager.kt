package io.yannickfan.avero.minecraft

import io.yannickfan.avero.runtime.AndroidJavaRuntimeCatalog
import io.yannickfan.avero.runtime.AndroidJavaRuntimeInstaller
import io.yannickfan.avero.runtime.RuntimeArch
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
    private val managedRoot: File? = null,
    private val installer: AndroidJavaRuntimeInstaller = AndroidJavaRuntimeInstaller()
) {
    fun requirementFor(
        metadata: MinecraftVersionMetadata,
        architecture: String = System.getProperty("os.arch") ?: "unknown"
    ): RuntimeRequirement {
        val major = metadata.javaMajorVersion
        val arch = resolveRuntimeArch(architecture)
        val runtimePackage = arch?.let { AndroidJavaRuntimeCatalog.find(major, it) }

        if (arch == null || runtimePackage == null) {
            return RuntimeRequirement(
                javaMajorVersion = major,
                architecture = architecture,
                state = RuntimeState.UNSUPPORTED
            )
        }

        val installation = discover(major, architecture)
        return RuntimeRequirement(
            javaMajorVersion = major,
            architecture = arch.assetToken,
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
        val arch = resolveRuntimeArch(architecture) ?: return null
        val runtimePackage = AndroidJavaRuntimeCatalog.find(javaMajorVersion, arch) ?: return null
        val runtimeHome = File(root, runtimePackage.id)
        val installed = installer.findInstalled(runtimePackage, runtimeHome) ?: return null
        if (!installed.javaExecutable.canExecute()) return null

        return RuntimeInstallation(
            javaMajorVersion = runtimePackage.majorVersion,
            architecture = runtimePackage.arch.assetToken,
            home = installed.home,
            javaExecutable = installed.javaExecutable
        )
    }

    companion object {
        fun resolveRuntimeArch(architecture: String): RuntimeArch? =
            RuntimeArch.fromAbi(architecture) ?: when (architecture.lowercase()) {
                "aarch64", "arm64" -> RuntimeArch.ARM64
                "armv7l", "armv7", "arm" -> RuntimeArch.ARM
                "amd64" -> RuntimeArch.X86_64
                "i386", "i486", "i586", "i686" -> RuntimeArch.X86
                else -> null
            }
    }
}
