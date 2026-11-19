package dev.kalpane.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import dev.kalpane.data.Geometry
import dev.kalpane.data.Item
import dev.kalpane.data.Picture
import dev.kalpane.data.Pt
import dev.kalpane.data.Shape
import dev.kalpane.data.ShapeKind
import dev.kalpane.data.Stroke
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Turns pointer input on the page into drawing, erasing, selecting and transforming. */
class CanvasController(private val state: PageState, private val tools: ToolState) {
    var fingerDrawing = false
    /** Page units per screen dp, so handles and hit areas keep a constant size on screen. */
    var hu = 1f

    private sealed interface Active
    private class Ink(val highlighter: Boolean) : Active
    private class Erase(var last: Pt, var started: Boolean = false) : Active
    private class Lasso(val start: Pt) : Active
    private class NewShape(val start: Pt, val id: String = dev.kalpane.data.newId(), var started: Boolean = false) : Active
    private abstract class Transform(val start: Pt, val originals: List<Item>) : Active { var started = false }
    private class Move(start: Pt, originals: List<Item>) : Transform(start, originals)
    private class Scale(start: Pt, originals: List<Item>, val corner: Pt, val fixed: Pt) : Transform(start, originals)
    private class Resize(start: Pt, originals: List<Item>, val corner: Int) : Transform(start, originals)
    private class LineEnd(start: Pt, originals: List<Item>, val end: Int) : Transform(start, originals)
    private class Rotate(start: Pt, originals: List<Item>, val center: Pt) : Transform(start, originals)

    private var active: Active? = null

    /** The MotionEvent being dispatched, for the low-latency ink overlay. */
    var lastEvent: android.view.MotionEvent? = null
    /** True while the overlay draws the stroke, so the page skips its own live stroke. */
    var wet = false
    /** Theme ink colour, used when the pen colour follows the theme. */
    var inkArgb = 0xFF1C1B19.toInt()
    val penInk get() = (active as? Ink)?.highlighter == false
    val liveArgb get() = liveColor ?: inkArgb
    val livePressure get() = tools.pressure.value

    /** Style of the stroke being drawn, for live rendering. */
    val liveHighlighter get() = (active as? Ink)?.highlighter == true
    val liveWidth get() = if (liveHighlighter) tools.highlighterWidth else tools.penWidth
    val liveColor: Int? get() = if (liveHighlighter) tools.highlighterColor else tools.penColor

