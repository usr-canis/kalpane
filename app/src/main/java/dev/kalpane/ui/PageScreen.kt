package dev.kalpane.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowRightAlt
import androidx.compose.material.icons.automirrored.outlined.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.FlipToFront
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.HighlightAlt
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kalpane.AppViewModel
import dev.kalpane.data.Geometry
import dev.kalpane.data.Item
import dev.kalpane.data.ListKind
import dev.kalpane.data.MarkdownEdit
import dev.kalpane.data.PAGE_WIDTH
import dev.kalpane.data.PageRef
import dev.kalpane.data.Picture
import dev.kalpane.data.ShapeKind
import dev.kalpane.data.Edit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** Mutable viewport facts the screen needs outside composition (image placement). */
private class Viewport { var scale = 1f; var height = 0f }

@Composable
fun PageScreen(vm: AppViewModel, ref: PageRef, tools: ToolState, onBack: () -> Unit, onRenamed: (PageRef) -> Unit) {
    var state by remember(ref) { mutableStateOf<PageState?>(null) }
    LaunchedEffect(ref) {
        val data = vm.load(ref)
        state = PageState(data.text, data.canvas.items)
    }
    val s = state
    if (s == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    PageEditor(vm, ref, s, tools, onBack, onRenamed)
}

@Composable
private fun PageEditor(vm: AppViewModel, ref: PageRef, s: PageState, tools: ToolState, onBack: () -> Unit, onRenamed: (PageRef) -> Unit) {
    val scope = rememberCoroutineScope()
    val fingerDrawing by vm.fingerDrawing.collectAsStateWithLifecycle()
    val controller = remember(s) { CanvasController(s, tools) }
    val bitmaps = remember(ref) { mutableStateMapOf<String, ImageBitmap>() }
    val viewport = remember { Viewport() }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    var skipSave by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }

    // Autosave shortly after changes, and when leaving.
    LaunchedEffect(s.version) {
        if (s.version > 0) { delay(500); vm.save(ref, s.data()) }
    }
    DisposableEffect(s) {
        onDispose { if (!skipSave && s.version > 0) vm.save(ref, s.data()) }
    }
    BackHandler {
        if (s.selection.isNotEmpty()) s.selection = emptySet() else onBack()
    }

    val assets = s.items.filterIsInstance<Picture>().map { it.asset }.toSet()
    LaunchedEffect(assets) {
        assets.filter { it !in bitmaps }.forEach { a -> vm.image(ref.notebook, a)?.let { bitmaps[a] = it } }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val img = vm.importImage(ref.notebook, uri) ?: return@launch
            val w = min(PAGE_WIDTH * 0.6f, img.width.toFloat())
            val h = w * img.height / img.width
            val centerY = (vScroll.value + viewport.height / 2) / viewport.scale
            val pic = Picture(asset = img.asset, x = (PAGE_WIDTH - w) / 2, y = max(24f, centerY - h / 2), w = w, h = h)
            s.commit(s.items + pic)
            s.selection = setOf(pic.id)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(title = ref.name, onBack = { if (s.selection.isNotEmpty()) s.selection = emptySet(); onBack() }) {
            IconButton(onClick = s::undo, enabled = s.canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, "Undo") }
            IconButton(onClick = s::redo, enabled = s.canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, "Redo") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename page") }, onClick = { menu = false; dialog = "rename" })
                    DropdownMenuItem(text = { Text("Delete page") }, onClick = { menu = false; dialog = "delete" })
                }
            }
        }
        if (s.selection.isNotEmpty()) SelectionStrip(s) else ToolStrip(tools, onImage = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PageSurface(s, controller, fingerDrawing, bitmaps, viewport, vScroll, hScroll, focus)
        }
        if (s.textFocused) FormatBar(s) else Spacer(Modifier.navigationBarsPadding())
    }

    when (dialog) {
        "rename" -> NameDialog("Rename page", ref.name, "Rename", { dialog = null }) { name ->
            dialog = null
            skipSave = true
            vm.save(ref, s.data())
            vm.renamePage(ref, name, onRenamed)
        }
        "delete" -> ConfirmDialog("Delete page?", "“${ref.name}” will be deleted.", "Delete", { dialog = null }) {
            dialog = null
            skipSave = true
            vm.deletePage(ref)
            onBack()
        }
    }
}

