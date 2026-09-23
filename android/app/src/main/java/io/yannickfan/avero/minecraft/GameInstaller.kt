package io.yannickfan.avero.minecraft

import java.io.File

data class InstallProgress(
    val completedFiles: Int,
    val totalFiles: Int,
    val currentFile: String
)

data class CoreInstallResult(
    val versionId: String,
    val downloadedFiles: Int,
    val skippedLibrariesWithoutArtifact: Int,
    val skippedLibrariesByRule: Int,
    val root: File
)

class GameInstaller(
    private val downloader: FileDownloader = FileDownloader(),
    private val rules: RuleEvaluator = RuleEvaluator()
) {
    suspend fun installVanillaCore(
        summary: MinecraftVersionSummary,
        metadata: MinecraftVersionMetadata,
        root: File,
        context: RuleContext = MinecraftPlatform.androidRuleContext(),
        onProgress: (InstallProgress) -> Unit = {}
    ): CoreInstallResult {
        require(summary.id == metadata.id) {
            "Manifest entry and version metadata do not match"
        }

        val layout = InstanceLayout(root)
        val allowedLibraries = metadata.libraries.filter { rules.isAllowed(it.rules, context) }
        val libraries = allowedLibraries.mapNotNull { lib ->
            lib.artifact?.path?.let { path -> Triple(lib.name, lib.artifact, layout.library(path)) }
        }

        val tasks = buildList {
            add(
                Triple(
                    "${metadata.id}.json",
                    DownloadSpec(summary.metadataUrl, summary.sha1, null),
                    layout.versionJson(metadata.id)
                )
            )
            add(Triple("${metadata.id}.jar", metadata.client, layout.clientJar(metadata.id)))
            add(
                Triple(
                    "assets/indexes/${metadata.assetIndexId}.json",
                    metadata.assetIndex,
                    layout.assetIndex(metadata.assetIndexId)
                )
            )
            addAll(libraries)
            metadata.logging?.let { logging ->
                add(
                    Triple(
                        "log_configs/${logging.fileId}",
                        logging.file,
                        layout.loggingConfig(logging.fileId)
                    )
                )
            }
        }

        tasks.forEachIndexed { index, (label, spec, destination) ->
            onProgress(InstallProgress(index, tasks.size, label))
            if (!downloader.isValid(spec, destination)) {
                downloader.download(spec, destination)
            }
            onProgress(InstallProgress(index + 1, tasks.size, label))
        }

        return CoreInstallResult(
            versionId = metadata.id,
            downloadedFiles = tasks.size,
            skippedLibrariesWithoutArtifact = allowedLibraries.count { it.artifact?.path == null },
            skippedLibrariesByRule = metadata.libraries.size - allowedLibraries.size,
            root = root
        )
    }
}
