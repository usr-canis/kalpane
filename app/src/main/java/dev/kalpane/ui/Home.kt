package dev.kalpane.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kalpane.AppViewModel
import dev.kalpane.R
import dev.kalpane.data.NotebookInfo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MessageHost(vm: AppViewModel, snackbar: SnackbarHostState) {
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
}

@Composable
fun HomeScreen(vm: AppViewModel, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val notebooks by vm.notebooks.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val changes by vm.changes.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<String?>(null) } // "new", "rename:<name>", "delete:<name>"
    LaunchedEffect(Unit) { vm.refreshNotebooks() }
    MessageHost(vm, snackbar)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        floatingActionButton = {
            if (!notebooks.isNullOrEmpty()) {
                FloatingActionButton(
                    onClick = { dialog = "new" },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(2.dp, 2.dp, 2.dp, 2.dp),
                    modifier = Modifier.navigationBarsPadding(),
                ) { Icon(Icons.Filled.Add, contentDescription = "New notebook") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(R.drawable.kalpane_logo), contentDescription = null, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text("ಕಲ್ಪನೆ", fontFamily = Mallige, fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (sync.repo != null) GitBar(vm, onBranches = { dialog = "branches" }, onPush = { dialog = "push" })
            val list = notebooks
            when {
                list == null -> Unit
                list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ExtendedFloatingActionButton(
                        onClick = { dialog = "new" },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                        icon = { Icon(Icons.Filled.Add, null) },
                        text = { Text("New notebook") },
                    )
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(list, key = { it.name }) { book ->
                        NotebookCard(
                            book,
                            changes = if (sync.repo == null) null else changes.keys.count { it.startsWith("${book.name}/") },
                            onGit = { action ->
                                if (action == "pullbook") {
                                    vm.pull(AppViewModel.Scope.Book(book.name))
                                } else {
                                    dialog = "$action:${book.name}"
                                }
                            },
                            onOpen = { onOpen(book.name) },
                            onRename = { dialog = "rename:${book.name}" },
                            onDelete = { dialog = "delete:${book.name}" },
                        )
                    }
                }
            }
        }
    }

    val d = dialog
    when {
        d == null -> Unit
        d == "push" -> PushDialog(vm, AppViewModel.Scope.All) { dialog = null }
        d == "branches" -> BranchDialog(vm) { dialog = null }
        d.startsWith("pushbook:") -> PushDialog(vm, AppViewModel.Scope.Book(d.removePrefix("pushbook:"))) { dialog = null }
        d.startsWith("historybook:") -> HistoryDialog(vm, AppViewModel.Scope.Book(d.removePrefix("historybook:"))) { dialog = null }
        d == "new" -> NameDialog("New notebook", "", "Create", { dialog = null }) { name ->
            dialog = null
            vm.createNotebook(name, onOpen)
        }
        d.startsWith("rename:") -> NameDialog("Rename notebook", d.removePrefix("rename:"), "Rename", { dialog = null }) {
            vm.renameNotebook(d.removePrefix("rename:"), it); dialog = null
        }
        d.startsWith("delete:") -> {
            val name = d.removePrefix("delete:")
            val count = notebooks?.firstOrNull { it.name == name }?.pages ?: 0
            ConfirmDialog(
                "Delete notebook?",
                "“$name” and its $count ${if (count == 1) "page" else "pages"} will be deleted.",
                "Delete",
                { dialog = null },
            ) { vm.deleteNotebook(name); dialog = null }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotebookCard(
    book: NotebookInfo,
    changes: Int?,
    onGit: (String) -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Column {
        Box {
            Row(
                Modifier.fillMaxWidth().aspectRatio(0.78f).clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceContainer)
                    .combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
            ) {
                Box(Modifier.width(10.dp).fillMaxHeight().background(colors.primary))
                Box(Modifier.fillMaxSize().padding(16.dp)) {
                    if (changes != null && changes > 0) {
                        StatusBadge("$changes", MaterialTheme.colorScheme.primary, Modifier.align(Alignment.BottomEnd))
                    }
                    Text(
                        book.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (changes != null) {
                    DropdownMenuItem(text = { Text("Push notebook") }, onClick = { menu = false; onGit("pushbook") })
                    DropdownMenuItem(text = { Text("Pull notebook") }, onClick = { menu = false; onGit("pullbook") })
                    DropdownMenuItem(text = { Text("History") }, onClick = { menu = false; onGit("historybook") })
                    HorizontalDivider()
                }
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
            }
        }
        Text(
            "${book.pages} ${if (book.pages == 1) "page" else "pages"}  ·  ${formatDate(book.modified)}",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp),
        )
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val dayFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val yearFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun formatDate(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(millis).atZone(zone)
    val today = LocalDate.now(zone)
    return when {
        time.toLocalDate() == today -> timeFormat.format(time)
        time.toLocalDate() == today.minusDays(1) -> "Yesterday"
        time.year == today.year -> dayFormat.format(time)
        else -> yearFormat.format(time)
    }
}
