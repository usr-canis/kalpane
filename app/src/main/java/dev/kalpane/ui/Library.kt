package dev.kalpane.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kalpane.AppViewModel
import dev.kalpane.LibraryState
import dev.kalpane.R
import dev.kalpane.data.NoteInfo
import dev.kalpane.data.NoteRef
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private sealed interface LibraryDialog {
    data object NewNotebook : LibraryDialog
    data class RenameNotebook(val name: String) : LibraryDialog
    data class DeleteNotebook(val name: String) : LibraryDialog
    data class Move(val ref: NoteRef) : LibraryDialog
}

@Composable
fun LibraryScreen(vm: AppViewModel, onOpen: (NoteRef) -> Unit, onSettings: () -> Unit) {
    val state by vm.library.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<LibraryDialog?>(null) }

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    val deleteNote: (NoteRef) -> Unit = { ref ->
        vm.delete(ref)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar("Note deleted", actionLabel = "Undo") == SnackbarResult.ActionPerformed) vm.undoDelete()
        }
    }
    val notebookActions = NotebookActions(
        select = vm::select,
        rename = { dialog = LibraryDialog.RenameNotebook(it) },
        delete = { dialog = LibraryDialog.DeleteNotebook(it) },
        create = { dialog = LibraryDialog.NewNotebook },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { vm.newNote(onOpen) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(2.dp, 2.dp, 2.dp, 2.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) { Icon(Icons.Filled.Add, contentDescription = "New note") }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 720.dp
            Column(Modifier.fillMaxSize()) {
                Header(onSettings)
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        NotebookRail(state, notebookActions)
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(Modifier.weight(1f)) {
                            SearchField(state.query, vm::search, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                            NoteList(state, onOpen, deleteNote, { dialog = LibraryDialog.Move(it) })
                        }
                    }
                } else {
                    SearchField(state.query, vm::search, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    NotebookChips(state, notebookActions)
                    NoteList(state, onOpen, deleteNote, { dialog = LibraryDialog.Move(it) })
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        LibraryDialog.NewNotebook -> NameDialog("New notebook", "", "Create", { dialog = null }) {
            vm.createNotebook(it); dialog = null
        }
        is LibraryDialog.RenameNotebook -> NameDialog("Rename notebook", d.name, "Rename", { dialog = null }) {
            vm.renameNotebook(d.name, it); dialog = null
        }
        is LibraryDialog.DeleteNotebook -> {
            val count = state.counts[d.name] ?: 0
            ConfirmDialog(
                title = "Delete notebook?",
                body = if (count == 0) "“${d.name}” will be deleted." else "“${d.name}” and its $count ${if (count == 1) "note" else "notes"} will be deleted.",
                confirm = "Delete",
                onDismiss = { dialog = null },
            ) { vm.deleteNotebook(d.name); dialog = null }
        }
        is LibraryDialog.Move -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Move to") },
            text = {
                Column {
                    state.notebooks.filter { it != d.ref.notebook }.forEach { book ->
                        Text(
                            book,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { vm.move(d.ref, book); dialog = null }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}

private class NotebookActions(
    val select: (String?) -> Unit,
    val rename: (String) -> Unit,
    val delete: (String) -> Unit,
    val create: () -> Unit,
)

@Composable
private fun Header(onSettings: () -> Unit) {
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
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainer)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onChange("") }) {
                Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Wraps a notebook entry with a long-press menu. "All" (null) has no menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotebookEntry(
    name: String?,
    actions: NotebookActions,
    content: @Composable (Modifier) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        content(
            Modifier.combinedClickable(
                onClick = { actions.select(name) },
                onLongClick = { if (name != null) menu = true },
            )
        )
        if (name != null) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; actions.rename(name) })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; actions.delete(name) })
            }
        }
    }
}

@Composable
private fun NotebookChips(state: LibraryState, actions: NotebookActions) {
    val colors = MaterialTheme.colorScheme
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(listOf<String?>(null) + state.notebooks, key = { it ?: "\u0000all" }) { book ->
            val selected = state.selected == book
            NotebookEntry(book, actions) { click ->
                Text(
                    book ?: "All",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.onPrimary else colors.onSurface,
                    modifier = Modifier.clip(CircleShape)
                        .background(if (selected) colors.primary else colors.surfaceContainer)
                        .then(click)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        item(key = "\u0000new") {
            Box(
                Modifier.clip(CircleShape).background(colors.surfaceContainer).clickable(onClick = actions.create)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New notebook", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun NotebookRail(state: LibraryState, actions: NotebookActions) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        Modifier.width(248.dp).fillMaxHeight(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        items(listOf<String?>(null) + state.notebooks, key = { it ?: "\u0000all" }) { book ->
            val selected = state.selected == book
            val count = if (book == null) state.counts.values.sum() else state.counts[book] ?: 0
            NotebookEntry(book, actions) { click ->
                Row(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.primary.copy(alpha = 0.12f) else colors.background)
                        .then(click).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        book ?: "All notes",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        color = if (selected) colors.primary else colors.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("$count", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
        }
        item(key = "\u0000new") {
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = actions.create)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New notebook", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NoteList(state: LibraryState, onOpen: (NoteRef) -> Unit, onDelete: (NoteRef) -> Unit, onMove: (NoteRef) -> Unit) {
    if (state.loaded && state.notes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (state.query.isBlank()) "No notes" else "No results",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp)) {
        items(state.notes, key = { it.ref.encode() }) { note ->
            NoteRow(note, showNotebook = state.selected == null, state.notebooks.size > 1, onOpen, onDelete, onMove)
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    note: NoteInfo,
    showNotebook: Boolean,
    canMove: Boolean,
    onOpen: (NoteRef) -> Unit,
    onDelete: (NoteRef) -> Unit,
    onMove: (NoteRef) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = { onOpen(note.ref) }, onLongClick = { menu = true })
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                note.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (note.preview.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    note.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                formatDate(note.modified) + if (showNotebook) "  ·  ${note.ref.notebook}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (canMove) DropdownMenuItem(text = { Text("Move to…") }, onClick = { menu = false; onMove(note.ref) })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete(note.ref) })
        }
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
