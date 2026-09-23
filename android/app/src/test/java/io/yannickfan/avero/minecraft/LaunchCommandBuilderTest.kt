package io.yannickfan.avero.minecraft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LaunchCommandBuilderTest {
    @Test
    fun resolvesIdentityClasspathAndLoggingVariables() {
        val plan = LaunchPlan(
            versionId = "1.test",
            mainClass = "net.minecraft.client.main.Main",
            javaMajorVersion = 21,
            classpathEntries = listOf("libraries/a.jar", "versions/1.test/1.test.jar"),
            jvmArguments = listOf("-Djava.library.path=${natives_directory}", "-cp", "${classpath}"),
            gameArguments = listOf("--username", "${auth_player_name}", "--uuid", "${auth_uuid}"),
            logging = LoggingSpec(
                argument = "-Dlog4j.configurationFile=${path}",
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
}
