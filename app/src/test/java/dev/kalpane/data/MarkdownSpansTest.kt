package dev.kalpane.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSpansTest {
    private fun kinds(text: String) = MarkdownSpans.parse(text).map { it.kind to text.substring(it.start, it.end) }

    @Test fun headingMarkerIsSyntax() {
        assertEquals(listOf(Kind.Syntax to "## ", Kind.Heading to "Title"), kinds("## Title"))
        assertEquals(2, MarkdownSpans.parse("## Title")[1].level)
    }

    @Test fun inlineEmphasisAndCode() {
        val k = kinds("a **b** *c* ~~d~~ `e*f*`")
        assertTrue(Kind.Bold to "b" in k)
        assertTrue(Kind.Italic to "c" in k)
        assertTrue(Kind.Strike to "d" in k)
        assertTrue(Kind.Code to "e*f*" in k)
        assertTrue(k.none { it.first == Kind.Italic && it.second == "f" })
    }

    @Test fun linksHideTheirUrl() {
        assertEquals(listOf(Kind.Syntax to "[", Kind.Link to "site", Kind.Syntax to "](https://a.b)"), kinds("[site](https://a.b)"))
    }

    @Test fun tasksBulletsQuotesAndRules() {
        val k = kinds("- [x] done\n- item\n> quote\n---")
        assertTrue(Kind.TaskDone to "- [x]" in k)
        assertTrue(Kind.Done to " done" in k)
        assertTrue(Kind.Bullet to "-" in k)
        assertTrue(Kind.Quote to ">" in k)
        assertTrue(Kind.Rule to "---" in k)
    }

    @Test fun codeBlocksAreNotParsedInline() {
        val k = kinds("```\n**x**\n```")
        assertEquals(listOf(Kind.Syntax to "```", Kind.CodeBlock to "**x**", Kind.Syntax to "```"), k)
    }

    @Test fun escapedMarkersStayLiteral() {
        val k = kinds("\\*not italic\\*")
        assertTrue(k.none { it.first == Kind.Italic })
        assertEquals(2, k.count { it.first == Kind.Syntax })
    }

    @Test fun spansStayInsideText() {
        val text = "# a\n- [ ] \n> \n**\n`\n[x](\n~~~~"
        MarkdownSpans.parse(text).forEach { assertTrue(it.start in 0..it.end && it.end <= text.length) }
    }
}
