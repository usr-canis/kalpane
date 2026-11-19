package dev.kalpane.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kalpane.AppViewModel
import dev.kalpane.data.NoteRef

@Composable
fun KalpaneApp(vm: AppViewModel) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val dark = isDark(theme)
    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(dark) {
        val transparent = Color.Transparent.toArgb()
        val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    KalpaneTheme(theme) {
        // "note:<ref>", "settings" or "" for the library.
        var screen by rememberSaveable { mutableStateOf("") }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Crossfade(targetState = screen, animationSpec = tween(180), label = "screen") { current ->
                when {
                    current.startsWith("note:") -> EditorScreen(
                        vm = vm,
                        ref = NoteRef.decode(current.removePrefix("note:")),
                        onClose = { screen = "" },
                    )
                    current == "settings" -> SettingsScreen(vm, onBack = { screen = "" })
                    else -> LibraryScreen(
                        vm = vm,
                        onOpen = { screen = "note:" + it.encode() },
                        onSettings = { screen = "settings" },
                    )
                }
            }
        }
    }
}
