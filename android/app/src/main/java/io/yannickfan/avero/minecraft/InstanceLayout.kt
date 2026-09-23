package io.yannickfan.avero.minecraft

import java.io.File

class InstanceLayout(private val root: File) {
    fun versionDirectory(versionId: String) = File(root, "versions/$versionId")
    fun versionJson(versionId: String) = File(versionDirectory(versionId), "$versionId.json")
    fun clientJar(versionId: String) = File(versionDirectory(versionId), "$versionId.jar")

    fun library(path: String): File {
        val normalized = normalizeLibraryPath(path)
        return File(root, normalized)
    }

    fun assetIndex(id: String) = File(root, "assets/indexes/$id.json")
    fun loggingConfig(fileId: String) = File(root, "log_configs/$fileId")
    fun assetObject(hash: String): File {
        require(hash.length >= 2)
        return File(root, "assets/objects/${hash.take(2)}/$hash")
    }

    fun gameDirectory(instanceName: String) =
        File(root, "instances/${sanitize(instanceName)}/game")

    private fun normalizeLibraryPath(path: String): String {
        val normalized = path.replace('\\', '/').trimStart('/')
        require(normalized.isNotBlank()) { "Library artifact path is empty" }
        require(normalized.split('/').none { it == ".." }) {
            "Library artifact path escapes the managed library directory: $path"
        }
        val relative = normalized.removePrefix("libraries/")
        require(relative.isNotBlank()) { "Library artifact path is empty" }
        return "libraries/$relative"
    }

    private fun sanitize(value: String): String {
        val sanitized = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (sanitized.isBlank() || sanitized == "." || sanitized == "..") "_" else sanitized
    }
}
