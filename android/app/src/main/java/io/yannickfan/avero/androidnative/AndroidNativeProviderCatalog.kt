package io.yannickfan.avero.androidnative

import io.yannickfan.avero.minecraft.DownloadSpec
import io.yannickfan.avero.minecraft.MinecraftVersionMetadata
import io.yannickfan.avero.runtime.RuntimeArch

data class AndroidNativeProviderComponent(
    val fileName: String,
    val download: DownloadSpec
)

data class AndroidNativeProviderPackage(
    val id: String,
    val lwjglVersion: String,
    val arch: RuntimeArch,
    val nativeArchive: DownloadSpec,
    val javaComponents: List<AndroidNativeProviderComponent>,
    val upstreamProject: String = "AngelAuraMC/Amethyst-Android",
    val upstreamCommit: String = UPSTREAM_COMMIT,
    val upstreamLicense: String = "LGPL-3.0"
) {
    companion object {
        const val UPSTREAM_COMMIT =
            "330c6eae3164df64bdc4828e946a9e62cc5169e4"
    }
}

data class AndroidNativeProviderPlan(
    val id: String,
    val lwjglVersion: String,
    val classpathEntries: List<String>,
    val nativeDirectory: String
)

object AndroidNativeProviderCatalog {
    private data class Artifact(
        val fileName: String,
        val size: Long,
        val gitBlobSha1: String
    )

    private data class VersionArtifacts(
        val nativeAar: Artifact,
        val components: List<Artifact>
    )

    private val versions = mapOf(
        "3.3.3" to VersionArtifacts(
            nativeAar = Artifact(
                fileName = "lwjgl-3.3.3-natives-release.aar",
                size = 16_269_516,
                gitBlobSha1 = "4f58d4ddeeaf4e8a337d2bfd7955bd0ed7015de8"
            ),
            components = listOf(
                Artifact("jsr305.jar", 19_936, "59222d9ca5e5654f5dcf6680783d0e265baa848f"),
                Artifact("lwjgl-3.3.3-merged-modules.jar", 1_081_868, "37518cb2c1cd9b298bd4165e2b9d18b414ff4196"),
                Artifact("lwjgl-freetype.jar", 451_815, "28cb82b13145430fd7e281b79d20b9db18"),
                Artifact("lwjgl-lwjglx.jar", 237_225, "5e048e788112f09975ee0469220a1c6201856d58"),
                Artifact("lwjgl-nanovg.jar", 126_928, "79374dcd85c5580831507cd02bf5be93701ebc8e"),
                Artifact("lwjgl-openal.jar", 109_538, "1738288135d83676ce92dbe1633c53e323da312e"),
                Artifact("lwjgl-shaderc.jar", 24_400, "15ca06e9a0ce6a05e6a975ab6534fd19cda29b89"),
                Artifact("lwjgl-spvc.jar", 127_690, "8ca5a19098af1f6e22c9213f740e235cd4ead475"),
                Artifact("lwjgl-stb.jar", 115_903, "0480c8efc4d8359935e349f6f96eb3663a0b362f"),
                Artifact("lwjgl-tinyfd.jar", 7_711, "89d38b2c0824ce369cbe8e85476188d98167347a"),
                Artifact("lwjgl-vma.jar", 98_286, "a8757c2405b5689697449b8853cb84d47188de6c"),
                Artifact("lwjgl-vulkan.jar", 6_106_103, "ffb4a042b4becf6c282adcf1dae66355f810e22b"),
                Artifact("lwjgl.jar", 793_777, "85322cf449e8cec02ce9b698adf9e9908109b519")
            )
        ),
        "3.4.1" to VersionArtifacts(
            nativeAar = Artifact(
                fileName = "lwjgl-3.4.1-natives-release.aar",
                size = 16_567_622,
                gitBlobSha1 = "523e976c9fa282a63407e39fbe8d7c846efe0778"
            ),
            components = listOf(
                Artifact("lwjgl-3.4.1-merged-modules.jar", 1_105_331, "13a5ab69ac726cfe7adfcbccee951a10c128f65c"),
                Artifact("lwjgl-freetype.jar", 465_796, "928e2828489ae7734b266c7614c9406862249720"),
                Artifact("lwjgl-lwjglx.jar", 237_121, "2d47d906f495d98248bfd5f91d0d6ec3429ca2eb"),
                Artifact("lwjgl-nanovg.jar", 76_348, "c0a962516299c71220298d9c3dde96cd80d98874"),
                Artifact("lwjgl-openal.jar", 150_981, "85a9de4d930da39bd2a7d22927fdf0bb3130a9c2"),
                Artifact("lwjgl-sdl.jar", 992_333, "1a9a04e37073cf34fd8c29ada8b6a8030a33877f"),
                Artifact("lwjgl-shaderc.jar", 147_618, "aa7dc9a5ea006d5068ba7ad1dc655b87e7fab6e0"),
                Artifact("lwjgl-spng.jar", 114_650, "46b8af6cbbaec61808e2473a5b346c9bc5e72f7b"),
                Artifact("lwjgl-spvc.jar", 141_553, "3ee06375eb621d650b4ef4e36200d5b9c47ce73b"),
                Artifact("lwjgl-stb.jar", 136_953, "a7db37cb621d3959cfe1fdf17113b171c0956ac4"),
                Artifact("lwjgl-tinyfd.jar", 7_677, "0b1ef1fb8bb7e1cc2723a65cc073573ce831d290"),
                Artifact("lwjgl-vma.jar", 103_366, "20dcad845e0e89251629f37ad408e264dd7ca983"),
                Artifact("lwjgl-vulkan.jar", 8_540_645, "236996afdd2d67e72adb71b7b117db8b0ed93b08"),
                Artifact("lwjgl.jar", 1_169_477, "f77c2f24ed0f8034d98195a3e2092dfc5e64d293")
            )
        )
    )

