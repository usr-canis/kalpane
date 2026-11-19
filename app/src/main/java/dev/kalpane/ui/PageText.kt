package dev.kalpane.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import dev.kalpane.data.Edit
import dev.kalpane.data.MarkdownEdit
import dev.kalpane.data.MarkdownSpans

/** Body line height as a multiple of text size; ruled paper uses the same spacing. */
const val LINE_HEIGHT = 1.6f

fun TextFieldValue.toEdit() = Edit(text, selection.start, selection.end)
fun Edit.toValue() = TextFieldValue(text, androidx.compose.ui.text.TextRange(start, end))

private val taskBox = Regex("""^\s*[-*+] \[[ xX]]""")

/** Applies a typed change: checkbox taps, list continuation and undo grouping. */
fun handleTextChange(state: PageState, new: TextFieldValue) {
    val old = state.text
    if (new.text == old.text) {
        val at = new.selection.start
        if (new.selection.collapsed && at != old.selection.start) {
            val lineStart = MarkdownEdit.lineStart(new.text, at)
            val oldLine = MarkdownEdit.lineStart(old.text, old.selection.start)
            val box = taskBox.find(new.text.substring(lineStart, MarkdownEdit.lineEnd(new.text, lineStart)))
            if ((lineStart != oldLine || !state.textFocused) && box != null && at - lineStart in box.range.first..box.range.last + 2) {
                MarkdownEdit.toggleTask(new.text, lineStart)?.let { toggled ->
                    state.setText(old.copy(text = toggled, composition = null), merge = false)
                    return
                }
            }
        }
        state.setSelection(new)
        return
    }
    val newline = new.text.length == old.text.length + 1 && new.selection.collapsed &&
        new.selection.start > 0 && new.text[new.selection.start - 1] == '\n'
    if (newline) {
        MarkdownEdit.onNewline(Edit(new.text, new.selection.start))?.let {
            state.setText(it.toValue(), merge = false)
            return
        }
    }
    state.setText(new, merge = !newline)
}

/** The page's Markdown text. Sizes are in page units (the caller scales the density). */
@Composable
fun PageText(
    state: PageState,
    palette: MarkdownPalette,
    focus: FocusRequester,
    font: NoteFont,
    textSize: Float,
    onHeight: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val mono = codeFontFamily(font)
    val family = noteFontFamily(font)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current.density
    val value = state.text
    val reveal = if (state.textFocused) revealRange(value.text, value.selection.start, value.selection.end) else null
    val blocks = remember(value.text) { MarkdownSpans.codeBlocks(value.text) }

    BasicTextField(
        value = value,
        onValueChange = { handleTextChange(state, it) },
        textStyle = TextStyle(color = colors.onSurface, fontFamily = family, fontSize = textSize.sp, lineHeight = (textSize * LINE_HEIGHT).sp),
        visualTransformation = MarkdownTransformation(reveal, palette, mono, textSize),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        onTextLayout = { layout = it; onHeight(it.size.height / density) },
        modifier = modifier
            .focusRequester(focus)
            .onFocusChanged { state.textFocused = it.isFocused }
            .drawBehind {
                // Code blocks get a full-width panel behind their lines.
                val l = layout ?: return@drawBehind
                val textLength = l.layoutInput.text.length
                blocks.forEach { block ->
                    if (textLength == 0) return@forEach
                    val first = l.getLineForOffset(block.first.coerceIn(0, textLength))
                    val last = l.getLineForOffset(block.last.coerceIn(0, textLength))
                    val top = l.getLineTop(first)
                    val bottom = l.getLineBottom(last)
                    val pad = 8.sp.toPx()
                    drawRoundRect(
                        palette.codeBackground,
                        topLeft = Offset(-pad, top),
                        size = Size(size.width + 2 * pad, bottom - top),
                        cornerRadius = CornerRadius(pad, pad),
                    )
                }
            },
    )
}
