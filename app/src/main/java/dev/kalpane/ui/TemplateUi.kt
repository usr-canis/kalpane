package dev.kalpane.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import dev.kalpane.AppViewModel
import dev.kalpane.data.MarkdownText
import dev.kalpane.data.PageInfo
import dev.kalpane.data.PageRef
import dev.kalpane.data.Template
import dev.kalpane.data.Templates

/** "New page": pick a built-in or your own template. */
@Composable
fun TemplateDialog(vm: AppViewModel, onPick: (Template) -> Unit, onDismiss: () -> Unit) {
    var templates by remember { mutableStateOf<List<Template>?>(null) }
    LaunchedEffect(Unit) { templates = vm.templates() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New page") },
        text = {
            val list = templates
            if (list == null) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 460.dp),
                ) {
                    val own = list.filter { !it.builtIn }
                    items(list.filter { it.builtIn }, key = { "b:" + it.name }) { t -> TemplateTile(t) { onPick(t) } }
                    if (own.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("Your templates", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        }
                        items(own, key = { "u:" + it.name }) { t -> TemplateTile(t) { onPick(t) } }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TemplateTile(t: Template, onClick: () -> Unit) {
    val data = Templates.instantiate(t)
    val info = PageInfo(PageRef(Templates.NOTEBOOK, t.name), MarkdownText.title(data.text), data.canvas.items, 0L)
    Column {
        PageThumbnail(info, Modifier.fillMaxWidth(), onClick = onClick)
        Text(t.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp))
    }
}
