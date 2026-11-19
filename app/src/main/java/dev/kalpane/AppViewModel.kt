package dev.kalpane

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kalpane.data.NotebookInfo
import dev.kalpane.data.PageData
import dev.kalpane.data.PageInfo
import dev.kalpane.data.PageRef
import dev.kalpane.data.SearchHit
import dev.kalpane.data.Template
import dev.kalpane.data.Templates
import dev.kalpane.data.TrashEntry
import dev.kalpane.data.Paper
import dev.kalpane.data.writeAtomic
import dev.kalpane.sync.GitHubSync
import dev.kalpane.sync.SyncException
import dev.kalpane.sync.SyncPlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

/** Defaults for new and unstyled pages, stored as names so the data layer stays UI-free. */
data class PageDefaults(val font: String = "Sans", val size: Float = 17f, val paper: Paper = Paper.Plain)

/** Remembered pen settings. */
data class PenPrefs(val width: Float = 2.5f, val highlighterWidth: Float = 18f, val pressure: String = "Normal")

/** An imported image: stored asset name and pixel size. */
data class ImportedImage(val asset: String, val width: Int, val height: Int)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val services = Services.get(app)
    private val store = services.store
    private val prefs = services.prefs
    private val lock = services.lock
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

    private val vault = services.vault

    private val _sync = MutableStateFlow(
        SyncStatus(
            repo = prefs.getString("syncRepo", null),
            branch = prefs.getString("syncBranch", "main") ?: "main",
            lastSync = prefs.getLong("lastSync", 0L).takeIf { it > 0 },
            expiry = prefs.getString("tokenExpiry", null),
        )
    )
    val sync: StateFlow<SyncStatus> = _sync.asStateFlow()

    private val _defaults = MutableStateFlow(
        PageDefaults(
            font = prefs.getString("noteFont", "Sans") ?: "Sans",
            size = prefs.getFloat("textSize", 17f),
            paper = runCatching { Paper.valueOf(prefs.getString("paper", "Plain")!!) }.getOrDefault(Paper.Plain),
        )
    )
    val defaults: StateFlow<PageDefaults> = _defaults.asStateFlow()

    fun setDefaults(d: PageDefaults) {
        _defaults.value = d
        prefs.edit().putString("noteFont", d.font).putFloat("textSize", d.size).putString("paper", d.paper.name).apply()
    }

    val penPrefs = PenPrefs(
        prefs.getFloat("penWidth", 2.5f), prefs.getFloat("highlighterWidth", 18f), prefs.getString("pressure", "Normal") ?: "Normal",
    )

    fun savePen(p: PenPrefs) {
        prefs.edit().putFloat("penWidth", p.width).putFloat("highlighterWidth", p.highlighterWidth).putString("pressure", p.pressure).apply()
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    private suspend fun <T> disk(block: () -> T): T = withContext(Dispatchers.IO) { lock.withLock { block() } }

    private fun io(block: () -> Unit) = viewModelScope.launch {
        try { disk(block) } catch (e: Exception) { _message.value = e.message ?: "Something went wrong." }
    }

    private fun reloadNotebooks() {
        _notebooks.value = store.notebooks()
        _recent.value = store.recent()
        _tags.value = store.tags()
    }
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

    fun renameNotebook(old: String, name: String) = io {
        val renamed = store.renameNotebook(old, name)
        if (old in _pinned.value) setPinned(_pinned.value - old + renamed)
        reloadNotebooks()
    }
    fun deleteNotebook(name: String) = io {
        services.trash.trashNotebook(store, name)
        reloadNotebooks(); reloadTrash()
        if (_sync.value.repo != null) _changes.value = computeChanges()
        _message.value = "Notebook moved to trash"
    }

    fun createPage(notebook: String, template: Template? = null, onCreated: (PageRef) -> Unit) = viewModelScope.launch {
        val ref = runCatching {
            disk {
                store.createPage(notebook).also { ref ->
                    if (template != null) store.write(ref, Templates.instantiate(template))
                    reloadPages(notebook)
                }
            }
        }.onFailure { _message.value = it.message }.getOrNull() ?: return@launch
        onCreated(ref)
    }

    /** Built-in templates followed by pages of the Templates notebook. */
    suspend fun templates(): List<Template> = disk {
        val own = if (store.notebooks().any { it.name == Templates.NOTEBOOK }) {
            store.pageNames(Templates.NOTEBOOK).map { Template(it, store.read(PageRef(Templates.NOTEBOOK, it)), false) }
        } else emptyList()
        Templates.builtIn + own
    }

    /** Copies a page into the Templates notebook. */
    fun saveAsTemplate(ref: PageRef, data: PageData) = io {
        if (store.notebooks().none { it.name == Templates.NOTEBOOK }) store.createNotebook(Templates.NOTEBOOK, withPage = false)
        val created = store.createPage(Templates.NOTEBOOK)
        val named = store.renamePage(created, ref.name)
        store.write(named, data)
        reloadNotebooks()
        if (_sync.value.repo != null) _changes.value = computeChanges()
        _message.value = "Saved “${named.name}” to ${Templates.NOTEBOOK}"
    }

    fun renamePage(ref: PageRef, name: String, onRenamed: (PageRef) -> Unit = {}) = viewModelScope.launch {
        val new = runCatching { disk { store.renamePage(ref, name).also { reloadPages(ref.notebook) } } }
            .onFailure { _message.value = it.message }.getOrNull() ?: return@launch
        onRenamed(new)
    }

    fun deletePage(ref: PageRef) = io {
        services.trash.trashPage(store, ref)
        reloadPages(ref.notebook); reloadTrash()
        if (_sync.value.repo != null) _changes.value = computeChanges()
        _message.value = "Page moved to trash"
    }

    // ---- Trash ----

    private val _trash = MutableStateFlow<List<TrashEntry>>(emptyList())
    val trash: StateFlow<List<TrashEntry>> = _trash.asStateFlow()
    private fun reloadTrash() { _trash.value = services.trash.list() }
    fun refreshTrash() = io { services.trash.purge(); reloadTrash() }

    fun restore(entry: TrashEntry) = io {
        val name = services.trash.restore(store, entry.id)
        reloadTrash(); reloadNotebooks()
        _pages.value?.let { reloadPages(it.notebook) }
        if (_sync.value.repo != null) _changes.value = computeChanges()
        _message.value = "Restored “$name”"
    }

    fun deleteForever(entry: TrashEntry) = io { services.trash.delete(entry.id); reloadTrash() }
    fun emptyTrash() = io { services.trash.empty(); reloadTrash() }

    // ---- Search and recent pages ----

    private val _results = MutableStateFlow<List<SearchHit>?>(null)
    /** Null when not searching. */
    val results: StateFlow<List<SearchHit>?> = _results.asStateFlow()
    private var searchJob: kotlinx.coroutines.Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) { _results.value = null; return }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(150)
            _results.value = disk { store.search(query) }
        }
    }

    private val _recent = MutableStateFlow<List<PageInfo>>(emptyList())
    val recent: StateFlow<List<PageInfo>> = _recent.asStateFlow()

    // ---- Links, tags and pins ----

    suspend fun resolveLink(from: String, target: String): PageRef? = disk { store.resolveLink(from, target) }
    suspend fun backlinks(ref: PageRef): List<PageRef> = disk { store.backlinks(ref) }
    suspend fun allPages(): List<PageRef> = disk { store.allPages() }

    fun createNamedPage(notebook: String, name: String, onCreated: (PageRef) -> Unit) = viewModelScope.launch {
        val ref = runCatching { disk { store.createNamedPage(notebook, name) } }.onFailure { _message.value = it.message }.getOrNull() ?: return@launch
        onCreated(ref)
    }

    private val _tags = MutableStateFlow<Map<String, Int>>(emptyMap())
    val tags: StateFlow<Map<String, Int>> = _tags.asStateFlow()

    private val _pinned = MutableStateFlow(prefs.getStringSet("pinned", emptySet())!!.toSet())
    val pinned: StateFlow<Set<String>> = _pinned.asStateFlow()

    fun togglePin(notebook: String) = setPinned(if (notebook in _pinned.value) _pinned.value - notebook else _pinned.value + notebook)

    private fun setPinned(value: Set<String>) {
        _pinned.value = value
        prefs.edit().putStringSet("pinned", value).apply()
    }

    /** Marks a page as open so background backup waits. */
    fun setEditing(on: Boolean) { services.editing = on }

    // ---- Automatic backup ----

    private val _autoBackup = MutableStateFlow(prefs.getBoolean("autoBackup", false))
    val autoBackup: StateFlow<Boolean> = _autoBackup.asStateFlow()

    fun setAutoBackup(on: Boolean) {
        _autoBackup.value = on
        prefs.edit().putBoolean("autoBackup", on).apply()
        AutoPushWorker.schedule(getApplication(), on)
    }
    fun movePage(ref: PageRef, offset: Int) = io { store.movePage(ref, offset); reloadPages(ref.notebook) }

    suspend fun load(ref: PageRef): PageData = disk { store.read(ref) }

    suspend fun pageRefs(notebook: String): List<PageRef> = disk { store.pageNames(notebook).map { PageRef(notebook, it) } }

    fun save(ref: PageRef, data: PageData) = io {
        store.write(ref, data)
        if (_sync.value.repo != null) _changes.value = computeChanges()
    }

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

    private fun readBase(repo: String, branch: String) = services.readBase(repo, branch)

    /** Verifies access, then stores the token encrypted on this device. Reports an error message or null. */
    fun connect(repo: String, branch: String, token: String, onDone: (String?) -> Unit) = viewModelScope.launch {
        val error = try {
            val info = withContext(Dispatchers.IO) { GitHubSync(repo.trim(), branch.trim(), token.trim()).verify() }
            vault.put(repo.trim(), token.trim())
            val expiry = info.tokenExpiry?.take(10)
            prefs.edit().putString("syncRepo", repo.trim()).putString("syncBranch", branch.trim())
                .putString("tokenExpiry", expiry).apply()
            _sync.value = SyncStatus(repo.trim(), branch.trim(), expiry = expiry)
            refreshStatus()
            null
        } catch (e: Exception) {
            e.message ?: "Could not add the remote."
        }
        onDone(error)
    }

    fun disconnect() {
        setAutoBackup(false)
        _sync.value.repo?.let { vault.remove(it) }
        prefs.edit().remove("syncRepo").remove("syncBranch").remove("tokenExpiry").remove("lastSync").apply()
        _sync.value = SyncStatus()
        _changes.value = emptyMap()
    }

    // ---- Git: status, pull, push, branches, history ----

    private val shaCache = HashMap<String, Triple<Long, Long, String>>()

    /** Local changes since the last sync: path → 'M', 'A' or 'D'. Empty when not connected. */
    private val _changes = MutableStateFlow<Map<String, Char>>(emptyMap())
    val changes: StateFlow<Map<String, Char>> = _changes.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    /** Label of the Git operation in progress, if any. */
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private fun scanCached(): Map<String, String> {
        val root = store.root
        val out = HashMap<String, String>()
        root.walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = f.relativeTo(root).invariantSeparatorsPath
            if (!SyncPlanner.isNotePath(rel)) return@forEach
            val cached = shaCache[rel]
            val sha = if (cached != null && cached.first == f.length() && cached.second == f.lastModified()) cached.third
            else SyncPlanner.blobSha(f.readBytes()).also { shaCache[rel] = Triple(f.length(), f.lastModified(), it) }
            out[rel] = sha
        }
        return out
    }

    private fun computeChanges(): Map<String, Char> {
        val status = _sync.value
        val repo = status.repo ?: return emptyMap()
        val base = readBase(repo, status.branch)
        val local = scanCached()
        val out = HashMap<String, Char>()
        local.forEach { (path, sha) -> if (base[path] == null) out[path] = 'A' else if (base[path] != sha) out[path] = 'M' }
        base.keys.filter { it !in local }.forEach { out[it] = 'D' }
        return out
    }

    fun refreshStatus() = io { _changes.value = computeChanges() }

    /** What a Git operation covers. */
    sealed interface Scope {
        val label: String
        data object All : Scope { override val label = "all notebooks" }
        data class Book(val name: String) : Scope { override val label get() = name }
        data class Page(val ref: PageRef) : Scope { override val label get() = "${ref.notebook} / ${ref.name}" }
    }

    private fun predicate(scope: Scope): (String) -> Boolean = when (scope) {
        Scope.All -> { _ -> true }
        is Scope.Book -> { p -> p.startsWith("${scope.name}/") }
        is Scope.Page -> {
            val nb = scope.ref.notebook
            val assets = store.read(scope.ref).canvas.items.filterIsInstance<dev.kalpane.data.Picture>().map { "$nb/assets/${it.asset}" }.toSet()
            val own = setOf("$nb/${scope.ref.name}.md", "$nb/${scope.ref.name}.canvas.json", "$nb/notebook.json")
            ({ p -> p in own || p in assets })
        }
    }

    /** Changed files within [scope], for the commit dialog. */
    fun changesIn(scope: Scope): List<Pair<String, Char>> {
        val pageFiles = (scope as? Scope.Page)?.let { listOf("${it.ref.notebook}/${it.ref.name}.md", "${it.ref.notebook}/${it.ref.name}.canvas.json") }
        return _changes.value.filter { (path, _) ->
            when (scope) {
                Scope.All -> true
                is Scope.Book -> path.startsWith("${scope.name}/")
                is Scope.Page -> path in pageFiles!! || (path.startsWith("${scope.ref.notebook}/assets/"))
            }
        }.toList().sortedBy { it.first }
    }

    fun defaultMessage(scope: Scope) = when (scope) {
        Scope.All -> "Update notes"
        is Scope.Book -> "Update ${scope.name}"
        is Scope.Page -> "Update ${scope.ref.notebook}/${scope.ref.name}"
    }

    private fun client(): GitHubSync = services.client(_sync.value.branch)

    private fun git(label: String, block: () -> String?) {
        if (_busy.value != null) return
        _busy.value = label
        viewModelScope.launch {
            try {
                val message = disk {
                    val result = block()
                    reloadNotebooks()
                    _pages.value?.let { reloadPages(it.notebook) }
                    _changes.value = computeChanges()
                    result
                }
                message?.let { _message.value = it }
            } catch (e: Exception) {
                _message.value = e.message ?: "$label failed. Local changes are kept."
            } finally {
                _busy.value = null
            }
        }
    }

    private fun runGit(scope: Scope, mode: GitHubSync.Mode, message: String): String {
        val branch = _sync.value.branch
        val result = services.runGit(mode, message.ifBlank { defaultMessage(scope) }, predicate(scope))
        _sync.value = _sync.value.copy(lastSync = System.currentTimeMillis(), expiry = prefs.getString("tokenExpiry", null))
        val conflicts = if (result.conflicts > 0) " ${result.conflicts} conflicting ${if (result.conflicts == 1) "page" else "pages"} saved as “(conflict)”." else ""
        return when {
            mode == GitHubSync.Mode.Push && result.pushed > 0 ->
                "Pushed ${result.pushed} ${if (result.pushed == 1) "file" else "files"} to $branch · ${result.head.take(7)}.$conflicts"
            result.pulled > 0 -> "Pulled ${result.pulled} ${if (result.pulled == 1) "change" else "changes"} from $branch.$conflicts"
            else -> "Already up to date.$conflicts"
        }
    }

    fun pull(scope: Scope = Scope.All) = git("Pull") { runGit(scope, GitHubSync.Mode.Pull, "") }

    /** Pulls the same scope first, then commits and pushes it. */
    fun push(scope: Scope, message: String) = git("Push") { runGit(scope, GitHubSync.Mode.Push, message) }

    fun syncNow() = push(Scope.All, defaultMessage(Scope.All))

    /** Restores a page to its last synced version. */
    fun discard(ref: PageRef, onDone: () -> Unit) = viewModelScope.launch {
        try {
            disk {
                for (path in listOf("${ref.notebook}/${ref.name}.md", "${ref.notebook}/${ref.name}.canvas.json")) {
                    val file = File(store.root, path)
                    val bytes = services.baseBytes(path)
                    if (bytes == null) file.delete() else writeAtomic(file, bytes)
                }
                _changes.value = computeChanges()
                reloadPages(ref.notebook)
            }
            onDone()
        } catch (e: Exception) {
            _message.value = e.message ?: "Could not discard changes."
        }
    }

    /** The page as of the last pull or push, for the diff view. */
    suspend fun baseVersion(ref: PageRef): PageData = disk {
        val text = services.baseBytes("${ref.notebook}/${ref.name}.md")?.decodeToString() ?: ""
        val canvas = services.baseBytes("${ref.notebook}/${ref.name}.canvas.json")?.let {
            runCatching { dev.kalpane.data.canvasJson.decodeFromString(dev.kalpane.data.CanvasDoc.serializer(), it.decodeToString()) }.getOrNull()
        } ?: dev.kalpane.data.CanvasDoc()
        PageData(text, canvas)
    }

    /** The page as of [commit], or null if it did not exist then. */
    suspend fun versionAt(ref: PageRef, commit: String): PageData? = try {
        withContext(Dispatchers.IO) {
            val c = client()
            val text = c.fileAt("${ref.notebook}/${ref.name}.md", commit)
            val canvas = c.fileAt("${ref.notebook}/${ref.name}.canvas.json", commit)
            if (text == null && canvas == null) null
            else PageData(
                text?.decodeToString() ?: "",
                canvas?.let { runCatching { dev.kalpane.data.canvasJson.decodeFromString(dev.kalpane.data.CanvasDoc.serializer(), it.decodeToString()) }.getOrNull() }
                    ?: dev.kalpane.data.CanvasDoc(),
            )
        }
    } catch (e: Exception) {
        _message.value = e.message; null
    }

    suspend fun branches(): List<String> = try {
        withContext(Dispatchers.IO) { client().branches() }
    } catch (e: Exception) {
        _message.value = e.message; emptyList()
    }

    /** Switches branch. With local changes this is refused, as in `git checkout`. */
    fun switchBranch(name: String) = git("Switch branch") {
        val status = _sync.value
        if (name == status.branch) return@git null
        if (computeChanges().isNotEmpty()) throw SyncException("Push or discard your changes before switching branch.")
        val repo = status.repo!!
        val token = vault.get(repo) ?: throw SyncException("Add the remote again in Settings.")
        val branchClient = GitHubSync(repo, name, token)
        val base = branchClient.checkout(store.root)
        services.writeBase(repo, name, base)
        services.storeObjects(base, branchClient)
        prefs.edit().putString("syncBranch", name).apply()
        _sync.value = _sync.value.copy(branch = name)
        "Switched to $name."
    }

    /** Creates a branch from the current one and switches to it, keeping local changes (`git switch -c`). */
    fun createBranch(name: String) = git("Create branch") {
        val status = _sync.value
        val repo = status.repo!!
        client().createBranch(name.trim())
        val base = readBase(repo, status.branch)
        services.writeBase(repo, name.trim(), base)
        prefs.edit().putString("syncBranch", name.trim()).apply()
        _sync.value = _sync.value.copy(branch = name.trim())
        "Created and switched to ${name.trim()}."
    }

    suspend fun history(scope: Scope): List<GitHubSync.Commit> = try {
        withContext(Dispatchers.IO) {
            val paths = when (scope) {
                Scope.All -> listOf("")
                is Scope.Book -> listOf(scope.name)
                is Scope.Page -> listOf("${scope.ref.notebook}/${scope.ref.name}.md", "${scope.ref.notebook}/${scope.ref.name}.canvas.json")
            }
            client().history(paths)
        }
    } catch (e: Exception) {
        _message.value = e.message; emptyList()
    }

    /** Days until the token expires, if GitHub reported a date. */
    fun daysUntilExpiry(): Long? = _sync.value.expiry?.let {
        runCatching { java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), java.time.LocalDate.parse(it)) }.getOrNull()
    }

    init {
        refreshNotebooks()
        refreshTrash()
        refreshStatus()
        daysUntilExpiry()?.let { days ->
            if (_sync.value.repo != null && days <= 7) {
                _message.value = if (days < 0) "Your GitHub token has expired. Edit the remote in Settings."
                else "Your GitHub token expires in $days ${if (days == 1L) "day" else "days"}."
            }
        }
    }
}
