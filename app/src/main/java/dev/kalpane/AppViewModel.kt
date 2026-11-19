package dev.kalpane

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kalpane.data.NoteInfo
import dev.kalpane.data.NoteRef
import dev.kalpane.data.NoteStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class ThemeMode { System, Light, Dark }

data class LibraryState(
    val notebooks: List<String> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
    val selected: String? = null,
    val query: String = "",
    val notes: List<NoteInfo> = emptyList(),
    val loaded: Boolean = false,
)

data class Deleted(val ref: NoteRef, val text: String)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = NoteStore(File(app.filesDir, "notes"))
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val lock = Mutex() // Fair: queued disk operations run in order.

    private val _library = MutableStateFlow(LibraryState(selected = prefs.getString("notebook", null)))
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private val _theme = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString("theme", "System")!!) }.getOrDefault(ThemeMode.System)
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    var lastDeleted: Deleted? = null
        private set

    init {
        io {
            if (store.notebooks().isEmpty()) store.createNotebook("Notes")
        }
        refresh()
    }

    private fun io(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) { lock.withLock { block() } }
        } catch (e: Exception) {
            _message.value = e.message ?: "Something went wrong."
        }
    }

    private fun reload() {
        val state = _library.value
        val books = store.notebooks()
        val selected = state.selected?.takeIf { it in books }
        val all = store.notes()
        val counts = all.groupingBy { it.ref.notebook }.eachCount()
        val notes = store.search(state.query, selected)
        _library.update { it.copy(notebooks = books, counts = counts, selected = selected, notes = notes, loaded = true) }
    }

    fun refresh() = io { reload() }

    fun select(notebook: String?) {
        _library.update { it.copy(selected = notebook) }
        prefs.edit().putString("notebook", notebook).apply()
        refresh()
    }

    fun search(query: String) {
        _library.update { it.copy(query = query) }
        refresh()
    }

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    /** Creates a note in the selected (or first) notebook and reports it once it exists. */
    fun newNote(onCreated: (NoteRef) -> Unit) = viewModelScope.launch {
        val ref = withContext(Dispatchers.IO) {
            lock.withLock {
                val book = _library.value.selected ?: store.notebooks().firstOrNull() ?: store.createNotebook("Notes")
                store.createNote(book)
            }
        }
        onCreated(ref)
    }

    suspend fun load(ref: NoteRef): String = withContext(Dispatchers.IO) { lock.withLock { store.read(ref) } }

    fun save(ref: NoteRef, text: String) = io { store.write(ref, text) }

    /** Final save when leaving the editor: blank notes are discarded, others take their title as file name. */
    fun close(ref: NoteRef, text: String) = io {
        if (text.isBlank()) {
            store.delete(ref)
        } else {
            store.write(ref, text)
            store.renameToTitle(ref)
        }
        reload()
    }

    fun delete(ref: NoteRef) = io {
        lastDeleted = Deleted(ref, store.read(ref))
        store.delete(ref)
        reload()
    }

    fun undoDelete() {
        val deleted = lastDeleted ?: return
        lastDeleted = null
        io {
            val target = if (store.file(deleted.ref).exists()) store.createNote(deleted.ref.notebook) else deleted.ref
            store.write(target, deleted.text)
            reload()
        }
    }

    fun move(ref: NoteRef, notebook: String) = io { store.move(ref, notebook); reload() }

    fun createNotebook(name: String) = io {
        val created = store.createNotebook(name)
        _library.update { it.copy(selected = created) }
        prefs.edit().putString("notebook", created).apply()
        reload()
    }

    fun renameNotebook(old: String, name: String) = io {
        val renamed = store.renameNotebook(old, name)
        if (_library.value.selected == old) {
            _library.update { it.copy(selected = renamed) }
            prefs.edit().putString("notebook", renamed).apply()
        }
        reload()
    }

    fun deleteNotebook(name: String) = io {
        store.deleteNotebook(name)
        if (store.notebooks().isEmpty()) store.createNotebook("Notes")
        reload()
    }
}
