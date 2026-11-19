package dev.kalpane.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.kalpane.data.PageStyle
import dev.kalpane.data.Paper
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
        state = PageState(data.text, data.canvas.items, data.canvas.style)
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
    val context = LocalContext.current
    val fingerDrawing by vm.fingerDrawing.collectAsStateWithLifecycle()
    val defaults by vm.defaults.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val changes by vm.changes.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val font = NoteFont.entries.firstOrNull { it.name == (s.style.font ?: defaults.font) } ?: NoteFont.Sans
    val size = s.style.size ?: defaults.size
    val paperStyle = s.style.paper ?: defaults.paper
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
        vm.setEditing(true)
        onDispose {
            vm.setEditing(false)
            if (!skipSave && s.version > 0) vm.save(ref, s.data())
        }
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
            if (sync.repo != null) {
                val status = pageStatus(changes, ref.notebook, ref.name)
                Box {
                    IconButton(onClick = { vm.save(ref, s.data()); dialog = "push" }, enabled = busy == null) {
                        Icon(Icons.Outlined.CloudUpload, "Push page", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (status != null) Box(
                        Modifier.align(Alignment.TopEnd).padding(10.dp).size(8.dp).clip(CircleShape).background(statusColor(status)),
                    )
                }
            }
            IconButton(onClick = s::undo, enabled = s.canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, "Undo") }
            IconButton(onClick = s::redo, enabled = s.canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, "Redo") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Page style") }, onClick = { menu = false; dialog = "style" })
                    DropdownMenuItem(text = { Text("Export as PDF") }, onClick = {
                        menu = false; vm.save(ref, s.data()); scope.launch { exportPage(context, vm, ref, pdf = true) }
                    })
                    DropdownMenuItem(text = { Text("Export as image") }, onClick = {
                        menu = false; vm.save(ref, s.data()); scope.launch { exportPage(context, vm, ref, pdf = false) }
                    })
                    if (sync.repo != null) {
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Pull page") }, onClick = { menu = false; vm.save(ref, s.data()); vm.pull(AppViewModel.Scope.Page(ref)) })
                        DropdownMenuItem(text = { Text("History") }, onClick = { menu = false; dialog = "history" })
                        if (pageStatus(changes, ref.notebook, ref.name) != null) {
                            DropdownMenuItem(text = { Text("Discard changes") }, onClick = { menu = false; dialog = "discard" })
                        }
                    }
                    HorizontalDivider()
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
            PageSurface(s, controller, fingerDrawing, bitmaps, viewport, vScroll, hScroll, focus, font, size, paperStyle)
            if (busy != null) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter))
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
        "delete" -> ConfirmDialog("Move to trash?", "“${ref.name}” moves to trash. You can restore it for 30 days.", "Move to trash", { dialog = null }) {
            dialog = null
            skipSave = true
            vm.deletePage(ref)
            onBack()
        }
        "push" -> PushDialog(vm, AppViewModel.Scope.Page(ref)) { dialog = null }
        "history" -> HistoryDialog(vm, AppViewModel.Scope.Page(ref)) { dialog = null }
        "style" -> PageStyleDialog(
            style = PageStyle(font.name, size, paperStyle),
            title = "Page style",
            onDismiss = { dialog = null },
        ) { s.applyStyle(it) }
        "discard" -> ConfirmDialog(
            "Discard changes?",
            "“${ref.name}” goes back to its last pushed version. This cannot be undone.",
            "Discard",
            { dialog = null },
        ) {
            dialog = null
            skipSave = true
            vm.discard(ref) { onBack() }
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
    font: NoteFont,
    textSize: Float,
    paperStyle: Paper,
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
        controller.inkArgb = colors.onSurface.toArgb()
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
                            .then(if (PAGE_WIDTH * scale < vw - 1) Modifier.shadow((3 * hu).dp, RoundedCornerShape((4 * hu).dp)) else Modifier)
                            .drawBehind { drawPaper(paperStyle, paper, colors.outlineVariant.copy(alpha = if (dark) 0.5f else 0.8f), textSize, scale) }
                            .canvasInput(controller) { scale },
                    ) {
                        PageText(
                            s, palette, focus, font, textSize,
                            onHeight = { textHeight = it },
                            modifier = Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp),
                        )
                        Canvas(Modifier.fillMaxSize()) {
                            withTransform({ scale(scale, scale, Offset.Zero) }) {
                                val ink = colors.onSurface
                                s.items.forEach { drawItem(it, ink, cache, bitmaps, colors.surfaceContainerHigh) }
                                if (s.live.isNotEmpty() && !controller.wet) {
                                    val pts = s.live.toList()
                                    if (controller.liveHighlighter) drawHighlighter(pts, controller.liveWidth, itemColor(controller.liveColor, ink).copy(alpha = 0.38f))
                                    else drawPath(strokeOutline(pts, controller.liveWidth, false, controller.livePressure), itemColor(controller.liveColor, ink))
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
private fun WidthSlider(value: Float, range: ClosedFloatingPointRange<Float>, preview: @Composable () -> Unit, onChange: (Float) -> Unit) {
    Column(Modifier.width(280.dp).padding(horizontal = 16.dp, vertical = 4.dp)) {
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) { preview() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Width", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Text("%.1f".format(value), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(32.dp))
        }
    }
}

/** A sample stroke drawn with the current pen settings, including simulated pressure. */
@Composable
private fun StrokePreview(width: Float, sensitivity: Float, color: Color, highlighter: Boolean) {
    Canvas(Modifier.fillMaxWidth().height(36.dp)) {
        val pts = ArrayList<Float>()
        val n = 40
        for (i in 0..n) {
            val t = i / n.toFloat()
            val x = 12f + t * (size.width - 24f)
            val y = size.height / 2 + kotlin.math.sin(t * 2 * Math.PI).toFloat() * size.height * 0.28f
            pts += x; pts += y; pts += (0.15f + 0.85f * kotlin.math.sin(t * Math.PI).toFloat())
        }
        val k = density // page units at about 1 unit per dp
        if (highlighter) drawHighlighter(pts, width * k, color.copy(alpha = 0.38f))
        else drawPath(strokeOutline(pts, width * k, false, sensitivity), color)
    }
}

@Composable
private fun PressureRow(current: Pressure, onPick: (Pressure) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("Pressure", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.width(248.dp)) {
            Pressure.entries.forEachIndexed { i, p ->
                SegmentedButton(
                    selected = p == current,
                    onClick = { onPick(p) },
                    shape = SegmentedButtonDefaults.itemShape(i, Pressure.entries.size),
                    icon = {},
                ) { Text(p.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
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
            WidthSlider(tools.penWidth, MIN_PEN..MAX_PEN, {
                StrokePreview(tools.penWidth, tools.pressure.value, itemColor(tools.penColor, ink), false)
            }) { tools.penWidth = it }
            PressureRow(tools.pressure) { tools.pressure = it }
        }
        ToolButton(Icons.Outlined.BorderColor, "Highlighter", tools.tool == Tool.Highlighter, { tools.tool = Tool.Highlighter }) {
            ColorRow(HighlighterColors, tools.highlighterColor, ink) { c -> c?.let { tools.highlighterColor = it } }
            WidthSlider(tools.highlighterWidth, MIN_HIGHLIGHTER..MAX_HIGHLIGHTER, {
                StrokePreview(tools.highlighterWidth, 0f, Color(tools.highlighterColor), true)
            }) { tools.highlighterWidth = it }
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
            WidthSlider(tools.penWidth, MIN_PEN..MAX_PEN, {
                StrokePreview(tools.penWidth, 0f, itemColor(tools.penColor, ink), false)
            }) { tools.penWidth = it }
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
            HeadingMenu(s)
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

@Composable
private fun HeadingMenu(s: PageState) {
    var open by remember { mutableStateOf(false) }
    val current = MarkdownEdit.headingLevel(s.text.text, s.text.selection.start)
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.Title, "Heading", tint = if (current > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (1..6).forEach { level ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "Heading $level",
                            fontSize = (15 * HeadingScale[level - 1]).sp,
                            fontWeight = if (level <= 2) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (level == current) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        )
                    },
                    leadingIcon = { Text("H$level", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { open = false; s.setText(MarkdownEdit.setHeading(s.text.toEdit(), level).toValue(), merge = false) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Normal text", color = if (current == 0) MaterialTheme.colorScheme.primary else Color.Unspecified) },
                leadingIcon = { Text("¶", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = { open = false; s.setText(MarkdownEdit.setHeading(s.text.toEdit(), 0).toValue(), merge = false) },
            )
        }
    }
}

/** Ruled lines follow the text's line height so writing sits on them. */
fun DrawScope.drawPaper(paper: Paper, background: Color, line: Color, textSize: Float, scale: Float, topUnits: Float = 40f) {
    drawRect(background)
    val lh = textSize * LINE_HEIGHT * scale
    val top = topUnits * scale
    val stroke = maxOf(1f, 0.6f * scale)
    when (paper) {
        Paper.Plain -> Unit
        Paper.Ruled -> {
            var y = top + lh
            while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), stroke); y += lh }
        }
        Paper.Grid -> {
            var y = top + lh
            while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), stroke * 0.8f); y += lh }
            var x = (size.width / 2) % lh
            while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), stroke * 0.8f); x += lh }
        }
        Paper.Dots -> {
            var y = top + lh
            while (y < size.height) {
                var x = (size.width / 2) % lh
                while (x < size.width) { drawCircle(line, 1.4f * scale, Offset(x, y)); x += lh }
                y += lh
            }
        }
    }
}
