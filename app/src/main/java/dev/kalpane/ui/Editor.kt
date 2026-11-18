@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package dev.kalpane.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.pointerInteropFilter
import android.view.MotionEvent
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import dev.kalpane.*
import dev.kalpane.data.*
import dev.kalpane.ink.NotebookCanvas

@Composable fun EditorScreen(s: AppState,vm: NotebookViewModel) {
    val page=s.page ?: return
    var more by remember { mutableStateOf(false) }
    var eraserMenu by remember { mutableStateOf(false) }
    var colors by remember { mutableStateOf(false) }
    var thumbnails by remember { mutableStateOf(false) }
    var canvasView by remember { mutableStateOf<NotebookCanvas?>(null) }
    var inlineValue by remember(page.id) { mutableStateOf<TextFieldValue?>(null) }
    var inlinePosition by remember(page.id) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var pagePosition by remember(page.id) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val focusRequester=remember { FocusRequester() }
    val bringIntoViewRequester=remember {BringIntoViewRequester()}
    val keyboard=LocalSoftwareKeyboardController.current
    val focusManager=LocalFocusManager.current
    val codeFamily=noteFont(TextFace.Monospace)
    val markdownTransformation=remember(codeFamily) {MarkdownVisualTransformation(codeFamily)}
    var editingText by remember(page.id) { mutableStateOf<Layer?>(null) }
    var typingStarted by remember(page.id) { mutableStateOf(false) }
    var revealSource by remember(page.id) { mutableStateOf(false) }
    var baseTextHeight by remember(page.id) { mutableStateOf<Float?>(null) }
    var baseLayerHeight by remember(page.id) { mutableStateOf<Float?>(null) }
    fun stopTyping() {focusManager.clearFocus();inlineValue=null;editingText=null;keyboard?.hide()}
    BackHandler { if(inlineValue!=null) stopTyping() else if(s.focus) vm.focus() else vm.screen("pages") }
    var layerMenu by remember { mutableStateOf<Layer?>(null) }
    var rename by remember { mutableStateOf(false) }
    val night=isNight(s.prefs.theme)
    val keyboardVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
    val compactHeight=LocalConfiguration.current.screenHeightDp<480 || keyboardVisible
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::importImage) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        if(!s.focus) Row(Modifier.fillMaxWidth().heightIn(min=if(compactHeight) 48.dp else 64.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            ActionIcon(Icons.AutoMirrored.Outlined.ArrowBack,"Pages") {stopTyping();vm.screen("pages")}
            Column(Modifier.weight(1f).clickable {rename=true}.padding(horizontal=8.dp,vertical=8.dp)) {
                if(!compactHeight) Text(s.notebook?.title.orEmpty(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                AnimatedContent(targetState=page.title,transitionSpec={fadeIn(tween(if(s.prefs.reducedMotion) 0 else 150)) togetherWith fadeOut(tween(if(s.prefs.reducedMotion) 0 else 100))},label="Page") {title->Text(title,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)}
            }
            ActionIcon(Icons.Outlined.Add,"New page") {stopTyping();vm.createPage("")}
            ActionIcon(Icons.Outlined.Fullscreen,"Enter focus mode") {stopTyping();vm.focus()}
        }
            Surface(shape=RoundedCornerShape(18.dp),shadowElevation=6.dp,tonalElevation=2.dp,color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.align(Alignment.CenterHorizontally).padding(vertical=4.dp).height(48.dp)) {
                if(s.focus) ActionIcon(Icons.Outlined.Edit,"Expand tools and exit focus mode",selected=true) {stopTyping();vm.focus()}
                else Row(Modifier.height(48.dp).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                    ActionIcon(Icons.Outlined.Edit,"Pen",selected=s.tool==Tool.Pen) {stopTyping();vm.tool(Tool.Pen)}
                    Box {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).then(if(s.tool==Tool.Eraser || s.tool==Tool.PartialEraser) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier).combinedClickable(role=Role.Button,onClickLabel="Eraser",onLongClickLabel="Choose eraser mode",onClick={stopTyping();vm.tool(if(s.prefs.partialEraser) Tool.PartialEraser else Tool.Eraser)},onLongClick={stopTyping();eraserMenu=true}).semantics {contentDescription="Eraser"},contentAlignment=Alignment.Center) {
                            val tint=MaterialTheme.colorScheme.onSurfaceVariant
                            Canvas(Modifier.size(22.dp)) {rotate(40f) {val w=size.width;val h=size.height;drawRoundRect(tint,Offset(w*.2f,h*.12f),Size(w*.6f,h*.76f),CornerRadius(w*.1f),style=Stroke(2.dp.toPx()));drawLine(tint,Offset(w*.2f,h*.58f),Offset(w*.8f,h*.58f),2.dp.toPx())}}
                        }
                        DropdownMenu(expanded=eraserMenu,onDismissRequest={eraserMenu=false}) {
                            DropdownMenuItem(text={Text("Erase part")},onClick={vm.preferences {it.copy(partialEraser=true)};vm.tool(Tool.PartialEraser);eraserMenu=false})
                            DropdownMenuItem(text={Text("Erase stroke")},onClick={vm.preferences {it.copy(partialEraser=false)};vm.tool(Tool.Eraser);eraserMenu=false})
                        }
                    }
                    ActionIcon(Icons.Outlined.NearMe,"Select and move objects",selected=s.tool==Tool.Select) {stopTyping();vm.tool(Tool.Select)}
                    IconButton({stopTyping();colors=true},Modifier.size(48.dp)) { Box(Modifier.size(22.dp).clip(CircleShape).background(Color(s.penColor)).border(1.dp,MaterialTheme.colorScheme.outline,CircleShape).semantics {contentDescription="Ink color picker"}) }
                    ActionIcon(Icons.AutoMirrored.Outlined.Undo,"Undo",s.undoAvailable) {stopTyping();vm.undo()}
                    ActionIcon(Icons.Outlined.MoreHoriz,"More tools") {stopTyping();more=true}
                }
            }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val paperWidth=minOf(maxWidth,760.dp)
            val paperHeight=paperWidth*(page.height/page.width)+16.dp
            Box(Modifier.fillMaxSize().testTag("document-scroll").verticalScroll(rememberScrollState())) {
            Box(Modifier.width(paperWidth).height(paperHeight).align(Alignment.TopCenter)) {
            // A single persistent Ink view avoids losing in-flight strokes during Compose recomposition.
            AndroidView(factory={ context -> NotebookCanvas(context,vm.store).apply {
                onEdit=vm::edit
                onStroke={pageId,layer -> vm.state.value.workspace.document.pages().find { it.id==pageId }?.let { vm.edit(it.copy(layers=it.layers+layer)) } }
                onTurn=vm::turn
                onLayerMenu={layerMenu=it}
                canvasView=this
            } },update={view->view.apply {
                onStylusEnd={stopTyping()}
                onTextEdit={layer,offset->viewPoint(layer.x,layer.y).let {point->inlinePosition=androidx.compose.ui.geometry.Offset(point.x,point.y);pagePosition=androidx.compose.ui.geometry.Offset(layer.x,layer.y);editingText=layer;typingStarted=false;revealSource=true;baseTextHeight=null;baseLayerHeight=layer.height;inlineValue=TextFieldValue(layer.text,selection=TextRange(offset.coerceIn(0,layer.text.length)))}}
                onTextTap={_,_,_,py -> val y=if(page.layers.isEmpty() && page.height<=1500f) 80f else py;val anchor=viewPoint(80f,y);editingText=null;typingStarted=false;revealSource=false;baseTextHeight=null;baseLayerHeight=s.prefs.textSize*1.5f;inlinePosition=androidx.compose.ui.geometry.Offset(anchor.x,anchor.y);pagePosition=androidx.compose.ui.geometry.Offset(80f,y);inlineValue=TextFieldValue("")}
            };view.bind(if(editingText!=null && inlineValue!=null) page.copy(layers=page.layers.filterNot {l->l.id==editingText?.id}) else page,s.tool,s.penColor,s.penWidth,s.prefs.fingerDrawing,night,s.prefs.gridSpacing)},modifier=Modifier.fillMaxSize())
            inlineValue?.let { fieldValue ->
                val text=fieldValue.text
                val density=LocalDensity.current
                val fieldWidth=with(density) { (canvasView?.let {canvas->canvas.viewPoint(editingText?.width ?: (page.width-160f),0f).x-canvas.viewPoint(0f,0f).x} ?: 280.dp.toPx()).coerceAtLeast(32.dp.toPx()).toDp() }
                val fieldFontSize=with(density) { (canvasView?.let {canvas->canvas.viewPoint(0f,editingText?.fontSize ?: s.prefs.textSize).y-canvas.viewPoint(0f,0f).y} ?: 16.sp.toPx()).coerceAtLeast(1f).toSp() }
                LaunchedEffect(Unit) { focusRequester.requestFocus(); keyboard?.show();delay(250);bringIntoViewRequester.bringIntoView() }
                BasicTextField(
                    value=fieldValue,
                    visualTransformation=if(revealSource) VisualTransformation.None else markdownTransformation,
                    onValueChange={nextValue->
                        inlineValue=nextValue
                        val value=nextValue.text
                        if(value!=fieldValue.text) {
                            val layer=(editingText ?: Layer(
                                kind=LayerKind.Text,x=pagePosition.x,y=pagePosition.y,
                                width=(page.width-pagePosition.x-80f).coerceAtLeast(32f),
                                color=s.penColor,textFace=s.prefs.textFace,fontSize=s.prefs.textSize,
                                height=s.prefs.textSize*1.5f,
                            )).copy(text=value)
                            if(value.isNotEmpty() || editingText!=null) {
                                vm.updateTextDuringSession(layer,!typingStarted)
                                typingStarted=true
                                editingText=layer
                            }
                        }
                    },
                    onTextLayout={layout->
                        canvasView?.let {canvas->
                            val measured=canvas.pageDistance(layout.size.height.toFloat())
                            val baseline=baseTextHeight
                            if(baseline==null) baseTextHeight=measured
                            else if(typingStarted) editingText?.let {layer->
                                val height=((baseLayerHeight ?: layer.height)+measured-baseline).coerceAtLeast(layer.fontSize*1.5f)
                                vm.resizeTextDuringSession(layer.id,height)
                            }
                        }
                    },
                    modifier=Modifier.align(Alignment.TopStart)
                        .offset {androidx.compose.ui.unit.IntOffset(inlinePosition.x.toInt().coerceAtLeast(0),inlinePosition.y.toInt().coerceAtLeast(0))}
                        .width(fieldWidth).bringIntoViewRequester(bringIntoViewRequester)
                        .pointerInteropFilter {event->
                            val stylus=(0 until event.pointerCount).any {event.getToolType(it)==MotionEvent.TOOL_TYPE_STYLUS || event.getToolType(it)==MotionEvent.TOOL_TYPE_ERASER}
                            if(stylus) {
                                val forwarded=MotionEvent.obtain(event)
                                forwarded.offsetLocation(inlinePosition.x,inlinePosition.y)
                                canvasView?.dispatchTouchEvent(forwarded)
                                forwarded.recycle()
                                true
                            } else false
                        }.focusRequester(focusRequester),
                    textStyle=MaterialTheme.typography.bodyLarge.copy(
                        color=Color(editingText?.color ?: s.penColor),fontSize=fieldFontSize,
                        fontFamily=noteFont(editingText?.textFace ?: s.prefs.textFace),
                    ),
                    decorationBox={inner->Box {if(text.isEmpty()) Text("Type…",color=Color.Gray);inner()}},
                )
            }


            }
            }
        }
        if(!s.focus && !keyboardVisible) Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            val index=s.notebook!!.pages.indexOfFirst {it.id==page.id}
            ActionIcon(Icons.Outlined.ChevronLeft,"Previous page",index>0) {stopTyping();vm.turn(-1)}
            TextButton({stopTyping();thumbnails=true},Modifier.weight(1f)) {
                Text("${index+1} / ${s.notebook!!.pages.size}",style=MaterialTheme.typography.labelMedium)
                if(!compactHeight) {Spacer(Modifier.width(12.dp)); Text(if(s.saving) "Saving…" else "Saved locally",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            ActionIcon(Icons.Outlined.ChevronRight,"Next page",index<s.notebook!!.pages.lastIndex) {stopTyping();vm.turn(1)}
        }
    }
    if(more) ModalBottomSheet(onDismissRequest={more=false}) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) { Text("Tools",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f)); ActionIcon(Icons.AutoMirrored.Outlined.Redo,"Redo",s.redoAvailable) {vm.redo()} }
            Text("Pen width · ${"%.1f".format(s.penWidth)}",Modifier.padding(start=24.dp,top=12.dp),style=MaterialTheme.typography.labelLarge)
            Slider(s.penWidth,vm::width,valueRange=1f..18f,modifier=Modifier.padding(horizontal=24.dp).semantics {contentDescription="Pen width"})
            Row(Modifier.padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(1f,3f,6f,12f).forEach {w-> FilterChip(s.penWidth==w,{vm.width(w)},label={Text("${w.toInt()}")}) } }
            ListAction(Icons.Outlined.AddPhotoAlternate,"Import image","Choose from Android Photo Picker") {more=false;picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}
            ListAction(Icons.Outlined.PanTool,"Navigate & turn pages","Swipe to turn at fit size. Pinch to zoom; drag to pan.") {vm.tool(Tool.Navigate);more=false}
            ListAction(Icons.Outlined.Layers,"Page objects","Order, lock, edit, or remove any layer") {more=false;thumbnails=false;layerMenu=page.layers.lastOrNull();if(page.layers.isEmpty()) vm.message("Add ink, text, or an image first.")}
            ListAction(Icons.Outlined.GridView,"Page thumbnails") {more=false;thumbnails=true}
            ListAction(Icons.Outlined.Fullscreen,"Focus mode","Just paper and a single pen button") {more=false;vm.focus()}
            SettingSwitch("Finger drawing","Off keeps ink exclusive to the stylus.",s.prefs.fingerDrawing) {vm.preferences {p->p.copy(fingerDrawing=it)}}
            ListAction(Icons.Outlined.Settings,"Settings") {more=false;vm.screen("settings")}
            Spacer(Modifier.height(20.dp))
        }
    }
    if(thumbnails) ModalBottomSheet(onDismissRequest={thumbnails=false}) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) { Text("Pages",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f)); TextButton({vm.createPage("");thumbnails=false}) {Text("New page")} }
            LazyColumn(Modifier.heightIn(max=480.dp),contentPadding=PaddingValues(16.dp)) {
                itemsIndexed(s.notebook!!.pages,key={_,p->p.id}) { i,p -> Surface(onClick={vm.openPage(p.id);thumbnails=false},shape=RoundedCornerShape(12.dp),color=if(p.id==page.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent) {
                    Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) { PageThumbnail(p,Modifier.size(44.dp,62.dp),vm.store); Text("${i+1}  ${p.title}",Modifier.weight(1f)); ActionIcon(Icons.Outlined.ArrowUpward,"Move ${p.title} earlier",i>0) {vm.reorder(p,-1)}; ActionIcon(Icons.Outlined.ArrowDownward,"Move ${p.title} later",i<s.notebook!!.pages.lastIndex) {vm.reorder(p,1)} }
                } }
            }
        }
    }
    if(colors) ColorPicker(s.penColor,s.prefs.recentColors,{colors=false}) {vm.color(it);colors=false}
    if(rename) NameDialog("Rename page",page.title,{rename=false}) {vm.edit(page.copy(title=it))}
    layerMenu?.let { chosen -> LayerDialog(page,chosen,{layerMenu=null},vm::edit) {layer->canvasView?.viewPoint(layer.x,layer.y)?.let {point->inlinePosition=androidx.compose.ui.geometry.Offset(point.x,point.y);pagePosition=androidx.compose.ui.geometry.Offset(layer.x,layer.y);editingText=layer;typingStarted=false;revealSource=true;baseTextHeight=null;baseLayerHeight=layer.height;inlineValue=TextFieldValue(layer.text,selection=TextRange(layer.text.length))};layerMenu=null} }
}

