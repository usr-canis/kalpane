package dev.kalpane

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kalpane.data.*
import dev.kalpane.sync.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File

enum class Tool { Pen, Eraser, Select, Navigate, Text }
data class AppState(
    val loaded: Boolean = false, val fatalError: String? = null,
    val prefs: Preferences = Preferences(), val workspace: Workspace = Workspace(),
    val screen: String = "notebooks", val tool: Tool = Tool.Pen,
    val penColor: Int = 0xff252522.toInt(), val penWidth: Float = 3f,
    val focus: Boolean = false, val saving: Boolean = false, val busy: Boolean = false,
    val switchingWorkspace: Boolean = false,
    val message: String? = null, val connection: String = "Not connected", val branches: List<String> = emptyList(),
    val undoAvailable: Boolean = false, val redoAvailable: Boolean = false,
) {
    val notebook: Notebook? get() = workspace.document.notebooks.find { it.id == workspace.lastNotebook }
    val page: Page? get() = notebook?.pages?.find { it.id == workspace.lastPage }
}
class NotebookViewModel(app: Application) : AndroidViewModel(app) {
    val store = DiskStore(File(app.filesDir,"notebooks"))
    private val vault = CredentialVault(app)
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    private val writes = Channel<Pair<Long, suspend () -> Unit>>(Channel.UNLIMITED)
    @Volatile private var revision = 0L
    @Volatile private var saveFailure: Throwable? = null
    private val undo = mutableMapOf<String,ArrayDeque<Page>>()
    private val redo = mutableMapOf<String,ArrayDeque<Page>>()
    init {
        viewModelScope.launch(Dispatchers.IO) {
            for ((version,write) in writes) {
                runCatching { write() }.onSuccess {
                    saveFailure = null
                    _state.update { if (version == revision) it.copy(saving = false) else it }
                }.onFailure { error -> saveFailure = error; _state.update { it.copy(message = "Save failed: ${error.message}. Keep the app open and free storage.", saving = true) } }
            }
        }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { val p = store.readPreferences(); p to store.read(p.repo,p.branch) } }
                .onSuccess { (p,w) -> _state.value = AppState(loaded = true,prefs = p,workspace = w,penWidth = p.penWidth) }
                .onFailure { error -> _state.update { it.copy(loaded = true,fatalError = error.message ?: "Local data could not be opened.") } }
        }
    }
    private fun persist() {
        val s = _state.value; val version = ++revision
        _state.update { it.copy(saving = true) }
        writes.trySend(version to { store.save(s.prefs.repo,s.prefs.branch,s.workspace); store.savePreferences(s.prefs) })
    }
    private suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        writes.send(revision to {
            val failure = saveFailure
            if(failure == null) done.complete(Unit)
            else { done.completeExceptionally(failure); throw failure }
        }); done.await()
    }
    fun message(message: String?) { _state.update { it.copy(message = message) } }
    fun screen(screen: String) { _state.update { it.copy(screen = screen) } }
    fun tool(tool: Tool) { _state.update { it.copy(tool = tool) } }
    fun focus() { _state.update { it.copy(focus = !it.focus) } }
    fun width(width: Float) { _state.update { it.copy(penWidth = width) } }
    fun color(color: Int) {
        _state.update { it.copy(penColor = color,prefs = it.prefs.copy(recentColors = (listOf(color) + it.prefs.recentColors).distinct().take(10))) }; persist()
    }
    fun preferences(transform: (Preferences) -> Preferences) { _state.update { it.copy(prefs = transform(it.prefs)) }; persist() }
    fun createNotebook(name: String) {
        val n = Notebook(title = name.trim().ifBlank { "Untitled notebook" },pages = listOf(Page(title = "First thoughts",paper = _state.value.prefs.paper)))
        _state.update { it.copy(workspace = it.workspace.copy(document = it.workspace.document.copy(notebooks = it.workspace.document.notebooks + n),lastNotebook = n.id,lastPage = n.pages.first().id),screen = "pages") }; persist()
    }
    fun renameNotebook(id: String, name: String) {
        _state.update { it.copy(workspace = it.workspace.copy(document = it.workspace.document.copy(notebooks = it.workspace.document.notebooks.map { n -> if (n.id == id) n.copy(title = name.trim().ifBlank { n.title }) else n }))) }; persist()
    }
    fun openNotebook(id: String) {
        _state.update { s -> val n = s.workspace.document.notebooks.first { it.id == id }; s.copy(screen = "pages",workspace = s.workspace.copy(lastNotebook = id,lastPage = n.pages.find { it.id == s.workspace.lastPage }?.id ?: n.pages.firstOrNull()?.id)) }; persist()
    }
    fun openPage(id: String) { _state.update { it.copy(screen = "editor",workspace = it.workspace.copy(lastPage = id)) }; historyState(); persist() }
    fun createPage(name: String) {
        val s = _state.value; val n = s.notebook ?: return
        val p = Page(title = name.trim().ifBlank { "Page ${n.pages.size + 1}" },paper = s.prefs.paper)
        _state.update { it.copy(workspace = it.workspace.copy(document = it.workspace.document.copy(notebooks = it.workspace.document.notebooks.map { nb -> if(nb.id == n.id) nb.copy(pages = nb.pages + p) else nb }),lastPage = p.id),screen = "editor") }; historyState(); persist()
    }
    fun turn(delta: Int) { val s = _state.value; val n = s.notebook ?: return; val i = n.pages.indexOfFirst { it.id == s.page?.id }; n.pages.getOrNull(i + delta)?.let { openPage(it.id) } }
    fun reorder(page: Page, delta: Int) {
        val n = _state.value.notebook ?: return
        _state.update { it.copy(workspace = it.workspace.copy(document = it.workspace.document.copy(notebooks = it.workspace.document.notebooks.map { nb -> if(nb.id == n.id) page.movePageIn(nb,delta) else nb }))) }; persist()
    }
    fun edit(page: Page) {
        val before = _state.value.workspace.document.pages().find { it.id == page.id } ?: return
        if (before == page) return
        undo.getOrPut(page.id) { ArrayDeque() }.apply { addLast(before); if(size > 60) removeFirst() }; redo.remove(page.id)
        replace(page.copy(modified = System.currentTimeMillis()))
    }
    private fun replace(page: Page) { _state.update { it.copy(workspace = it.workspace.copy(document = it.workspace.document.replace(page))) }; historyState(); persist() }
    fun undo() { val p = _state.value.page ?: return; val previous = undo[p.id]?.removeLastOrNull() ?: return; redo.getOrPut(p.id) { ArrayDeque() }.addLast(p); replace(previous) }
    fun redo() { val p = _state.value.page ?: return; val next = redo[p.id]?.removeLastOrNull() ?: return; undo.getOrPut(p.id) { ArrayDeque() }.addLast(p); replace(next) }
    private fun historyState() { _state.update { it.copy(undoAvailable = !undo[it.page?.id].isNullOrEmpty(),redoAvailable = !redo[it.page?.id].isNullOrEmpty()) } }
    fun addText(text: String, existing: Layer? = null) {
        val p = _state.value.page ?: return
        val l = existing?.copy(text = text) ?: Layer(kind = LayerKind.Text,text = text,color = _state.value.penColor,textFace = _state.value.prefs.textFace,fontSize = _state.value.prefs.textSize)
        edit(p.copy(layers = if(existing == null) p.layers + l else p.layers.map { if(it.id == l.id) l else it }))
    }
    fun importImage(uri: Uri) {
        if(_state.value.busy) {message("Finish the current import or backup first.");return}
        val target = _state.value.page?.id ?: return
        _state.update {it.copy(busy=true)}
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                        val output=java.io.ByteArrayOutputStream()
                        val buffer=ByteArray(8192)
                        while(true) {
                            val count=stream.read(buffer)
                            if(count<0) break
                            require(output.size()+count<=20*1024*1024) { "Choose an image smaller than 20 MB." }
                            output.write(buffer,0,count)
                        }
                        val bytes=output.toByteArray()
                        require(bytes.size <= 20 * 1024 * 1024) { "Choose an image smaller than 20 MB." }; bytes
                    } ?: error("Image could not be opened.")
                    val size = runCatching {ImageAssets.dimensions(bytes)}.getOrElse {throw IllegalArgumentException("This image format is not supported.",it)}
                    store.importAsset(bytes) to (size.height.toFloat() / size.width)
                }
            }.onSuccess { (asset,ratio) ->
                val p = _state.value.workspace.document.pages().find { it.id == target } ?: return@onSuccess
                edit(p.copy(layers = p.layers + Layer(kind = LayerKind.Image,asset = asset,width = 420f,height = (420f * ratio).coerceIn(30f,1000f))))
                tool(Tool.Select); message("Image added. Drag to position; drag its corner to resize.")
            }.onFailure { message(it.message) }
            _state.update {it.copy(busy=false)}
        }
    }
    fun exportArchive(uri: Uri) {
        if(_state.value.busy) return
        val snapshot=_state.value.workspace.document
        _state.update {it.copy(busy=true)}
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val temp=File(getApplication<Application>().cacheDir,"portable-export.zip")
                    try {
                        temp.outputStream().use {PortableArchive.export(snapshot,store,it)}
                        getApplication<Application>().contentResolver.openOutputStream(uri,"wt")?.use {out->temp.inputStream().use {it.copyTo(out)}} ?: error("Could not open the export destination.")
                    } finally {temp.delete()}
                }
                message("Portable backup exported, including images. Credentials are excluded.")
            } catch(e: Exception) {if(e is CancellationException) throw e;message("Export failed: ${e.message}")}
            finally {_state.update {it.copy(busy=false)}}
        }
    }
    fun importArchive(uri: Uri) {
        if(_state.value.busy) return
        _state.update {it.copy(busy=true)}
        viewModelScope.launch {
            try {
                val imported=withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use {PortableArchive.import(store,it)} ?: error("Could not open backup.")
                }
                val notebooks=PortableArchive.asNewNotebooks(imported)
                _state.update {it.copy(workspace=it.workspace.copy(document=it.workspace.document.copy(notebooks=it.workspace.document.notebooks+notebooks)))}
                persist();message("Imported ${notebooks.size} notebooks as separate copies. Existing pages are unchanged.")
            } catch(e: Exception) {if(e is CancellationException) throw e;message("Import failed: ${e.message}")}
            finally {_state.update {it.copy(busy=false)}}
        }
    }
    private fun client(): GitHubBackup { val s = _state.value; return GitHubBackup(s.prefs.repo,vault.get(s.prefs.repo) ?: error("Enter and verify a credential first."),store) }
    private fun sync(switchingWorkspace: Boolean = false, block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, switchingWorkspace = switchingWorkspace) }
        viewModelScope.launch {
            try { flush(); block() } catch (e: Exception) { if(e is CancellationException) throw e; _state.update { it.copy(message = e.message,connection = "Action needed") } }
            finally { _state.update { it.copy(busy = false, switchingWorkspace = false) } }
        }
    }
    fun verify(repoInput: String, tokenInput: String) = sync(switchingWorkspace = true) {
        val repo = repoInput.trim().removePrefix("https://github.com/").removeSuffix("/").removeSuffix(".git")
        val token = tokenInput.trim().ifBlank { withContext(Dispatchers.IO) { vault.get(repo) } ?: error("Enter a fine-grained token scoped to this repository.") }
        val names = withContext(Dispatchers.IO) { GitHubBackup(repo,token,store).verify().also { vault.put(repo,token) } }
        val s = _state.value
        val branch = s.prefs.branch.takeIf { it in names } ?: names.first()
        val newWorkspace = withContext(Dispatchers.IO) {
            if (repo == s.prefs.repo && branch == s.prefs.branch) s.workspace
            else if(store.hasWorkspace(repo,branch)) store.read(repo,branch)
            else Workspace(document = s.workspace.document,lastNotebook = s.workspace.lastNotebook,lastPage = s.workspace.lastPage)
        }
        _state.update { it.copy(prefs = it.prefs.copy(repo = repo,branch = branch),workspace = newWorkspace,branches = names,connection = "Verified · private repository",message = "Connected. Pull before your first push.") }; persist()
        if(repo != s.prefs.repo || branch != s.prefs.branch) {undo.clear();redo.clear();historyState()}
    }
    fun push() = sync {
        val s = _state.value
        val newHead = withContext(Dispatchers.IO) { client().push(s.prefs.branch,s.workspace) }
        _state.update { it.copy(workspace = it.workspace.copy(base = s.workspace.document,remoteHead = newHead,lastBackup = System.currentTimeMillis()),connection = "Connected",message = "Backup pushed. Any newer edits remain pending.") }; persist()
    }
    fun pull() = sync {
        val branch = _state.value.prefs.branch
        val remote = withContext(Dispatchers.IO) { client().pull(branch) }
        val s = _state.value
        val merge = Merge.documents(s.workspace.base,s.workspace.document,remote.document)
        val n = merge.document.notebooks.find { it.id == s.workspace.lastNotebook }
        val lastPage = n?.pages?.find { it.id == s.workspace.lastPage }?.id
        _state.update { it.copy(workspace = it.workspace.copy(document = merge.document,base = remote.document,remoteHead = remote.head,lastBackup = System.currentTimeMillis(),lastNotebook = n?.id,lastPage = lastPage),screen = if(it.screen == "editor" && lastPage == null) {if(n == null) "notebooks" else "pages"} else it.screen,connection = "Connected",message = if(merge.conflicts == 0) "Pulled and merged. Local edits are preserved." else "${merge.conflicts} conflict(s). Both page versions are kept.") }
        undo.clear(); redo.clear(); historyState(); persist()
    }
    fun switchBranch(branch: String) = sync(switchingWorkspace = true) {
        require(GitHubBackup.validBranch(branch))
        val s = _state.value
        val next = withContext(Dispatchers.IO) {
            if(store.hasWorkspace(s.prefs.repo,branch)) store.read(s.prefs.repo,branch)
            else { val r = client().pull(branch); Workspace(document = r.document,base = r.document,remoteHead = r.head,lastBackup = System.currentTimeMillis()) }
        }
        _state.update { it.copy(prefs = it.prefs.copy(branch = branch),workspace = next,message = "Switched to $branch. Edits on ${s.prefs.branch} are kept locally.") }
        undo.clear(); redo.clear(); historyState(); persist()
    }
    fun createBranch(name: String) = sync {
        withContext(Dispatchers.IO) { client().createBranch(name,_state.value.prefs.branch) }
        _state.update { it.copy(branches = (it.branches + name).distinct(),message = "Created $name from the remote head. Use Switch branch to open it.") }
    }
    fun forgetCredential() { vault.remove(_state.value.prefs.repo); _state.update { it.copy(connection = "Credential removed") } }
}
