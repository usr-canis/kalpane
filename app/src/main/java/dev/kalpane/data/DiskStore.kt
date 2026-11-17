package dev.kalpane.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.encodeToString

/** Single-writer store: caller serializes mutations on its IO actor. */
class DiskStore(val root: File) {
    val assets = File(root, "assets").apply { mkdirs() }
    private fun workspace(repo: String, branch: String) = File(root, "workspaces/${hash("$repo\n$branch".toByteArray())}.json")
    fun read(repo: String, branch: String): Workspace = readRecoverable(workspace(repo, branch)) { notebookJson.decodeFromString<Workspace>(it).also { w -> w.document.validate(); w.base.validate() } } ?: Workspace()
    fun save(repo: String, branch: String, workspace: Workspace) = writeAtomic(workspace(repo, branch), notebookJson.encodeToString(workspace).toByteArray())
    fun readPreferences(): Preferences = readRecoverable(File(root, "preferences.json")) { notebookJson.decodeFromString<Preferences>(it) } ?: Preferences()
    fun savePreferences(prefs: Preferences) = writeAtomic(File(root, "preferences.json"), notebookJson.encodeToString(prefs).toByteArray())
    fun asset(name: String): File { require(name.matches(Regex("[a-f0-9]{64}"))); return File(assets, name) }
    fun importAsset(bytes: ByteArray): String {
        require(bytes.size <= 20 * 1024 * 1024) { "Images must be 20 MB or smaller." }
        val name = hash(bytes); val file = asset(name)
        if (!file.exists() || hash(file.readBytes()) != name) writeAtomic(file, bytes, keepPrevious = false)
        return name
    }
    fun hasWorkspace(repo: String, branch: String) = workspace(repo, branch).exists()
    private fun <T> readRecoverable(file: File, decode: (String) -> T): T? {
        val previous = File(file.path + ".previous")
        if (!file.exists() && !previous.exists()) return null
        val current = if (file.exists()) runCatching { decode(file.readText()) } else null
        if (current?.isSuccess == true) return current.getOrThrow()
        val backup = if (previous.exists()) runCatching { decode(previous.readText()) } else null
        if (backup?.isSuccess == true) {
            if (file.exists()) Files.copy(file.toPath(), File(file.path + ".corrupt-${System.currentTimeMillis()}").toPath())
            writeAtomic(file, previous.readBytes(), keepPrevious = false)
            return backup.getOrThrow()
        }
        error("Local notebook data could not be opened. Original files are preserved; do not clear app storage.")
    }
    companion object {
        fun writeAtomic(file: File, bytes: ByteArray, keepPrevious: Boolean = true) {
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".writing")
            FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
            if (keepPrevious && file.exists()) {
                val backupTemp = File(file.path + ".previous.writing")
                FileOutputStream(backupTemp).use { it.write(file.readBytes()); it.fd.sync() }
                Files.move(backupTemp.toPath(), File(file.path + ".previous").toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
