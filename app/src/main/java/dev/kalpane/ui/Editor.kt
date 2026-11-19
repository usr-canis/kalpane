package dev.kalpane.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kalpane.AppViewModel
import dev.kalpane.data.Edit
import dev.kalpane.data.ListKind
import dev.kalpane.data.MarkdownEdit
import dev.kalpane.data.NoteRef
import kotlinx.coroutines.delay

private fun TextFieldValue.toEdit() = Edit(text, selection.start, selection.end)
private fun Edit.toValue() = TextFieldValue(text, TextRange(start, end))

/** Undo history of text snapshots; quick successive edits merge into one step. */
private class History {
    private val undo = ArrayDeque<TextFieldValue>()
    private val redo = ArrayDeque<TextFieldValue>()
    private var lastPush = 0L
    var version by mutableStateOf(0)
        private set

    fun record(before: TextFieldValue, merge: Boolean) {
        val now = System.currentTimeMillis()
        if (!merge || now - lastPush > 900 || undo.isEmpty()) {
            undo.addLast(before)
            if (undo.size > 300) undo.removeFirst()
        }
        lastPush = now
        redo.clear()
        version++
    }

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    fun undo(current: TextFieldValue): TextFieldValue? = undo.removeLastOrNull()?.also {
        redo.addLast(current); lastPush = 0; version++
    }

    fun redo(current: TextFieldValue): TextFieldValue? = redo.removeLastOrNull()?.also {
        undo.addLast(current); lastPush = 0; version++
    }
}

@Composable
fun EditorScreen(vm: AppViewModel, ref: NoteRef, onClose: () -> Unit) {
    var value by remember(ref) { mutableStateOf<TextFieldValue?>(null) }
    var focused by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val history = remember(ref) { History() }
    val focus = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme
    val mono = appFont(mono = true)
    val palette = remember(colors) {
        MarkdownPalette(
            text = colors.onSurface,
            muted = colors.onSurfaceVariant,
            syntax = colors.onSurfaceVariant.copy(alpha = 0.55f),
            accent = colors.primary,
            codeBackground = colors.surfaceContainerHigh,
            rule = colors.outline,
        )
    }

    LaunchedEffect(ref) {
        val text = vm.load(ref)
        if (text.isEmpty()) {
            value = TextFieldValue("# ", TextRange(2))
            focus.requestFocus()
        } else {
            value = TextFieldValue(text)
        }
    }
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

    fun apply(next: TextFieldValue, merge: Boolean = false) {
        val old = value ?: return
        if (next.text != old.text) history.record(old, merge)
        value = next
    }
    fun edit(transform: (Edit) -> Edit) {
        val old = value ?: return
        apply(transform(old.toEdit()).toValue())
    }

    fun onChange(new: TextFieldValue) {
        val old = value ?: return
        if (new.text == old.text) {
            // A tap on a rendered checkbox toggles it and leaves the cursor where it was.
            val at = new.selection.start
            if (new.selection.collapsed && at != old.selection.start) {
                val lineStart = MarkdownEdit.lineStart(new.text, at)
                val oldLine = MarkdownEdit.lineStart(old.text, old.selection.start)
                val box = Regex("""^\s*[-*+] \[[ xX]]""").find(new.text.substring(lineStart, MarkdownEdit.lineEnd(new.text, lineStart)))
                if ((lineStart != oldLine || !focused) && box != null && at - lineStart in box.range.first..box.range.last + 2) {
                    MarkdownEdit.toggleTask(new.text, lineStart)?.let { toggled ->
                        apply(old.copy(text = toggled, composition = null))
                        return
                    }
                }
            }
            value = new
            return
        }
        val inserted = new.text.length == old.text.length + 1 && new.selection.collapsed &&
            new.selection.start > 0 && new.text[new.selection.start - 1] == '\n'
        if (inserted) {
            MarkdownEdit.onNewline(Edit(new.text, new.selection.start))?.let {
                apply(it.toValue())
                return
            }
        }
        apply(new, merge = !inserted)
    }

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
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            if (current != null) {
                val reveal = if (focused) revealRange(current.text, current.selection.start, current.selection.end) else null
                BasicTextField(
                    value = current,
                    onValueChange = ::onChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = colors.onSurface,
                        fontSize = 17.sp,
                        lineHeight = 27.sp,
                    ),
                    visualTransformation = MarkdownTransformation(reveal, palette, mono),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxSize()
                        .focusRequester(focus)
                        .onFocusChanged { focused = it.isFocused }
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp, bottom = 24.dp),
                )
            }
        }
        if (focused && current != null) {
            FormatBar(
                canUndo = history.version >= 0 && history.canUndo,
                canRedo = history.version >= 0 && history.canRedo,
                onUndo = { history.undo(current)?.let { value = it } },
                onRedo = { history.redo(current)?.let { value = it } },
                onHeading = { edit(MarkdownEdit::cycleHeading) },
                onWrap = { open, closeMark -> edit { MarkdownEdit.toggleWrap(it, open, closeMark) } },
                onLink = { edit(MarkdownEdit::link) },
                onList = { kind -> edit { MarkdownEdit.toggleList(it, kind) } },
                onQuote = { edit { MarkdownEdit.toggleQuote(it) } },
            )
        } else {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun FormatBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onHeading: () -> Unit,
    onWrap: (String, String) -> Unit,
    onLink: () -> Unit,
    onList: (ListKind) -> Unit,
    onQuote: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(colors.surface).navigationBarsPadding()) {
        HorizontalDivider(color = colors.outlineVariant)
        Row(
            Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tool(Icons.AutoMirrored.Outlined.Undo, "Undo", canUndo, onUndo)
            Tool(Icons.AutoMirrored.Outlined.Redo, "Redo", canRedo, onRedo)
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp), color = colors.outlineVariant)
            Tool(Icons.Outlined.Title, "Heading", onClick = onHeading)
            Tool(Icons.Outlined.FormatBold, "Bold") { onWrap("**", "**") }
            Tool(Icons.Outlined.FormatItalic, "Italic") { onWrap("*", "*") }
            Tool(Icons.Outlined.FormatStrikethrough, "Strikethrough") { onWrap("~~", "~~") }
            Tool(Icons.Outlined.Code, "Code") { onWrap("`", "`") }
            Tool(Icons.Outlined.Link, "Link", onClick = onLink)
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp), color = colors.outlineVariant)
            Tool(Icons.AutoMirrored.Outlined.FormatListBulleted, "Bulleted list") { onList(ListKind.Bullet) }
            Tool(Icons.Outlined.FormatListNumbered, "Numbered list") { onList(ListKind.Ordered) }
            Tool(Icons.Outlined.CheckBox, "Checklist") { onList(ListKind.Task) }
            Tool(Icons.Outlined.FormatQuote, "Quote", onClick = onQuote)
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun Tool(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
        )
    }
}
