package dev.kalpane.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kalpane.data.PageStyle
import dev.kalpane.data.Paper

/** Text sizes offered for pages, in page units. */
val TextSizes = listOf("S" to 15f, "M" to 17f, "L" to 20f, "XL" to 24f)

/** Paper, font and size pickers. Changes apply as they are made. */
@Composable
fun PageStyleControls(style: PageStyle, onChange: (PageStyle) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val paperColor = if (colors.background.luminance() < 0.5f) colors.surfaceContainer else Color.White
    Column {
        Text("Paper", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Paper.entries.forEach { p ->
                val selected = style.paper == p
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(
                        Modifier.size(52.dp, 64.dp).clip(RoundedCornerShape(8.dp))
                            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, RoundedCornerShape(8.dp))
                            .clickable { onChange(style.copy(paper = p)) },
                    ) { drawPaper(p, paperColor, colors.outline.copy(alpha = 0.5f), 7f, density, topUnits = 0f) }
                    Text(p.name, style = MaterialTheme.typography.labelSmall, color = if (selected) colors.primary else colors.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Font", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        NoteFont.entries.forEach { f ->
            val selected = style.font == f.name
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onChange(style.copy(font = f.name)) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(f.label, fontFamily = noteFontFamily(f), fontSize = 16.sp, color = if (selected) colors.primary else colors.onSurface, modifier = Modifier.weight(1f))
                Text("Aa ಕ 123", fontFamily = noteFontFamily(f), fontSize = 14.sp, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Text size", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TextSizes.forEachIndexed { i, (label, value) ->
                SegmentedButton(
                    selected = style.size == value,
                    onClick = { onChange(style.copy(size = value)) },
                    shape = SegmentedButtonDefaults.itemShape(i, TextSizes.size),
                    icon = {},
                ) { Text(label) }
            }
        }
    }
}

@Composable
fun PageStyleDialog(style: PageStyle, title: String, onDismiss: () -> Unit, onChange: (PageStyle) -> Unit) {
    var current by remember { mutableStateOf(style) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PageStyleControls(current) { current = it; onChange(it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
