package dev.kalpane.data

/**
 * Line-based Markdown scanner used for live styling. Every span refers to
 * offsets in the original text; the editor never changes the text length.
 */
enum class Kind {
    /** Markup characters (`#`, `**`, `](url)`...). Hidden away from the cursor. */
    Syntax,
    Heading, Bold, Italic, Strike, Code, CodeBlock, Link, Image, Quote,
    /** The list marker character; shown as a bullet away from the cursor. */
    Bullet,
    /** `- [ ]` or `- [x]` (5 chars). Shown as a checkbox away from the cursor. */
    TaskOpen, TaskDone,
    /** Text of a completed task. */
    Done,
    OrderedMarker, Rule,
}

data class MdSpan(val start: Int, val end: Int, val kind: Kind, val level: Int = 0)

/** Start and end offsets of one line, excluding the newline. */
data class MdLine(val start: Int, val end: Int)

object MarkdownSpans {
    private val heading = Regex("""^(#{1,6})\s+""")
    private val task = Regex("""^(\s*)([-*+]) \[([ xX])](\s|$)""")
    private val bullet = Regex("""^(\s*)([-*+])\s+""")
    private val ordered = Regex("""^(\s*)(\d+[.)])\s+""")
    private val quote = Regex("""^(\s*)(>)\s?""")
    private val rule = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    private val fence = Regex("""^\s*```""")

    private val escape = Regex("""\\([\\`*_{}\[\]()#+\-.!~>|])""")
    private val codeSpan = Regex("""(`+)([^`]|[^`].*?[^`])\1""")
    private val image = Regex("""!\[([^\]]*)]\(([^)\s]*)\)""")
    private val link = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")
    private val bold = Regex("""(\*\*|__)(?=\S)(.+?)(?<=\S)\1""")
    private val italicStar = Regex("""(?<![*\w])\*(?=[^\s*])(.+?)(?<=[^\s*])\*(?![*\w])""")
    private val italicUnderscore = Regex("""(?<![_\w])_(?=[^\s_])(.+?)(?<=[^\s_])_(?![_\w])""")
    private val strike = Regex("""~~(?=\S)(.+?)(?<=\S)~~""")

    fun lines(text: String): List<MdLine> {
        val out = ArrayList<MdLine>()
        var start = 0
        while (true) {
            val nl = text.indexOf('\n', start)
            if (nl < 0) { out += MdLine(start, text.length); break }
            out += MdLine(start, nl)
            start = nl + 1
        }
        return out
    }

    /** Character ranges of fenced code blocks, including both fence lines. */
    fun codeBlocks(text: String): List<IntRange> {
        val blocks = ArrayList<IntRange>()
        var open: Int? = null
        for (line in lines(text)) {
            if (fence.containsMatchIn(text.substring(line.start, line.end))) {
                val o = open
                if (o == null) open = line.start else { blocks += o..line.end; open = null }
            }
        }
        open?.let { blocks += it..text.length }
        return blocks
    }

    fun parse(text: String): List<MdSpan> {
        val spans = ArrayList<MdSpan>()
        val blocks = codeBlocks(text)
        for (line in lines(text)) {
            val s = line.start
            val content = text.substring(s, line.end)
            val block = blocks.firstOrNull { s in it }
            if (block != null) {
                val isFence = fence.containsMatchIn(content)
                spans += MdSpan(s, line.end, if (isFence) Kind.Syntax else Kind.CodeBlock)
                continue
            }
            if (rule.matches(content) && content.isNotBlank()) {
                spans += MdSpan(s, line.end, Kind.Rule)
                continue
            }
            var body = 0 // offset within the line where inline content starts
            heading.find(content)?.let { m ->
                val level = m.groupValues[1].length
                spans += MdSpan(s, s + m.range.last + 1, Kind.Syntax)
                spans += MdSpan(s + m.range.last + 1, line.end, Kind.Heading, level)
                body = m.range.last + 1
            } ?: task.find(content)?.let { m ->
                val boxStart = s + m.groups[2]!!.range.first
                val done = m.groupValues[3] != " "
                spans += MdSpan(boxStart, boxStart + 5, if (done) Kind.TaskDone else Kind.TaskOpen)
                body = m.groups[3]!!.range.last + 2
                if (done) spans += MdSpan(s + minOf(body, content.length), line.end, Kind.Done)
            } ?: bullet.find(content)?.let { m ->
                val at = s + m.groups[2]!!.range.first
                spans += MdSpan(at, at + 1, Kind.Bullet)
                body = m.range.last + 1
            } ?: ordered.find(content)?.let { m ->
                spans += MdSpan(s + m.groups[2]!!.range.first, s + m.groups[2]!!.range.last + 1, Kind.OrderedMarker)
                body = m.range.last + 1
            } ?: quote.find(content)?.let { m ->
                val at = s + m.groups[2]!!.range.first
                spans += MdSpan(at, at + 1, Kind.Quote, level = 1)
                spans += MdSpan(at + 1, line.end, Kind.Quote)
                body = m.range.last + 1
            }
            inline(text, s + minOf(body, content.length), line.end, spans)
        }
        return spans
    }

    private fun inline(text: String, from: Int, to: Int, spans: MutableList<MdSpan>) {
        if (from >= to) return
        val line = text.substring(from, to)
        val taken = BooleanArray(line.length) // characters claimed by code, links, escapes
        fun free(r: IntRange) = r.none { taken[it] }
        fun claim(r: IntRange) = r.forEach { taken[it] = true }
        fun add(a: Int, b: Int, kind: Kind) { if (b > a) spans += MdSpan(from + a, from + b, kind) }

        escape.findAll(line).forEach { m ->
            add(m.range.first, m.range.first + 1, Kind.Syntax); claim(m.range)
        }
        codeSpan.findAll(line).forEach { m ->
            if (!free(m.range)) return@forEach
            val tick = m.groupValues[1].length
            add(m.range.first, m.range.first + tick, Kind.Syntax)
            add(m.range.first + tick, m.range.last + 1 - tick, Kind.Code)
            add(m.range.last + 1 - tick, m.range.last + 1, Kind.Syntax)
            claim(m.range)
        }
        image.findAll(line).forEach { m ->
            if (!free(m.range)) return@forEach
            add(m.range.first, m.range.last + 1, Kind.Image); claim(m.range)
        }
        link.findAll(line).forEach { m ->
            if (!free(m.range)) return@forEach
            val textEnd = m.groups[1]!!.range.last + 1
            add(m.range.first, m.range.first + 1, Kind.Syntax)
            add(m.range.first + 1, textEnd, Kind.Link)
            add(textEnd, m.range.last + 1, Kind.Syntax)
            claim(m.range.first..m.range.first)
            claim(textEnd..m.range.last)
        }
        fun emphasis(regex: Regex, kind: Kind) = regex.findAll(line).forEach { m ->
            val marker = (m.range.last + 1 - m.range.first - m.groups.last()!!.value.length) / 2
            val open = m.range.first until m.range.first + marker
            val close = m.range.last + 1 - marker..m.range.last
            if (!free(open) || !free(close)) return@forEach
            add(open.first, open.last + 1, Kind.Syntax)
            add(open.last + 1, close.first, kind)
            add(close.first, close.last + 1, Kind.Syntax)
            claim(open); claim(close)
        }
        emphasis(bold, Kind.Bold)
        emphasis(strike, Kind.Strike)
        emphasis(italicStar, Kind.Italic)
        emphasis(italicUnderscore, Kind.Italic)
    }
}
