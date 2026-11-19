package dev.kalpane.data

/** One line of a unified diff. [kind] is ' ', '+', '-' or '@' (hunk header). */
data class DiffLine(val kind: Char, val text: String, val oldNo: Int? = null, val newNo: Int? = null)

data class CanvasDiff(val added: Int, val removed: Int, val changed: Int) {
    val isEmpty get() = added == 0 && removed == 0 && changed == 0
}

object Diff {
    /** Line diff by longest common subsequence, grouped into hunks with [context] lines around changes. */
    fun lines(old: String, new: String, context: Int = 3): List<DiffLine> {
        val a = if (old.isEmpty()) emptyList() else old.split('\n')
        val b = if (new.isEmpty()) emptyList() else new.split('\n')
        // Trim a common prefix and suffix so the table only covers the changed middle.
        var start = 0
        while (start < a.size && start < b.size && a[start] == b[start]) start++
        var endA = a.size; var endB = b.size
        while (endA > start && endB > start && a[endA - 1] == b[endB - 1]) { endA--; endB-- }
        val ma = a.subList(start, endA); val mb = b.subList(start, endB)
        val ops = ArrayList<DiffLine>()
        for (i in 0 until start) ops += DiffLine(' ', a[i], i + 1, i + 1)
        if (ma.size * mb.size > 4_000_000) {
            // Too large for the table: show it as a replacement.
            ma.forEachIndexed { i, t -> ops += DiffLine('-', t, start + i + 1, null) }
            mb.forEachIndexed { i, t -> ops += DiffLine('+', t, null, start + i + 1) }
        } else {
            val lcs = Array(ma.size + 1) { IntArray(mb.size + 1) }
            for (i in ma.indices.reversed()) for (j in mb.indices.reversed()) {
                lcs[i][j] = if (ma[i] == mb[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
            var i = 0; var j = 0
            while (i < ma.size || j < mb.size) {
                when {
                    i < ma.size && j < mb.size && ma[i] == mb[j] -> { ops += DiffLine(' ', ma[i], start + i + 1, start + j + 1); i++; j++ }
                    j < mb.size && (i == ma.size || lcs[i][j + 1] >= lcs[i + 1][j]) -> { ops += DiffLine('+', mb[j], null, start + j + 1); j++ }
                    else -> { ops += DiffLine('-', ma[i], start + i + 1, null); i++ }
                }
            }
        }
        for (k in 0 until a.size - endA) ops += DiffLine(' ', a[endA + k], endA + k + 1, endB + k + 1)
        return hunks(ops, context)
    }

    private fun hunks(ops: List<DiffLine>, context: Int): List<DiffLine> {
        val changed = ops.indices.filter { ops[it].kind != ' ' }
        if (changed.isEmpty()) return emptyList()
        val keep = BooleanArray(ops.size)
        changed.forEach { c -> for (k in maxOf(0, c - context)..minOf(ops.size - 1, c + context)) keep[k] = true }
        val out = ArrayList<DiffLine>()
        var k = 0
        while (k < ops.size) {
            if (!keep[k]) { k++; continue }
            val from = k
            while (k < ops.size && keep[k]) k++
            val block = ops.subList(from, k)
            val oldStart = block.firstNotNullOfOrNull { it.oldNo } ?: 0
            val newStart = block.firstNotNullOfOrNull { it.newNo } ?: 0
            val oldCount = block.count { it.kind != '+' }
            val newCount = block.count { it.kind != '-' }
            out += DiffLine('@', "@@ -$oldStart,$oldCount +$newStart,$newCount @@")
            out += block
        }
        return out
    }

    fun stats(diff: List<DiffLine>) = diff.count { it.kind == '+' } to diff.count { it.kind == '-' }

    /** Drawing changes by item id: new ids, missing ids, and ids whose item changed. */
    fun canvas(old: List<Item>, new: List<Item>): CanvasDiff {
        val before = old.associateBy { it.id }
        val after = new.associateBy { it.id }
        return CanvasDiff(
            added = after.keys.count { it !in before },
            removed = before.keys.count { it !in after },
            changed = after.count { (id, item) -> before[id]?.let { it != item } == true },
        )
    }
}
