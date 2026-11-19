package dev.kalpane.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.kalpane.data.Kind
import dev.kalpane.data.MarkdownEdit
import dev.kalpane.data.MarkdownSpans

data class MarkdownPalette(
    val text: Color,
    val muted: Color,
    val syntax: Color,
    val accent: Color,
    val codeBackground: Color,
    val rule: Color,
    val keyword: Color,
    val string: Color,
    val number: Color,
)

private val hidden = SpanStyle(fontSize = 0.5.sp, color = Color.Transparent, letterSpacing = 0.sp)
/** Heading sizes relative to body text, H1 to H6. */
val HeadingScale = listOf(1.75f, 1.5f, 1.3f, 1.15f, 1.0f, 0.9f)

/** Character range whose Markdown is shown raw: the selected lines, widened to a whole code block. */
fun revealRange(text: String, selStart: Int, selEnd: Int): IntRange {
    var a = MarkdownEdit.lineStart(text, minOf(selStart, selEnd))
    var b = MarkdownEdit.lineEnd(text, maxOf(selStart, selEnd))
    MarkdownSpans.codeBlocks(text).forEach { block ->
        if (a <= block.last && b >= block.first) { a = minOf(a, block.first); b = maxOf(b, block.last) }
    }
    return a..b
}

/**
 * Styles Markdown in place. The output always has the same length as the input, so
 * offsets map one to one: markup is collapsed rather than removed, and list and task
 * markers are swapped for glyphs of the same length.
 */
fun styleMarkdown(text: String, reveal: IntRange?, p: MarkdownPalette, mono: FontFamily, base: Float = 17f): AnnotatedString {
    val spans = MarkdownSpans.parse(text)
    val chars = text.toCharArray()
    val styles = ArrayList<AnnotatedString.Range<SpanStyle>>()
    val paragraphs = ArrayList<AnnotatedString.Range<ParagraphStyle>>()
    fun style(s: SpanStyle, a: Int, b: Int) { if (b > a) styles += AnnotatedString.Range(s, a, b) }
    val dim = SpanStyle(color = p.syntax)

    for (span in spans) {
        val shown = reveal != null && span.start <= reveal.last && span.end >= reveal.first
        val (a, b) = span.start to span.end
        when (span.kind) {
            Kind.Syntax -> style(if (shown) dim else hidden, a, b)
            Kind.Heading -> {
                val size = base * HeadingScale[span.level - 1]
                val weight = when (span.level) { 1, 2 -> FontWeight.Bold; 3, 4 -> FontWeight.SemiBold; else -> FontWeight.SemiBold }
                style(SpanStyle(fontSize = size.sp, fontWeight = weight, color = if (span.level == 6) p.muted else Color.Unspecified), a, b)
                val lineStart = MarkdownEdit.lineStart(text, a)
                paragraphs += AnnotatedString.Range(ParagraphStyle(lineHeight = (size * 1.5f).sp), lineStart, b)
            }
            Kind.Bold -> style(SpanStyle(fontWeight = FontWeight.SemiBold), a, b)
            Kind.Italic -> style(SpanStyle(fontStyle = FontStyle.Italic), a, b)
            Kind.Strike -> style(SpanStyle(textDecoration = TextDecoration.LineThrough), a, b)
            Kind.Code -> style(SpanStyle(fontFamily = mono, fontSize = 0.9.em, background = p.codeBackground), a, b)
            Kind.CodeBlock -> style(SpanStyle(fontFamily = mono, fontSize = 0.86.em), a, b)
            Kind.WikiLink -> style(SpanStyle(color = p.accent, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline), a, b)
            Kind.Tag -> style(SpanStyle(color = p.accent, background = p.accent.copy(alpha = 0.12f)), a, b)
            Kind.FenceInfo -> style(SpanStyle(fontFamily = mono, fontSize = 0.75.em, color = p.muted), a, b)
            Kind.CodeKeyword -> style(SpanStyle(color = p.keyword, fontWeight = FontWeight.Medium), a, b)
            Kind.CodeString -> style(SpanStyle(color = p.string), a, b)
            Kind.CodeNumber -> style(SpanStyle(color = p.number), a, b)
            Kind.CodeComment -> style(SpanStyle(color = p.muted, fontStyle = FontStyle.Italic), a, b)
            Kind.Link -> style(SpanStyle(color = p.accent, textDecoration = TextDecoration.Underline), a, b)
            Kind.Image -> style(dim, a, b)
            Kind.Quote -> if (span.level == 1) {
                if (shown) style(dim, a, b) else { chars[a] = '│'; style(SpanStyle(color = p.accent, fontWeight = FontWeight.Bold), a, b) }
            } else {
                style(SpanStyle(color = p.muted, fontStyle = FontStyle.Italic), a, b)
            }
            Kind.Bullet -> if (shown) style(dim, a, b) else { chars[a] = '•'; style(SpanStyle(color = p.muted), a, b) }
            Kind.TaskOpen, Kind.TaskDone -> if (shown) style(dim, a, b) else {
                val done = span.kind == Kind.TaskDone
                chars[a] = if (done) '☑' else '☐'
                style(SpanStyle(color = if (done) p.accent else p.muted, fontSize = 1.15.em), a, a + 1)
                style(hidden, a + 1, b)
            }
            Kind.Done -> style(SpanStyle(color = p.muted, textDecoration = TextDecoration.LineThrough), a, b)
            Kind.OrderedMarker -> style(SpanStyle(color = p.muted), a, b)
            Kind.Rule -> if (shown) style(dim, a, b) else {
                for (i in a until b) if (!chars[i].isWhitespace()) chars[i] = '─'
                style(SpanStyle(color = p.rule, letterSpacing = 0.sp), a, b)
            }
        }
    }
    return AnnotatedString(String(chars), styles, paragraphs)
}

class MarkdownTransformation(
    private val reveal: IntRange?,
    private val palette: MarkdownPalette,
    private val mono: FontFamily,
    private val base: Float = 17f,
) : VisualTransformation {
    override fun filter(text: AnnotatedString) =
        TransformedText(styleMarkdown(text.text, reveal, palette, mono, base), OffsetMapping.Identity)

    override fun equals(other: Any?) =
        other is MarkdownTransformation && other.reveal == reveal && other.palette == palette && other.mono == mono && other.base == base

    override fun hashCode() = (reveal?.hashCode() ?: 0) * 31 + palette.hashCode()
}
