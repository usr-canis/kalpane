package dev.kalpane.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DiffTest {
    @Test fun unifiedHunksWithContext() {
        val old = (1..10).joinToString("\n") { "line $it" }
        val new = old.replace("line 5", "line five")
        val d = Diff.lines(old, new, context = 1)
        assertEquals("@@ -4,3 +4,3 @@", d.first().text)
        assertEquals(listOf(' ', '-', '+', ' '), d.drop(1).map { it.kind })
        assertEquals(1 to 1, Diff.stats(d))
    }

    @Test fun noChangesIsEmptyAndAddsCount() {
        assertTrue(Diff.lines("a\nb", "a\nb").isEmpty())
        assertEquals(2 to 0, Diff.stats(Diff.lines("", "x\ny")))
    }

    @Test fun canvasChangesByItemId() {
        val a = Stroke(id = "a", points = listOf(0f, 0f, 1f))
        val b = Stroke(id = "b", points = listOf(0f, 0f, 1f))
        val moved = a.copy(points = listOf(5f, 5f, 1f))
        assertEquals(CanvasDiff(added = 1, removed = 1, changed = 1),
            Diff.canvas(listOf(a, b), listOf(moved, Stroke(id = "c", points = listOf(1f, 1f, 1f)))))
    }

    @Test fun linksAndTagsAreParsed() {
        val text = "See [[Work/Plan]] and #idea, not #1 or a#b"
        val spans = MarkdownSpans.parse(text).map { it.kind to text.substring(it.start, it.end) }
        assertTrue(Kind.WikiLink to "Work/Plan" in spans)
        assertTrue(Kind.Tag to "#idea" in spans)
        assertEquals(1, spans.count { it.first == Kind.Tag })
    }

    @Test fun templatesFillDates() {
        val t = Templates.builtIn.first { it.name == "Meeting notes" }
        val filled = Templates.instantiate(t, LocalDateTime.of(2026, 11, 19, 9, 5))
        assertTrue(filled.text.startsWith("# Meeting · 19 Nov 2026"))
    }
}
