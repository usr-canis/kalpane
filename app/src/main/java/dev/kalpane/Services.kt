package dev.kalpane

import android.content.Context
import android.content.SharedPreferences
import dev.kalpane.data.NoteStore
import dev.kalpane.data.Trash
import dev.kalpane.data.hash
import dev.kalpane.data.writeAtomic
import dev.kalpane.sync.CredentialVault
import dev.kalpane.sync.GitHubSync
import dev.kalpane.sync.SyncException
import dev.kalpane.sync.SyncResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Process-wide state shared by the UI and background backup: the notes folder, settings,
 * the encrypted token and the lock that serializes all disk and Git work.
 */
class Services private constructor(context: Context) {
    val store = NoteStore(File(context.filesDir, "notes"))
    val trash = Trash(File(context.filesDir, "trash"))
    val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val vault = CredentialVault(context)
    /** Fair, so queued operations run in order. */
    val lock = Mutex()
    /** True while a page is open; background backup then waits so it never pulls under the editor. */
    @Volatile var editing = false

    private val syncDir = File(context.filesDir, "sync").apply { mkdirs() }
    private val baseSerializer = MapSerializer(String.serializer(), String.serializer())

    val repo: String? get() = prefs.getString("syncRepo", null)
    val branch: String get() = prefs.getString("syncBranch", "main") ?: "main"

    private fun baseFile(repo: String, branch: String) = File(syncDir, "base-${hash("$repo\n$branch".toByteArray()).take(16)}.json")

    /** File state (path → blob SHA) after the last sync of [branch]. */
    fun readBase(repo: String, branch: String): Map<String, String> = runCatching {
        Json.decodeFromString(baseSerializer, baseFile(repo, branch).readText())
    }.getOrDefault(emptyMap())

    fun writeBase(repo: String, branch: String, base: Map<String, String>) =
        writeAtomic(baseFile(repo, branch), Json.encodeToString(baseSerializer, base).toByteArray())

    fun client(branch: String = this.branch): GitHubSync {
        val repo = repo ?: throw SyncException("Connect a repository in Settings.")
        val token = vault.get(repo) ?: throw SyncException("Reconnect GitHub in Settings.")
        return GitHubSync(repo, branch, token)
    }

    /** Runs a pull or push and records the new base. Call while holding [lock]. */
    fun runGit(mode: GitHubSync.Mode, message: String, scope: (String) -> Boolean): SyncResult {
        val repo = repo ?: throw SyncException("Connect a repository in Settings.")
        val c = client()
        val result = c.run(store.root, readBase(repo, branch), mode, message, scope)
        writeBase(repo, branch, result.base)
        val edit = prefs.edit().putLong("lastSync", System.currentTimeMillis())
        c.tokenExpiry?.take(10)?.let { edit.putString("tokenExpiry", it) }
        edit.apply()
        return result
    }

    companion object {
        @Volatile private var instance: Services? = null
        fun get(context: Context): Services =
            instance ?: synchronized(this) { instance ?: Services(context.applicationContext).also { instance = it } }
    }
}
