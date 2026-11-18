package dev.kalpane.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

fun id(): String = UUID.randomUUID().toString()
val notebookJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

@Serializable data class InkPoint(
    val x: Float, val y: Float, val time: Long,
    val pressure: Float = -1f, val tilt: Float = -1f, val orientation: Float = -1f,
    val unitCm: Float = 0f,
)
@Serializable enum class TextFace { Sans, Serif, Monospace }
@Serializable enum class LayerKind { Ink, Text, Image }
@Serializable data class Layer(
    val id: String = id(), val kind: LayerKind,
    val x: Float = 80f, val y: Float = 100f, val width: Float = 360f, val height: Float = 220f,
    val locked: Boolean = false, val text: String = "", val asset: String = "",
    val color: Int = 0xff252522.toInt(), val penWidth: Float = 3f, val fontSize: Float = 26f,
    val points: List<InkPoint> = emptyList(), val stylus: Boolean = true, val textFace: TextFace = TextFace.Sans,
)
@Serializable enum class Paper { Plain, Dots, Grid, Ruled }
@Serializable data class Page(
    val id: String = id(), val title: String = "Untitled page", val width: Float = 1000f,
    val height: Float = 1414f, val paper: Paper = Paper.Dots,
    val layers: List<Layer> = emptyList(), val modified: Long = System.currentTimeMillis(),
)
@Serializable data class Notebook(val id: String = id(), val title: String = "Untitled notebook", val pages: List<Page> = emptyList())
@Serializable data class Document(val schema: Int = 1, val notebooks: List<Notebook> = emptyList()) {
    fun pages(): List<Page> = notebooks.flatMap { it.pages }
    fun assets(): Set<String> = pages().flatMap { it.layers }.filter { it.kind == LayerKind.Image }.map { it.asset }.toSet()
    fun replace(page: Page): Document = copy(notebooks = notebooks.map { n -> n.copy(pages = n.pages.map { if (it.id == page.id) page else it }) })
    fun validate(): Document {
        require(schema == 1) { "This backup uses an unsupported format. Update Kalpane first." }
        require(notebooks.size <= 2000 && pages().size <= 20000) { "Backup exceeds supported notebook size." }
        require(notebooks.map { it.id }.distinct().size == notebooks.size && pages().map { it.id }.distinct().size == pages().size)
        pages().forEach { p ->
            require(p.width.isFinite() && p.width in 100f..10000f && p.height.isFinite() && p.height in 100f..10000f)
            require(p.layers.size <= 100000 && p.layers.map { it.id }.distinct().size == p.layers.size)
            p.layers.forEach { l ->
                require(listOf(l.x,l.y,l.width,l.height,l.penWidth,l.fontSize).all { it.isFinite() })
                require(l.width > 0 && l.height > 0 && l.penWidth in 0.1f..100f && l.fontSize in 1f..300f)
                require(l.points.size <= 1000000 && l.points.all { it.x.isFinite() && it.y.isFinite() && it.time >= 0 &&
                    (it.pressure == -1f || it.pressure in 0f..1f) && (it.tilt == -1f || it.tilt in 0f..1.5707964f) &&
                    (it.orientation == -1f || it.orientation in 0f..6.2831855f) && (it.unitCm == -1f || it.unitCm >= 0f && it.unitCm.isFinite()) })
                require(l.points.zipWithNext().all { (a,b) -> b.time >= a.time })
                if (l.kind == LayerKind.Image) require(l.asset.matches(Regex("[a-f0-9]{64}")))
            }
        }
        return this
    }
}
@Serializable enum class ThemeMode { System, Light, Dark }
@Serializable data class Preferences(
    val theme: ThemeMode = ThemeMode.System, val paper: Paper = Paper.Dots,
    val textFace: TextFace = TextFace.Sans, val textSize: Float = 44f,
    val penWidth: Float = 3f, val fingerDrawing: Boolean = false,
    val partialEraser: Boolean = false,
    val reducedMotion: Boolean = false, val gridSpacing: Float = 28f,
    val recentColors: List<Int> = listOf(0xff252522.toInt(), 0xffE0730C.toInt(), 0xff316a69.toInt(), 0xff5269a3.toInt()),
    val repo: String = "", val branch: String = "main",
)
@Serializable data class Workspace(
    val document: Document = Document(), val base: Document = Document(), val remoteHead: String? = null,
    val lastNotebook: String? = null, val lastPage: String? = null, val lastBackup: Long? = null,
) {
    val pending: Boolean get() = document != base
    val pendingPages: Int get() {
        val before = base.pages().associateBy { it.id }
        val after = document.pages().associateBy { it.id }
        return (before.keys + after.keys).count { before[it] != after[it] }
    }
}

/** A three-way merge. Conflict copies are deliberate user-visible pages, never discarded edits. */
object Merge {
    data class Result(val document: Document, val conflicts: Int)
    fun documents(base: Document, local: Document, remote: Document): Result {
        var conflicts = 0
        val b = base.notebooks.associateBy { it.id }
        val l = local.notebooks.associateBy { it.id }
        val r = remote.notebooks.associateBy { it.id }
        val order = (local.notebooks.map { it.id } + remote.notebooks.map { it.id }).distinct()
        val merged = order.mapNotNull { key ->
            val bn = b[key]; val ln = l[key]; val rn = r[key]
            when {
                ln == rn -> ln
                ln == bn -> rn
                rn == bn -> ln
                ln == null -> rn
                rn == null -> ln
                else -> {
                    val bp = bn?.pages?.associateBy { it.id }.orEmpty()
                    val lp = ln.pages.associateBy { it.id }; val rp = rn.pages.associateBy { it.id }
                    val pageOrder = if (ln.pages.map { it.id } == bn?.pages?.map { it.id }) rn.pages.map { it.id } + ln.pages.map { it.id } else ln.pages.map { it.id } + rn.pages.map { it.id }
                    val pages = pageOrder.distinct().flatMap { pid ->
                        val pb = bp[pid]; val pl = lp[pid]; val pr = rp[pid]
                        when {
                            pl == pr -> listOfNotNull(pl)
                            pl == pb -> listOfNotNull(pr)
                            pr == pb -> listOfNotNull(pl)
                            pl == null -> { conflicts++; listOfNotNull(pr?.copy(title = "${pr.title} · remote recovery")) }
                            pr == null -> { conflicts++; listOf(pl.copy(title = "${pl.title} · local recovery")) }
                            else -> { conflicts++; listOf(pl, pr.copy(id = id(), title = "${pr.title} · remote conflict")) }
                        }
                    }
                    ln.copy(title = if (ln.title == bn?.title) rn.title else ln.title, pages = pages)
                }
            }
        }
        return Result(Document(notebooks = merged), conflicts)
    }
}

fun Page.moveLayer(layerId: String, front: Boolean): Page {
    val layer = layers.find { it.id == layerId } ?: return this
    val rest = layers.filterNot { it.id == layerId }
    return copy(layers = if (front) rest + layer else listOf(layer) + rest)
}
fun Page.movePageIn(notebook: Notebook, delta: Int): Notebook {
    val from = notebook.pages.indexOfFirst { it.id == id }
    if (from < 0) return notebook
    val to = (from + delta).coerceIn(0, notebook.pages.lastIndex)
    val pages = notebook.pages.toMutableList(); pages.add(to, pages.removeAt(from))
    return notebook.copy(pages = pages)
}
