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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kalpane.AppViewModel
import dev.kalpane.BuildConfig
import dev.kalpane.ThemeMode
import dev.kalpane.PageDefaults
import dev.kalpane.data.PageStyle
import dev.kalpane.data.Paper

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
            SectionLabel("Pages")
            val defaults by vm.defaults.collectAsStateWithLifecycle()
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                PageStyleControls(PageStyle(defaults.font, defaults.size, defaults.paper)) {
                    vm.setDefaults(PageDefaults(it.font ?: "Sans", it.size ?: 17f, it.paper ?: Paper.Plain))
                }
            }
            SectionLabel("Drawing")
            val handwriting by vm.handwriting.collectAsStateWithLifecycle()
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Handwriting language", style = MaterialTheme.typography.bodyLarge)
                    Text("For converting handwriting to text, on device", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                dev.kalpane.ai.HandwritingLanguage.entries.forEachIndexed { i, l ->
                    SegmentedButton(
                        selected = handwriting == l, onClick = { vm.setHandwriting(l) },
                        shape = SegmentedButtonDefaults.itemShape(i, dev.kalpane.ai.HandwritingLanguage.entries.size), icon = {},
                    ) { Text(l.label) }
                }
            }
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
            var crash by remember { mutableStateOf(vm.crashLog()) }
            var showCrash by remember { mutableStateOf(false) }
            crash?.let { log ->
                TextButton(onClick = { showCrash = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("View last crash report") }
                if (showCrash) {
                    val clipboard = LocalClipboardManager.current
                    AlertDialog(
                        onDismissRequest = { showCrash = false },
                        title = { Text("Last crash") },
                        text = {
                            Text(log, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()))
                        },
                        confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(log)); showCrash = false }) { Text("Copy") } },
                        dismissButton = { TextButton(onClick = { vm.clearCrashLog(); crash = null; showCrash = false }) { Text("Delete") } },
                    )
                }
            }
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
