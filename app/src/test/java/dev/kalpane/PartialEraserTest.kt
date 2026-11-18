package dev.kalpane

import dev.kalpane.data.*
import dev.kalpane.ink.eraseParts
import org.junit.Assert.*
import org.junit.Test

class PartialEraserTest {
    @Test fun erasingMiddleSplitsStrokeAndPreservesInkAttributes() {
        val stroke=Layer(kind=LayerKind.Ink,color=0xff123456.toInt(),penWidth=3f,points=listOf(InkPoint(0f,0f,0,pressure=.5f),InkPoint(100f,0f,100,pressure=.8f)))
        val parts=eraseParts(stroke,listOf(50f to 0f),8f)
        assertEquals(2,parts.size)
        assertTrue(parts.first().points.last().x<50f)
        assertTrue(parts.last().points.first().x>50f)
        assertTrue(parts.all {it.color==stroke.color && it.penWidth==stroke.penWidth && it.id!=stroke.id})
        assertEquals(listOf(stroke),eraseParts(stroke,listOf(50f to 100f),8f))
    }
}
