package io.yannickfan.avero.game

import io.yannickfan.avero.androidnative.AndroidVanillaLaunchPreparer
import io.yannickfan.avero.androidnative.PreparedAndroidVanillaLaunch
import io.yannickfan.avero.minecraft.AssetInstaller
import io.yannickfan.avero.minecraft.InstanceLayout
import io.yannickfan.avero.minecraft.LauncherInstance
import io.yannickfan.avero.minecraft.MinecraftVersionMetadata
import io.yannickfan.avero.runtime.AndroidJavaRuntimeCatalog
import io.yannickfan.avero.runtime.AndroidJavaRuntimeInstaller
import io.yannickfan.avero.runtime.InstalledJavaRuntime
import io.yannickfan.avero.runtime.RuntimeArch
import java.io.File

data class ReadyGameLaunch(
    val runtime: InstalledJavaRuntime,
    val prepared: PreparedAndroidVanillaLaunch
)

class GameLaunchReadinessChecker(
    private val runtimeInstaller: AndroidJavaRuntimeInstaller =
        AndroidJavaRuntimeInstaller(),
    private val launchPreparer: AndroidVanillaLaunchPreparer =
        AndroidVanillaLaunchPreparer(),
    private val assetInstaller: AssetInstaller = AssetInstaller()
) {
    suspend fun prepareOrThrow(
        metadata: MinecraftVersionMetadata,
        request: GameLaunchRequest,
        minecraftRoot: File,
        runtimeRoot: File,
        gameDirectory: File,
        arch: RuntimeArch
    ): ReadyGameLaunch {
        require(request.versionId == metadata.id) {
            "Launch request version does not match installed metadata"
        }

        val layout = InstanceLayout(minecraftRoot)
        require(layout.clientJar(metadata.id).isFile) {
            "Minecraft client jar is not installed"
        }
        val assetIndex = layout.assetIndex(metadata.assetIndexId)
        require(assetIndex.isFile) {
            "Minecraft asset index is not installed"
        }
        val missingOrCorruptAsset = assetInstaller.parseIndex(assetIndex)
            .firstOrNull { asset -> !assetInstaller.isInstalled(asset, minecraftRoot) }
        require(missingOrCorruptAsset == null) {
            "Minecraft asset is missing or corrupt: " + missingOrCorruptAsset?.logicalName
        }
        metadata.logging?.let { logging ->
            require(layout.loggingConfig(logging.fileId).isFile) {
                "Minecraft logging configuration is not installed"
            }
        }

        val runtimePackage = requireNotNull(
            AndroidJavaRuntimeCatalog.find(metadata.javaMajorVersion, arch)
        ) {
            "No managed Java ${metadata.javaMajorVersion} runtime for " +
                arch.assetToken
        }
        val runtimeHome = File(runtimeRoot, runtimePackage.id)
        val runtime = requireNotNull(
            runtimeInstaller.findInstalled(runtimePackage, runtimeHome)
        ) {
            "Required managed Java runtime is not installed"
        }

        val prepared = launchPreparer.prepareInstalled(
            metadata = metadata,
            instance = LauncherInstance(
                name = "Avero launch",
                versionId = metadata.id,
                memoryMb = request.memoryMb,
                javaMajorVersion = metadata.javaMajorVersion
            ),
            minecraftRoot = minecraftRoot,
            gameDirectory = gameDirectory,
            arch = arch
        )

        val missingClasspath = prepared.plan.classpathEntries
            .map { File(minecraftRoot, it) }
            .filterNot(File::isFile)
        require(missingClasspath.isEmpty()) {
            "Launch classpath is incomplete: ${missingClasspath.size} file(s) missing"
        }

        return ReadyGameLaunch(
            runtime = runtime,
            prepared = prepared
        )
    }
}
