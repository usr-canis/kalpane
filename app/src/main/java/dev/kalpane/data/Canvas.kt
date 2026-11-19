package dev.kalpane.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Logical page width. Text and drawings share these units on every device. */
const val PAGE_WIDTH = 800f

fun newId(): String = UUID.randomUUID().toString().take(12)

@Serializable
enum class ShapeKind { Line, Arrow, Rectangle, Ellipse }

/** Everything drawn on a page. List order is z-order (last is on top). */
@Serializable
sealed interface Item { val id: String }

/** Points are flattened as x, y, pressure triples. A null colour follows the theme's ink colour. */
@Serializable @SerialName("stroke")
data class Stroke(
    override val id: String = newId(),
    val points: List<Float>,
    val color: Int? = null,
    val width: Float = 2.5f,
    val highlighter: Boolean = false,
    /** How strongly pen pressure changes the width: 0 is none, 1 is strong. */
    val pressure: Float = 0.65f,
) : Item {
    val count get() = points.size / 3
    fun x(i: Int) = points[i * 3]
    fun y(i: Int) = points[i * 3 + 1]
    fun p(i: Int) = points[i * 3 + 2]
}

/**
 * Rectangles and ellipses use a box rotated about its centre. Lines and arrows run from
 * (x, y) to (x + w, y + h) and are never rotated; their end points move instead.
 */
@Serializable @SerialName("shape")
data class Shape(
    override val id: String = newId(),
    val kind: ShapeKind,
    val x: Float, val y: Float, val w: Float, val h: Float,
    val rotation: Float = 0f,
    val color: Int? = null,
    val width: Float = 2.5f,
) : Item

@Serializable @SerialName("image")
data class Picture(
    override val id: String = newId(),
    val asset: String,
    val x: Float, val y: Float, val w: Float, val h: Float,
    val rotation: Float = 0f,
) : Item

@Serializable
enum class Paper { Plain, Ruled, Dots, Grid }

/** Per-page text and paper settings; null values follow the defaults in Settings. */
@Serializable
data class PageStyle(val font: String? = null, val size: Float? = null, val paper: Paper? = null)

@Serializable
data class CanvasDoc(
    val version: Int = 1,
    val width: Float = PAGE_WIDTH,
    val style: PageStyle = PageStyle(),
    val items: List<Item> = emptyList(),
)

val canvasJson = Json { ignoreUnknownKeys = true; encodeDefaults = false; classDiscriminator = "type" }

data class Pt(val x: Float, val y: Float) {
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    fun rotate(angle: Float, c: Pt): Pt {
        val s = sin(angle); val k = cos(angle)
        val dx = x - c.x; val dy = y - c.y
        return Pt(c.x + dx * k - dy * s, c.y + dx * s + dy * k)
    }
}

data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val center get() = Pt((left + right) / 2, (top + bottom) / 2)
    val width get() = right - left
    val height get() = bottom - top
    fun union(o: Box) = Box(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))
    fun contains(p: Pt, pad: Float = 0f) = p.x in left - pad..right + pad && p.y in top - pad..bottom + pad
}

object Geometry {
    fun isLine(item: Item) = item is Shape && (item.kind == ShapeKind.Line || item.kind == ShapeKind.Arrow)

    /** Corners of a rotated box item, clockwise from top-left. */
    fun corners(x: Float, y: Float, w: Float, h: Float, rotation: Float): List<Pt> {
        val c = Pt(x + w / 2, y + h / 2)
        return listOf(Pt(x, y), Pt(x + w, y), Pt(x + w, y + h), Pt(x, y + h)).map { it.rotate(rotation, c) }
    }

    fun bounds(item: Item): Box = when (item) {
        is Stroke -> {
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
            for (i in 0 until item.count) {
                l = min(l, item.x(i)); r = max(r, item.x(i)); t = min(t, item.y(i)); b = max(b, item.y(i))
            }
            val pad = item.width / 2
            if (item.count == 0) Box(0f, 0f, 0f, 0f) else Box(l - pad, t - pad, r + pad, b + pad)
        }
        is Shape -> if (isLine(item)) {
            Box(min(item.x, item.x + item.w), min(item.y, item.y + item.h), max(item.x, item.x + item.w), max(item.y, item.y + item.h))
        } else boxOf(corners(item.x, item.y, item.w, item.h, item.rotation))
        is Picture -> boxOf(corners(item.x, item.y, item.w, item.h, item.rotation))
    }

    fun boundsOf(items: Collection<Item>): Box? = items.map(::bounds).reduceOrNull { a, b -> a.union(b) }

