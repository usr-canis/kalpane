package dev.kalpane.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kalpane.AppViewModel
import dev.kalpane.R
import dev.kalpane.data.Diff
import dev.kalpane.data.Geometry
import dev.kalpane.data.PAGE_WIDTH
import dev.kalpane.data.PageData
import dev.kalpane.data.PageRef
import dev.kalpane.data.Picture
import dev.kalpane.sync.GitHubSync

@Composable
private fun pagePalette(): MarkdownPalette {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    return MarkdownPalette(
        text = colors.onSurface, muted = colors.onSurfaceVariant, syntax = colors.onSurfaceVariant.copy(alpha = 0.55f),
        accent = colors.primary, codeBackground = if (dark) colors.surfaceContainerHighest else colors.surfaceContainer,
        rule = colors.outline,
        keyword = if (dark) Color(0xFFC792EA) else Color(0xFF7B3FA0),
        string = if (dark) Color(0xFFA5D6A7) else Color(0xFF2E7D32),
        number = if (dark) Color(0xFF90CAF9) else Color(0xFF1565C0),
    )
}

/** A read-only rendering of a page at the available width. */
@Composable
fun StaticPage(vm: AppViewModel, notebook: String, data: PageData, modifier: Modifier = Modifier) {
    val defaults by vm.defaults.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val paper = if (dark) colors.surfaceContainer else Color.White
    val font = NoteFont.entries.firstOrNull { it.name == (data.canvas.style.font ?: defaults.font) } ?: NoteFont.Sans
    val textSize = data.canvas.style.size ?: defaults.size
    val paperStyle = data.canvas.style.paper ?: defaults.paper
    val palette = pagePalette()
    val family = noteFontFamily(font)
    val mono = codeFontFamily(font)
    val cache = remember { PathCache() }
    val bitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }
    var textHeight by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(data) {
        data.canvas.items.filterIsInstance<Picture>().forEach { p -> vm.image(notebook, p.asset)?.let { bitmaps[p.asset] = it } }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val scale = constraints.maxWidth / PAGE_WIDTH
        val height = maxOf(textHeight + 120f, (Geometry.boundsOf(data.canvas.items)?.bottom ?: 0f) + 40f, 300f)
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
            Box(
                Modifier.size(PAGE_WIDTH.dp, height.dp).clip(RoundedCornerShape(6.dp))
                    .drawBehind { drawPaper(paperStyle, paper, colors.outlineVariant.copy(alpha = 0.7f), textSize, scale) },
            ) {
                Text(
                    styleMarkdown(data.text, null, palette, mono, textSize),
                    style = TextStyle(color = colors.onSurface, fontFamily = family, fontSize = textSize.sp, lineHeight = (textSize * LINE_HEIGHT).sp),
                    onTextLayout = { textHeight = it.size.height.toFloat() / scale },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 56.dp, vertical = 40.dp),
                )
                Canvas(Modifier.fillMaxSize()) {
                    withTransform({ scale(scale, scale, Offset.Zero) }) {
                        data.canvas.items.forEach { drawItem(it, colors.onSurface, cache, bitmaps, colors.surfaceContainerHigh) }
                    }
                }
            }
        }
    }
}

/** Unified diff of a page against its last pushed version, like `git diff`. */
@Composable
fun DiffDialog(vm: AppViewModel, ref: PageRef, current: PageData? = null, onDismiss: () -> Unit) {
    var base by remember { mutableStateOf<PageData?>(null) }
    var now by remember { mutableStateOf(current) }
    LaunchedEffect(ref) {
        base = vm.baseVersion(ref)
        if (now == null) now = vm.load(ref)
    }
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { OctIcon(R.drawable.oct_file_diff, null) },
        title = { Text("Changes") },
        text = {
            val b = base
            val c = now
            if (b == null || c == null) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
            val lines = remember(b, c) { Diff.lines(b.text, c.text) }
            val canvas = remember(b, c) { Diff.canvas(b.canvas.items, c.canvas.items) }
            val (added, removed) = Diff.stats(lines)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${ref.notebook}/${ref.name}.md", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace,
                        color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("+$added", color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(6.dp))
                    Text("−$removed", color = colors.error, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                }
                if (!canvas.isEmpty) {
                    Text(
                        "Drawing: " + listOfNotNull(
                            canvas.added.takeIf { it > 0 }?.let { "$it added" },
                            canvas.removed.takeIf { it > 0 }?.let { "$it removed" },
                            canvas.changed.takeIf { it > 0 }?.let { "$it changed" },
                        ).joinToString(", "),
                        style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (lines.isEmpty()) {
                    Text(if (canvas.isEmpty) "No changes since the last push." else "No text changes.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Box(
                        Modifier.heightIn(max = 420.dp).clip(RoundedCornerShape(8.dp))
                            .border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp)),
                    ) {
                        LazyColumn(Modifier.horizontalScroll(rememberScrollState())) {
                            items(lines) { line -> DiffRow(line) }
                        }
                    }
                }
            }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun DiffRow(line: dev.kalpane.data.DiffLine) {
    val colors = MaterialTheme.colorScheme
    val green = Color(0xFF2E7D32)
    val (bg, fg) = when (line.kind) {
        '+' -> green.copy(alpha = 0.12f) to colors.onSurface
        '-' -> colors.error.copy(alpha = 0.12f) to colors.onSurface
        '@' -> colors.primary.copy(alpha = 0.08f) to colors.onSurfaceVariant
        else -> Color.Transparent to colors.onSurfaceVariant
    }
    Row(Modifier.background(bg).padding(horizontal = 8.dp, vertical = 1.dp)) {
        Text(
            if (line.kind == '@') "" else (line.oldNo?.toString() ?: "").padStart(4) + " " + (line.newNo?.toString() ?: "").padStart(4),
            fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = colors.outline,
        )
        Text(
            (if (line.kind == '@') "" else " ${line.kind} ") + line.text,
            fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = fg, softWrap = false,
        )
    }
}

/** A page as of one commit, with restore. */
@Composable
fun VersionDialog(vm: AppViewModel, ref: PageRef, commit: GitHubSync.Commit, onRestore: (PageData) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var data by remember { mutableStateOf<PageData?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(commit.sha) { data = vm.versionAt(ref, commit.sha); loaded = true }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { OctIcon(R.drawable.oct_versions, null) },
        title = { Text(commit.message, maxLines = 2) },
        text = {
            Column {
                Text(commit.sha.take(7) + " · " + ref.name, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                val d = data
                when {
                    !loaded -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    d == null -> Text("This page did not exist in that commit.", style = MaterialTheme.typography.bodyMedium)
                    else -> Box(
                        Modifier.heightIn(max = 440.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                            .verticalScroll(rememberScrollState()),
                    ) { StaticPage(vm, ref.notebook, d) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { data?.let(onRestore); onDismiss() }, enabled = data != null) { Text("Restore this version") }
        },
        dismissButton = {
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(commit.url))) }) { Text("View on GitHub") }
        },
    )
}
