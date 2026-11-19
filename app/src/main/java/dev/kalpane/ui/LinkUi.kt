package dev.kalpane.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.kalpane.AppViewModel
import dev.kalpane.R
import dev.kalpane.data.PageRef

@Composable
private fun PageRow(ref: PageRef, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp)) {
        Text(ref.name, style = MaterialTheme.typography.bodyLarge)
        Text(ref.notebook, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Pages that link here with `[[...]]`. */
@Composable
fun BacklinksDialog(vm: AppViewModel, ref: PageRef, onOpen: (PageRef) -> Unit, onDismiss: () -> Unit) {
    var links by remember { mutableStateOf<List<PageRef>?>(null) }
    LaunchedEffect(ref) { links = vm.backlinks(ref) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { OctIcon(R.drawable.oct_cross_reference, null) },
        title = { Text("Backlinks") },
        text = {
            val list = links
            when {
                list == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Text("No pages link here yet. Link to this page with [[${ref.name}]].", style = MaterialTheme.typography.bodyMedium)
                else -> LazyColumn(Modifier.heightIn(max = 360.dp)) { items(list, key = { it.encode() }) { r -> PageRow(r) { onOpen(r) } } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Picks a page to insert as `[[Page]]` (or `[[Notebook/Page]]` across notebooks). */
@Composable
fun LinkPickerDialog(vm: AppViewModel, notebook: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var pages by remember { mutableStateOf<List<PageRef>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { pages = vm.allPages() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { OctIcon(R.drawable.oct_link, null) },
        title = { Text("Link to page") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text("Page name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val list = pages?.filter { query.isBlank() || it.name.contains(query, true) || it.notebook.contains(query, true) }
                if (list == null) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp).padding(top = 8.dp)) {
                        items(list, key = { it.encode() }) { r ->
                            PageRow(r) { onPick(if (r.notebook == notebook) r.name else "${r.notebook}/${r.name}") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            // A name that does not exist yet becomes a link to a new page when tapped.
            TextButton(onClick = { onPick(query.trim()) }, enabled = query.isNotBlank()) { Text("Link “${query.trim()}”") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
