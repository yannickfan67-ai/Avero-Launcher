package io.yannickfan.avero.minecraft

import io.yannickfan.avero.androidnative.AndroidNativeProviderCatalog
import io.yannickfan.avero.androidnative.InstalledAndroidNativeProvider
import io.yannickfan.avero.auth.AuthenticatedMinecraftAccount
import io.yannickfan.avero.runtime.InstalledJavaRuntime
import io.yannickfan.avero.runtime.JvmLaunchEnvironment
import java.io.File

data class PreparedVanillaLaunch(
    val runtime: InstalledJavaRuntime,
    val nativeProvider: InstalledAndroidNativeProvider,
    val plan: LaunchPlan,
    val command: ResolvedLaunchCommand,
    val jvmEnvironment: JvmLaunchEnvironment
)

class VanillaLaunchPreparer(
    private val planner: LaunchPlanner = LaunchPlanner(),
    private val commandBuilder: LaunchCommandBuilder = LaunchCommandBuilder(),
    private val assetInstaller: AssetInstaller = AssetInstaller(),
    private val downloader: FileDownloader = FileDownloader()
) {
    suspend fun prepare(
        metadata: MinecraftVersionMetadata,
        instance: LauncherInstance,
        account: AuthenticatedMinecraftAccount,
        minecraftRoot: File,
        runtime: InstalledJavaRuntime,
        nativeProvider: InstalledAndroidNativeProvider
    ): PreparedVanillaLaunch {
        require(instance.loader == Loader.VANILLA) {
            "Only vanilla launch preparation is supported"
        }
        require(instance.versionId == metadata.id) {
            "Instance version does not match Minecraft metadata"
        }
        require(account.entitlements.hasAnyEntitlement) {
            "Minecraft account has no game entitlement"
        }

        val requiredJava = instance.javaMajorVersion ?: metadata.javaMajorVersion
        require(runtime.packageInfo.majorVersion == requiredJava) {
            "Installed Java ${runtime.packageInfo.majorVersion} does not match required Java $requiredJava"
        }
        require(runtime.packageInfo.arch == nativeProvider.packageInfo.arch) {
            "Java runtime and Android native provider ABIs do not match"
        }
        require(runtime.javaExecutable.isFile && runtime.javaExecutable.canExecute()) {
            "Installed Java executable is unavailable"
        }
        val jli = requireNotNull(runtime.jliLibrary) {
            "Installed Java runtime does not contain libjli.so"
        }
        require(jli.isFile) { "Installed libjli.so is unavailable" }

        val expectedProvider = requireNotNull(
            AndroidNativeProviderCatalog.detect(metadata, runtime.packageInfo.arch)
        ) {
            "No pinned Android LWJGL provider matches this Minecraft version"
        }
        require(expectedProvider.id == nativeProvider.packageInfo.id) {
            "Installed Android LWJGL provider does not match Minecraft metadata"
        }

        val providerPlan = AndroidNativeProviderCatalog.plan(expectedProvider)
        val expectedProviderRoot = File(
            minecraftRoot,
            "android-native/${expectedProvider.id}"
        ).canonicalFile
        require(nativeProvider.root.canonicalFile == expectedProviderRoot) {
            "Android LWJGL provider is outside the managed Minecraft root"
        }

        val expectedClasspathJars = providerPlan.classpathEntries.map { entry ->
            managedFile(minecraftRoot, entry)
        }
        require(
            nativeProvider.classpathJars.map { it.canonicalFile } ==
                expectedClasspathJars.map { it.canonicalFile }
        ) {
            "Installed Android LWJGL provider classpath does not match the pinned provider plan"
        }
        require(nativeProvider.classpathJars.all { it.isFile && it.length() > 0L }) {
            "Android LWJGL provider has missing Java components"
        }

        val expectedNatives = managedFile(
            minecraftRoot,
            providerPlan.nativeDirectory
        ).canonicalFile
        require(nativeProvider.nativeDirectory.canonicalFile == expectedNatives) {
            "Android LWJGL native directory does not match the pinned provider plan"
        }
        require(
            nativeProvider.nativeLibraries.isNotEmpty() &&
                nativeProvider.nativeLibraries.all { library ->
                    library.isFile &&
                        library.name.endsWith(".so") &&
                        library.canonicalFile.parentFile == expectedNatives
                }
        ) {
            "Android LWJGL provider has no valid native libraries for this ABI"
        }

        val layout = InstanceLayout(minecraftRoot)
        val assetIndex = layout.assetIndex(metadata.assetIndexId)
        require(
            downloader.isValid(metadata.assetIndex, assetIndex)
        ) {
            "Minecraft asset index is missing or failed integrity validation: " +
                metadata.assetIndexId
        }

        var invalidAsset: AssetObjectSpec? = null
        for (asset in assetInstaller.parseIndex(assetIndex)) {
            if (!downloader.isValid(asset.downloadSpec, layout.assetObject(asset.hash))) {
                invalidAsset = asset
                break
            }
        }
        require(invalidAsset == null) {
            "Minecraft asset is missing or failed integrity validation: " +
                invalidAsset?.logicalName
        }

        val plan = planner.createVanillaPlan(
            metadata = metadata,
            instance = instance,
            androidNativeProvider = providerPlan
        )
        require(plan.nativeArchives.isEmpty()) {
            "Desktop Mojang native archives must not be selected for Android"
        }

        val missingClasspath = plan.classpathEntries.firstOrNull { entry ->
            !managedFile(minecraftRoot, entry).isFile
        }
        require(missingClasspath == null) {
            "Minecraft classpath entry is not installed: $missingClasspath"
        }

        val loggingFile = metadata.logging?.let { logging ->
            layout.loggingConfig(logging.fileId).also { file ->
                require(file.isFile) {
                    "Minecraft logging configuration is not installed: ${logging.fileId}"
                }
            }
        }

        val gameDirectory = layout.gameDirectory(instance.name)
        val launchEnvironment = LaunchEnvironment(
            versionName = metadata.id,
            versionType = metadata.type,
            minecraftRoot = minecraftRoot,
            gameDirectory = gameDirectory,
            assetsRoot = File(minecraftRoot, "assets"),
            assetsIndexName = metadata.assetIndexId,
            nativesDirectory = nativeProvider.nativeDirectory,
            libraryDirectory = File(minecraftRoot, "libraries"),
            loggingConfigFile = loggingFile
        )
        val command = commandBuilder.resolve(
            plan = plan,
            identity = LaunchIdentity(
                playerName = account.profile.name,
                uuid = account.profile.id,
                accessToken = account.minecraftAccessToken
            ),
            environment = launchEnvironment
        )

        val instanceRoot = requireNotNull(gameDirectory.parentFile)
        val jvmEnvironment = JvmLaunchEnvironment(
            javaHome = runtime.home,
            gameDirectory = gameDirectory,
            tempDirectory = File(instanceRoot, "tmp"),
            homeDirectory = instanceRoot,
            extraLibraryDirectories = listOf(nativeProvider.nativeDirectory)
        )

        return PreparedVanillaLaunch(
            runtime = runtime,
            nativeProvider = nativeProvider,
            plan = plan,
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
