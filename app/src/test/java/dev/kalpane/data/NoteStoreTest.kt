package dev.kalpane.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NoteStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun store() = NoteStore(tmp.newFolder("notes"))

    @Test fun newNotebookHasOnePage() {
        val s = store()
        val book = s.createNotebook("Work")
        assertEquals(listOf("Page 1"), s.pageNames(book))
        assertEquals(1, s.notebooks().single().pages)
    }

    @Test fun pagesKeepOrderAndMove() {
        val s = store()
        val book = s.createNotebook("A")
        s.createPage(book); s.createPage(book)
        assertEquals(listOf("Page 1", "Page 2", "Page 3"), s.pageNames(book))
        s.movePage(PageRef(book, "Page 3"), -1)
        assertEquals(listOf("Page 1", "Page 3", "Page 2"), s.pageNames(book))
    }

    @Test fun textAndCanvasRoundTrip() {
        val s = store()
        val ref = PageRef(s.createNotebook("A"), "Page 1")
        val items = listOf(
            Stroke(id = "s", points = listOf(1f, 2f, 0.5f, 3f, 4f, 0.5f)),
            Shape(id = "r", kind = ShapeKind.Rectangle, x = 1f, y = 2f, w = 3f, h = 4f, rotation = 0.5f),
            Picture(id = "p", asset = "a".repeat(64) + ".png", x = 0f, y = 0f, w = 10f, h = 10f),
        )
        s.write(ref, PageData("# Hi", CanvasDoc(style = PageStyle("JetBrainsMono", 20f, Paper.Ruled), items = items)))
        val back = s.read(ref)
        assertEquals("# Hi", back.text)
        assertEquals(items, back.canvas.items)
        assertEquals(Paper.Ruled, back.canvas.style.paper)
        assertEquals("# Hi", s.textFile(ref).readText())
    }

    @Test fun renameMovesBothFilesAndKeepsOrder() {
        val s = store()
        val book = s.createNotebook("A")
        s.createPage(book)
        val ref = PageRef(book, "Page 1")
        s.write(ref, PageData("x", CanvasDoc(items = listOf(Stroke(points = listOf(0f, 0f, 1f))))))
        val renamed = s.renamePage(ref, "Ideas")
        assertEquals(listOf("Ideas", "Page 2"), s.pageNames(book))
        assertTrue(s.canvasFile(renamed).exists())
        assertFalse(s.canvasFile(ref).exists())
    }

    @Test fun deletePageRemovesFiles() {
        val s = store()
        val ref = PageRef(s.createNotebook("A"), "Page 1")
        s.deletePage(ref)
        assertTrue(s.pageNames("A").isEmpty())
    }

    @Test fun trashAndRestorePage() {
        val s = store()
        val trash = Trash(tmp.newFolder("trash"))
        val book = s.createNotebook("A")
        s.createPage(book)
        val ref = PageRef(book, "Page 1")
        s.write(ref, PageData("hello", CanvasDoc()))
        trash.trashPage(s, ref)
        assertEquals(listOf("Page 2"), s.pageNames(book))
        val entry = trash.list().single()
        assertEquals("Page 1", trash.restore(s, entry.id))
        assertEquals(listOf("Page 2", "Page 1"), s.pageNames(book))
        assertEquals("hello", s.read(ref).text)
        assertTrue(trash.list().isEmpty())
    }

    @Test fun trashNotebookRestoresUnderFreeName() {
        val s = store()
        val trash = Trash(tmp.newFolder("trash"))
        s.createNotebook("A")
        trash.trashNotebook(s, "A")
        s.createNotebook("A")
        assertEquals("A 2", trash.restore(s, trash.list().single().id))
    }

    @Test fun purgeRemovesOldItems() {
        val s = store()
        val trash = Trash(tmp.newFolder("trash"))
        val book = s.createNotebook("A")
        trash.trashPage(s, PageRef(book, "Page 1"), now = 0L)
        trash.purge(days = 30, now = 31L * 24 * 60 * 60 * 1000)
        assertTrue(trash.list().isEmpty())
    }

    @Test fun searchFindsTextAndRecentOrders() {
        val s = store()
        val book = s.createNotebook("A")
        s.write(PageRef(book, "Page 1"), PageData("# Plan\nShip the **release** today", CanvasDoc()))
        val hit = s.search("RELEASE").single()
        assertEquals("release", hit.snippet.substring(hit.start, hit.start + hit.length))
        assertTrue(s.search("missing").isEmpty())
        assertEquals(PageRef(book, "Page 1"), s.recent(1).single().ref)
    }

    @Test fun linksBacklinksAndTags() {
        val s = store()
        val a = s.createNotebook("A")
        val b = s.createNotebook("B")
        val target = s.createNamedPage(b, "Plan")
        s.write(PageRef(a, "Page 1"), PageData("Go to [[B/Plan]] #todo", CanvasDoc()))
        s.write(target, PageData("#todo #idea", CanvasDoc()))
        assertEquals(target, s.resolveLink(a, "B/Plan"))
        assertEquals(target, s.resolveLink(a, "plan"))
        assertEquals(listOf(PageRef(a, "Page 1")), s.backlinks(target))
        assertEquals(mapOf("#todo" to 2, "#idea" to 1), s.tags())
        assertEquals(1, s.search("#idea").size)
    }
}
