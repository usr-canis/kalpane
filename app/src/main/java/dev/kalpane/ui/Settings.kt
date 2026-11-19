package dev.kalpane.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kalpane.AppViewModel
import dev.kalpane.BuildConfig
import dev.kalpane.ThemeMode

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val fingerDrawing by vm.fingerDrawing.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Settings", onBack = onBack)
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        ) {
            SectionLabel("Theme")
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth().height(52.dp)
                        .selectable(selected = theme == mode, role = Role.RadioButton) { vm.setTheme(mode) }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(mode.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    RadioButton(selected = theme == mode, onClick = null)
                }
            }
            SectionLabel("Drawing")
            Row(
                Modifier.fillMaxWidth().height(64.dp)
                    .toggleable(value = fingerDrawing, role = Role.Switch) { vm.setFingerDrawing(it) }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Draw with finger", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "When off, fingers type and scroll; the stylus draws.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = fingerDrawing, onCheckedChange = null)
            }
            SyncSection(vm)
            SectionLabel("About")
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}