@Composable
private fun PageSurface(
    s: PageState,
    controller: CanvasController,
    fingerDrawing: Boolean,
    bitmaps: Map<String, ImageBitmap>,
    viewport: Viewport,
    vScroll: ScrollState,
    hScroll: ScrollState,
    focus: FocusRequester,
) {
    val base = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val paper = if (dark) colors.surfaceContainer else Color.White
    val palette = remember(colors) {
        MarkdownPalette(
            text = colors.onSurface,
            muted = colors.onSurfaceVariant,
            syntax = colors.onSurfaceVariant.copy(alpha = 0.55f),
            accent = colors.primary,
            codeBackground = if (dark) colors.surfaceContainerHighest else colors.surfaceContainer,
            rule = colors.outline,
            keyword = if (dark) Color(0xFFC792EA) else Color(0xFF7B3FA0),
            string = if (dark) Color(0xFFA5D6A7) else Color(0xFF2E7D32),
            number = if (dark) Color(0xFF90CAF9) else Color(0xFF1565C0),
        )
    }
    val cache = remember { PathCache() }
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }
    var textHeight by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val vw = constraints.maxWidth.toFloat()
        val vh = constraints.maxHeight.toFloat()
        val baseScale = min(vw / PAGE_WIDTH, base.density * 1.6f)
        val scale = baseScale * zoom
        viewport.scale = scale; viewport.height = vh
        controller.hu = base.density / scale
        controller.fingerDrawing = fingerDrawing
        val itemsBottom = Geometry.boundsOf(s.items)?.bottom ?: 0f
        val pageHeight = max(max(textHeight + 120f, itemsBottom + 240f), vh / scale)
        val hu = base.density / scale

        Box(
            Modifier.fillMaxSize()
                .pinchZoom { z, c ->
                    val old = zoom
                    val new = (old * z).coerceIn(1f, 4f)
                    if (new != old) {
                        zoom = new
                        val k = new / old
                        val ty = ((vScroll.value + c.y) * k - c.y).toInt()
                        val tx = ((hScroll.value + c.x) * k - c.x).toInt()
                        scope.launch { delay(16); vScroll.scrollTo(ty); hScroll.scrollTo(tx) }
                    }
                }
                .verticalScroll(vScroll)
                .horizontalScroll(hScroll),
        ) {
            val outerWidth = with(base) { max(PAGE_WIDTH * scale, vw).toDp() }
            val outerHeight = with(base) { (pageHeight * scale).toDp() }
            Box(Modifier.width(outerWidth).height(outerHeight)) {
                CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
                    Box(
                        Modifier.align(Alignment.TopCenter)
                            .size(PAGE_WIDTH.dp, pageHeight.dp)
                            .background(paper)
                            .then(if (PAGE_WIDTH * scale < vw - 1) Modifier.border((hu).dp, colors.outlineVariant) else Modifier)
                            .canvasInput(controller) { scale },
                    ) {
                        PageText(
                            s, palette, focus,
                            onHeight = { textHeight = it },
                            modifier = Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp),
                        )
                        Canvas(Modifier.fillMaxSize()) {
                            withTransform({ scale(scale, scale, Offset.Zero) }) {
                                val ink = colors.onSurface
                                s.items.forEach { drawItem(it, ink, cache, bitmaps, colors.surfaceContainerHigh) }
                                if (s.live.isNotEmpty()) {
                                    val pts = s.live.toList()
                                    if (controller.liveHighlighter) drawHighlighter(pts, controller.liveWidth, itemColor(controller.liveColor, ink).copy(alpha = 0.38f))
                                    else drawPath(strokeOutline(pts, controller.liveWidth, false), itemColor(controller.liveColor, ink))
                                }
                                val selected = s.items.filter { it.id in s.selection }
                                selectionFrame(selected, hu)?.let { drawSelection(it, colors.primary, paper, hu) }
                                drawLasso(s.lasso, colors.primary, hu)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    menu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                .background(if (selected) colors.primary.copy(alpha = 0.14f) else Color.Transparent)
                .combinedClickable(
                    onClick = { if (selected && menu != null) open = true else onClick() },
                    onLongClick = { onClick(); if (menu != null) open = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = if (selected) colors.primary else colors.onSurfaceVariant)
        }
        if (menu != null) DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
    }
}

@Composable
private fun ColorRow(colors: List<Int?>, current: Int?, ink: Color, onPick: (Int?) -> Unit) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { c ->
            val color = itemColor(c, ink)
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(color)
                    .border(if (c == current) 3.dp else 1.dp, if (c == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}

@Composable
private fun WidthRow(widths: List<Float>, current: Float, onPick: (Float) -> Unit) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        widths.forEachIndexed { i, w ->
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (w == current) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onPick(w) },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size((6 + i * 6).dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
            }
        }
    }
}

private fun shapeIcon(kind: ShapeKind) = when (kind) {
    ShapeKind.Line -> Icons.Outlined.HorizontalRule
    ShapeKind.Arrow -> Icons.AutoMirrored.Outlined.ArrowRightAlt
    ShapeKind.Rectangle -> Icons.Outlined.CropSquare
    ShapeKind.Ellipse -> Icons.Outlined.Circle
}

@Composable
private fun ToolStrip(tools: ToolState, onImage: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().height(52.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        ToolButton(Icons.Outlined.Draw, "Pen", tools.tool == Tool.Pen, { tools.tool = Tool.Pen }) {
            ColorRow(PenColors, tools.penColor, ink) { tools.penColor = it }
            WidthRow(PenWidths, tools.penWidth) { tools.penWidth = it }
        }
        ToolButton(Icons.Outlined.BorderColor, "Highlighter", tools.tool == Tool.Highlighter, { tools.tool = Tool.Highlighter }) {
            ColorRow(HighlighterColors, tools.highlighterColor, ink) { c -> c?.let { tools.highlighterColor = it } }
            WidthRow(HighlighterWidths, tools.highlighterWidth) { tools.highlighterWidth = it }
        }
        ToolButton(Icons.Outlined.AutoFixNormal, "Eraser", tools.tool == Tool.Eraser, { tools.tool = Tool.Eraser }) { dismiss ->
            DropdownMenuItem(text = { Text("Erase whole strokes") }, onClick = { tools.partialEraser = false; dismiss() },
                trailingIcon = { if (!tools.partialEraser) Icon(Icons.Outlined.CheckBox, null) })
            DropdownMenuItem(text = { Text("Erase parts of strokes") }, onClick = { tools.partialEraser = true; dismiss() },
                trailingIcon = { if (tools.partialEraser) Icon(Icons.Outlined.CheckBox, null) })
        }
        ToolButton(Icons.Outlined.HighlightAlt, "Select", tools.tool == Tool.Select, { tools.tool = Tool.Select })
        ToolButton(shapeIcon(tools.shape), "Shapes", tools.tool == Tool.Shape, { tools.tool = Tool.Shape }) { dismiss ->
            ShapeKind.entries.forEach { kind ->
                DropdownMenuItem(
                    text = { Text(kind.name) },
                    leadingIcon = { Icon(shapeIcon(kind), null) },
                    onClick = { tools.shape = kind; tools.tool = Tool.Shape; dismiss() },
                )
            }
            ColorRow(PenColors, tools.penColor, ink) { tools.penColor = it }
            WidthRow(PenWidths, tools.penWidth) { tools.penWidth = it }
        }
        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        ToolButton(Icons.Outlined.AddPhotoAlternate, "Insert image", false, onImage)
    }
}

private fun reorder(items: List<Item>, ids: Set<String>, forward: Boolean): List<Item> {
    val list = items.toMutableList()
    val indices = if (forward) list.indices.reversed() else list.indices
    for (i in indices) {
        val j = if (forward) i + 1 else i - 1
        if (j in list.indices && list[i].id in ids && list[j].id !in ids) {
            val t = list[i]; list[i] = list[j]; list[j] = t
        }
    }
    return list
}

@Composable
private fun SelectionStrip(s: PageState) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        Tool(Icons.Outlined.FlipToFront, "Bring forward") { s.commit(reorder(s.items, s.selection, forward = true)) }
        Tool(Icons.Outlined.FlipToBack, "Send backward") { s.commit(reorder(s.items, s.selection, forward = false)) }
        Tool(Icons.Outlined.Delete, "Delete") {
            s.commit(s.items.filterNot { it.id in s.selection })
            s.selection = emptySet()
        }
        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        TextButton(onClick = { s.selection = emptySet() }) { Text("Done") }
    }
}

