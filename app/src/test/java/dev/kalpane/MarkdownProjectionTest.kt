package dev.kalpane

import dev.kalpane.data.projectMarkdown
import dev.kalpane.data.Markdown
import org.junit.Assert.*
import org.junit.Test

class MarkdownProjectionTest {
    @Test fun projectsFormattingAndKeepsCursorOffsetsValid() {
        val source="# Heading\n**bold** *italic* ~~gone~~ `code` [site](https://example.com)\n\\*literal\\*\n- [x] done\n```\n**raw**\n```"
        val p=projectMarkdown(source)
        assertEquals("Heading\nbold italic gone code site\n*literal*\n☑ done\n\n**raw**\n",p.text)
        assertEquals(source.length+1,p.toVisual.size)
        assertEquals(p.text.length+1,p.toSource.size)
        assertTrue(p.toVisual.toList().zipWithNext().all {(a,b)->a<=b})
        assertTrue(p.toSource.toList().zipWithNext().all {(a,b)->a<=b})
        assertTrue(p.toVisual.all {it in 0..p.text.length})
        assertTrue(p.toSource.all {it in 0..source.length})
    }
    @Test fun projectionMatchesSavedMarkdownRenderingForShortcutsAndEscapes() {
        val samples=listOf("# - [ ] item", "\\# literal", "[link](https://example.com) after", "**unfinished", "- [ ]", "text\n", "```kotlin\nval x = 1\n```")
        samples.forEach {source->
            val projected=projectMarkdown(source)
            val expected=Markdown.parse(source).joinToString("\n") {block->(block.checked?.let {if(it) "☑ " else "☐ "} ?: "")+block.text}
            assertEquals(source,expected,projected.text)
        }
    }
}
