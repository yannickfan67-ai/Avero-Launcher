package io.yannickfan.avero.minecraft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
                variable("auth_uuid")
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
        assertEquals(listOf("--username", "NoxVala", "--uuid", "1234"), command.gameArguments)
    }
    @Test
    fun unresolvedVariableFailsBeforeProcessLaunch() {
        val variable = 36.toChar().toString() + "{missing_variable}"
        val plan = LaunchPlan(
            versionId = "1.test",
            mainClass = "net.minecraft.client.main.Main",
            javaMajorVersion = 21,
            classpathEntries = emptyList(),
            jvmArguments = listOf(variable),
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

}
