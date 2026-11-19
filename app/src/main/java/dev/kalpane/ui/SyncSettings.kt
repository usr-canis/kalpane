package dev.kalpane.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kalpane.AppViewModel
import dev.kalpane.R
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily

private const val TOKEN_PAGE = "https://github.com/settings/personal-access-tokens/new"

@Composable
fun SyncSection(vm: AppViewModel) {
    val status by vm.sync.collectAsStateWithLifecycle()
    var connecting by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    SectionLabel("Remote")
    val repo = status.repo
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        if (repo == null) {
            Text(
                "Use a private GitHub repository as origin for your notebooks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { connecting = true }) {
                OctIcon(R.drawable.oct_mark_github, null, Modifier.size(18.dp), LocalContentColor.current)
                Spacer(Modifier.width(8.dp))
                Text("Add remote")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OctIcon(R.drawable.oct_repo, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("origin", style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                Text(repo, style = MaterialTheme.typography.bodyLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                OctIcon(R.drawable.oct_git_branch, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(status.branch, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            }
            val details = listOfNotNull(
                status.lastSync?.let { "Last pull or push ${formatDate(it)}" } ?: "Never pushed",
                status.expiry?.let { "token expires $it" },
            ).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val auto by vm.autoBackup.collectAsStateWithLifecycle()
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).toggleable(value = auto, role = Role.Switch) { vm.setAutoBackup(it) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Auto push", style = MaterialTheme.typography.bodyLarge)
                    Text("Pull and push to origin every 6 hours on Wi-Fi", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = auto, onCheckedChange = null)
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = vm::syncNow, enabled = !status.running) {
                    if (status.running) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Pull & push")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { connecting = true }) { Text("Edit remote") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { confirmDisconnect = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove remote") }
            }
        }
    }
    if (connecting) ConnectDialog(vm, status.repo.orEmpty(), status.branch) { connecting = false }
    if (confirmDisconnect) ConfirmDialog(
        "Remove remote?",
        "The token is removed from this device. Notebooks stay here and on GitHub.",
        "Remove",
        { confirmDisconnect = false },
    ) { vm.disconnect(); confirmDisconnect = false }
}

@Composable
private fun ConnectDialog(vm: AppViewModel, initialRepo: String, initialBranch: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var repo by remember { mutableStateOf(initialRepo) }
    var branch by remember { mutableStateOf(initialBranch) }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initialRepo.isEmpty()) "Add remote" else "Edit remote") },
        text = {
            Column {
                Text(
                    "Create a fine-grained token for one private repository with Contents: read and write.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TOKEN_PAGE))) },
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) { Text("Create a token on GitHub") }
                OutlinedTextField(repo, { repo = it }, label = { Text("Repository (owner/name)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(branch, { branch = it }, label = { Text("Branch") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    token, { token = it },
                    label = { Text("Personal access token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && repo.isNotBlank() && branch.isNotBlank() && token.isNotBlank(),
                onClick = {
                    busy = true; error = null
                    vm.connect(repo, branch, token) { e ->
                        busy = false
                        if (e == null) {
                            onDismiss()
                        } else {
                            error = e
                        }
                    }
                },
            ) { Text(if (busy) "Checking…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
