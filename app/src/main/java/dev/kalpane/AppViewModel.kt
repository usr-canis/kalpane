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

    init { refreshNotebooks() }
}
