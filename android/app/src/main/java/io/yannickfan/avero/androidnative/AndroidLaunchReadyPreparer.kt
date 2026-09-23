package io.yannickfan.avero.androidnative

import io.yannickfan.avero.auth.AuthenticatedMinecraftAccount
import io.yannickfan.avero.minecraft.AssetInstaller
import io.yannickfan.avero.minecraft.InstanceLayout
import io.yannickfan.avero.minecraft.LaunchCommandBuilder
import io.yannickfan.avero.minecraft.LaunchIdentity
import io.yannickfan.avero.minecraft.LauncherInstance
import io.yannickfan.avero.minecraft.MinecraftVersionMetadata
import io.yannickfan.avero.minecraft.ResolvedLaunchCommand
import io.yannickfan.avero.runtime.AndroidJavaRuntimeCatalog
import io.yannickfan.avero.runtime.InstalledJavaRuntime
import io.yannickfan.avero.runtime.JvmLaunchEnvironment
import java.io.File

data class LaunchReadyAndroidVanilla(
    val preparation: PreparedAndroidVanillaLaunch,
    val runtime: InstalledJavaRuntime,
    val command: ResolvedLaunchCommand,
    val jvmEnvironment: JvmLaunchEnvironment
)

class AndroidLaunchReadyPreparer(
    private val nativePreparer: AndroidVanillaLaunchPreparer =
        AndroidVanillaLaunchPreparer(),
    private val commandBuilder: LaunchCommandBuilder = LaunchCommandBuilder(),
    private val assetInstaller: AssetInstaller = AssetInstaller()
) {
    suspend fun prepareInstalled(
        metadata: MinecraftVersionMetadata,
        instance: LauncherInstance,
        account: AuthenticatedMinecraftAccount,
        minecraftRoot: File,
        runtime: InstalledJavaRuntime
    ): LaunchReadyAndroidVanilla {
        require(account.entitlements.hasAnyEntitlement) {
            "Minecraft account has no game entitlement"
        }

        val requiredJava = instance.javaMajorVersion ?: metadata.javaMajorVersion
        require(runtime.packageInfo.majorVersion == requiredJava) {
            "Installed Java ${runtime.packageInfo.majorVersion} does not match required Java $requiredJava"
        }
        val expectedRuntime = requireNotNull(
            AndroidJavaRuntimeCatalog.find(requiredJava, runtime.packageInfo.arch)
        ) {
            "No pinned Android Java runtime matches the selected version and ABI"
        }
        require(expectedRuntime.id == runtime.packageInfo.id) {
            "Installed Java runtime does not match the pinned runtime catalog"
        }
        require(runtime.javaExecutable.isFile && runtime.javaExecutable.canExecute()) {
            "Installed Java executable is unavailable"
        }
        val jli = requireNotNull(runtime.jliLibrary) {
            "Installed Java runtime does not contain libjli.so"
        }
        require(jli.isFile) { "Installed libjli.so is unavailable" }
        require(!jli.canWrite()) {
            "Installed libjli.so must be read-only before Android dynamic loading"
        }

        val layout = InstanceLayout(minecraftRoot)
        val gameDirectory = layout.gameDirectory(instance.name)
        val preparation = nativePreparer.prepareInstalled(
            metadata = metadata,
            instance = instance,
            minecraftRoot = minecraftRoot,
            gameDirectory = gameDirectory,
            arch = runtime.packageInfo.arch
        )

        require(preparation.plan.nativeArchives.isEmpty()) {
            "Desktop Mojang native archives must not be selected for Android"
        }

        val assetIndex = layout.assetIndex(metadata.assetIndexId)
        require(assetIndex.isFile) {
            "Minecraft asset index is not installed: ${metadata.assetIndexId}"
        }
        val missingAsset = assetInstaller.parseIndex(assetIndex)
            .firstOrNull { asset ->
                !assetInstaller.isInstalled(asset, minecraftRoot)
            }
        require(missingAsset == null) {
            "Minecraft asset is missing or incomplete: ${missingAsset?.logicalName}"
        }

        val missingClasspath = preparation.plan.classpathEntries
            .firstOrNull { entry ->
                !managedFile(minecraftRoot, entry).isFile
            }
        require(missingClasspath == null) {
            "Minecraft classpath entry is not installed: $missingClasspath"
        }

        metadata.logging?.let { logging ->
            require(layout.loggingConfig(logging.fileId).isFile) {
                "Minecraft logging configuration is not installed: ${logging.fileId}"
            }
        }

        val command = commandBuilder.resolve(
            plan = preparation.plan,
            identity = LaunchIdentity(
                playerName = account.profile.name,
                uuid = account.profile.id,
                accessToken = account.minecraftAccessToken
            ),
            environment = preparation.environment
        )

        val instanceRoot = requireNotNull(gameDirectory.parentFile)
        val jvmEnvironment = JvmLaunchEnvironment(
            javaHome = runtime.home,
            gameDirectory = gameDirectory,
            tempDirectory = File(instanceRoot, "tmp"),
            homeDirectory = instanceRoot,
            extraLibraryDirectories = listOf(
                preparation.nativeProvider.nativeDirectory
            )
        )

        return LaunchReadyAndroidVanilla(
            preparation = preparation,
            runtime = runtime,
            command = command,
            jvmEnvironment = jvmEnvironment
        )
    }

    private fun managedFile(root: File, relativePath: String): File {
        require(!File(relativePath).isAbsolute) {
            "Managed launch path must be relative: $relativePath"
        }
        val canonicalRoot = root.canonicalFile
        val candidate = File(canonicalRoot, relativePath).canonicalFile
        require(candidate.path.startsWith(canonicalRoot.path + File.separator)) {
            "Managed launch path escapes Minecraft root: $relativePath"
        }
        return candidate
    }
}
