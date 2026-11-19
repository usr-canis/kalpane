package dev.kalpane

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kalpane.data.NoteStore
import dev.kalpane.data.NotebookInfo
import dev.kalpane.data.PageData
import dev.kalpane.data.PageInfo
import dev.kalpane.data.PageRef
import dev.kalpane.data.hash
import dev.kalpane.data.writeAtomic
import dev.kalpane.sync.CredentialVault
import dev.kalpane.sync.GitHubSync
import dev.kalpane.sync.SyncException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class ThemeMode { System, Light, Dark }

data class PagesState(val notebook: String, val pages: List<PageInfo>)

/** Sync connection and progress. [repo] is null when not connected. */
data class SyncStatus(
    val repo: String? = null,
    val branch: String = "main",
    val running: Boolean = false,
    val lastSync: Long? = null,
    /** Token expiry date (yyyy-MM-dd) as reported by GitHub, if any. */
    val expiry: String? = null,
)

/** An imported image: stored asset name and pixel size. */
data class ImportedImage(val asset: String, val width: Int, val height: Int)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = NoteStore(File(app.filesDir, "notes"))
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val lock = Mutex() // Fair: queued disk operations run in order.
    private val images = LruCache<String, ImageBitmap>(40)

    private val _notebooks = MutableStateFlow<List<NotebookInfo>?>(null)
    val notebooks: StateFlow<List<NotebookInfo>?> = _notebooks.asStateFlow()

    private val _pages = MutableStateFlow<PagesState?>(null)
    val pages: StateFlow<PagesState?> = _pages.asStateFlow()

    private val _theme = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString("theme", "System")!!) }.getOrDefault(ThemeMode.System)
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _fingerDrawing = MutableStateFlow(prefs.getBoolean("fingerDrawing", false))
    val fingerDrawing: StateFlow<Boolean> = _fingerDrawing.asStateFlow()

    private val vault = CredentialVault(app)
    private val syncDir = File(app.filesDir, "sync").apply { mkdirs() }

    private val _sync = MutableStateFlow(
        SyncStatus(
            repo = prefs.getString("syncRepo", null),
            branch = prefs.getString("syncBranch", "main") ?: "main",
            lastSync = prefs.getLong("lastSync", 0L).takeIf { it > 0 },
            expiry = prefs.getString("tokenExpiry", null),
        )
    )
    val sync: StateFlow<SyncStatus> = _sync.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    private suspend fun <T> disk(block: () -> T): T = withContext(Dispatchers.IO) { lock.withLock { block() } }

    private fun io(block: () -> Unit) = viewModelScope.launch {
        try { disk(block) } catch (e: Exception) { _message.value = e.message ?: "Something went wrong." }
    }

    private fun reloadNotebooks() { _notebooks.value = store.notebooks() }
    private fun reloadPages(notebook: String) { _pages.value = PagesState(notebook, store.pages(notebook)) }

    fun refreshNotebooks() = io { reloadNotebooks() }
    fun openNotebook(notebook: String) = io { reloadPages(notebook) }

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    fun setFingerDrawing(on: Boolean) {
        _fingerDrawing.value = on
        prefs.edit().putBoolean("fingerDrawing", on).apply()
    }

    fun createNotebook(name: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        val created = runCatching { disk { store.createNotebook(name).also { reloadNotebooks() } } }
            .onFailure { _message.value = it.message }.getOrNull() ?: return@launch
        onCreated(created)
    }

    fun renameNotebook(old: String, name: String) = io { store.renameNotebook(old, name); reloadNotebooks() }
    fun deleteNotebook(name: String) = io { store.deleteNotebook(name); reloadNotebooks() }

    fun createPage(notebook: String, onCreated: (PageRef) -> Unit) = viewModelScope.launch {
        val ref = runCatching { disk { store.createPage(notebook) } }.onFailure { _message.value = it.message }.getOrNull() ?: return@launch
        onCreated(ref)
    }

    fun renamePage(ref: PageRef, name: String, onRenamed: (PageRef) -> Unit = {}) = viewModelScope.launch {
        val new = runCatching { disk { store.renamePage(ref, name).also { reloadPages(ref.notebook) } } }
            .onFailure { _message.value = it.message }.getOrNull() ?: return@launch
        onRenamed(new)
    }

    fun deletePage(ref: PageRef) = io { store.deletePage(ref); reloadPages(ref.notebook) }
    fun movePage(ref: PageRef, offset: Int) = io { store.movePage(ref, offset); reloadPages(ref.notebook) }

    suspend fun load(ref: PageRef): PageData = disk { store.read(ref) }

    fun save(ref: PageRef, data: PageData) = io { store.write(ref, data) }

    /** Copies a picked image into the notebook's assets. */
    suspend fun importImage(notebook: String, uri: Uri): ImportedImage? = try {
        withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val mime = resolver.getType(uri) ?: "image/jpeg"
            val ext = when {
                mime.endsWith("png") -> "png"
                mime.endsWith("webp") -> "webp"
                mime.endsWith("gif") -> "gif"
                else -> "jpg"
            }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read the image.")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) error("Unsupported image.")
            val asset = lock.withLock { store.importAsset(notebook, bytes, ext) }
            ImportedImage(asset, bounds.outWidth, bounds.outHeight)
        }
    } catch (e: Exception) {
        _message.value = e.message ?: "Could not import the image."
        null
    }

    /** Decodes an asset, downsampled to at most 2048 px on its longer side. */
    suspend fun image(notebook: String, asset: String): ImageBitmap? {
        val key = "$notebook/$asset"
        images.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = runCatching { store.assetFile(notebook, asset) }.getOrNull()?.takeIf { it.exists() } ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?.asImageBitmap()?.also { images.put(key, it) }
        }
    }

    private val baseSerializer = MapSerializer(String.serializer(), String.serializer())

    private fun baseFile(repo: String, branch: String) = File(syncDir, "base-${hash("$repo\n$branch".toByteArray()).take(16)}.json")

    private fun readBase(repo: String, branch: String): Map<String, String> = runCatching {
        Json.decodeFromString(baseSerializer, baseFile(repo, branch).readText())
    }.getOrDefault(emptyMap())

    /** Verifies access, then stores the token encrypted on this device. Reports an error message or null. */
    fun connect(repo: String, branch: String, token: String, onDone: (String?) -> Unit) = viewModelScope.launch {
        val error = try {
            val info = withContext(Dispatchers.IO) { GitHubSync(repo.trim(), branch.trim(), token.trim()).verify() }
            vault.put(repo.trim(), token.trim())
            val expiry = info.tokenExpiry?.take(10)
            prefs.edit().putString("syncRepo", repo.trim()).putString("syncBranch", branch.trim())
                .putString("tokenExpiry", expiry).apply()
            _sync.value = SyncStatus(repo.trim(), branch.trim(), expiry = expiry)
            null
        } catch (e: Exception) {
            e.message ?: "Could not connect."
        }
        onDone(error)
    }

    fun disconnect() {
        _sync.value.repo?.let { vault.remove(it) }
        prefs.edit().remove("syncRepo").remove("syncBranch").remove("tokenExpiry").remove("lastSync").apply()
        _sync.value = SyncStatus()
    }

    fun syncNow() {
        val status = _sync.value
        val repo = status.repo ?: return
        if (status.running) return
        _sync.value = status.copy(running = true)
        viewModelScope.launch {
            try {
                val result = disk {
                    val token = vault.get(repo) ?: throw SyncException("Reconnect GitHub in Settings.")
                    val client = GitHubSync(repo, status.branch, token)
                    val result = client.sync(store.root, readBase(repo, status.branch))
                    writeAtomic(baseFile(repo, status.branch), Json.encodeToString(baseSerializer, result.base).toByteArray())
                    reloadNotebooks()
                    _pages.value?.let { reloadPages(it.notebook) }
                    client.tokenExpiry?.take(10)?.let { prefs.edit().putString("tokenExpiry", it).apply() }
                    result
                }
                val now = System.currentTimeMillis()
                prefs.edit().putLong("lastSync", now).apply()
                _sync.value = _sync.value.copy(running = false, lastSync = now, expiry = prefs.getString("tokenExpiry", null))
                _message.value = when {
                    result.conflicts > 0 -> "Synced. ${result.conflicts} conflicting ${if (result.conflicts == 1) "page was" else "pages were"} kept as “(conflict)” copies."
                    result.pulled == 0 && result.pushed == 0 -> "Up to date"
                    else -> "Synced"
                }
            } catch (e: Exception) {
                _sync.value = _sync.value.copy(running = false)
                _message.value = e.message ?: "Sync failed. Your notes are safe on this device."
            }
        }
    }

    /** Days until the token expires, if GitHub reported a date. */
    fun daysUntilExpiry(): Long? = _sync.value.expiry?.let {
        runCatching { java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), java.time.LocalDate.parse(it)) }.getOrNull()
    }

    init {
        refreshNotebooks()
        daysUntilExpiry()?.let { days ->
            if (_sync.value.repo != null && days <= 7) {
                _message.value = if (days < 0) "Your GitHub token has expired. Create a new one in Settings."
                else "Your GitHub token expires in $days ${if (days == 1L) "day" else "days"}."
            }
        }
    }
}
