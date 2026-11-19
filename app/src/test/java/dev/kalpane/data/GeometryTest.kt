package dev.kalpane.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class GeometryTest {
    private val line = Stroke(id = "a", points = (0..10).flatMap { listOf(it * 10f, 0f, 1f) }, width = 2f)
    private fun close(a: Float, b: Float) = assertEquals(a, b, 0.01f)

    @Test fun hitAndBounds() {
        assertTrue(Geometry.hit(line, Pt(50f, 3f), 4f))
        assertFalse(Geometry.hit(line, Pt(50f, 20f), 4f))
        assertEquals(Box(-1f, -1f, 101f, 1f), Geometry.bounds(line))
    }

    @Test fun partialEraseSplitsStroke() {
        val parts = Geometry.erasePartial(listOf(line), Pt(50f, 0f), 5f)
        assertEquals(2, parts.size)
        assertEquals("a", parts[0].id)
        assertTrue(Geometry.eraseWhole(listOf(line), Pt(50f, 0f), 5f).isEmpty())
    }

    @Test fun lassoSelectsEnclosed() {
        val poly = listOf(Pt(-10f, -10f), Pt(110f, -10f), Pt(110f, 10f), Pt(-10f, 10f))
        assertEquals(setOf("a"), Geometry.lasso(listOf(line), poly))
    }

    @Test fun rotateBoxAboutCentreKeepsPosition() {
        val r = Shape(id = "r", kind = ShapeKind.Rectangle, x = 0f, y = 0f, w = 10f, h = 20f)
        val rotated = Geometry.rotate(r, (PI / 2).toFloat(), Pt(5f, 10f)) as Shape
        close(0f, rotated.x); close(0f, rotated.y)
        close((PI / 2).toFloat(), rotated.rotation)
    }

    @Test fun resizeKeepsOppositeCorner() {
        // Drag the bottom-right corner of an unrotated 10x10 box to (20, 30).
        val r = Geometry.resizeBox(0f, 0f, 10f, 10f, 0f, 2, Pt(20f, 30f), keepAspect = false)
        close(0f, r[0]); close(0f, r[1]); close(20f, r[2]); close(30f, r[3])
        val a = Geometry.resizeBox(0f, 0f, 10f, 10f, 0f, 2, Pt(20f, 30f), keepAspect = true)
        close(30f, a[2]); close(30f, a[3])
    }

    @Test fun rotatedResizeKeepsFixedCornerInPlace() {
        val rot = (PI / 2).toFloat()
        val before = Geometry.corners(0f, 0f, 10f, 20f, rot)
        val r = Geometry.resizeBox(0f, 0f, 10f, 20f, rot, 2, Pt(-20f, 30f), keepAspect = false)
        val after = Geometry.corners(r[0], r[1], r[2], r[3], rot)
        close(before[0].x, after[0].x); close(before[0].y, after[0].y)
    }

    @Test fun scaleAndTranslate() {
        val moved = Geometry.translate(line, 5f, 5f) as Stroke
        close(5f, moved.x(0)); close(5f, moved.y(0))
        val scaled = Geometry.scale(line, 2f, Pt(0f, 0f)) as Stroke
        close(200f, scaled.x(10))
    }
}
