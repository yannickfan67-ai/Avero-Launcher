package io.yannickfan.avero.minecraft

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

class NativeArchiveExtractor {
    suspend fun extract(
        archive: File,
        destination: File,
        excludes: List<String> = emptyList()
    ): Int = withContext(Dispatchers.IO) {
        destination.mkdirs()
        val canonicalRoot = destination.canonicalFile
        var extracted = 0

        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                if (shouldExclude(entry.name, excludes)) continue

                val output = File(destination, entry.name).canonicalFile
                require(output.path.startsWith(canonicalRoot.path + File.separator)) {
                    "Blocked zip path traversal: ${entry.name}"
                }

                output.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    output.outputStream().use { out -> input.copyTo(out) }
                }
                extracted++
            }
        }

        extracted
    }

    private fun shouldExclude(path: String, excludes: List<String>): Boolean {
        if (path.startsWith("META-INF/")) return true
        return excludes.any { prefix -> path.startsWith(prefix) }
    }
}
