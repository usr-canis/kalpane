package dev.kalpane.data

/** Text plus selection; all editing helpers are pure functions on this. */
data class Edit(val text: String, val start: Int, val end: Int = start) {
    val min get() = minOf(start, end)
    val max get() = maxOf(start, end)
}

enum class ListKind { Bullet, Ordered, Task }

object MarkdownEdit {
    private val listPrefix = Regex("""^(\s*)(?:([-*+]) \[[ xX]] |([-*+]) |(\d+)([.)]) )""")
    private val anyPrefix = Regex("""^(\s*)(#{1,6} |[-*+] \[[ xX]] |[-*+] |\d+[.)] |> )""")
    private val headingPrefix = Regex("""^(#{1,6}) """)

    fun lineStart(text: String, at: Int) = if (at <= 0) 0 else text.lastIndexOf('\n', at - 1) + 1
    fun lineEnd(text: String, at: Int) = text.indexOf('\n', at).let { if (it < 0) text.length else it }

    /** Continues or ends a list after Enter. [e] has its cursor right after the inserted newline. */
    fun onNewline(e: Edit): Edit? {
        val nl = e.start - 1
        if (nl < 0 || e.text.getOrNull(nl) != '\n') return null
        val prevStart = lineStart(e.text, nl)
        val prev = e.text.substring(prevStart, nl)
        val m = listPrefix.find(prev) ?: return null
        val indent = m.groupValues[1]
        if (prev.length == m.value.length) {
            // Empty item: end the list by clearing the marker and the new line.
            val text = e.text.removeRange(prevStart, nl + 1)
            return Edit(text, prevStart)
        }
        val next = when {
            m.groupValues[2].isNotEmpty() -> "${m.groupValues[2]} [ ] "
            m.groupValues[3].isNotEmpty() -> "${m.groupValues[3]} "
            else -> "${m.groupValues[4].toInt() + 1}${m.groupValues[5]} "
        }
        val insert = indent + next
        return Edit(e.text.substring(0, e.start) + insert + e.text.substring(e.start), e.start + insert.length)
    }

    /** Wraps the selection in [marker], or removes it if already wrapped. With no selection, inserts a pair. */
    fun toggleWrap(e: Edit, marker: String, closing: String = marker): Edit {
        val t = e.text
        val a = e.min; val b = e.max
        val before = t.substring(0, a); val after = t.substring(b)
        if (before.endsWith(marker) && after.startsWith(closing)) {
            val text = before.dropLast(marker.length) + t.substring(a, b) + after.drop(closing.length)
            return Edit(text, a - marker.length, b - marker.length)
        }
        val inner = t.substring(a, b)
        if (inner.length >= marker.length + closing.length && inner.startsWith(marker) && inner.endsWith(closing)) {
            val stripped = inner.substring(marker.length, inner.length - closing.length)
            return Edit(before + stripped + after, a, a + stripped.length)
        }
        return Edit(before + marker + inner + closing + after, a + marker.length, b + marker.length)
    }

    fun link(e: Edit): Edit {
        val inner = e.text.substring(e.min, e.max)
        val text = e.text.substring(0, e.min) + "[$inner]()" + e.text.substring(e.max)
        val cursor = if (inner.isEmpty()) e.min + 1 else e.min + inner.length + 3
        return Edit(text, cursor)
    }

    private fun mapLines(e: Edit, transform: (List<String>) -> List<String>): Edit {
        val start = lineStart(e.text, e.min)
        val end = lineEnd(e.text, e.max)
        val old = e.text.substring(start, end).split('\n')
        val new = transform(old)
        val joined = new.joinToString("\n")
        val text = e.text.substring(0, start) + joined + e.text.substring(end)
        // Keep the cursor on the same text where possible.
        val shiftFirst = new.first().length - old.first().length
        val shiftAll = joined.length - (end - start)
        return if (e.start == e.end) {
            Edit(text, (e.start + shiftFirst).coerceIn(start, start + new.first().length))
        } else {
            Edit(text, (e.min + shiftFirst).coerceAtLeast(start), (e.max + shiftAll).coerceIn(start, text.length))
        }
    }

    /** Cycles the current line(s) through # → ## → ### → plain. */
    fun cycleHeading(e: Edit): Edit = mapLines(e) { lines ->
        val current = headingPrefix.find(lines.first())?.groupValues?.get(1)?.length ?: 0
        val next = if (current >= 3) 0 else current + 1
        lines.map { line ->
            val bare = line.replace(anyPrefix, "$1")
            if (next == 0) bare else "#".repeat(next) + " " + bare.trimStart()
        }
    }

    /** Applies [kind] to the selected lines, or removes it when every line already has it. */
    fun toggleList(e: Edit, kind: ListKind): Edit = mapLines(e) { lines ->
        val has = lines.filter { it.isNotBlank() }.ifEmpty { lines }.all { line ->
            val m = listPrefix.find(line)
            when (kind) {
                ListKind.Task -> m?.groupValues?.get(2)?.isNotEmpty() == true
                ListKind.Bullet -> m?.groupValues?.get(3)?.isNotEmpty() == true
                ListKind.Ordered -> m?.groupValues?.get(4)?.isNotEmpty() == true
            }
        }
        var n = 0
        lines.map { line ->
            val indent = line.takeWhile { it == ' ' || it == '\t' }
            val bare = line.replace(anyPrefix, "$1").drop(indent.length)
            if (has || (line.isBlank() && lines.size > 1)) indent + bare
            else indent + when (kind) {
                ListKind.Bullet -> "- "
                ListKind.Task -> "- [ ] "
                ListKind.Ordered -> "${++n}. "
            } + bare
        }
    }

    /** Toggles the checkbox of a task line that starts at [lineStart]. Returns null if it is not a task. */
    fun toggleTask(text: String, lineStart: Int): String? {
        val end = lineEnd(text, lineStart)
        val line = text.substring(lineStart, end)
        val m = Regex("""^(\s*[-*+] \[)([ xX])]""").find(line) ?: return null
        val at = lineStart + m.groups[2]!!.range.first
        val mark = if (text[at] == ' ') 'x' else ' '
        return text.substring(0, at) + mark + text.substring(at + 1)
    }

    /** Adds or removes a "> " quote marker on the selected lines. */
    fun toggleQuote(e: Edit): Edit = mapLines(e) { lines ->
        val quoted = lines.filter { it.isNotBlank() }.ifEmpty { lines }.all { it.trimStart().startsWith(">") }
        lines.map { line ->
            if (quoted) line.replaceFirst(Regex("""^(\s*)> ?"""), "$1") else "> $line"
        }
    }
}
