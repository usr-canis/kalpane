package dev.kalpane

import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.ui.platform.ComposeView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.strokes.Stroke
import dev.kalpane.ui.KalpaneApp
import dev.kalpane.ui.WetInk

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The ink overlay sits above Compose and never takes touches; it only renders wet strokes.
        val ink = InProgressStrokesView(this).apply {
            addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
                override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                    // Wait for the page to draw the committed stroke before removing the wet one.
                    postOnAnimation { postOnAnimation { removeFinishedStrokes(strokes.keys) } }
                }
            })
        }
        WetInk.view = ink
        setContentView(FrameLayout(this).apply {
            addView(ComposeView(this@MainActivity).apply { setContent { KalpaneApp(vm) } }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(ink, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        })
    }

    override fun onDestroy() {
        if (WetInk.view?.context === this) WetInk.view = null
        super.onDestroy()
    }
}
