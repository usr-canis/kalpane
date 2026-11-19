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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kalpane.AppViewModel
import dev.kalpane.PenPrefs
import androidx.compose.runtime.snapshotFlow
import dev.kalpane.data.PageRef

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
    val tools = remember {
        ToolState().apply {
            penWidth = vm.penPrefs.width
            highlighterWidth = vm.penPrefs.highlighterWidth
            pressure = Pressure.entries.firstOrNull { it.name == vm.penPrefs.pressure } ?: Pressure.Normal
        }
    }
    LaunchedEffect(tools) {
        snapshotFlow { PenPrefs(tools.penWidth, tools.highlighterWidth, tools.pressure.name) }
            .collect { vm.savePen(it) }
    }
    KalpaneTheme(theme) {
        // "" (notebooks), "book:<name>", "page:<notebook>/<page>" or "settings".
        var screen by rememberSaveable { mutableStateOf("") }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Crossfade(targetState = screen, animationSpec = tween(160), label = "screen") { current ->
                when {
                    current.startsWith("page:") -> {
                        val ref = PageRef.decode(current.removePrefix("page:"))
                        PageScreen(
                            vm = vm,
                            ref = ref,
                            tools = tools,
                            onBack = { screen = "book:" + ref.notebook },
                            onRenamed = { screen = "page:" + it.encode() },
                        )
                    }
                    current.startsWith("book:") -> PagesScreen(
                        vm = vm,
                        notebook = current.removePrefix("book:"),
                        onOpen = { screen = "page:" + it.encode() },
                        onBack = { screen = "" },
                    )
                    current == "settings" -> SettingsScreen(vm, onBack = { screen = "" })
                    current == "trash" -> TrashScreen(vm, onBack = { screen = "" })
                    else -> HomeScreen(
                        vm = vm,
                        onOpen = { screen = "book:$it" },
                        onOpenPage = { screen = "page:" + it.encode() },
                        onSettings = { screen = "settings" },
                        onTrash = { screen = "trash" },
                    )
                }
            }
        }
    }
}
