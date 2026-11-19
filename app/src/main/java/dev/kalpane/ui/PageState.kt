package dev.kalpane.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import dev.kalpane.data.CanvasDoc
import dev.kalpane.data.Item
import dev.kalpane.data.PageData
import dev.kalpane.data.PageStyle
import dev.kalpane.data.Pt
import dev.kalpane.data.ShapeKind

enum class Tool { Pen, Highlighter, Eraser, Select, Shape }

/** Ink colours; null follows the theme (dark ink on light paper, light ink on dark). */
val PenColors: List<Int?> = listOf(null, 0xFFE0730C.toInt(), 0xFFD93025.toInt(), 0xFF1A73E8.toInt(), 0xFF188038.toInt(), 0xFF8E24AA.toInt())
val HighlighterColors: List<Int> = listOf(0xFFFFD54F.toInt(), 0xFF81C784.toInt(), 0xFFF48FB1.toInt(), 0xFF64B5F6.toInt())
const val MIN_PEN = 0.5f
const val MAX_PEN = 12f
const val MIN_HIGHLIGHTER = 6f
const val MAX_HIGHLIGHTER = 40f

/** Pen pressure sensitivity presets. */
enum class Pressure(val label: String, val value: Float) { Off("Off", 0f), Soft("Soft", 0.35f), Normal("Normal", 0.65f), Strong("Strong", 1f) }

@Stable
class ToolState {
    var tool by mutableStateOf(Tool.Pen)
    var penColor by mutableStateOf<Int?>(null)
    var penWidth by mutableStateOf(2.5f)
    var highlighterColor by mutableStateOf(HighlighterColors[0])
    var highlighterWidth by mutableStateOf(18f)
    var partialEraser by mutableStateOf(false)
    var pressure by mutableStateOf(Pressure.Normal)
    var shape by mutableStateOf(ShapeKind.Rectangle)
}

private data class Snapshot(val text: TextFieldValue, val items: List<Item>)

@Stable
class PageState(text: String, items: List<Item>, style: PageStyle) {
    var style by mutableStateOf(style)
        private set

    var text by mutableStateOf(TextFieldValue(text))
        private set
    var items by mutableStateOf(items)
        private set
    var selection by mutableStateOf<Set<String>>(emptySet())
    /** Points (x, y, pressure) of the stroke being drawn. */
    val live = mutableStateListOf<Float>()
    var lasso by mutableStateOf<List<Pt>>(emptyList())
    var textFocused by mutableStateOf(false)

    /** Increments on every content change; drives autosave. */
    var version by mutableIntStateOf(0)
        private set

    private val undo = ArrayDeque<Snapshot>()
    private val redo = ArrayDeque<Snapshot>()
    private var lastTextPush = 0L
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private fun push() {
        undo.addLast(Snapshot(text, items))
        if (undo.size > 300) undo.removeFirst()
        redo.clear()
        sync()
    }

    private fun sync() {
        canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty()
    }

    /** Text edits; typing within a short pause merges into one undo step. */
    fun setText(value: TextFieldValue, merge: Boolean) {
        if (value.text != text.text) {
            val now = System.currentTimeMillis()
            if (!merge || now - lastTextPush > 900) push()
            lastTextPush = now
            version++
        }
        text = value
    }

    /** Selection or composition changes that do not alter the text. */
    fun setSelection(value: TextFieldValue) { text = value }

    /** A complete canvas change, recorded for undo. */
    fun commit(new: List<Item>) {
        push()
        lastTextPush = 0
        items = new
        version++
    }

    /** Starts a continuous change (drag, erase): records undo once, then [update] freely. */
    fun begin() { push(); lastTextPush = 0 }

    fun update(new: List<Item>) { items = new; version++ }

    fun undo() {
        val s = undo.removeLastOrNull() ?: return
        redo.addLast(Snapshot(text, items))
        apply(s)
    }

    fun redo() {
        val s = redo.removeLastOrNull() ?: return
        undo.addLast(Snapshot(text, items))
        apply(s)
    }

    private fun apply(s: Snapshot) {
        text = s.text; items = s.items
        selection = selection.filter { id -> items.any { it.id == id } }.toSet()
        lastTextPush = 0
        version++
        sync()
    }

    fun applyStyle(new: PageStyle) { style = new; version++ }

    /** Replaces the page with another version (undoable). */
    fun restoreFrom(data: PageData) {
        push()
        text = TextFieldValue(data.text)
        items = data.canvas.items
        style = data.canvas.style
        selection = emptySet()
        lastTextPush = 0
        version++
    }

    fun data() = PageData(text.text, CanvasDoc(style = style, items = items))
}