@Composable private fun LayerDialog(page: Page, initial: Layer,onDismiss:()->Unit,onEdit:(Page)->Unit,onText:(Layer)->Unit) {
    val context=LocalContext.current
    var selectedId by remember { mutableStateOf(initial.id) }
    val l=page.layers.find {it.id==selectedId} ?: run {onDismiss();return}
    var x by remember(l) {mutableStateOf(l.x.toInt().toString())}; var y by remember(l) {mutableStateOf(l.y.toInt().toString())}
    var w by remember(l) {mutableStateOf(l.width.toInt().toString())}; var h by remember(l) {mutableStateOf(l.height.toInt().toString())}
    fun replace(layer: Layer) {onEdit(page.copy(layers=page.layers.map {if(it.id==layer.id) layer else it}))}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Page objects")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Front to back",style=MaterialTheme.typography.labelMedium)
        page.layers.asReversed().forEach { layer -> FilterChip(layer.id==l.id,{selectedId=layer.id},label={Text(when(layer.kind){LayerKind.Text->layer.text.take(32);LayerKind.Image->"Image";LayerKind.Ink->"Ink stroke"} + if(layer.locked) " · locked" else "")}) }
        HorizontalDivider(Modifier.padding(vertical=8.dp))
        TextButton({onEdit(page.moveLayer(l.id,true))}) {Text("Bring to front")}
        TextButton({onEdit(page.moveLayer(l.id,false))}) {Text("Send to back")}
        SettingSwitch("Lock position","Protect this object from moving or erasing.",l.locked) {replace(l.copy(locked=it))}
        if(l.kind==LayerKind.Text) {
            TextButton({onText(l)}) {Text("Edit text")}
            Markdown.parse(l.text).flatMap {it.spans}.mapNotNull {span->span.kind.takeIf {it.startsWith("link:")}?.removePrefix("link:")}.distinct().forEach {url->
                val uri=runCatching {Uri.parse(url)}.getOrNull()
                if(uri?.scheme in listOf("http","https")) TextButton({runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,uri))}.onFailure {Toast.makeText(context,"No app can open this link",Toast.LENGTH_SHORT).show()};onDismiss()}) {Text("Open $url",maxLines=1,overflow=TextOverflow.Ellipsis)}
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {TextFace.entries.forEach {face->FilterChip(l.textFace==face,{replace(l.copy(textFace=face))},label={Text(face.name)})}}
        }
        if(l.kind!=LayerKind.Ink) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(x,{x=it},label={Text("X")},singleLine=true,enabled=!l.locked,modifier=Modifier.weight(1f)); OutlinedTextField(y,{y=it},label={Text("Y")},singleLine=true,enabled=!l.locked,modifier=Modifier.weight(1f)) }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(w,{w=it},label={Text("Width")},singleLine=true,enabled=!l.locked,modifier=Modifier.weight(1f)); OutlinedTextField(h,{h=it},label={Text("Height")},singleLine=true,enabled=!l.locked,modifier=Modifier.weight(1f)) }
            TextButton({replace(l.copy(x=x.toFloat().coerceIn(0f,page.width-20),y=y.toFloat().coerceIn(0f,page.height-20),width=w.toFloat().coerceIn(32f,page.width*2),height=h.toFloat().coerceIn(32f,page.height*2)))},enabled=!l.locked && listOf(x,y,w,h).all {it.toFloatOrNull()?.isFinite()==true}) {Text("Apply position & size")}
        }
        TextButton({onEdit(page.copy(layers=page.layers.filterNot {it.id==l.id}));onDismiss()},colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) {Text("Remove")}
    }},confirmButton={TextButton(onDismiss){Text("Done")}})
}
