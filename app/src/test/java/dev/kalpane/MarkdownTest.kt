package dev.kalpane

import dev.kalpane.data.Markdown
import org.junit.Assert.*
import org.junit.Test

class MarkdownTest {
    @Test fun parsesFormattingAndEscapesWithoutLosingSource() {
        val b=Markdown.parse("# Title\n**bold** *italic* ~~gone~~ `code` [site](https://example.com)\n\\*literal\\*\n- [x] done\n```\n**raw**\n```")
        assertEquals(1,b[0].heading);assertEquals("Title",b[0].text)
        assertEquals(listOf("bold","italic","strike","code","link:https://example.com"),b[1].spans.map {it.kind})
        assertEquals("*literal*",b[2].text);assertEquals(true,b[3].checked)
        assertEquals("**raw**",b[5].text);assertTrue(b[5].code)
        assertEquals("- [ ] done",Markdown.toggleCheckbox("- [x] done",0))
        assertEquals("- [x] done",Markdown.toggleCheckbox("- [ ] done",0))
    }
}
