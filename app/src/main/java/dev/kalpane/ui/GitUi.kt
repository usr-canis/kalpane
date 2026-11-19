package dev.kalpane.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kalpane.AppViewModel
import dev.kalpane.R
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.res.painterResource
import dev.kalpane.sync.GitHubSync
import java.time.OffsetDateTime

/** Git status colours: modified amber, added green, deleted red. */
@Composable
fun statusColor(c: Char) = when (c) {
    'A' -> Color(0xFF2E7D32)
    'D' -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}

/** A small "M" / "A" / "D" or count pill. */
@Composable
fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

/** The page's combined status from its text and canvas files. */
fun pageStatus(changes: Map<String, Char>, notebook: String, page: String): Char? {
    val s = listOfNotNull(changes["$notebook/$page.md"], changes["$notebook/$page.canvas.json"])
    return when {
        s.isEmpty() -> null
        'A' in s -> 'A'
        s.all { it == 'D' } -> 'D'
        else -> 'M'
    }
}

/** Branch, change count, pull and push for the whole repository. */
@Composable
fun GitBar(vm: AppViewModel, onBranches: () -> Unit, onPush: () -> Unit) {
    val sync by vm.sync.collectAsStateWithLifecycle()
    val changes by vm.changes.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp).height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainer).clickable(onClick = onBranches)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OctIcon(R.drawable.oct_git_branch, null, Modifier.size(16.dp), colors.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(sync.branch, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                when (changes.size) { 0 -> "No changes"; 1 -> "1 change"; else -> "${changes.size} changes" },
                style = MaterialTheme.typography.labelMedium,
                color = if (changes.isEmpty()) colors.onSurfaceVariant else colors.primary,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.pull() }, enabled = busy == null) {
                OctIcon(R.drawable.oct_repo_pull, "Pull from origin", tint = colors.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onPush, enabled = busy == null) {
                OctIcon(R.drawable.oct_repo_push, null, Modifier.size(18.dp), LocalContentColor.current)
                Spacer(Modifier.width(6.dp))
                Text("Push")
            }
        }
        if (busy != null) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        else HorizontalDivider(color = colors.outlineVariant)
    }
}

/** "Commit & push" with the changed files and a commit message. */
@Composable
fun PushDialog(vm: AppViewModel, scope: AppViewModel.Scope, onDismiss: () -> Unit) {
    val sync by vm.sync.collectAsStateWithLifecycle()
    val changes = remember(scope) { vm.changesIn(scope) }
    var message by remember { mutableStateOf(vm.defaultMessage(scope)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { OctIcon(R.drawable.oct_git_commit, null) },
        title = { Text("Commit & push") },
        text = {
            Column {
                Text(
                    "${scope.label} → origin/${sync.branch}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (changes.isEmpty()) {
                    Text("Nothing to commit. This pulls the latest from origin/${sync.branch}.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(Modifier.heightIn(max = 200.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(changes) { (path, status) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OctIcon(diffIcon(status), status.toString(), Modifier.size(16.dp), statusColor(status))
                                Spacer(Modifier.width(8.dp))
                                Text(path, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(message, { message = it }, label = { Text("Commit summary") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.push(scope, message); onDismiss() }) { Text(if (changes.isEmpty()) "Pull from origin" else "Push to origin") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun HistoryDialog(vm: AppViewModel, scope: AppViewModel.Scope, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var commits by remember { mutableStateOf<List<GitHubSync.Commit>?>(null) }
    LaunchedEffect(scope) { commits = vm.history(scope) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { OctIcon(R.drawable.oct_history, null) },
        title = { Text("History") },
        text = {
            val list = commits
            when {
                list == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Text("No commits yet.", style = MaterialTheme.typography.bodyMedium)
                else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(list, key = { it.sha }) { c ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.url))) }
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                        ) {
                            Text(c.message, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val date = runCatching { formatDate(OffsetDateTime.parse(c.date).toInstant().toEpochMilli()) }.getOrDefault(c.date)
                            Text(
                                "${c.sha.take(7)} · $date",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun BranchDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val sync by vm.sync.collectAsStateWithLifecycle()
    val changes by vm.changes.collectAsStateWithLifecycle()
    var branches by remember { mutableStateOf<List<String>?>(null) }
    var newName by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { branches = vm.branches() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { OctIcon(R.drawable.oct_git_branch, null) },
        title = { Text("Branches") },
        text = {
            Column {
                val list = branches
                if (list == null) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(list) { b ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .clickable { vm.switchBranch(b); onDismiss() }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = b == sync.branch, onClick = null, modifier = Modifier.padding(8.dp))
                                Text(b, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                if (changes.isNotEmpty()) {
                    Text(
                        "You have ${changes.size} unpushed ${if (changes.size == 1) "change" else "changes"}. Push first to switch, or create a new branch to keep them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        newName, { newName = it.replace(" ", "-") },
                        label = { Text("New branch from ${sync.branch}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.createBranch(newName); onDismiss() }, enabled = newName.isNotBlank()) { Text("Create") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** A GitHub Octicon (bundled under the MIT licence). */
@Composable
fun OctIcon(res: Int, description: String?, modifier: Modifier = Modifier.size(20.dp), tint: Color = LocalContentColor.current) {
    Icon(painterResource(res), description, modifier, tint)
}

fun diffIcon(status: Char) = when (status) {
    'A' -> R.drawable.oct_diff_added
    'D' -> R.drawable.oct_diff_removed
    else -> R.drawable.oct_diff_modified
}
