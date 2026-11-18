package dev.kalpane.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kalpane.*
import dev.kalpane.data.*
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SettingsScreen(s: AppState,vm: NotebookViewModel) {
    val back={vm.screen(if(s.page!=null) "editor" else "notebooks")}
    BackHandler {if(!s.switchingWorkspace) back()}
    var repo by remember(s.prefs.repo) {mutableStateOf(s.prefs.repo)}
    // Credentials intentionally use neither rememberSaveable nor ViewModel state.
    var token by remember {mutableStateOf("")}
    var branchMenu by remember {mutableStateOf(false)}
    var newBranch by remember {mutableStateOf(false)}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {it?.let(vm::exportArchive)}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {it?.let(vm::importArchive)}
    LaunchedEffect(s.connection) {if(s.connection.startsWith("Verified")) token=""}
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min=72.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {ActionIcon(Icons.AutoMirrored.Outlined.ArrowBack,"Back",!s.switchingWorkspace,onClick=back);Text("Settings",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(start=12.dp))}
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.testTag("settings-list").widthIn(max=760.dp).fillMaxWidth().align(Alignment.TopCenter),contentPadding=PaddingValues(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                item {

                    Text("Appearance",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(top=8.dp,bottom=12.dp))
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {ThemeMode.entries.forEach {m->FilterChip(s.prefs.theme==m,{vm.preferences {it.copy(theme=m)}},label={Text(m.name)})}}
                    Text("Fixed colors, independent of your wallpaper. Paper stays cream so every saved ink color remains faithful.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    HorizontalDivider();Spacer(Modifier.height(20.dp));Text("Text & typography",style=MaterialTheme.typography.titleLarge)
                    Text("Default for new text. Kannada always uses Mallige.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {TextFace.entries.forEach {face->FilterChip(s.prefs.textFace==face,{vm.preferences {it.copy(textFace=face)}},label={Text(face.name)})}}
                    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().padding(vertical=12.dp)) {
                        Column(Modifier.padding(20.dp)) {
                            Text(s.prefs.textFace.displayName(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("The quick brown fox jumps over the lazy dog",fontFamily=noteFont(s.prefs.textFace),fontSize=22.sp,modifier=Modifier.padding(top=10.dp))
                            Text("ಕಲ್ಪನೆ",fontFamily=Mallige,fontSize=26.sp,modifier=Modifier.padding(top=8.dp))
                        }
                    }
                    Text("Text size · ${s.prefs.textSize.toInt()} page units",style=MaterialTheme.typography.labelLarge)
                    Slider(s.prefs.textSize,{v->vm.preferences {it.copy(textSize=v)}},valueRange=16f..64f,modifier=Modifier.semantics {contentDescription="Default text size"})
                    s.page?.let {p->TextButton({vm.edit(p.copy(layers=p.layers.map {l->if(l.kind==LayerKind.Text) l.copy(textFace=s.prefs.textFace,fontSize=s.prefs.textSize) else l}))}) {Text("Apply typography to current page text")}}
                }
                item {
                    HorizontalDivider(); Spacer(Modifier.height(20.dp)); Text("Paper & grid",style=MaterialTheme.typography.titleLarge)
                    Text("Default for new pages",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {Paper.entries.forEach {p->FilterChip(s.prefs.paper==p,{vm.preferences {it.copy(paper=p)}},label={Text(p.name)})}}
                    s.page?.let {p-> TextButton({vm.edit(p.copy(paper=s.prefs.paper))}) {Text("Apply ${s.prefs.paper.name.lowercase()} paper to current page")} }
                    Text("Grid spacing · ${s.prefs.gridSpacing.toInt()} page units",style=MaterialTheme.typography.labelLarge)
                    Slider(s.prefs.gridSpacing,{v->vm.preferences {it.copy(gridSpacing=v)}},valueRange=12f..64f,modifier=Modifier.semantics {contentDescription="Grid spacing"})
                }
                item {
                    HorizontalDivider();Spacer(Modifier.height(20.dp));Text("Pen & gestures",style=MaterialTheme.typography.titleLarge)
                    Text("Default pen width · ${"%.1f".format(s.prefs.penWidth)}",style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(top=16.dp))
                    Slider(s.prefs.penWidth,{v->vm.preferences {it.copy(penWidth=v)};vm.width(v)},valueRange=1f..18f,modifier=Modifier.semantics {contentDescription="Default pen width"})
                    SettingSwitch("Pen-only mode","Reject finger ink. Touch still works for controls and pinch zoom.",!s.prefs.fingerDrawing) {v->vm.preferences {it.copy(fingerDrawing=!v)}}
                    SettingSwitch("Finger drawing","Allow a single finger to draw when no pen is active.",s.prefs.fingerDrawing) {v->vm.preferences {it.copy(fingerDrawing=v)}}
                    SettingSwitch("Reduced motion","Remove page-turn and launch transitions.",s.prefs.reducedMotion) {v->vm.preferences {it.copy(reducedMotion=v)}}
                    Text("Page swipes work only with the Navigate tool at fit size. Stylus activity pauses touch gestures.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    HorizontalDivider();Spacer(Modifier.height(20.dp));Text("Portable notebooks",style=MaterialTheme.typography.titleLarge)
                    Text("Take a local ZIP backup with text, vector ink, and original images. Imports create separate notebook copies.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=8.dp))
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        OutlinedButton({export.launch("Kalpane-notebooks.zip")},enabled=!s.busy) {Text("Export notebooks")}
                        OutlinedButton({import.launch(arrayOf("application/zip","application/octet-stream"))},enabled=!s.busy) {Text("Import notebooks")}
                    }
                }
                item {
                    HorizontalDivider();Spacer(Modifier.height(24.dp));
                    Row(Modifier.padding(top=8.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {Icon(Icons.Outlined.CloudSync,null,tint=MaterialTheme.colorScheme.primary);Text("Git remote",style=MaterialTheme.typography.titleLarge)}
                    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainer,modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(s.connection,style=MaterialTheme.typography.titleSmall,modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})
                            Text("Branch · ${s.prefs.branch}",style=MaterialTheme.typography.bodyMedium)
                            Text(if(s.workspace.pending) "${s.workspace.pendingPages} changed pages · pending edits" else "No pending edits",style=MaterialTheme.typography.bodyMedium)
                            Text("Last successful sync · ${s.workspace.lastBackup?.let {DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(it))} ?: "Never"}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(repo,{repo=it},label={Text("Private repository")},placeholder={Text("owner/repository")},singleLine=true,enabled=!s.busy,modifier=Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(token,{token=it},label={Text("Repository-scoped token")},supportingText={Text("Fine-grained token · Contents: read and write. Leave blank to use the stored credential.")},visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=!s.busy,modifier=Modifier.fillMaxWidth())
                    Text("Encrypted with an Android Keystore-backed key. Credentials are excluded from the remote.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,bottom=12.dp))
                    Button({vm.verify(repo,token)},enabled=!s.busy && repo.isNotBlank()) {Text("Verify connection")}
                    if(s.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical=12.dp))
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(vm::push,enabled=!s.busy && s.prefs.repo.isNotBlank() && s.workspace.remoteHead!=null) {Icon(Icons.Outlined.CloudUpload,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Push")}
                        OutlinedButton(vm::pull,enabled=!s.busy && s.prefs.repo.isNotBlank()) {Icon(Icons.Outlined.CloudDownload,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Pull")}
                    }
                    Text("Pull before your first push. If the remote changes, pull again to merge. Conflicting pages are kept as separate copies.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=12.dp))
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Box {OutlinedButton({branchMenu=true},enabled=!s.busy && s.branches.isNotEmpty()) {Text("Switch branch")};DropdownMenu(branchMenu,{branchMenu=false}) {s.branches.forEach {name->DropdownMenuItem({Text(name)},{branchMenu=false;vm.switchBranch(name)},enabled=name!=s.prefs.branch)}}}
                        OutlinedButton({newBranch=true},enabled=!s.busy && s.prefs.repo.isNotBlank()) {Text("Create branch")}
                    }
                    Text("Unsynced edits stay with their branch. New branches start at the remote head.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
                    TextButton(vm::forgetCredential,enabled=!s.busy && s.prefs.repo.isNotBlank()) {Text("Remove stored credential")}
                }
                item {HorizontalDivider();Spacer(Modifier.height(16.dp));Brand();Text("Kalpane ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(24.dp))}
            }
        }
    }
    if(newBranch) NameDialog("Create branch",onDismiss={newBranch=false},onSave=vm::createBranch)
}
