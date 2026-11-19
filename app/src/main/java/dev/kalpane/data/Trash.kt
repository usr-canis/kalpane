package dev.kalpane.data

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class TrashEntry(
    val id: String,
    /** "page" or "notebook". */
    val kind: String,
    val notebook: String,
    val name: String,
    val deletedAt: Long,
)

/**
 * Deleted pages and notebooks, kept outside the notes folder (so sync treats them as deleted)
 * until restored or purged.
 */
class Trash(val dir: File) {
    init { dir.mkdirs() }

    private fun entryDir(id: String) = File(dir, id)
    private fun meta(id: String) = File(entryDir(id), "entry.json")

    fun list(): List<TrashEntry> = dir.listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { d ->
        runCatching { canvasJson.decodeFromString(TrashEntry.serializer(), File(d, "entry.json").readText()) }.getOrNull()
    }.sortedByDescending { it.deletedAt }

    private fun record(entry: TrashEntry) =
        writeAtomic(meta(entry.id), canvasJson.encodeToString(TrashEntry.serializer(), entry).toByteArray())

    fun trashPage(store: NoteStore, ref: PageRef, now: Long = System.currentTimeMillis()): TrashEntry {
        val entry = TrashEntry(newId(), "page", ref.notebook, ref.name, now)
        val files = File(entryDir(entry.id), "files").apply { mkdirs() }
        listOf(store.textFile(ref), store.canvasFile(ref)).filter { it.exists() }.forEach { f ->
            check(f.renameTo(File(files, f.name))) { "Could not move the page to trash." }
        }
        record(entry)
        store.deletePage(ref)
        return entry
    }

    fun trashNotebook(store: NoteStore, name: String, now: Long = System.currentTimeMillis()): TrashEntry {
        val entry = TrashEntry(newId(), "notebook", name, name, now)
        File(entryDir(entry.id), "files").mkdirs()
        check(File(store.root, name).renameTo(File(entryDir(entry.id), "files/$name"))) { "Could not move the notebook to trash." }
        record(entry)
        return entry
    }

    /** Puts an item back under a free name. Returns the restored page or notebook name. */
    fun restore(store: NoteStore, id: String): String {
        val entry = list().firstOrNull { it.id == id } ?: error("Item no longer in trash.")
        val files = File(entryDir(id), "files")
        val restored = when (entry.kind) {
            "notebook" -> store.restoreNotebook(File(files, entry.name), entry.name)
            else -> store.restorePage(entry.notebook, entry.name, File(files, "${entry.name}.md"), File(files, "${entry.name}.canvas.json")).name
        }
        entryDir(id).deleteRecursively()
        return restored
    }

    fun delete(id: String) { entryDir(id).deleteRecursively() }

    fun empty() { dir.listFiles().orEmpty().forEach { it.deleteRecursively() } }

    /** Deletes items older than [days]. */
    fun purge(days: Int = 30, now: Long = System.currentTimeMillis()) {
        val cutoff = now - days * 24L * 60 * 60 * 1000
        list().filter { it.deletedAt < cutoff }.forEach { delete(it.id) }
    }
}
