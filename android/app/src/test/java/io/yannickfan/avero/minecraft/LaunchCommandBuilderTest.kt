package io.yannickfan.avero.minecraft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import io.yannickfan.avero.androidnative.AndroidNativeProviderPlan
import java.io.File
import java.nio.file.Files

class LaunchCommandBuilderTest {
    private fun variable(name: String): String =
        36.toChar().toString() + "{" + name + "}"

    @Test
    fun resolvesIdentityClasspathAndLoggingVariables() {
        val plan = LaunchPlan(
            versionId = "1.test",
            mainClass = "net.minecraft.client.main.Main",
            javaMajorVersion = 21,
            classpathEntries = listOf("libraries/a.jar", "versions/1.test/1.test.jar"),
            jvmArguments = listOf(
                "-Djava.library.path=" + variable("natives_directory"),
                "-cp",
                variable("classpath")
            ),
            gameArguments = listOf(
                "--username",
                variable("auth_player_name"),
                "--uuid",
                variable("auth_uuid"),
                "--userProperties",
                variable("user_properties")
            ),
            logging = LoggingSpec(
                argument = "-Dlog4j.configurationFile=" + variable("path"),
                fileId = "client.xml",
                file = DownloadSpec("https://example.invalid/client.xml", null, null)
            )
        )

        val root = File("/tmp/avero-test")
        val command = LaunchCommandBuilder().resolve(
            plan = plan,
            identity = LaunchIdentity(
                playerName = "NoxVala",
                uuid = "1234",
                accessToken = "token"
            ),
            environment = LaunchEnvironment(
                versionName = "1.test",
                versionType = "release",
                minecraftRoot = root,
                gameDirectory = File(root, "instances/default/game"),
                assetsRoot = File(root, "assets"),
                assetsIndexName = "1",
                nativesDirectory = File(root, "natives"),
                libraryDirectory = File(root, "libraries"),
                loggingConfigFile = File(root, "log_configs/client.xml")
            )
        )

        assertEquals("net.minecraft.client.main.Main", command.mainClass)
        assertTrue(command.jvmArguments.first().contains("log_configs"))
        assertTrue(command.jvmArguments.joinToString(" ").contains("libraries/a.jar"))
        assertEquals(
            listOf("--username", "NoxVala", "--uuid", "1234", "--userProperties", "{}"),
            command.gameArguments
        )
    }

