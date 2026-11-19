package dev.kalpane.data

import java.io.File

/** A note is `<root>/<notebook>/<name>.md`. The folder mirrors the notes repository. */
data class NoteRef(val notebook: String, val name: String) {
    fun encode() = "$notebook/$name"

    companion object {
        fun decode(value: String): NoteRef {
            val slash = value.indexOf('/')
            return NoteRef(value.substring(0, slash), value.substring(slash + 1))
        }
    }
}

data class NoteInfo(
    val ref: NoteRef,
    val title: String,
    val preview: String,
    val modified: Long,
    val text: String,
)

/** Not thread-safe: callers serialize access. */
class NoteStore(val root: File) {
    init {
        root.mkdirs()
    }

    private fun dir(notebook: String) = File(root, notebook)
    fun file(ref: NoteRef) = File(dir(ref.notebook), "${ref.name}.md")

    fun notebooks(): List<String> =
        root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            .orEmpty().map { it.name }.sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun notes(notebook: String? = null): List<NoteInfo> {
        val books = notebook?.let { listOf(it) } ?: notebooks()
        return books.flatMap { book ->
            dir(book).listFiles { f -> f.isFile && f.name.endsWith(".md") && !f.name.startsWith(".") }
                .orEmpty().map { f ->
                    val text = f.readText()
                    val ref = NoteRef(book, f.name.removeSuffix(".md"))
                    NoteInfo(ref, MarkdownText.title(text).ifEmpty { ref.name }, MarkdownText.preview(text), f.lastModified(), text)
                }
        }.sortedByDescending { it.modified }
    }

    fun search(query: String, notebook: String? = null): List<NoteInfo> {
        val q = query.trim()
        if (q.isEmpty()) return notes(notebook)
        return notes(notebook).filter { it.text.contains(q, ignoreCase = true) || it.ref.name.contains(q, ignoreCase = true) }
    }

    fun read(ref: NoteRef): String = file(ref).takeIf { it.exists() }?.readText() ?: ""

    fun write(ref: NoteRef, text: String) = writeAtomic(file(ref), text.toByteArray())

    fun createNotebook(name: String): String {
        val unique = uniqueName(safeName(name, "Notebook")) { candidate ->
            notebooks().any { it.equals(candidate, ignoreCase = true) }
        }
        dir(unique).mkdirs()
        return unique
    }

    fun renameNotebook(old: String, name: String): String {
        val target = safeName(name, old)
        if (target == old) return old
        val unique = uniqueName(target) { c -> notebooks().any { it.equals(c, ignoreCase = true) && it != old } }
        check(dir(old).renameTo(dir(unique))) { "Could not rename notebook." }
        return unique
    }

    fun deleteNotebook(name: String) {
        dir(name).deleteRecursively()
    }

    fun createNote(notebook: String, text: String = ""): NoteRef {
        dir(notebook).mkdirs()
        val name = uniqueName("Untitled") { file(NoteRef(notebook, it)).exists() }
        val ref = NoteRef(notebook, name)
        write(ref, text)
        return ref
    }

    fun delete(ref: NoteRef) {
        file(ref).delete()
    }

    fun move(ref: NoteRef, notebook: String): NoteRef {
        if (ref.notebook == notebook) return ref
        dir(notebook).mkdirs()
        val name = uniqueName(ref.name) { file(NoteRef(notebook, it)).exists() }
        val target = NoteRef(notebook, name)
        check(file(ref).renameTo(file(target))) { "Could not move note." }
        return target
    }

    /** Renames the file to match the note's title, so the repository stays readable. */
    fun renameToTitle(ref: NoteRef): NoteRef {
        val title = MarkdownText.title(read(ref))
        val wanted = safeName(title)
        if (wanted == ref.name) return ref
        if (ref.name.startsWith(wanted) && ref.name.removePrefix(wanted).matches(Regex(" \\d+"))) return ref
        val name = uniqueName(wanted) { it != ref.name && file(NoteRef(ref.notebook, it)).exists() }
        val target = NoteRef(ref.notebook, name)
        if (name == ref.name) return ref
        check(file(ref).renameTo(file(target))) { "Could not rename note." }
        return target
    }
}
