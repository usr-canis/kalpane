package dev.kalpane.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownEditTest {
    @Test fun enterContinuesLists() {
        assertEquals(Edit("- a\n- ", 6), MarkdownEdit.onNewline(Edit("- a\n", 4)))
        assertEquals(Edit("1. a\n2. ", 8), MarkdownEdit.onNewline(Edit("1. a\n", 5)))
        assertEquals(Edit("  - [x] a\n  - [ ] ", 18), MarkdownEdit.onNewline(Edit("  - [x] a\n", 10)))
        assertNull(MarkdownEdit.onNewline(Edit("plain\n", 6)))
    }

    @Test fun enterOnEmptyItemEndsList() {
        assertEquals(Edit("- a\n", 4), MarkdownEdit.onNewline(Edit("- a\n- \n", 7)))
    }

    @Test fun wrapAndUnwrap() {
        val wrapped = MarkdownEdit.toggleWrap(Edit("say hi", 4, 6), "**")
        assertEquals(Edit("say **hi**", 6, 8), wrapped)
        assertEquals(Edit("say hi", 4, 6), MarkdownEdit.toggleWrap(wrapped, "**"))
        assertEquals(Edit("****", 2), MarkdownEdit.toggleWrap(Edit("", 0), "**"))
    }

    @Test fun headingCycles() {
        val h1 = MarkdownEdit.cycleHeading(Edit("Title", 5))
        assertEquals("# Title", h1.text)
        assertEquals(7, h1.start)
        assertEquals("## Title", MarkdownEdit.cycleHeading(h1).text)
        assertEquals("Title", MarkdownEdit.cycleHeading(Edit("### Title", 0)).text)
    }

    @Test fun listToggleOnSelection() {
        val on = MarkdownEdit.toggleList(Edit("a\nb", 0, 3), ListKind.Ordered)
        assertEquals("1. a\n2. b", on.text)
        assertEquals("a\nb", MarkdownEdit.toggleList(Edit(on.text, 0, on.text.length), ListKind.Ordered).text)
        assertEquals("- [ ] a", MarkdownEdit.toggleList(Edit("- a", 0), ListKind.Task).text)
    }

    @Test fun taskToggleAndQuote() {
        assertEquals("x\n- [x] a", MarkdownEdit.toggleTask("x\n- [ ] a", 2))
        assertNull(MarkdownEdit.toggleTask("- a", 0))
        assertEquals("> a", MarkdownEdit.toggleQuote(Edit("a", 0)).text)
        assertEquals("a", MarkdownEdit.toggleQuote(Edit("> a", 0)).text)
    }

    @Test fun linkInsertion() {
        assertEquals(Edit("[]()", 1), MarkdownEdit.link(Edit("", 0)))
        assertEquals(Edit("[go]()", 5), MarkdownEdit.link(Edit("go", 0, 2)))
    }

    @Test fun codeBlockKeepsIndentAndWraps() {
        assertEquals(Edit("```\n    a\n    ", 14), MarkdownEdit.onNewline(Edit("```\n    a\n", 10)))
        assertEquals(Edit("```\nx\n```", 5), MarkdownEdit.codeBlock(Edit("x", 1)))
        assertEquals("    a", MarkdownEdit.indent(Edit("a", 0)).text)
        assertEquals("a", MarkdownEdit.indent(Edit("    a", 0), outdent = true).text)
    }

    @Test fun headingLevels() {
        assertEquals("###### Note", MarkdownEdit.setHeading(Edit("- Note", 0), 6).text)
        assertEquals("Note", MarkdownEdit.setHeading(Edit("## Note", 0), 0).text)
        assertEquals(4, MarkdownEdit.headingLevel("x\n#### y", 3))
    }
}
