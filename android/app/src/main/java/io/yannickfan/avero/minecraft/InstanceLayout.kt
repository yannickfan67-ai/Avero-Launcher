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

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
