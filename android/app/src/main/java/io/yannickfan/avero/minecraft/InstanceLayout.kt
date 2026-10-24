package io.yannickfan.avero.minecraft
import java.io.File
import java.nio.file.Files

class InstanceLayout(private val root: File) {
    fun versionDirectory(versionId: String) = within("versions/${component(versionId)}")
    fun versionJson(versionId: String) = within("versions/${component(versionId)}/${component(versionId)}.json")
    fun clientJar(versionId: String) = within("versions/${component(versionId)}/${component(versionId)}.jar")
    fun library(path: String): File {
        val relative = path.replace('\\', '/').removePrefix("libraries/")
        require(relative.isNotBlank() && !relative.startsWith('/') && !relative.contains(':')) { "Unsafe library path" }
        require(relative.split('/').none { it.isBlank() || it == "." || it == ".." || it.any(Char::isISOControl) }) { "Unsafe library path" }
        return within("libraries/$relative")
    }
    fun assetIndex(id: String) = within("assets/indexes/${component(id)}.json")
    fun loggingConfig(fileId: String) = within("log_configs/${component(fileId)}")
    fun assetObject(hash: String): File {
        require(hash.matches(Regex("[a-fA-F0-9]{40}"))) { "Invalid asset SHA-1" }
        return within("assets/objects/${hash.take(2)}/$hash")
    }
    fun gameDirectory(instanceName: String) = within("instances/${sanitize(instanceName)}/game")
    private fun within(relative: String): File {
        val base = root.absoluteFile.normalize(); val candidate = File(base, relative).absoluteFile.normalize()
        require(candidate.path.startsWith(base.path + File.separator)) { "Path escapes game directory" }
        var item: File? = candidate
        while (item != null) {
            require(!Files.isSymbolicLink(item.toPath())) { "Managed path contains a symbolic link" }
            item = item.parentFile
        }
        return candidate
    }
    private fun component(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,159}"))) { "Unsafe metadata component" }
        return value
    }
    private fun sanitize(value: String): String {
        val clean = value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(128)
        return if (clean.isBlank() || clean == "." || clean == "..") "_" else clean
    }
}
