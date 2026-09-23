package io.yannickfan.avero.runtime

import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File

data class ExtractionResult(
    val files: Int,
    val directories: Int,
    val links: Int
)

class TarXzExtractor {
    suspend fun extract(
        archive: File,
        destination: File
    ): ExtractionResult = withContext(Dispatchers.IO) {
        require(archive.isFile) { "Runtime archive does not exist: $archive" }
        destination.mkdirs()
        val root = destination.canonicalFile
        var files = 0
        var directories = 0
        var links = 0
        val pendingHardLinks = mutableListOf<Pair<File, File>>()

        archive.inputStream().buffered().use { raw ->
            XZCompressorInputStream(BufferedInputStream(raw)).use { xz ->
                TarArchiveInputStream(xz).use { tar ->
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        val output = safeFile(root, entry.name)

                        when {
                            entry.isDirectory -> {
                                output.mkdirs()
                                applyMode(output, entry.mode)
                                directories++
                            }

                            entry.isSymbolicLink -> {
                                output.parentFile?.mkdirs()
                                if (output.exists()) output.delete()

                                val target = entry.linkName
                                require(!File(target).isAbsolute) {
                                    "Blocked absolute symlink target: $target"
                                }
                                val resolvedTarget = File(output.parentFile, target).canonicalFile
                                require(isInside(root, resolvedTarget)) {
                                    "Blocked symlink escaping runtime root: ${entry.name} -> $target"
                                }

                                Os.symlink(target, output.absolutePath)
                                links++
                            }

                            entry.isLink -> {
                                val target = safeFile(root, entry.linkName)
                                pendingHardLinks += output to target
                            }

                            entry.isFile -> {
                                output.parentFile?.mkdirs()
                                output.outputStream().buffered().use { out ->
                                    tar.copyTo(out)
                                }
                                applyMode(output, entry.mode)
                                files++
                            }
                        }
                    }
                }
            }
        }

        for ((output, target) in pendingHardLinks) {
            require(target.exists()) {
                "Hard-link target was not extracted: $target"
            }
            output.parentFile?.mkdirs()
            if (output.exists()) output.delete()
            Os.link(target.absolutePath, output.absolutePath)
            links++
        }

        ExtractionResult(files, directories, links)
    }

    private fun safeFile(root: File, entryName: String): File {
        val normalized = entryName.removePrefix("./")
        val output = File(root, normalized).canonicalFile
        require(isInside(root, output)) {
            "Blocked archive path traversal: $entryName"
        }
        return output
    }

    private fun isInside(root: File, file: File): Boolean =
        file.path == root.path || file.path.startsWith(root.path + File.separator)

    private fun applyMode(file: File, mode: Int) {
        file.setReadable((mode and 292) != 0, false)
        file.setWritable((mode and 146) != 0, false)
        file.setExecutable((mode and 73) != 0, false)
    }
}
