package dev.kalpane.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.kalpane.AppViewModel
import dev.kalpane.data.Geometry
import dev.kalpane.data.MarkdownSpans
import dev.kalpane.data.PAGE_WIDTH
import dev.kalpane.data.PageRef
import dev.kalpane.data.Paper
import dev.kalpane.data.Picture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil

private const val MARGIN_X = 56f
private const val MARGIN_TOP = 40f
private const val A4_W = 595f
private const val A4_H = 842f

/** Exports always use light paper and dark ink so they print well. */
private val ExportPalette = MarkdownPalette(
    text = Color(0xFF1C1B19), muted = Color(0xFF6B6A66), syntax = Color(0x8C6B6A66), accent = Color(0xFFE0730C),
    codeBackground = Color(0xFFF1F0EC), rule = Color(0xFFA3A19B),
    keyword = Color(0xFF7B3FA0), string = Color(0xFF2E7D32), number = Color(0xFF1565C0),
)
private val Ink = Color(0xFF1C1B19)

/** A page laid out for export, in page units. */
private class Prepared(val ref: PageRef, val layout: TextLayoutResult, val data: dev.kalpane.data.PageData, val paper: Paper,
                       val size: Float, val images: Map<String, ImageBitmap>) {
    val height = maxOf(MARGIN_TOP + layout.size.height + 40f, (Geometry.boundsOf(data.canvas.items)?.bottom ?: 0f) + 40f)
}

private suspend fun prepare(context: Context, vm: AppViewModel, ref: PageRef): Prepared {
    val data = vm.load(ref)
    val defaults = vm.defaults.value
    val font = NoteFont.entries.firstOrNull { it.name == (data.canvas.style.font ?: defaults.font) } ?: NoteFont.Sans
    val size = data.canvas.style.size ?: defaults.size
    val paper = data.canvas.style.paper ?: defaults.paper
    val family = if (font.res == null) FontFamily.SansSerif else FontFamily(Typefaces.get(context, font))
    val mono = FontFamily(Typefaces.get(context, if (font.mono) font else NoteFont.JetBrainsMono))
    val measurer = TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)
    val styled = styleMarkdown(data.text, null, ExportPalette, mono, size)
    val layout = measurer.measure(
        styled,
        style = TextStyle(color = Ink, fontFamily = family, fontSize = size.sp, lineHeight = (size * LINE_HEIGHT).sp),
        constraints = Constraints(maxWidth = (PAGE_WIDTH - 2 * MARGIN_X).toInt()),
    )
    val images = data.canvas.items.filterIsInstance<Picture>().map { it.asset }.toSet()
        .mapNotNull { a -> vm.image(ref.notebook, a)?.let { a to it } }.toMap()
    return Prepared(ref, layout, data, paper, size, images)
}

private fun DrawScope.drawPrepared(p: Prepared) {
    drawPaper(p.paper, Color.White, Color(0xFFD6D4CE), p.size, 1f)
    val l = p.layout
    val length = l.layoutInput.text.length
    if (length > 0) MarkdownSpans.codeBlocks(p.data.text).forEach { block ->
        val top = l.getLineTop(l.getLineForOffset(block.first.coerceIn(0, length)))
        val bottom = l.getLineBottom(l.getLineForOffset(block.last.coerceIn(0, length)))
        drawRoundRect(ExportPalette.codeBackground, Offset(MARGIN_X - 8f, MARGIN_TOP + top), Size(PAGE_WIDTH - 2 * MARGIN_X + 16f, bottom - top), CornerRadius(8f, 8f))
    }
    translate(MARGIN_X, MARGIN_TOP) { drawText(l) }
    val cache = PathCache()
    p.data.canvas.items.forEach { drawItem(it, Ink, cache, p.images, Color(0xFFECEAE6)) }
}

/** Page breaks for A4 slices, moved up to the nearest line end so text is not cut. */
private fun breaks(p: Prepared, sliceHeight: Float): List<Float> {
    val out = mutableListOf(0f)
    val lineEnds = (0 until p.layout.lineCount).map { MARGIN_TOP + p.layout.getLineBottom(it) }
    while (out.last() + sliceHeight < p.height) {
        val limit = out.last() + sliceHeight
        val atLine = lineEnds.lastOrNull { it in (out.last() + sliceHeight * 0.6f)..limit }
        out += atLine ?: limit
    }
    return out
}

private fun writePdf(pages: List<Prepared>, file: File) {
    val doc = PdfDocument()
    val scale = A4_W / PAGE_WIDTH
    val slice = A4_H / scale
    var number = 1
    try {
        for (p in pages) {
            val starts = breaks(p, slice)
            starts.forEachIndexed { i, start ->
                val end = starts.getOrNull(i + 1) ?: (start + slice)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_W.toInt(), A4_H.toInt(), number++).create())
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(page.canvas), Size(A4_W, A4_H)) {
                    withTransform({ scale(scale, scale, Offset.Zero) }) {
                        drawRect(Color.White, size = Size(PAGE_WIDTH, slice))
                        clipRect(0f, 0f, PAGE_WIDTH, end - start) {
                            translate(0f, -start) { drawPrepared(p) }
                        }
                    }
                }
                doc.finishPage(page)
            }
        }
        file.outputStream().use { doc.writeTo(it) }
    } finally {
        doc.close()
    }
}

private fun writePng(p: Prepared, file: File) {
    val maxPixels = 14000f
    val scale = minOf(1.5f, maxPixels / p.height)
    val bitmap = Bitmap.createBitmap((PAGE_WIDTH * scale).toInt(), ceil(p.height * scale).toInt(), Bitmap.Config.ARGB_8888)
    try {
        val canvas = android.graphics.Canvas(bitmap)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(canvas), Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
            withTransform({ scale(scale, scale, Offset.Zero) }) { drawPrepared(p) }
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } finally {
        bitmap.recycle()
    }
}

private fun share(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun exportDir(context: Context) = File(context.cacheDir, "exports").apply {
    mkdirs()
    listFiles().orEmpty().forEach { it.delete() } // Keep only the latest export.
}

private fun fileName(name: String) = name.replace(Regex("""[^\p{L}\p{N} ._-]"""), "_").ifBlank { "Kalpane" }

suspend fun exportPage(context: Context, vm: AppViewModel, ref: PageRef, pdf: Boolean) {
    try {
        val prepared = prepare(context, vm, ref)
        val file = withContext(Dispatchers.IO) {
            File(exportDir(context), fileName(ref.name) + if (pdf) ".pdf" else ".png").also {
                if (pdf) writePdf(listOf(prepared), it) else writePng(prepared, it)
            }
        }
        share(context, file, if (pdf) "application/pdf" else "image/png")
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

suspend fun exportNotebook(context: Context, vm: AppViewModel, notebook: String) {
    try {
        val refs = vm.pageRefs(notebook)
        val prepared = refs.map { prepare(context, vm, it) }
        val file = withContext(Dispatchers.IO) {
            File(exportDir(context), fileName(notebook) + ".pdf").also { writePdf(prepared, it) }
        }
        share(context, file, "application/pdf")
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
