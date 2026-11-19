package dev.kalpane.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kalpane.AppViewModel
import dev.kalpane.data.NoteRef
import kotlinx.coroutines.delay

@Composable
fun EditorScreen(vm: AppViewModel, ref: NoteRef, onClose: () -> Unit) {
    var value by remember(ref) { mutableStateOf<TextFieldValue?>(null) }
    var menu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(ref) {
        val text = vm.load(ref)
        value = TextFieldValue(text)
        if (text.isEmpty()) focus.requestFocus()
    }
    // Autosave shortly after typing pauses.
    val text = value?.text
    LaunchedEffect(text) {
        if (text != null) {
            delay(400)
            vm.save(ref, text)
        }
    }
    val close = {
        value?.let { vm.close(ref, it.text) }
        onClose()
    }
    BackHandler(onBack = close)

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(title = ref.notebook, onBack = close) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Delete") }, onClick = {
                        menu = false
                        value = null
                        vm.delete(ref)
                        onClose()
                    })
                }
            }
        }
        val current = value
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (current != null) {
                BasicTextField(
                    value = current,
                    onValueChange = { value = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        lineHeight = 27.sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().fillMaxSize()
                        .focusRequester(focus)
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                )
            }
        }
    }
}
