package dev.kalpane.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kalpane.AppViewModel
import dev.kalpane.R
import dev.kalpane.data.PAGE_WIDTH
import dev.kalpane.data.PageInfo
import dev.kalpane.data.PageRef

@Composable
fun PagesScreen(vm: AppViewModel, notebook: String, onOpen: (PageRef) -> Unit, onBack: () -> Unit) {
    val state by vm.pages.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val changes by vm.changes.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<Pair<String, PageRef>?>(null) }
    var version by remember { mutableStateOf<Pair<PageRef, dev.kalpane.sync.GitHubSync.Commit>?>(null) }
    version?.let { (ref, c) -> VersionDialog(vm, ref, c, onRestore = { vm.save(ref, it) }) { version = null } }
    LaunchedEffect(notebook) { vm.openNotebook(notebook) }
    MessageHost(vm, snackbar)
    BackHandler(onBack = onBack)
    val pages = state?.takeIf { it.notebook == notebook }?.pages

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        topBar = {
            Column {
                TopBar(title = notebook, onBack = onBack) {
                    if (sync.repo != null) {
                        IconButton(onClick = { vm.pull(AppViewModel.Scope.Book(notebook)) }, enabled = busy == null) {
                            OctIcon(R.drawable.oct_repo_pull, "Pull notebook", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { dialog = "push" to PageRef(notebook, "") }, enabled = busy == null) {
                            OctIcon(R.drawable.oct_repo_push, "Push notebook", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { dialog = "historybook" to PageRef(notebook, "") }) {
                            OctIcon(R.drawable.oct_history, "Notebook history", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (busy != null) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { dialog = "new" to PageRef(notebook, "") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(2.dp, 2.dp, 2.dp, 2.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) { Icon(Icons.Filled.Add, contentDescription = "New page") }
        },
    ) { padding ->
        if (pages != null) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(pages, key = { it.ref.name }) { page ->
                    PageCard(
                        page,
                        status = if (sync.repo == null) null else pageStatus(changes, notebook, page.ref.name),
                        connected = sync.repo != null,
                        first = page == pages.first(),
                        last = page == pages.last(),
                        onOpen = { onOpen(page.ref) },
                        onAction = { action ->
                            if (action.startsWith("move")) {
                                vm.movePage(page.ref, if (action == "move-") -1 else 1)
                            } else {
                                dialog = action to page.ref
                            }
                        },
                    )
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        else -> when (d.first) {
            "new" -> TemplateDialog(vm, onPick = { t -> dialog = null; vm.createPage(notebook, t, onOpen) }) { dialog = null }
            "push" -> PushDialog(vm, AppViewModel.Scope.Book(notebook)) { dialog = null }
            "historybook" -> HistoryDialog(vm, AppViewModel.Scope.Book(notebook)) { dialog = null }
            "pushpage" -> PushDialog(vm, AppViewModel.Scope.Page(d.second)) { dialog = null }
            "history" -> HistoryDialog(vm, AppViewModel.Scope.Page(d.second), onCommit = { version = d.second to it; dialog = null }) { dialog = null }
            "rename" -> NameDialog("Rename page", d.second.name, "Rename", { dialog = null }) { vm.renamePage(d.second, it); dialog = null }
            "delete" -> ConfirmDialog("Move to trash?", "“${d.second.name}” moves to trash. You can restore it for 30 days.", "Move to trash", { dialog = null }) {
                vm.deletePage(d.second); dialog = null
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageCard(
    page: PageInfo,
    status: Char?,
    connected: Boolean,
    first: Boolean,
    last: Boolean,
    onOpen: () -> Unit,
    onAction: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val paper = if (dark) colors.surfaceContainer else Color.White
    val cache = remember { PathCache() }
    var menu by remember { mutableStateOf(false) }
    Column {
        Box {
            PageThumbnail(page, Modifier.fillMaxWidth(), onLongClick = { menu = true }, onClick = onOpen)
            if (status != null) StatusBadge(status.toString(), statusColor(status), Modifier.align(Alignment.TopEnd).padding(8.dp))
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (connected) {
                    DropdownMenuItem(text = { Text("Push page") }, onClick = { menu = false; onAction("pushpage") })
                    DropdownMenuItem(text = { Text("History") }, onClick = { menu = false; onAction("history") })
                    HorizontalDivider()
                }
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onAction("rename") })
                if (!first) DropdownMenuItem(text = { Text("Move earlier") }, onClick = { menu = false; onAction("move-") })
                if (!last) DropdownMenuItem(text = { Text("Move later") }, onClick = { menu = false; onAction("move+") })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onAction("delete") })
            }
        }
        Text(
            page.ref.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp),
        )
        Text(formatDate(page.modified), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
    }
}

/** A small rendering of a page: its first lines of text and its drawings. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageThumbnail(page: PageInfo, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val paper = if (dark) colors.surfaceContainer else Color.White
    val cache = remember { PathCache() }
    BoxWithConstraints(
        modifier.aspectRatio(0.72f).clip(RoundedCornerShape(10.dp))
            .semantics { contentDescription = "${page.ref.notebook}, ${page.ref.name}" }
            .background(paper)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        val k = maxWidth / PAGE_WIDTH.dp // thumbnail dp per page unit
        if (page.preview.isNotEmpty()) {
            Text(
                page.preview,
                // Readable at thumbnail size rather than strictly to scale.
                fontSize = maxOf(17 * k, 7f).sp,
                lineHeight = maxOf(27 * k, 10f).sp,
                color = colors.onSurfaceVariant,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(horizontal = (56 * k).dp, vertical = (40 * k).dp),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val s = size.width / PAGE_WIDTH
            clipRect {
                withTransform({ scale(s, s, Offset.Zero) }) {
                    page.items.forEach { drawItem(it, colors.onSurface, cache, emptyMap(), colors.surfaceContainerHigh) }
                }
            }
        }
    }
}
