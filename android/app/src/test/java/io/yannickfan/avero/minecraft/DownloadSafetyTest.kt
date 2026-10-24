package io.yannickfan.avero.minecraft
import io.yannickfan.avero.downloads.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

class DownloadSafetyTest {
 @Test fun refusesUntrustedHostsAndUnsafeComponents() {
  listOf("https://piston-meta.mojang.com.evil.example/a", "https://user@piston-meta.mojang.com/a", "http://piston-meta.mojang.com/a", "file:///etc/passwd", "https://127.0.0.1/a").forEach {
   assertThrows(Exception::class.java) { DownloadPolicy.validate(it) }
  }
  val root=Files.createTempDirectory("avero-boundaries").toFile()
  try {
   val layout=InstanceLayout(root)
   listOf("../outside", "/absolute", "..", "a/b").forEach { assertThrows(IllegalArgumentException::class.java) { layout.versionJson(it) } }
   assertThrows(IllegalArgumentException::class.java) { layout.assetObject("aa/../../outside") }
   val outside=Files.createTempDirectory("avero-outside").toFile()
   try { Files.createSymbolicLink(File(root,"libraries").toPath(),outside.toPath()); assertThrows(IllegalArgumentException::class.java) { layout.library("example.jar") } }
   finally { File(root,"libraries").delete(); outside.deleteRecursively() }
  } finally { root.deleteRecursively() }
 }
 @Test fun recoversInterruptedQueueAndExportsOnlyOperationalFields() = runBlocking {
  val root=Files.createTempDirectory("avero-store").toFile()
  try {
   val store=DownloadStore(root)
   val task=DownloadTask(UUID.randomUUID().toString(),"1.21.4","private-name","Downloading",message="private-message")
   store.saveTasks(listOf(task)); assertEquals("Paused",store.loadTasks().single().state)
   val report=store.report(listOf(task))
   assertFalse(report.contains("private-name")); assertFalse(report.contains("private-message")); assertFalse(report.contains(root.path))
   store.recordComplete("1.21.4","Test"); assertEquals("1.21.4",store.localVersions().single().version)
  } finally {root.deleteRecursively()}
 }
 @Test fun deduplicatesAssetObjectsWithoutLosingHashValidation() = runBlocking {
  val root=Files.createTempDirectory("avero-assets").toFile()
  try {
   val index=File(root,"index.json"); index.writeText("""{"objects":{"a":{"hash":"0000000000000000000000000000000000000000","size":1},"b":{"hash":"0000000000000000000000000000000000000000","size":1}}}""")
   assertEquals(2,AssetInstaller().parseIndex(index).size)
   index.writeText("""{"objects":{"a":{"hash":"../escape","size":1}}}""")
   try { AssetInstaller().parseIndex(index); fail("Unsafe hash accepted") } catch(_:IllegalArgumentException) {}
  } finally {root.deleteRecursively()}
 }
}
