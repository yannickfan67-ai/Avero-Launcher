package io.yannickfan.avero.androidnative

import io.yannickfan.avero.minecraft.InstanceLayout
import io.yannickfan.avero.minecraft.LaunchEnvironment
import io.yannickfan.avero.minecraft.LaunchPlan
import io.yannickfan.avero.minecraft.LaunchPlanner
import io.yannickfan.avero.minecraft.LauncherInstance
import io.yannickfan.avero.minecraft.MinecraftPlatform
import io.yannickfan.avero.minecraft.MinecraftVersionMetadata
import io.yannickfan.avero.runtime.RuntimeArch
import java.io.File

data class PreparedAndroidVanillaLaunch(
    val plan: LaunchPlan,
    val environment: LaunchEnvironment,
    val nativeProvider: InstalledAndroidNativeProvider
)

class AndroidVanillaLaunchPreparer(
    private val providerInstaller: AndroidNativeProviderInstaller =
        AndroidNativeProviderInstaller(),
    private val launchPlanner: LaunchPlanner = LaunchPlanner()
) {
    fun prepareInstalled(
        metadata: MinecraftVersionMetadata,
        instance: LauncherInstance,
        minecraftRoot: File,
        gameDirectory: File,
        arch: RuntimeArch
    ): PreparedAndroidVanillaLaunch {
        require(instance.versionId == metadata.id) {
            "Instance version does not match Minecraft metadata"
        }

        val providerPackage = requireNotNull(
            AndroidNativeProviderCatalog.detect(metadata, arch)
        ) {
            "No pinned Android LWJGL provider for the selected Minecraft version"
        }
        val providerPlan = AndroidNativeProviderCatalog.plan(providerPackage)
        val providerRoot = File(
            minecraftRoot,
            "android-native/${providerPackage.id}"
        )
        val installed = requireNotNull(
            providerInstaller.findInstalled(providerPackage, providerRoot)
        ) {
            "Android LWJGL provider is not installed: ${providerPackage.id}"
        }

        val plan = launchPlanner.createVanillaPlan(
            metadata = metadata,
            instance = instance,
            context = MinecraftPlatform.androidRuleContext(),
            androidNativeProvider = providerPlan
        )

        val expectedNativeDirectory =
            File(minecraftRoot, providerPlan.nativeDirectory).canonicalFile
        require(installed.nativeDirectory.canonicalFile == expectedNativeDirectory) {
            "Installed Android native provider does not match launch plan"
        }

        val layout = InstanceLayout(minecraftRoot)
        val environment = LaunchEnvironment(
            versionName = metadata.id,
            versionType = metadata.type,
            minecraftRoot = minecraftRoot,
            gameDirectory = gameDirectory,
            assetsRoot = File(minecraftRoot, "assets"),
            assetsIndexName = metadata.assetIndexId,
            nativesDirectory = installed.nativeDirectory,
            libraryDirectory = File(minecraftRoot, "libraries"),
            loggingConfigFile = metadata.logging?.let {
                layout.loggingConfig(it.fileId)
            }
        )

        return PreparedAndroidVanillaLaunch(
            plan = plan,
            environment = environment,
            nativeProvider = installed
        )
    }
}
