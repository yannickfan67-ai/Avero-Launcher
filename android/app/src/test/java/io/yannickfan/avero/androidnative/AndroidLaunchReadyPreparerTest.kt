package io.yannickfan.avero.androidnative

import io.yannickfan.avero.auth.AuthenticatedMinecraftAccount
import io.yannickfan.avero.auth.MinecraftEntitlements
import io.yannickfan.avero.auth.MinecraftProfile
import io.yannickfan.avero.minecraft.ConditionalArgument
import io.yannickfan.avero.minecraft.DownloadSpec
import io.yannickfan.avero.minecraft.InstanceLayout
import io.yannickfan.avero.minecraft.LauncherInstance
import io.yannickfan.avero.minecraft.LibrarySpec
import io.yannickfan.avero.minecraft.MinecraftVersionMetadata
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

class AndroidLaunchReadyPreparerTest {
    private fun variable(name: String): String =
        36.toChar().toString() + "{" + name + "}"

    @Test
    fun combinesInstalledGameAccountRuntimeAndProvider() = runBlocking {
        val root = Files.createTempDirectory("avero-launch-ready-").toFile()
        try {
            val fixture = fixture(root)
            val ready = AndroidLaunchReadyPreparer().prepareInstalled(
                metadata = fixture.metadata,
                instance = fixture.instance,
                account = fixture.account,
                minecraftRoot = root,
                runtime = fixture.runtime
            )

            assertTrue(
                ready.preparation.plan.classpathEntries.contains(
                    "libraries/org/lwjgl/lwjgl/3.4.1/lwjgl-3.4.1.jar"
                )
            )

            val jvmText = ready.command.jvmArguments.joinToString(" ")
            assertTrue(jvmText.contains(fixture.nativeDirectory.absolutePath))
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
                ready.command.gameArguments
            )
            assertEquals(
                listOf(fixture.nativeDirectory),
                ready.jvmEnvironment.extraLibraryDirectories
            )
            assertEquals(fixture.runtime.home, ready.jvmEnvironment.javaHome)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingHashedAssetBlocksLaunchReadyState() {
        val root = Files.createTempDirectory("avero-missing-asset-").toFile()
        try {
            val fixture = fixture(root)
            fixture.assetObject.delete()

            val error = assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    AndroidLaunchReadyPreparer().prepareInstalled(
                        metadata = fixture.metadata,
                        instance = fixture.instance,
                        account = fixture.account,
                        minecraftRoot = root,
                        runtime = fixture.runtime
                    )
                }
            }

            assertTrue(error.message.orEmpty().contains("asset"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun dotDotInstanceNameStaysInsideInstancesRoot() {
        val root = Files.createTempDirectory("avero-instance-path-").toFile()
        try {
            val layout = InstanceLayout(root)
            val game = layout.gameDirectory("..").canonicalFile
            val instances = File(root, "instances").canonicalFile

            assertTrue(game.path.startsWith(instances.path + File.separator))
            assertEquals(File(instances, "_/game").canonicalFile, game)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun fixture(root: File): Fixture {
        val version = "1.21.4"
        val lwjglPath = "org/lwjgl/lwjgl/3.4.1/lwjgl-3.4.1.jar"
        val layout = InstanceLayout(root)

        layout.clientJar(version).apply {
            parentFile?.mkdirs()
            writeText("client")
        }
        layout.library(lwjglPath).apply {
            parentFile?.mkdirs()
            writeText("library")
        }

        val assetHash = "aa" + "0".repeat(38)
        layout.assetIndex("17").apply {
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
                        path = lwjglPath
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
            check(setExecutable(true, false))
        }
        val jli = File(runtimeHome, "lib/jli/libjli.so").apply {
            parentFile?.mkdirs()
            writeText("jli")
            check(setWritable(false, false))
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
        val jars = File(providerRoot, "jars").apply { mkdirs() }
        providerPackage.javaComponents.forEach { component ->
            File(jars, component.fileName).writeText("patched")
        }
        val nativeDirectory = File(root, providerPlan.nativeDirectory).apply {
            mkdirs()
        }
        File(nativeDirectory, "liblwjgl.so").writeText("native")

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
            nativeDirectory = nativeDirectory,
            assetObject = assetObject
        )
    }

    private data class Fixture(
        val metadata: MinecraftVersionMetadata,
        val instance: LauncherInstance,
        val account: AuthenticatedMinecraftAccount,
        val runtime: InstalledJavaRuntime,
        val nativeDirectory: File,
        val assetObject: File
    )
}