    private fun boxOf(points: List<Pt>) =
        Box(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })

    fun distanceToSegment(p: Pt, a: Pt, b: Pt): Float {
        val dx = b.x - a.x; val dy = b.y - a.y
        val len = dx * dx + dy * dy
        val t = if (len == 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /** Point in the item's unrotated frame. */
    fun toLocal(p: Pt, x: Float, y: Float, w: Float, h: Float, rotation: Float) =
        p.rotate(-rotation, Pt(x + w / 2, y + h / 2))

    fun hit(item: Item, p: Pt, tolerance: Float): Boolean = when (item) {
        is Stroke -> {
            val reach = tolerance + item.width / 2
            if (item.count == 1) hypot(p.x - item.x(0), p.y - item.y(0)) <= reach
            else (0 until item.count - 1).any { i ->
                distanceToSegment(p, Pt(item.x(i), item.y(i)), Pt(item.x(i + 1), item.y(i + 1))) <= reach
            }
        }
        is Shape -> if (isLine(item)) {
            distanceToSegment(p, Pt(item.x, item.y), Pt(item.x + item.w, item.y + item.h)) <= tolerance + item.width / 2
        } else {
            val l = toLocal(p, item.x, item.y, item.w, item.h, item.rotation)
            Box(item.x, item.y, item.x + item.w, item.y + item.h).contains(l, tolerance)
        }
        is Picture -> {
            val l = toLocal(p, item.x, item.y, item.w, item.h, item.rotation)
            Box(item.x, item.y, item.x + item.w, item.y + item.h).contains(l, tolerance)
        }
    }

    /** Topmost item under [p]; strokes are only hit when [strokes] is true. */
    fun topAt(items: List<Item>, p: Pt, tolerance: Float, strokes: Boolean = true): Item? =
        items.lastOrNull { (strokes || it !is Stroke) && hit(it, p, tolerance) }

    /** Removes the parts of strokes within [radius] of [p]; whole shapes are removed when hit. */
    fun erasePartial(items: List<Item>, p: Pt, radius: Float): List<Item> = items.flatMap { item ->
        when {
            item is Stroke && hit(item, p, radius) -> {
                val runs = ArrayList<List<Float>>()
                var run = ArrayList<Float>()
                for (i in 0 until item.count) {
                    if (hypot(item.x(i) - p.x, item.y(i) - p.y) <= radius + item.width / 2) {
                        if (run.isNotEmpty()) { runs += run; run = ArrayList() }
                    } else {
                        run.add(item.x(i)); run.add(item.y(i)); run.add(item.p(i))
                    }
                }
                if (run.isNotEmpty()) runs += run
                runs.filter { it.size >= 6 }.mapIndexed { n, pts -> item.copy(id = if (n == 0) item.id else newId(), points = pts) }
            }
            item is Shape && hit(item, p, radius) -> emptyList()
            else -> listOf(item)
        }
    }

    fun eraseWhole(items: List<Item>, p: Pt, radius: Float): List<Item> =
        items.filterNot { (it is Stroke || it is Shape) && hit(it, p, radius) }

    fun insidePolygon(p: Pt, poly: List<Pt>): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    /** Items enclosed by a lasso: most of a stroke's points, or a shape or image's centre. */
    fun lasso(items: List<Item>, poly: List<Pt>): Set<String> {
        if (poly.size < 3) return emptySet()
        return items.filter { item ->
            when (item) {
                is Stroke -> (0 until item.count).count { insidePolygon(Pt(item.x(it), item.y(it)), poly) } * 2 > item.count
                else -> insidePolygon(bounds(item).center, poly)
            }
        }.map { it.id }.toSet()
    }

    fun translate(item: Item, dx: Float, dy: Float): Item = when (item) {
        is Stroke -> item.copy(points = item.points.mapIndexed { i, v -> when (i % 3) { 0 -> v + dx; 1 -> v + dy; else -> v } })
        is Shape -> item.copy(x = item.x + dx, y = item.y + dy)
        is Picture -> item.copy(x = item.x + dx, y = item.y + dy)
    }

    /** Scales about [o]. Rotated boxes keep their rotation and scale their size. */
    fun scale(item: Item, s: Float, o: Pt): Item {
        fun sx(v: Float) = o.x + (v - o.x) * s
        fun sy(v: Float) = o.y + (v - o.y) * s
        return when (item) {
            is Stroke -> item.copy(points = item.points.mapIndexed { i, v -> when (i % 3) { 0 -> sx(v); 1 -> sy(v); else -> v } })
            is Shape -> {
                val c = Pt(item.x + item.w / 2, item.y + item.h / 2)
                val w = item.w * s; val h = item.h * s
                if (isLine(item)) item.copy(x = sx(item.x), y = sy(item.y), w = w, h = h)
                else item.copy(x = sx(c.x) - w / 2, y = sy(c.y) - h / 2, w = w, h = h)
            }
            is Picture -> {
                val c = Pt(item.x + item.w / 2, item.y + item.h / 2)
                val w = item.w * s; val h = item.h * s
                item.copy(x = sx(c.x) - w / 2, y = sy(c.y) - h / 2, w = w, h = h)
            }
        }
    }

    fun rotate(item: Item, angle: Float, o: Pt): Item = when (item) {
        is Stroke -> {
            val out = item.points.toMutableList()
            for (i in 0 until item.count) {
                val r = Pt(item.x(i), item.y(i)).rotate(angle, o)
                out[i * 3] = r.x; out[i * 3 + 1] = r.y
            }
            item.copy(points = out)
        }
        is Shape -> if (isLine(item)) {
            val a = Pt(item.x, item.y).rotate(angle, o)
            val b = Pt(item.x + item.w, item.y + item.h).rotate(angle, o)
            item.copy(x = a.x, y = a.y, w = b.x - a.x, h = b.y - a.y)
        } else {
            val c = Pt(item.x + item.w / 2, item.y + item.h / 2).rotate(angle, o)
            item.copy(x = c.x - item.w / 2, y = c.y - item.h / 2, rotation = normalize(item.rotation + angle))
        }
        is Picture -> {
            val c = Pt(item.x + item.w / 2, item.y + item.h / 2).rotate(angle, o)
            item.copy(x = c.x - item.w / 2, y = c.y - item.h / 2, rotation = normalize(item.rotation + angle))
        }
    }

    fun normalize(a: Float): Float {
        var r = a % (2 * Math.PI.toFloat())
        if (r < 0) r += 2 * Math.PI.toFloat()
        return r
    }

    fun angle(c: Pt, p: Pt) = atan2(p.y - c.y, p.x - c.x)

    /**
     * Resizes a rotated box by dragging corner [corner] (0..3, clockwise from top-left) to [p],
     * keeping the opposite corner fixed. With [keepAspect] the original proportions are kept.
     */
    fun resizeBox(x: Float, y: Float, w: Float, h: Float, rotation: Float, corner: Int, p: Pt, keepAspect: Boolean, min: Float = 12f): FloatArray {
        val c = Pt(x + w / 2, y + h / 2)
        val local = p.rotate(-rotation, c)
        val fixed = listOf(Pt(x + w, y + h), Pt(x, y + h), Pt(x, y), Pt(x + w, y))[corner]
        var nw = abs(local.x - fixed.x).coerceAtLeast(min)
        var nh = abs(local.y - fixed.y).coerceAtLeast(min)
        if (keepAspect && w > 0 && h > 0) {
            val s = max(nw / w, nh / h)
            nw = w * s; nh = h * s
        }
        val sxSign = if (corner == 1 || corner == 2) 1 else -1
        val sySign = if (corner == 2 || corner == 3) 1 else -1
        // New local centre, then back to page space; the fixed corner stays put.
        val localCenter = Pt(fixed.x + sxSign * nw / 2, fixed.y + sySign * nh / 2)
        val worldFixed = fixed.rotate(rotation, c)
        val offset = Pt(localCenter.x - fixed.x, localCenter.y - fixed.y).rotate(rotation, Pt(0f, 0f))
        val nc = worldFixed + offset
        return floatArrayOf(nc.x - nw / 2, nc.y - nh / 2, nw, nh)
    }

    /** Moving-average smoothing of a stroke's x/y, keeping its end points. */
    fun smooth(points: List<Float>): List<Float> {
        val n = points.size / 3
        if (n < 3) return points
        val out = points.toMutableList()
        for (i in 1 until n - 1) {
            out[i * 3] = (points[(i - 1) * 3] + 2 * points[i * 3] + points[(i + 1) * 3]) / 4
            out[i * 3 + 1] = (points[(i - 1) * 3 + 1] + 2 * points[i * 3 + 1] + points[(i + 1) * 3 + 1]) / 4
        }
        return out
    }

    /** Rounds coordinates to 0.1 units to keep files small. */
    fun compact(points: List<Float>): List<Float> = points.mapIndexed { i, v ->
        if (i % 3 == 2) (v * 100).toInt() / 100f else (v * 10).toInt() / 10f
    }
}
