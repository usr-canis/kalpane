package dev.kalpane.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.kalpane.*
import dev.kalpane.data.*
import android.graphics.Typeface
import android.text.TextPaint
import android.text.StaticLayout
import android.text.Layout
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun LibraryScreen(s: AppState, vm: NotebookViewModel) {
    var create by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<Notebook?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal=24.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min=84.dp),verticalAlignment=Alignment.CenterVertically) {
            Brand(Modifier.weight(1f)); ActionIcon(Icons.Outlined.Search,"Search notebooks and pages") {search=true}; ActionIcon(Icons.Outlined.Settings,"Settings") { vm.screen("settings") }
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(28.dp)); SectionLabel("The engineer’s notebook")
        Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=24.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Your notebooks",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
            FilledTonalIconButton({create=true},Modifier.size(48.dp)) { Icon(Icons.Outlined.Add,"New notebook") }
        }
        if(s.workspace.document.notebooks.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainer,modifier=Modifier.size(112.dp)) { Box(contentAlignment=Alignment.Center) { Icon(Icons.Outlined.AutoStories,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary) } }
                Spacer(Modifier.height(28.dp)); Text("Make room for an idea.",style=MaterialTheme.typography.headlineSmall)
                Text("Sketch a circuit. Work through a proof.\nKeep the thought that started it all.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=12.dp,bottom=28.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                Button({create=true},contentPadding=PaddingValues(24.dp,14.dp)) { Icon(Icons.Outlined.Add,null); Spacer(Modifier.width(8.dp)); Text("Create a notebook") }
            }
        } else {
            LazyVerticalGrid(columns=GridCells.Adaptive(240.dp),modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(bottom=24.dp)) {
                items(s.workspace.document.notebooks,key={it.id}) { n ->
                    Card(onClick={vm.openNotebook(n.id)},shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.heightIn(min=136.dp)) {
                            Box(Modifier.width(4.dp).height(136.dp).background(Amber))
                            Column(Modifier.weight(1f).padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.MenuBook,null,tint=MaterialTheme.colorScheme.primary); Spacer(Modifier.weight(1f)); ActionIcon(Icons.Outlined.MoreHoriz,"Rename ${n.title}") { rename=n } }
                                Text(n.title,style=MaterialTheme.typography.titleLarge,fontFamily=if(n.title.any { it in '\u0C80'..'\u0CFF' }) Mallige else null,maxLines=2,overflow=TextOverflow.Ellipsis)
                                Text("${n.pages.size} ${if(n.pages.size==1) "page" else "pages"}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.OfflinePin,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if(s.saving) "Saving on this device…" else "Saved on this device · Ready offline",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if(search) SearchDialog(s,{search=false}) {notebookId,pageId->vm.openNotebook(notebookId);if(pageId!=null) vm.openPage(pageId);search=false}
    if(create) NameDialog("New notebook",onDismiss={create=false},onSave=vm::createNotebook)
    rename?.let { n -> NameDialog("Rename notebook",n.title,{rename=null}) {vm.renameNotebook(n.id,it)} }
}

@Composable fun PagesScreen(s: AppState, vm: NotebookViewModel) {
    BackHandler { vm.screen("notebooks") }
    var create by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<Page?>(null) }
    val n=s.notebook ?: return
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal=16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min=72.dp),verticalAlignment=Alignment.CenterVertically) {
            ActionIcon(Icons.AutoMirrored.Outlined.ArrowBack,"Notebooks") { vm.screen("notebooks") }
            Column(Modifier.weight(1f).padding(start=8.dp)) { SectionLabel("Notebook"); Text(n.title,style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis) }
            ActionIcon(Icons.Outlined.Settings,"Settings") {vm.screen("settings")}
        }
        Row(Modifier.fillMaxWidth().padding(vertical=16.dp,horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("${n.pages.size} pages",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
            FilledTonalButton({create=true}) { Icon(Icons.Outlined.Add,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("New page") }
        }
        s.page?.let { p -> Surface(onClick={vm.openPage(p.id)},shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.fillMaxWidth().padding(horizontal=8.dp).padding(bottom=20.dp)) {
            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("PICK UP WHERE YOU LEFT OFF",style=MaterialTheme.typography.labelSmall,letterSpacing=1.sp); Text(p.title,style=MaterialTheme.typography.titleMedium) }; Icon(Icons.AutoMirrored.Outlined.ArrowForward,"Continue last page") }
        } }
        LazyVerticalGrid(GridCells.Adaptive(160.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp),contentPadding=PaddingValues(8.dp,0.dp,8.dp,24.dp)) {
            itemsIndexed(n.pages,key={_,p->p.id}) { i,p ->
                Column {
                    Card(onClick={vm.openPage(p.id)},shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) { PageThumbnail(p,Modifier.fillMaxWidth().aspectRatio(.78f).padding(12.dp),vm.store) }
                    Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(p.title,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis); Text("${i+1} · ${p.layers.size} objects",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        var menu by remember {mutableStateOf(false)}
                        Box { ActionIcon(Icons.Outlined.MoreVert,"Page actions for ${p.title}") {menu=true}; DropdownMenu(menu,{menu=false}) {
                            DropdownMenuItem({Text("Rename")},{menu=false;rename=p})
                            DropdownMenuItem({Text("Move earlier")},{menu=false;vm.reorder(p,-1)},enabled=i>0)
                            DropdownMenuItem({Text("Move later")},{menu=false;vm.reorder(p,1)},enabled=i<n.pages.lastIndex)
                        } }
                    }
                }
            }
        }
    }
    if(create) NameDialog("New page",onDismiss={create=false},onSave=vm::createPage)
    rename?.let {p-> NameDialog("Rename page",p.title,{rename=null}) {vm.edit(p.copy(title=it))} }
}

