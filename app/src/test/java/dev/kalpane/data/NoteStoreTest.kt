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

    @Test fun notesAreMarkdownFilesInNotebookFolders() {
        val s = store()
        val book = s.createNotebook("Work")
        val ref = s.createNote(book)
        s.write(ref, "# Plan\n\nShip it")
        assertEquals("# Plan\n\nShip it", s.file(ref).readText())
        assertEquals("Work/Untitled.md", s.file(ref).relativeTo(s.root).path)
    }

    @Test fun renameToTitleUsesSafeUniqueNames() {
        val s = store()
        val a = s.createNote("Notes")
        s.write(a, "# Ideas: a/b?\nbody")
        assertEquals("Ideas a b", s.renameToTitle(a).name)
        val b = s.createNote("Notes")
        s.write(b, "# Ideas: a/b?")
        assertEquals("Ideas a b 2", s.renameToTitle(b).name)
        // Already matching names are stable.
        assertEquals("Ideas a b 2", s.renameToTitle(NoteRef("Notes", "Ideas a b 2")).name)
    }

    @Test fun kannadaTitlesAreKept() {
        val s = store()
        val ref = s.createNote("Notes")
        s.write(ref, "ಕಲ್ಪನೆ ಟಿಪ್ಪಣಿ")
        assertEquals("ಕಲ್ಪನೆ ಟಿಪ್ಪಣಿ", s.renameToTitle(ref).name)
    }

    @Test fun moveSearchAndDelete() {
        val s = store()
        s.createNotebook("A"); s.createNotebook("B")
        val ref = s.createNote("A", "hello world")
        val moved = s.move(ref, "B")
        assertEquals(listOf(moved), s.search("WORLD").map { it.ref })
        assertTrue(s.search("missing").isEmpty())
        s.delete(moved)
        assertFalse(s.file(moved).exists())
    }

    @Test fun notebookNamesAreUniqueIgnoringCase() {
        val s = store()
        assertEquals("Work", s.createNotebook("Work"))
        assertEquals("work 2", s.createNotebook("work"))
        assertEquals(listOf("Work", "work 2"), s.notebooks())
    }
}