@Composable
private fun FormatBar(s: PageState) {
    val colors = MaterialTheme.colorScheme
    fun edit(transform: (Edit) -> Edit) = s.setText(transform(s.text.toEdit()).toValue(), merge = false)
    Column(Modifier.fillMaxWidth().background(colors.surface).navigationBarsPadding()) {
        HorizontalDivider(color = colors.outlineVariant)
        Row(
            Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tool(Icons.Outlined.Title, "Heading") { edit(MarkdownEdit::cycleHeading) }
            Tool(Icons.Outlined.FormatBold, "Bold") { edit { MarkdownEdit.toggleWrap(it, "**") } }
            Tool(Icons.Outlined.FormatItalic, "Italic") { edit { MarkdownEdit.toggleWrap(it, "*") } }
            Tool(Icons.Outlined.FormatStrikethrough, "Strikethrough") { edit { MarkdownEdit.toggleWrap(it, "~~") } }
            Tool(Icons.Outlined.Link, "Link") { edit(MarkdownEdit::link) }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp), color = colors.outlineVariant)
            Tool(Icons.AutoMirrored.Outlined.FormatListBulleted, "Bulleted list") { edit { MarkdownEdit.toggleList(it, ListKind.Bullet) } }
            Tool(Icons.Outlined.FormatListNumbered, "Numbered list") { edit { MarkdownEdit.toggleList(it, ListKind.Ordered) } }
            Tool(Icons.Outlined.CheckBox, "Checklist") { edit { MarkdownEdit.toggleList(it, ListKind.Task) } }
            Tool(Icons.Outlined.FormatQuote, "Quote") { edit(MarkdownEdit::toggleQuote) }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp), color = colors.outlineVariant)
            Tool(Icons.Outlined.Code, "Inline code") { edit { MarkdownEdit.toggleWrap(it, "`") } }
            Tool(Icons.Outlined.DataObject, "Code block") { edit(MarkdownEdit::codeBlock) }
            Tool(Icons.AutoMirrored.Outlined.FormatIndentIncrease, "Indent") { edit { MarkdownEdit.indent(it) } }
            Tool(Icons.AutoMirrored.Outlined.FormatIndentDecrease, "Outdent") { edit { MarkdownEdit.indent(it, outdent = true) } }
        }
    }
}

@Composable
private fun Tool(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, label, tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant)
    }
}