    private fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)

    /** Returns true if the canvas takes this gesture; otherwise text editing and scrolling get it. */
    fun begin(p: Pt, stylus: Boolean, eraserTip: Boolean, pressure: Float): Boolean {
        val selected = state.items.filter { it.id in state.selection }
        selectionFrame(selected, hu)?.let { frame ->
            val reach = 22 * hu
            frame.rotateHandle?.let { r ->
                if (dist(p, r) < reach) {
                    val c = Pt(frame.corners.sumOf { it.x.toDouble() }.toFloat() / 4, frame.corners.sumOf { it.y.toDouble() }.toFloat() / 4)
                    active = Rotate(p, selected, c); return true
                }
            }
            frame.handles.forEachIndexed { i, h ->
                if (dist(p, h) < reach) {
                    val single = frame.single
                    active = when {
                        single is Shape && Geometry.isLine(single) -> LineEnd(p, selected, i)
                        single is Shape || single is Picture -> Resize(p, selected, i)
                        else -> Scale(p, selected, h, frame.corners[(i + 2) % 4])
                    }
                    return true
                }
            }
            val inside = (frame.corners.size == 4 && Geometry.insidePolygon(p, frame.corners)) ||
                selected.any { Geometry.hit(it, p, 10 * hu) }
            if (inside) { active = Move(p, selected); return true }
            state.selection = emptySet()
        }
        if (eraserTip) { active = Erase(p); erase(p); return true }
        val draws = stylus || fingerDrawing
        when (tools.tool) {
            Tool.Shape -> { active = NewShape(p); return true }
            Tool.Select -> if (draws) { active = Lasso(p); state.lasso = listOf(p); return true }
            else -> Unit
        }
        if (!draws) {
            val hit = Geometry.topAt(state.items, p, 6 * hu, strokes = tools.tool == Tool.Select) ?: return false
            state.selection = setOf(hit.id)
            active = Move(p, listOf(hit))
            return true
        }
        when (tools.tool) {
            Tool.Pen -> { active = Ink(false); addPoint(p, if (stylus) pressure else 0f) }
            Tool.Highlighter -> { active = Ink(true); addPoint(p, 0.5f) }
            Tool.Eraser -> { active = Erase(p); erase(p) }
            else -> return false
        }
        return true
    }

    private fun addPoint(p: Pt, pressure: Float) {
        val n = state.live.size / 3
        if (n > 0 && dist(p, Pt(state.live[n * 3 - 3], state.live[n * 3 - 2])) < 0.4f) return
        state.live.add(p.x); state.live.add(p.y); state.live.add(pressure)
    }

    private fun erase(p: Pt) {
        val a = active as? Erase ?: return
        val radius = 10 * hu
        // Sample along the movement so fast swipes do not skip strokes.
        val steps = max(1, (dist(a.last, p) / (radius / 2)).roundToInt())
        var items = state.items
        for (s in 1..steps) {
            val t = s / steps.toFloat()
            val q = Pt(a.last.x + (p.x - a.last.x) * t, a.last.y + (p.y - a.last.y) * t)
            items = if (tools.partialEraser) Geometry.erasePartial(items, q, radius) else Geometry.eraseWhole(items, q, radius)
        }
        a.last = p
        if (items != state.items) {
            if (!a.started) { state.begin(); a.started = true }
            state.update(items)
        }
    }

    private fun replace(updated: List<Item>) {
        val byId = updated.associateBy { it.id }
        state.update(state.items.map { byId[it.id] ?: it })
    }

    fun move(p: Pt, pressure: Float, stylus: Boolean) {
        when (val a = active) {
            null -> Unit
            is Ink -> addPoint(p, if (a.highlighter) 0.5f else if (stylus) pressure else 0f)
            is Erase -> erase(p)
            is Lasso -> state.lasso = state.lasso + p
            is NewShape -> {
                if (!a.started) {
                    if (dist(a.start, p) < 4 * hu) return
                    state.begin(); a.started = true
                }
                val kind = tools.shape
                val shape = if (kind == ShapeKind.Line || kind == ShapeKind.Arrow) {
                    Shape(a.id, kind, a.start.x, a.start.y, p.x - a.start.x, p.y - a.start.y, color = tools.penColor, width = tools.penWidth)
                } else {
                    Shape(a.id, kind, min(a.start.x, p.x), min(a.start.y, p.y), abs(p.x - a.start.x), abs(p.y - a.start.y), color = tools.penColor, width = tools.penWidth)
                }
                state.update(state.items.filter { it.id != a.id } + shape)
            }
            is Transform -> {
                if (!a.started) {
                    if (dist(a.start, p) < 2 * hu) return
                    state.begin(); a.started = true
                }
                transform(a, p)
            }
        }
    }

    private fun transform(a: Transform, p: Pt) {
        when (a) {
            is Move -> replace(a.originals.map { Geometry.translate(it, p.x - a.start.x, p.y - a.start.y) })
            is Scale -> {
                val s = (dist(p, a.fixed) / dist(a.corner, a.fixed).coerceAtLeast(1f)).coerceIn(0.05f, 20f)
                replace(a.originals.map { Geometry.scale(it, s, a.fixed) })
            }
            is Resize -> {
                val item = a.originals.single()
                replace(listOf(when (item) {
                    is Shape -> Geometry.resizeBox(item.x, item.y, item.w, item.h, item.rotation, a.corner, p, keepAspect = false)
                        .let { r -> item.copy(x = r[0], y = r[1], w = r[2], h = r[3]) }
                    is Picture -> Geometry.resizeBox(item.x, item.y, item.w, item.h, item.rotation, a.corner, p, keepAspect = true)
                        .let { r -> item.copy(x = r[0], y = r[1], w = r[2], h = r[3]) }
                    else -> item
                }))
            }
            is LineEnd -> {
                val s = a.originals.single() as Shape
                val end = Pt(s.x + s.w, s.y + s.h)
                replace(listOf(if (a.end == 0) s.copy(x = p.x, y = p.y, w = end.x - p.x, h = end.y - p.y) else s.copy(w = p.x - s.x, h = p.y - s.y)))
            }
            is Rotate -> {
                var delta = Geometry.angle(a.center, p) - Geometry.angle(a.center, a.start)
                // Snap to 45° steps when close.
                val step = (Math.PI / 4).toFloat()
                val snapped = (delta / step).roundToInt() * step
                if (abs(delta - snapped) < 0.06f) delta = snapped
                replace(a.originals.map { Geometry.rotate(it, delta, a.center) })
            }
        }
    }

    fun end() {
        when (val a = active) {
            is Ink -> {
                if (state.live.size >= 3) {
                    val points = Geometry.compact(Geometry.smooth(state.live.toList()))
                    val stroke = if (a.highlighter) Stroke(points = points, color = tools.highlighterColor, width = tools.highlighterWidth, highlighter = true)
                    else Stroke(points = points, color = tools.penColor, width = tools.penWidth, pressure = tools.pressure.value)
                    state.commit(state.items + stroke)
                }
                state.live.clear()
            }
            is Lasso -> {
                val path = state.lasso
                val length = path.zipWithNext { x, y -> dist(x, y) }.sum()
                state.selection = if (length < 12 * hu) {
                    Geometry.topAt(state.items, a.start, 8 * hu)?.let { setOf(it.id) } ?: emptySet()
                } else Geometry.lasso(state.items, path)
                state.lasso = emptyList()
            }
            is NewShape -> if (a.started) {
                val shape = state.items.firstOrNull { it.id == a.id } as? Shape
                if (shape != null && max(abs(shape.w), abs(shape.h)) < 6f) state.update(state.items.filter { it.id != a.id })
                else state.selection = setOf(a.id)
            }
            else -> Unit
        }
        active = null
    }

    fun cancel() {
        if (active is Ink) state.live.clear()
        if (active is Lasso) state.lasso = emptyList()
        active = null
    }
}

