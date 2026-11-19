package dev.kalpane.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kalpane.AppViewModel
import dev.kalpane.data.TrashEntry

@Composable
fun TrashScreen(vm: AppViewModel, onBack: () -> Unit) {
    val items by vm.trash.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf<TrashEntry?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshTrash() }
    MessageHost(vm, snackbar)
    BackHandler(onBack = onBack)
    val colors = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        topBar = {
            TopBar(title = "Trash", onBack = onBack) {
                if (items.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text("Empty") }
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Trash is empty", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 48.dp)) {
                item {
                    Text(
                        "Items are deleted permanently after 30 days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                items(items, key = { it.id }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (entry.kind == "notebook") Icons.Outlined.Book else Icons.Outlined.Description,
                            null, tint = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (entry.kind == "notebook") "Notebook" else entry.notebook) + " · deleted " + formatDate(entry.deletedAt),
                                style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { vm.restore(entry) }) { Text("Restore") }
                        IconButton(onClick = { confirm = entry }) {
                            Icon(Icons.Outlined.DeleteForever, "Delete forever", tint = colors.error)
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = colors.outlineVariant)
                }
            }
        }
    }
    confirm?.let { entry ->
        ConfirmDialog("Delete forever?", "“${entry.name}” cannot be recovered.", "Delete", { confirm = null }) {
            vm.deleteForever(entry); confirm = null
        }
    }
    if (confirmEmpty) ConfirmDialog("Empty trash?", "Everything in trash is deleted permanently.", "Empty", { confirmEmpty = false }) {
        vm.emptyTrash(); confirmEmpty = false
    }
}
