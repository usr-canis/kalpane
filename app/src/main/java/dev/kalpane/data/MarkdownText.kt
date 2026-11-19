package dev.kalpane.data

/** Plain-text helpers for titles, previews and search. */
object MarkdownText {
    private val image = Regex("""!\[[^\]]*]\([^)]*\)""")
    private val link = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val prefix = Regex("""^\s*(#{1,6}\s+|>\s*|[-*+]\s+\[[ xX]]\s+|[-*+]\s+|\d+[.)]\s+)""")
    private val emphasis = Regex("""(\*\*|__|~~|`|\*|_)""")

    /** Strips inline Markdown so a line reads as plain text. */
    fun plain(line: String): String = line
        .replace(image, "")
        .replace(link, "$1")
        .replace(prefix, "")
        .replace(emphasis, "")
        .trim()

    private fun contentLines(text: String): List<String> {
        var inFence = false
        return text.lines().mapNotNull { raw ->
            if (raw.trimStart().startsWith("```")) { inFence = !inFence; null }
            else if (inFence) raw.trim().takeIf { it.isNotEmpty() }
            else plain(raw).takeIf { it.isNotEmpty() && !Regex("^[-*_]{3,}$").matches(it) }
        }
    }

    fun title(text: String): String = contentLines(text).firstOrNull()?.take(120) ?: ""

    fun preview(text: String, max: Int = 160): String =
        contentLines(text).drop(1).joinToString(" ").take(max)
}