/** Routes page pointer input to [controller] before the text field sees it. */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.canvasInput(controller: CanvasController, scale: () -> Float): Modifier =
    motionEventSpy { controller.lastEvent = it }.pointerInput(controller) {
    var lastStylusUp = 0L
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val s = scale()
        fun pt(o: Offset) = Pt(o.x / s, o.y / s)
        val stylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
        if (!stylus && down.uptimeMillis - lastStylusUp < 400) {
            // A palm landing just after the pen lifts: swallow the whole touch.
            down.consume()
            do {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                e.changes.forEach { it.consume() }
            } while (e.changes.any { it.pressed })
            return@awaitEachGesture
        }
        if (!controller.begin(pt(down.position), stylus, down.type == PointerType.Eraser, down.pressure)) return@awaitEachGesture
        down.consume()
        if (stylus && controller.penInk) {
            controller.wet = controller.lastEvent?.let { WetInk.start(it, controller.liveArgb, controller.liveWidth * s) } ?: false
        }
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            event.changes.forEach { it.consume() } // Rejects palms and other fingers meanwhile.
            if (change == null) {
                if (controller.wet) WetInk.cancel(controller.lastEvent)
                controller.wet = false
                controller.cancel(); break
            }
            change.historical.forEach { controller.move(pt(it.position), change.pressure, stylus) }
            if (change.pressed) {
                controller.move(pt(change.position), change.pressure, stylus)
                if (controller.wet) controller.lastEvent?.let { WetInk.move(it) }
            } else {
                if (controller.wet) controller.lastEvent?.let { WetInk.finish(it) }
                controller.wet = false
                controller.end()
                if (stylus) lastStylusUp = change.uptimeMillis
                break
            }
        }
    }
}

/** Two-finger pinch zoom; ignored while a stylus is down. */
fun Modifier.pinchZoom(onZoom: (zoom: Float, centroid: Offset) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2 && pressed.all { it.type == PointerType.Touch }) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) onZoom(zoom, event.calculateCentroid())
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
