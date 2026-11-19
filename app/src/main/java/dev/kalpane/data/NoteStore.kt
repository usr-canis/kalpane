package dev.kalpane.data

import kotlinx.serialization.Serializable
import java.io.File

/**
 * Layout (mirrors the notes repository):
 * ```
 * <notebook>/notebook.json        page order
 * <notebook>/<page>.md            text
 * <notebook>/<page>.canvas.json   drawings, shapes and images
 * <notebook>/assets/<sha256>.<ext>
 * ```
 */
data class PageRef(val notebook: String, val name: String) {
    fun encode() = "$notebook/$name"

    companion object {
        fun decode(value: String): PageRef {
            val slash = value.indexOf('/')
            return PageRef(value.substring(0, slash), value.substring(slash + 1))
        }
    }
}

data class NotebookInfo(val name: String, val pages: Int, val modified: Long)

data class PageInfo(val ref: PageRef, val preview: String, val items: List<Item>, val modified: Long)

data class PageData(val text: String, val canvas: CanvasDoc)

/** A search match: [snippet] is plain text with the match at [start] for [length] characters. */
data class SearchHit(val ref: PageRef, val snippet: String, val start: Int, val length: Int)

@Serializable
private data class NotebookMeta(val pages: List<String> = emptyList())

/** Not thread-safe: callers serialize access. */
class NoteStore(val root: File) {
    init { root.mkdirs() }

    private fun dir(notebook: String) = File(root, notebook)
    private fun metaFile(notebook: String) = File(dir(notebook), "notebook.json")
    fun textFile(ref: PageRef) = File(dir(ref.notebook), "${ref.name}.md")
    fun canvasFile(ref: PageRef) = File(dir(ref.notebook), "${ref.name}.canvas.json")
    fun assetFile(notebook: String, asset: String): File {
        require(asset.matches(Regex("[a-f0-9]{64}\\.[a-z]{3,4}"))) { "Invalid asset name." }
        return File(dir(notebook), "assets/$asset")
    }

    private fun pageExists(notebook: String, name: String) =
        File(dir(notebook), "$name.md").exists() || File(dir(notebook), "$name.canvas.json").exists()

