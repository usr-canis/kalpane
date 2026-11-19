package dev.kalpane.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as StrokeStyle
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.kalpane.data.Geometry
import dev.kalpane.data.Item
import dev.kalpane.data.Picture
import dev.kalpane.data.Pt
import dev.kalpane.data.Shape
import dev.kalpane.data.ShapeKind
import dev.kalpane.data.Stroke
import java.util.IdentityHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Width multiplier for a pressure reading; readings of 0 mean the device reported none. */
fun pressureCurve(p: Float) = if (p <= 0f) 1f else (0.3f + 1.2f * p).coerceIn(0.25f, 1.5f)

/**
 * Filled outline of a stroke, with round end caps. [sensitivity] (0..1) sets how much pressure
 * changes the width; pressure is smoothed so lines swell and taper without blobs.
 */
fun strokeOutline(points: List<Float>, width: Float, highlighter: Boolean, sensitivity: Float = 0.65f): Path {
    val n = points.size / 3
    val path = Path()
    if (n == 0) return path
    fun x(i: Int) = points[i * 3]
    fun y(i: Int) = points[i * 3 + 1]
    val widths = FloatArray(n)
    if (highlighter || sensitivity <= 0f) {
        widths.fill(width)
    } else {
        // Smooth pressure forwards and backwards, then apply the curve.
        val p = FloatArray(n) { points[it * 3 + 2] }
        for (i in 1 until n) p[i] = p[i - 1] * 0.6f + p[i] * 0.4f
        for (i in n - 2 downTo 0) p[i] = p[i + 1] * 0.4f + p[i] * 0.6f
        val taper = minOf(4, n / 4)
        for (i in 0 until n) {
            var w = width * (1f - sensitivity + sensitivity * pressureCurve(p[i]))
            if (taper > 0) {
                val edge = minOf(i, n - 1 - i)
                if (edge < taper) w *= 0.55f + 0.45f * edge / taper
            }
            widths[i] = w
        }
    }
    fun w(i: Int) = widths[i]
    if (n == 1) {
        val r = w(0) / 2
        path.addOval(Rect(x(0) - r, y(0) - r, x(0) + r, y(0) + r))
        return path
    }
    val left = ArrayList<Offset>(n); val right = ArrayList<Offset>(n)
    for (i in 0 until n) {
        val a = maxOf(i - 1, 0); val b = minOf(i + 1, n - 1)
        var dx = x(b) - x(a); var dy = y(b) - y(a)
        val len = hypot(dx, dy)
        if (len < 1e-4f) { dx = 1f; dy = 0f } else { dx /= len; dy /= len }
        val h = w(i) / 2
        left += Offset(x(i) - dy * h, y(i) + dx * h)
        right += Offset(x(i) + dy * h, y(i) - dx * h)
    }
    path.moveTo(left[0].x, left[0].y)
    for (i in 1 until n) {
        val m = (left[i - 1] + left[i]) / 2f
        path.quadraticTo(left[i - 1].x, left[i - 1].y, m.x, m.y)
    }
    path.lineTo(left[n - 1].x, left[n - 1].y)
    path.lineTo(right[n - 1].x, right[n - 1].y)
    for (i in n - 2 downTo 0) {
        val m = (right[i + 1] + right[i]) / 2f
        path.quadraticTo(right[i + 1].x, right[i + 1].y, m.x, m.y)
    }
    path.lineTo(right[0].x, right[0].y)
    path.close()
    if (!highlighter) {
        for (i in listOf(0, n - 1)) {
            val r = w(i) / 2
            path.addOval(Rect(x(i) - r, y(i) - r, x(i) + r, y(i) + r))
        }
    }
    return path
}

/** Caches stroke outlines by instance; items are immutable. */
class PathCache {
    private val map = IdentityHashMap<Stroke, Path>()
    fun get(s: Stroke): Path {
        if (map.size > 4000) map.clear()
        return map.getOrPut(s) { strokeOutline(s.points, s.width, s.highlighter, s.pressure) }
    }
}

fun itemColor(color: Int?, ink: Color) = color?.let { Color(it) } ?: ink

fun DrawScope.drawItem(item: Item, ink: Color, cache: PathCache, images: Map<String, ImageBitmap>, placeholder: Color) {
    when (item) {
        is Stroke -> if (item.highlighter) {
            drawHighlighter(item.points, item.width, itemColor(item.color, ink).copy(alpha = 0.38f))
        } else {
            drawPath(cache.get(item), itemColor(item.color, ink))
        }
        is Shape -> drawShape(item, itemColor(item.color, ink))
        is Picture -> {
            val c = Offset(item.x + item.w / 2, item.y + item.h / 2)
            rotateRad(item.rotation, c) {
                val image = images[item.asset]
                if (image != null) {
                    drawImage(
                        image,
                        dstOffset = IntOffset(item.x.toInt(), item.y.toInt()),
                        dstSize = IntSize(item.w.toInt().coerceAtLeast(1), item.h.toInt().coerceAtLeast(1)),
                    )
                } else {
                    drawRect(placeholder, Offset(item.x, item.y), Size(item.w, item.h))
                }
            }
        }
    }
}

