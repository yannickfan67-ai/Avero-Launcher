package io.yannickfan.avero.minecraft

import io.yannickfan.avero.androidnative.AndroidNativeProviderCatalog
import io.yannickfan.avero.androidnative.InstalledAndroidNativeProvider
import io.yannickfan.avero.auth.AuthenticatedMinecraftAccount
import io.yannickfan.avero.auth.MinecraftEntitlements
import io.yannickfan.avero.auth.MinecraftProfile
import io.yannickfan.avero.runtime.AndroidJavaRuntimeCatalog
import io.yannickfan.avero.runtime.InstalledJavaRuntime
import io.yannickfan.avero.runtime.RuntimeArch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VanillaLaunchPreparerTest {
    private fun variable(name: String): String =
        36.toChar().toString() + "{" + name + "}"

    @Test
    fun buildsLaunchReadyCommandFromInstalledPieces() = runBlocking {
        val root = Files.createTempDirectory("avero-launch-ready-").toFile()
        try {
            val fixture = fixture(root)
            val prepared = VanillaLaunchPreparer().prepare(
                metadata = fixture.metadata,
                instance = fixture.instance,
                account = fixture.account,
                minecraftRoot = root,
                runtime = fixture.runtime,
                nativeProvider = fixture.provider
            )

            val providerPlan = AndroidNativeProviderCatalog.plan(
                fixture.provider.packageInfo
            )
            assertEquals(
                providerPlan.classpathEntries,
                prepared.plan.classpathEntries.take(providerPlan.classpathEntries.size)
            )
            assertTrue(
                prepared.plan.classpathEntries.contains(
                    "libraries/org/lwjgl/lwjgl/3.4.1/lwjgl-3.4.1.jar"
                )
            )

            val jvmText = prepared.command.jvmArguments.joinToString(" ")
            assertTrue(jvmText.contains(fixture.provider.nativeDirectory.absolutePath))
            assertTrue(
                jvmText.contains(
                    File(
                        root,
                        "libraries/org/lwjgl/lwjgl/3.4.1/lwjgl-3.4.1.jar"
                    ).absolutePath
                )
            )

            assertEquals(
                listOf(
                    "--username",
                    "NoxVala",
                    "--userProperties",
                    "{}"
                ),
                prepared.command.gameArguments
            )
            assertEquals(
                listOf(fixture.provider.nativeDirectory),
                prepared.jvmEnvironment.extraLibraryDirectories
            )
            assertEquals(fixture.runtime.home, prepared.jvmEnvironment.javaHome)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun sameSizeCorruptAssetPreventsLaunchPreparation() {
        val root = Files.createTempDirectory("avero-launch-corrupt-asset-").toFile()
        try {
            val fixture = fixture(root)
            fixture.assetObject.writeText("payloae")

            val error = assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    VanillaLaunchPreparer().prepare(
                        metadata = fixture.metadata,
                        instance = fixture.instance,
                        account = fixture.account,
                        minecraftRoot = root,
                        runtime = fixture.runtime,
                        nativeProvider = fixture.provider
                    )
                }
            }

            assertTrue(error.message.orEmpty().contains("integrity"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingAssetPreventsLaunchPreparation() {
        val root = Files.createTempDirectory("avero-launch-missing-asset-").toFile()
        try {
            val fixture = fixture(root)
            fixture.assetObject.delete()

            val error = assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    VanillaLaunchPreparer().prepare(
                        metadata = fixture.metadata,
                        instance = fixture.instance,
                        account = fixture.account,
                        minecraftRoot = root,
                        runtime = fixture.runtime,
                        nativeProvider = fixture.provider
                    )
                }
            }

            assertTrue(error.message.orEmpty().contains("asset"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun fixture(root: File): Fixture {
        val version = "1.21.4"
        val assetHash = "f07e5a815613c5abeddc4b682247a4c42d8a95df"
        val layout = InstanceLayout(root)

        layout.clientJar(version).apply {
            parentFile?.mkdirs()
            writeText("client")
        }

        val libraryPath = "org/lwjgl/lwjgl/3.4.1/lwjgl-3.4.1.jar"
        layout.library(libraryPath).apply {
            parentFile?.mkdirs()
            writeText("library")
        }

        val assetIndex = layout.assetIndex("17").apply {
            parentFile?.mkdirs()
            writeText(
                """
                {
                  "objects": {
                    "minecraft/test.txt": {
                      "hash": "$assetHash",
                      "size": 7
                    }
                  }
                }
                """.trimIndent()
            )
        }
        assertTrue(assetIndex.isFile)

        val assetObject = layout.assetObject(assetHash).apply {
            parentFile?.mkdirs()
            writeText("payload")
        }

        val metadata = MinecraftVersionMetadata(
            id = version,
            type = "release",
            mainClass = "net.minecraft.client.main.Main",
            javaMajorVersion = 21,
            client = DownloadSpec(
                url = "https://example.invalid/client.jar",
                sha1 = null,
                size = null
            ),
            assetIndexId = "17",
            assetIndex = DownloadSpec(
                url = "https://example.invalid/assets.json",
                sha1 = null,
                size = null
            ),
            libraries = listOf(
                LibrarySpec(
                    name = "org.lwjgl:lwjgl:3.4.1",
                    artifact = DownloadSpec(
                        url = "https://example.invalid/lwjgl.jar",
                        sha1 = null,
                        size = null,
                        path = libraryPath
                    )
                )
            ),
            logging = null,
            gameArguments = listOf(
                ConditionalArgument(
                    listOf(
                        "--username",
                        variable("auth_player_name"),
                        "--userProperties",
                        variable("user_properties")
                    )
                )
            ),
            jvmArguments = listOf(
                ConditionalArgument(
                    listOf(
                        "-Djava.library.path=" + variable("natives_directory")
                    )
                ),
                ConditionalArgument(
                    listOf("-cp", variable("classpath"))
                )
            )
        )

        val runtimePackage = requireNotNull(
            AndroidJavaRuntimeCatalog.find(21, RuntimeArch.ARM64)
        )
        val runtimeHome = File(root, "runtime").apply { mkdirs() }
        val java = File(runtimeHome, "bin/java").apply {
            parentFile?.mkdirs()
            writeText("java")
            setExecutable(true, false)
        }
        val jli = File(runtimeHome, "lib/jli/libjli.so").apply {
            parentFile?.mkdirs()
            writeText("jli")
        }
        val runtime = InstalledJavaRuntime(
            packageInfo = runtimePackage,
            home = runtimeHome,
            javaExecutable = java,
            jvmLibrary = null,
            jliLibrary = jli
        )

        val providerPackage = requireNotNull(
            AndroidNativeProviderCatalog.find("3.4.1", RuntimeArch.ARM64)
        )
        val providerPlan = AndroidNativeProviderCatalog.plan(providerPackage)
        val providerRoot = File(root, "android-native/${providerPackage.id}")
        val classpathJars = providerPlan.classpathEntries.map { entry ->
            File(root, entry).apply {
                parentFile?.mkdirs()
                writeText("patched")
            }
        }
        val nativeDirectory = File(root, providerPlan.nativeDirectory).apply {
            mkdirs()
        }
        val nativeLibrary = File(nativeDirectory, "liblwjgl.so").apply {
            writeText("native")
        }
        val provider = InstalledAndroidNativeProvider(
            packageInfo = providerPackage,
            root = providerRoot,
            classpathJars = classpathJars,
            nativeDirectory = nativeDirectory,
            nativeLibraries = listOf(nativeLibrary)
        )

        val account = AuthenticatedMinecraftAccount(
            profile = MinecraftProfile(
                id = "0123456789abcdef0123456789abcdef",
                name = "NoxVala"
            ),
            minecraftAccessToken = "minecraft-token",
            minecraftTokenExpiresInSeconds = 3600,
            microsoftRefreshToken = "refresh-token",
            entitlements = MinecraftEntitlements(
                names = listOf("game_minecraft")
            )
        )

        return Fixture(
            metadata = metadata,
            instance = LauncherInstance(
                name = "Latest release",
                versionId = version,
                memoryMb = 2048
            ),
            account = account,
            runtime = runtime,
            provider = provider,
            assetObject = assetObject
        )
    }

    private data class Fixture(
        val metadata: MinecraftVersionMetadata,
        val instance: LauncherInstance,
        val account: AuthenticatedMinecraftAccount,
        val runtime: InstalledJavaRuntime,
        val provider: InstalledAndroidNativeProvider,
        val assetObject: File
    )
}