    fun detect(
        metadata: MinecraftVersionMetadata,
        arch: RuntimeArch
    ): AndroidNativeProviderPackage? {
        val version = metadata.libraries
            .asSequence()
            .map { it.name.split(':') }
            .firstOrNull { parts ->
                parts.size >= 3 &&
                    parts[0] == "org.lwjgl" &&
                    parts[1] == "lwjgl"
            }
            ?.get(2)
            ?: return null

        return find(version, arch)
    }

    fun find(
        lwjglVersion: String,
        arch: RuntimeArch
    ): AndroidNativeProviderPackage? {
        val artifacts = versions[lwjglVersion] ?: return null
        val commit = AndroidNativeProviderPackage.UPSTREAM_COMMIT

        fun raw(path: String): String =
            "https://raw.githubusercontent.com/AngelAuraMC/Amethyst-Android/" +
                "$commit/$path"

        val native = artifacts.nativeAar
        val components = artifacts.components.map { artifact ->
            AndroidNativeProviderComponent(
                fileName = artifact.fileName,
                download = DownloadSpec(
                    url = raw(
                        "app_pojavlauncher/src/main/assets/components/" +
                            "lwjgl3/$lwjglVersion/${artifact.fileName}"
                    ),
                    sha1 = null,
                    size = artifact.size,
                    gitBlobSha1 = artifact.gitBlobSha1
                )
            )
        }

        val id = "amethyst-lwjgl-${lwjglVersion}-${arch.assetToken}"
        return AndroidNativeProviderPackage(
            id = id,
            lwjglVersion = lwjglVersion,
            arch = arch,
            nativeArchive = DownloadSpec(
                url = raw(
                    "app_pojavlauncher/libs/${native.fileName}"
                ),
                sha1 = null,
                size = native.size,
                gitBlobSha1 = native.gitBlobSha1
            ),
            javaComponents = components
        )
    }

    fun plan(pkg: AndroidNativeProviderPackage): AndroidNativeProviderPlan =
        AndroidNativeProviderPlan(
            id = pkg.id,
            lwjglVersion = pkg.lwjglVersion,
            classpathEntries = pkg.javaComponents.map { component ->
                "android-native/${pkg.id}/jars/${component.fileName}"
            },
            nativeDirectory = "android-native/${pkg.id}/natives"
        )
}