fun DrawScope.drawHighlighter(points: List<Float>, width: Float, color: Color) {
    val n = points.size / 3
    if (n == 0) return
    val path = Path().apply {
        moveTo(points[0], points[1])
        if (n == 1) lineTo(points[0] + 0.1f, points[1])
        for (i in 1 until n) lineTo(points[i * 3], points[i * 3 + 1])
    }
    drawPath(path, color, style = StrokeStyle(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

fun DrawScope.drawShape(s: Shape, color: Color) {
    val style = StrokeStyle(width = s.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (s.kind) {
        ShapeKind.Line, ShapeKind.Arrow -> {
            val a = Offset(s.x, s.y); val b = Offset(s.x + s.w, s.y + s.h)
            drawLine(color, a, b, s.width, StrokeCap.Round)
            if (s.kind == ShapeKind.Arrow) {
                val angle = atan2(b.y - a.y, b.x - a.x)
                val len = 12f + s.width * 3
                for (side in listOf(-1, 1)) {
                    val t = angle + Math.PI.toFloat() + side * 0.5f
                    drawLine(color, b, Offset(b.x + cos(t) * len, b.y + sin(t) * len), s.width, StrokeCap.Round)
                }
            }
        }
        ShapeKind.Rectangle, ShapeKind.Ellipse -> rotateRad(s.rotation, Offset(s.x + s.w / 2, s.y + s.h / 2)) {
            if (s.kind == ShapeKind.Rectangle) drawRect(color, Offset(s.x, s.y), Size(s.w, s.h), style = style)
            else drawOval(color, Offset(s.x, s.y), Size(s.w, s.h), style = style)
        }
    }
}

/** Selection outline and handles. [hu] is one screen dp in page units. */
fun DrawScope.drawSelection(sel: SelectionFrame, accent: Color, surface: Color, hu: Float) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(6 * hu, 4 * hu))
    val outline = StrokeStyle(width = 1.5f * hu, pathEffect = dash)
    if (sel.corners.size == 4) {
        val path = Path().apply {
            moveTo(sel.corners[0].x, sel.corners[0].y)
            sel.corners.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, accent, style = outline)
    }
    sel.rotateHandle?.let { r ->
        val top = Offset((sel.corners[0].x + sel.corners[1].x) / 2, (sel.corners[0].y + sel.corners[1].y) / 2)
        drawLine(accent, top, Offset(r.x, r.y), 1.5f * hu)
    }
    (sel.handles + listOfNotNull(sel.rotateHandle)).forEach { h ->
        drawCircle(surface, 7 * hu, Offset(h.x, h.y))
        drawCircle(accent, 7 * hu, Offset(h.x, h.y), style = StrokeStyle(width = 2 * hu))
    }
}

fun DrawScope.drawLasso(points: List<Pt>, accent: Color, hu: Float) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, accent, style = StrokeStyle(width = 1.5f * hu, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6 * hu, 4 * hu))))
}

/** Geometry of the current selection in page units. */
data class SelectionFrame(
    val corners: List<Pt>,
    /** Corner handles (0..3 clockwise from top-left) or the two line end points. */
    val handles: List<Pt>,
    val rotateHandle: Pt?,
    val single: Item?,
)

fun selectionFrame(selected: List<Item>, hu: Float): SelectionFrame? {
    if (selected.isEmpty()) return null
    val single = selected.singleOrNull()
    if (single is Shape && Geometry.isLine(single)) {
        val a = Pt(single.x, single.y); val b = Pt(single.x + single.w, single.y + single.h)
        return SelectionFrame(emptyList(), listOf(a, b), null, single)
    }
    val corners = when (single) {
        is Shape -> Geometry.corners(single.x, single.y, single.w, single.h, single.rotation)
        is Picture -> Geometry.corners(single.x, single.y, single.w, single.h, single.rotation)
        else -> {
            val b = Geometry.boundsOf(selected)!!
            val pad = 6 * hu
            listOf(Pt(b.left - pad, b.top - pad), Pt(b.right + pad, b.top - pad), Pt(b.right + pad, b.bottom + pad), Pt(b.left - pad, b.bottom + pad))
        }
    }
    val topMid = Pt((corners[0].x + corners[1].x) / 2, (corners[0].y + corners[1].y) / 2)
    val center = Pt(corners.sumOf { it.x.toDouble() }.toFloat() / 4, corners.sumOf { it.y.toDouble() }.toFloat() / 4)
    val dx = topMid.x - center.x; val dy = topMid.y - center.y
    val len = hypot(dx, dy).coerceAtLeast(1f)
    val rotate = Pt(topMid.x + dx / len * 28 * hu, topMid.y + dy / len * 28 * hu)
    return SelectionFrame(corners, corners, rotate, single)
}