    fun notebooks(): List<NotebookInfo> =
        root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }.orEmpty().map { d ->
            val names = pageNames(d.name)
            val modified = names.maxOfOrNull { pageModified(PageRef(d.name, it)) } ?: d.lastModified()
            NotebookInfo(d.name, names.size, modified)
        }.sortedByDescending { it.modified }

    /** Page names in their saved order; pages added outside the app are appended. */
    fun pageNames(notebook: String): List<String> {
        val onDisk = dir(notebook).listFiles().orEmpty().mapNotNull { f ->
            when {
                f.name.startsWith(".") -> null
                f.name.endsWith(".canvas.json") -> f.name.removeSuffix(".canvas.json")
                f.name.endsWith(".md") -> f.name.removeSuffix(".md")
                else -> null
            }
        }.toSet()
        val saved = runCatching { canvasJson.decodeFromString(NotebookMeta.serializer(), metaFile(notebook).readText()).pages }
            .getOrDefault(emptyList())
        return saved.filter { it in onDisk } + onDisk.filter { it !in saved }.sorted()
    }

    private fun saveOrder(notebook: String, names: List<String>) =
        writeAtomic(metaFile(notebook), canvasJson.encodeToString(NotebookMeta.serializer(), NotebookMeta(names)).toByteArray())

    private fun pageModified(ref: PageRef) = maxOf(textFile(ref).lastModified(), canvasFile(ref).lastModified())

    fun pages(notebook: String): List<PageInfo> = pageNames(notebook).map { name ->
        val ref = PageRef(notebook, name)
        val data = read(ref)
        PageInfo(ref, MarkdownText.preview(data.text, 240).let { rest ->
            listOf(MarkdownText.title(data.text), rest).filter { it.isNotEmpty() }.joinToString("\n")
        }, data.canvas.items, pageModified(ref))
    }

    fun read(ref: PageRef): PageData {
        val text = textFile(ref).takeIf { it.exists() }?.readText() ?: ""
        val canvas = canvasFile(ref).takeIf { it.exists() }?.let {
            runCatching { canvasJson.decodeFromString(CanvasDoc.serializer(), it.readText()) }.getOrNull()
        } ?: CanvasDoc()
        return PageData(text, canvas)
    }

    fun write(ref: PageRef, data: PageData) {
        writeAtomic(textFile(ref), data.text.toByteArray())
        val canvas = canvasFile(ref)
        if (data.canvas.items.isEmpty() && data.canvas.style == PageStyle()) canvas.delete()
        else writeAtomic(canvas, canvasJson.encodeToString(CanvasDoc.serializer(), data.canvas).toByteArray())
    }

    fun createNotebook(name: String, withPage: Boolean = true): String {
        val existing = notebooks().map { it.name.lowercase() }
        val unique = uniqueName(safeName(name, "Notebook")) { it.lowercase() in existing }
        dir(unique).mkdirs()
        if (withPage) createPage(unique) else saveOrder(unique, emptyList())
        return unique
    }

    fun renameNotebook(old: String, name: String): String {
        val target = safeName(name, old)
        if (target == old) return old
        val existing = notebooks().map { it.name.lowercase() }.filter { it != old.lowercase() }
        val unique = uniqueName(target) { it.lowercase() in existing }
        check(dir(old).renameTo(dir(unique))) { "Could not rename notebook." }
        return unique
    }

    fun deleteNotebook(name: String) { dir(name).deleteRecursively() }

    fun createPage(notebook: String, after: String? = null): PageRef {
        val names = pageNames(notebook).toMutableList()
        var n = names.size + 1
        while (pageExists(notebook, "Page $n")) n++
        val name = "Page $n"
        writeAtomic(textFile(PageRef(notebook, name)), ByteArray(0))
        val at = after?.let { names.indexOf(it) + 1 }?.takeIf { it > 0 } ?: names.size
        names.add(at, name)
        saveOrder(notebook, names)
        return PageRef(notebook, name)
    }

    fun renamePage(ref: PageRef, name: String): PageRef {
        val target = safeName(name, ref.name)
        if (target == ref.name) return ref
        val unique = uniqueName(target) { pageExists(ref.notebook, it) }
        val new = PageRef(ref.notebook, unique)
        val order = pageNames(ref.notebook).map { if (it == ref.name) unique else it }
        if (textFile(ref).exists()) check(textFile(ref).renameTo(textFile(new))) { "Could not rename page." }
        if (canvasFile(ref).exists()) check(canvasFile(ref).renameTo(canvasFile(new))) { "Could not rename page." }
        saveOrder(ref.notebook, order)
        return new
    }

    fun deletePage(ref: PageRef) {
        textFile(ref).delete()
        canvasFile(ref).delete()
        saveOrder(ref.notebook, pageNames(ref.notebook))
    }

    fun movePage(ref: PageRef, offset: Int) {
        val names = pageNames(ref.notebook).toMutableList()
        val from = names.indexOf(ref.name)
        val to = (from + offset).coerceIn(0, names.size - 1)
        if (from < 0 || from == to) return
        names.add(to, names.removeAt(from))
        saveOrder(ref.notebook, names)
    }

    /** Stores image bytes once per notebook under their SHA-256. */
    fun importAsset(notebook: String, bytes: ByteArray, extension: String): String {
        require(bytes.size <= 20 * 1024 * 1024) { "Images must be 20 MB or smaller." }
        val name = "${hash(bytes)}.${extension.lowercase()}"
        val file = assetFile(notebook, name)
        if (!file.exists()) writeAtomic(file, bytes)
        return name
    }

    /** Moves trashed page files back as a new last page, under a free name. */
    fun restorePage(notebook: String, name: String, text: File, canvas: File): PageRef {
        dir(notebook).mkdirs()
        val names = pageNames(notebook)
        val unique = uniqueName(name) { pageExists(notebook, it) }
        val ref = PageRef(notebook, unique)
        if (text.exists()) check(text.renameTo(textFile(ref))) { "Could not restore the page." }
        if (canvas.exists()) check(canvas.renameTo(canvasFile(ref))) { "Could not restore the page." }
        if (!textFile(ref).exists()) writeAtomic(textFile(ref), ByteArray(0))
        saveOrder(notebook, names + unique)
        return ref
    }

    fun restoreNotebook(folder: File, name: String): String {
        val existing = notebooks().map { it.name.lowercase() }
        val unique = uniqueName(name) { it.lowercase() in existing }
        check(folder.renameTo(dir(unique))) { "Could not restore the notebook." }
        return unique
    }

    /** Pages whose name or text contains [query], ignoring case. */
    fun search(query: String, limit: Int = 50): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val hits = ArrayList<SearchHit>()
        for (book in notebooks()) for (name in pageNames(book.name)) {
            val ref = PageRef(book.name, name)
            val text = read(ref).text
            val plain = if (q.startsWith("#")) text.lineSequence().filter { it.isNotBlank() }.joinToString("  ")
            else text.lineSequence().map { MarkdownText.plain(it) }.filter { it.isNotBlank() }.joinToString("  ")
            val at = plain.indexOf(q, ignoreCase = true)
            when {
                at >= 0 -> {
                    val from = maxOf(0, at - 40)
                    val snippet = (if (from > 0) "…" else "") + plain.substring(from, minOf(plain.length, at + q.length + 80))
                    hits += SearchHit(ref, snippet, at - from + (if (from > 0) 1 else 0), q.length)
                }
                name.contains(q, ignoreCase = true) -> hits += SearchHit(ref, plain.take(120), -1, 0)
            }
            if (hits.size >= limit) return hits
        }
        return hits
    }

    /** Most recently edited pages across all notebooks. */
    fun recent(limit: Int = 8): List<PageInfo> =
        notebooks().flatMap { b -> pageNames(b.name).map { PageRef(b.name, it) } }
            .sortedByDescending { pageModified(it) }.take(limit)
            .map { ref -> val d = read(ref); PageInfo(ref, MarkdownText.title(d.text), d.canvas.items, pageModified(ref)) }

    /** All pages, by notebook and order. */
    fun allPages(): List<PageRef> = notebooks().sortedBy { it.name.lowercase() }.flatMap { b -> pageNames(b.name).map { PageRef(b.name, it) } }

    /** Resolves a `[[target]]` written in [from]: `Notebook/Page`, a page in the same notebook, then any notebook. */
    fun resolveLink(from: String, target: String): PageRef? {
        val t = target.trim()
        if ('/' in t) {
            val ref = PageRef(t.substringBefore('/').trim(), t.substringAfter('/').trim())
            if (pageExists(ref.notebook, ref.name)) return ref
        }
        if (pageExists(from, t)) return PageRef(from, t)
        return allPages().firstOrNull { it.name.equals(t, ignoreCase = true) }
    }

    /** Pages that link to [ref]. */
    fun backlinks(ref: PageRef): List<PageRef> = allPages().filter { it != ref }.filter { page ->
        MarkdownSpans.wiki.findAll(read(page).text).any { resolveLink(page.notebook, it.groupValues[1]) == ref }
    }

    /** Every `#tag` outside code, with how many pages use it. */
    fun tags(): Map<String, Int> {
        val counts = HashMap<String, Int>()
        for (page in allPages()) {
            val text = read(page).text
            val spans = MarkdownSpans.parse(text).filter { it.kind == Kind.Tag }
            spans.map { text.substring(it.start, it.end).lowercase() }.toSet().forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        return counts.toList().sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first }).toMap()
    }

    /** Creates a page with a given name at the end of [notebook]. */
    fun createNamedPage(notebook: String, name: String): PageRef = renamePage(createPage(notebook), name)
}
