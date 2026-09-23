package io.yannickfan.avero.androidnative

import io.yannickfan.avero.minecraft.DownloadSpec
import io.yannickfan.avero.minecraft.MinecraftVersionMetadata
import io.yannickfan.avero.runtime.RuntimeArch

data class AndroidNativeProviderPackage(
    val id: String,
    val lwjglVersion: String,
    val arch: RuntimeArch,
    val download: DownloadSpec,
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
    val classpathEntry: String,
    val nativeDirectory: String
)

object AndroidNativeProviderCatalog {
    private data class Artifact(
        val fileName: String,
        val size: Long,
        val gitBlobSha1: String
    )

    private val artifacts = mapOf(
        "3.3.3" to Artifact(
            fileName = "lwjgl-3.3.3-natives-release.aar",
            size = 16_269_516,
            gitBlobSha1 = "4f58d4ddeeaf4e8a337d2bfd7955bd0ed7015de8"
        ),
        "3.4.1" to Artifact(
            fileName = "lwjgl-3.4.1-natives-release.aar",
            size = 16_567_622,
            gitBlobSha1 = "523e976c9fa282a63407e39fbe8d7c846efe0778"
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
        val artifact = artifacts[lwjglVersion] ?: return null
        val commit = AndroidNativeProviderPackage.UPSTREAM_COMMIT
        val url =
            "https://raw.githubusercontent.com/AngelAuraMC/Amethyst-Android/" +
                "$commit/app_pojavlauncher/libs/${artifact.fileName}"

        val id = "amethyst-lwjgl-${lwjglVersion}-${arch.assetToken}"
        return AndroidNativeProviderPackage(
            id = id,
            lwjglVersion = lwjglVersion,
            arch = arch,
            download = DownloadSpec(
                url = url,
                sha1 = null,
                size = artifact.size,
                path = null,
                sha256 = null,
                gitBlobSha1 = artifact.gitBlobSha1
            )
        )
    }

    fun plan(pkg: AndroidNativeProviderPackage): AndroidNativeProviderPlan =
        AndroidNativeProviderPlan(
            id = pkg.id,
            lwjglVersion = pkg.lwjglVersion,
            classpathEntry = "android-native/${pkg.id}/classes.jar",
            nativeDirectory = "android-native/${pkg.id}/natives"
        )
}
