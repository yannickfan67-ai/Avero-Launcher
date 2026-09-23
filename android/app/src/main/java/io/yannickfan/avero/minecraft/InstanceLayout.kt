package io.yannickfan.avero.minecraft

import java.io.File

class InstanceLayout(private val root: File) {
    fun versionDirectory(versionId: String) = File(root, "versions/$versionId")
    fun versionJson(versionId: String) = File(versionDirectory(versionId), "$versionId.json")
    fun clientJar(versionId: String) = File(versionDirectory(versionId), "$versionId.jar")

    fun library(path: String) = File(root, "libraries/$path")

    fun assetIndex(id: String) = File(root, "assets/indexes/$id.json")
    fun loggingConfig(fileId: String) = File(root, "log_configs/$fileId")
    fun assetObject(hash: String): File {
        require(hash.length >= 2)
        return File(root, "assets/objects/${hash.take(2)}/$hash")
    }

    fun gameDirectory(instanceName: String) =
        File(root, "instances/${sanitize(instanceName)}/game")

    fun nativesDirectory(instanceName: String) =
        File(root, "instances/${sanitize(instanceName)}/natives")

    fun androidNativeArtifact(
        providerId: String,
        abi: String,
        fileName: String
    ): File {
        require(fileName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*"))) {
            "Android native artifact file name must be simple"
        }
        return File(
            root,
            "native-providers/${sanitize(providerId)}/${sanitize(abi)}/$fileName"
        )
    }

    private fun sanitize(value: String): String {
        val sanitized = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (sanitized.isBlank() || sanitized == "." || sanitized == "..") {
            "_"
        } else {
            sanitized
        }
    }
}