@Composable fun PageThumbnail(page: Page, modifier: Modifier=Modifier, store: DiskStore?=null) {
    val context=LocalContext.current
    val font=remember {ResourcesCompat.getFont(context,dev.kalpane.R.font.mallige)}
    val images by produceState<Map<String,android.graphics.Bitmap>>(emptyMap(),page.id,page.layers,store) {
        value=withContext(Dispatchers.IO) {
            page.layers.filter {it.kind==LayerKind.Image}.mapNotNull {l->
                runCatching {
                    val file=store?.asset(l.asset) ?: return@runCatching null
                    l.asset to ImageAssets.decode(file,320)
                }.getOrNull()
            }.toMap()
        }
    }
    Canvas(modifier.background(Color(0xffFBF9F4))) {
        val sx=size.width/page.width; val sy=size.height/page.height
        if(page.paper!=Paper.Plain) { for(x in 1..14) for(y in 1..20) drawCircle(Color(0xffDDDDD4),.8f,Offset(size.width*x/15,size.height*y/21)) }
        page.layers.forEach { l -> when(l.kind) {
            LayerKind.Ink -> { val path=Path(); l.points.forEachIndexed { i,p -> if(i==0) path.moveTo(p.x*sx,p.y*sy) else path.lineTo(p.x*sx,p.y*sy) }; drawPath(path,Color(l.color),style=Stroke(maxOf(1f,l.penWidth*sx),cap=StrokeCap.Round)) }
            LayerKind.Text -> drawIntoCanvas {c->
                val native=c.nativeCanvas;native.save();native.scale(sx,sy);native.translate(l.x,l.y)
                val paint=TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {color=l.color;textSize=l.fontSize;typeface=NoteTypefaces.get(context,l.textFace)}
                StaticLayout.Builder.obtain(l.text,0,l.text.length,paint,l.width.toInt().coerceAtLeast(1)).setAlignment(Layout.Alignment.ALIGN_NORMAL).build().draw(native)
                native.restore()
            }
            LayerKind.Image -> images[l.asset]?.let {bitmap->drawIntoCanvas {c->c.nativeCanvas.drawBitmap(bitmap,null,android.graphics.RectF(l.x*sx,l.y*sy,(l.x+l.width)*sx,(l.y+l.height)*sy),android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG))}}
        } }
    }
}

@Composable private fun SearchDialog(s: AppState,onDismiss:()->Unit,onOpen:(String,String?)->Unit) {
    var query by remember {mutableStateOf("")}
    data class Match(val notebookId: String,val pageId: String?,val title: String,val detail: String)
    val results=remember(query,s.workspace.document) {
        if(query.isBlank()) emptyList() else s.workspace.document.notebooks.flatMap {n->
            val matches=mutableListOf<Match>()
            if(n.title.contains(query,true)) matches+=Match(n.id,null,n.title,"Notebook · ${n.pages.size} pages")
            n.pages.forEach {p->if(p.title.contains(query,true) || p.layers.any {it.kind==LayerKind.Text && it.text.contains(query,true)}) matches+=Match(n.id,p.id,p.title,n.title)}
            matches
        }.take(80)
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Find a thought")},text={Column {
        OutlinedTextField(query,{query=it},label={Text("Search titles and text")},singleLine=true,modifier=Modifier.fillMaxWidth())
        androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max=360.dp)) {
            items(results.size) {i->val result=results[i];ListItem(headlineContent={Text(result.title)},supportingContent={Text(result.detail)},modifier=Modifier.clickable {onOpen(result.notebookId,result.pageId)})}
            if(query.isNotBlank() && results.isEmpty()) item {Text("No matching thoughts yet.",Modifier.padding(vertical=20.dp),style=MaterialTheme.typography.bodyMedium)}
        }
    }},confirmButton={TextButton(onDismiss){Text("Close")}})
}
