package dev.kalpane.sync

import java.io.File
import java.security.MessageDigest

/** What a sync must do, decided from three snapshots of path → Git blob SHA. */
data class SyncPlan(
    val download: List<String> = emptyList(),
    val deleteLocal: List<String> = emptyList(),
    val upload: List<String> = emptyList(),
    val deleteRemote: List<String> = emptyList(),
    /** Changed differently on both sides. */
    val conflicts: List<String> = emptyList(),
) {
    val isEmpty get() = download.isEmpty() && deleteLocal.isEmpty() && upload.isEmpty() && deleteRemote.isEmpty() && conflicts.isEmpty()
}

object SyncPlanner {
    /**
     * Three-way comparison against [base], the state after the last sync.
     * A side that still matches the base takes the other side's version. If both changed,
     * a deletion loses to an edit, and two different edits are a conflict.
     */
    fun plan(local: Map<String, String>, remote: Map<String, String>, base: Map<String, String>): SyncPlan {
        val download = ArrayList<String>(); val deleteLocal = ArrayList<String>()
        val upload = ArrayList<String>(); val deleteRemote = ArrayList<String>()
        val conflicts = ArrayList<String>()
        for (path in (local.keys + remote.keys + base.keys).sorted()) {
            val l = local[path]; val r = remote[path]; val b = base[path]
            when {
                l == r -> Unit
                l == b -> if (r == null) deleteLocal += path else download += path
                r == b -> if (l == null) deleteRemote += path else upload += path
                l == null -> download += path
                r == null -> upload += path
                else -> conflicts += path
            }
        }
        return SyncPlan(download, deleteLocal, upload, deleteRemote, conflicts)
    }

    /** Git's blob object id for [bytes]. */
    fun blobSha(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob ${bytes.size}\u0000".toByteArray())
        md.update(bytes)
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** True for paths that belong to notes: inside a notebook folder, nothing hidden or temporary. */
    fun isNotePath(path: String): Boolean {
        val parts = path.split('/')
        return parts.size >= 2 && parts.none { it.isEmpty() || it.startsWith(".") } && !path.endsWith(".writing")
    }

    fun scan(root: File): Map<String, String> =
        root.walkTopDown().filter { it.isFile }.mapNotNull { f ->
            val rel = f.relativeTo(root).invariantSeparatorsPath
            if (isNotePath(rel)) rel to blobSha(f.readBytes()) else null
        }.toMap()

    /** "nb/Page 1.md" → "nb/Page 1 (conflict).md"; also handles ".canvas.json". */
    fun conflictPath(path: String, exists: (String) -> Boolean): String {
        val ext = listOf(".canvas.json", ".md", ".json").firstOrNull { path.endsWith(it) }
            ?: path.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        val stem = path.removeSuffix(ext)
        var candidate = "$stem (conflict)$ext"
        var n = 2
        while (exists(candidate)) candidate = "$stem (conflict $n)$ext".also { n++ }
        return candidate
    }
}