    @Test
    fun unresolvedVariableFailsBeforeProcessLaunch() {
        val plan = LaunchPlan(
            versionId = "1.test",
            mainClass = "net.minecraft.client.main.Main",
            javaMajorVersion = 21,
            classpathEntries = emptyList(),
            jvmArguments = listOf(variable("missing_variable")),
            gameArguments = emptyList()
        )
        val root = File("/tmp/avero-test")

        val error = assertThrows(IllegalArgumentException::class.java) {
            LaunchCommandBuilder().resolve(
                plan = plan,
                identity = LaunchIdentity(
                    playerName = "Player",
                    uuid = "1234",
                    accessToken = "token"
                ),
                environment = LaunchEnvironment(
                    versionName = "1.test",
                    versionType = "release",
                    minecraftRoot = root,
                    gameDirectory = File(root, "instances/default/game"),
                    assetsRoot = File(root, "assets"),
                    assetsIndexName = "1",
                    nativesDirectory = File(root, "natives"),
                    libraryDirectory = File(root, "libraries")
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("missing_variable"))
    }
    @Test
    fun nativeDependentPlanWithoutProviderOrArchivesIsRejected() {
        val root = Files.createTempDirectory("avero-launch-test-").toFile()
        try {
            val plan = LaunchPlan(
                versionId = "1.test",
                mainClass = "net.minecraft.client.main.Main",
                javaMajorVersion = 21,
                classpathEntries = emptyList(),
                jvmArguments = emptyList(),
                gameArguments = emptyList(),
                requiresNatives = true
            )

            val error = assertThrows(IllegalArgumentException::class.java) {
                LaunchCommandBuilder().resolve(
                    plan = plan,
                    identity = identity(),
                    environment = environment(root, File(root, "natives"))
                )
            }

            assertTrue(error.message.orEmpty().contains("native provider"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun installedAndroidProviderAllowsCommandResolution() {
        val root = Files.createTempDirectory("avero-launch-test-").toFile()
        try {
            val provider = providerPlan()
            provider.classpathEntries.forEach { relative ->
                File(root, relative).apply {
                    parentFile!!.mkdirs()
                    writeText("jar")
                }
            }
            val natives = File(root, provider.nativeDirectory).apply { mkdirs() }
            File(natives, "liblwjgl.so").writeText("native")

            val plan = LaunchPlan(
                versionId = "1.test",
                mainClass = "net.minecraft.client.main.Main",
                javaMajorVersion = 21,
                classpathEntries = provider.classpathEntries,
                jvmArguments = emptyList(),
                gameArguments = emptyList(),
                androidNativeProvider = provider
            )

            val command = LaunchCommandBuilder().resolve(
                plan = plan,
                identity = identity(),
                environment = environment(root, natives)
            )

            assertEquals("net.minecraft.client.main.Main", command.mainClass)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun providerWithWrongNativesDirectoryIsRejected() {
        val root = Files.createTempDirectory("avero-launch-test-").toFile()
        try {
            val provider = providerPlan()
            provider.classpathEntries.forEach { relative ->
                File(root, relative).apply {
                    parentFile!!.mkdirs()
                    writeText("jar")
                }
            }
            val providerNatives = File(root, provider.nativeDirectory).apply { mkdirs() }
            File(providerNatives, "liblwjgl.so").writeText("native")

            val plan = LaunchPlan(
                versionId = "1.test",
                mainClass = "net.minecraft.client.main.Main",
                javaMajorVersion = 21,
                classpathEntries = provider.classpathEntries,
                jvmArguments = emptyList(),
                gameArguments = emptyList(),
                requiresNatives = true,
                androidNativeProvider = provider
            )

            val error = assertThrows(IllegalArgumentException::class.java) {
                LaunchCommandBuilder().resolve(
                    plan = plan,
                    identity = identity(),
                    environment = environment(root, File(root, "wrong-natives"))
                )
            }

            assertTrue(error.message.orEmpty().contains("natives_directory"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun providerManagedPathCannotEscapeMinecraftRoot() {
        val root = Files.createTempDirectory("avero-launch-test-").toFile()
        try {
            val provider = AndroidNativeProviderPlan(
                id = "bad",
                lwjglVersion = "3.4.1",
                classpathEntries = listOf("../outside.jar"),
                nativeDirectory = "../outside-natives"
            )
            val plan = LaunchPlan(
                versionId = "1.test",
                mainClass = "net.minecraft.client.main.Main",
                javaMajorVersion = 21,
                classpathEntries = provider.classpathEntries,
                jvmArguments = emptyList(),
                gameArguments = emptyList(),
                requiresNatives = true,
                androidNativeProvider = provider
            )

            val error = assertThrows(IllegalArgumentException::class.java) {
                LaunchCommandBuilder().resolve(
                    plan = plan,
                    identity = identity(),
                    environment = environment(root, File(root, "natives"))
                )
            }

            assertTrue(error.message.orEmpty().contains("escapes"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun providerPlan() = AndroidNativeProviderPlan(
        id = "test-provider",
        lwjglVersion = "3.4.1",
        classpathEntries = listOf(
            "android-native/test-provider/jars/lwjgl.jar",
            "android-native/test-provider/jars/lwjgl-openal.jar"
        ),
        nativeDirectory = "android-native/test-provider/natives"
    )

    private fun identity() = LaunchIdentity(
        playerName = "Player",
        uuid = "1234",
        accessToken = "token"
    )

    private fun environment(root: File, natives: File) = LaunchEnvironment(
        versionName = "1.test",
        versionType = "release",
        minecraftRoot = root,
        gameDirectory = File(root, "instances/default/game"),
        assetsRoot = File(root, "assets"),
        assetsIndexName = "1",
        nativesDirectory = natives,
        libraryDirectory = File(root, "libraries")
    )

}
