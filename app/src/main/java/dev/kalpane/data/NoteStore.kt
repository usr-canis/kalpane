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

    fun createNotebook(name: String): String {
        val existing = notebooks().map { it.name.lowercase() }
        val unique = uniqueName(safeName(name, "Notebook")) { it.lowercase() in existing }
        dir(unique).mkdirs()
        createPage(unique)
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
}
