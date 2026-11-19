package dev.kalpane.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTextTest {
    @Test fun titleSkipsMarkupAndBlankLines() {
        assertEquals("Hello world", MarkdownText.title("\n\n# **Hello** _world_\nbody"))
        assertEquals("Task", MarkdownText.title("- [ ] Task"))
        assertEquals("", MarkdownText.title("   \n"))
    }

    @Test fun previewIsPlainTextAfterTitle() {
        val text = "# Title\n\nSee [docs](https://x.y) and `code`.\n![](assets/a.svg)\n---\n> quote"
        assertEquals("See docs and code. quote", MarkdownText.preview(text))
    }

    @Test fun safeNameStripsPathCharacters() {
        assertEquals("a b c", safeName(" a/b\\c "))
        assertEquals("Untitled", safeName("..."))
        assertEquals("hidden", safeName(".hidden"))
    }
}
