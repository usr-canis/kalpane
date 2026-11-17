package dev.kalpane.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kalpane.NotebookViewModel
import kotlinx.coroutines.delay

@Composable fun KalpaneApp(vm: NotebookViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    var launched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(s.loaded) { if(s.loaded) { delay(if(s.prefs.reducedMotion) 0 else 550); launched=true } }
    KalpaneTheme(s.prefs.theme) {
        val snackbar=remember { SnackbarHostState() }
        LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it,withDismissAction=true,duration=SnackbarDuration.Long); vm.message(null) } }
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            if(!s.loaded || !launched) {
                Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                    Brand(large=true); Spacer(Modifier.height(20.dp)); Text("A place for your thinking.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if(s.fatalError!=null) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),verticalArrangement=Arrangement.Center) { Text("Your files are preserved",style=MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(16.dp)); Text(s.fatalError!!) }
            } else {
                Box(Modifier.fillMaxSize()) {
                    when(s.screen) {
                        "settings" -> SettingsScreen(s,vm)
                        "editor" -> if(s.page!=null) EditorScreen(s,vm) else PagesScreen(s,vm)
                        "pages" -> if(s.notebook!=null) PagesScreen(s,vm) else LibraryScreen(s,vm)
                        else -> LibraryScreen(s,vm)
                    }
                    SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom=if(s.screen=="editor") 100.dp else 12.dp))
                }
            }
        }
    }
}
