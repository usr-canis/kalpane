package dev.kalpane

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.kalpane.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/** Fixture screens are saved as CI artifacts; these are not seeded into user installations. */
class LayoutCaptureTest {
    @get:Rule val ui=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun capture(name: String) {
        ui.waitForIdle()
        // Compose can be idle before SurfaceFlinger presents a rotated/resized window.
        instrumentation.uiAutomation.waitForIdle(500,5000)
        val bitmap=instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"screenshots").apply {mkdirs()}
        File(dir,"font-${instrumentation.targetContext.resources.configuration.fontScale}-$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun capturePhoneOrTabletLayoutsAndAccessibleTools() {
        val context=instrumentation.targetContext
        check(context.packageName=="dev.kalpane") {"Fixtures must run only against the test application."}
        val store=DiskStore(File(context.filesDir,"notebooks"))
        val bitmap=Bitmap.createBitmap(600,240,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.drawColor(Color.rgb(238,243,237))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(70,104,88);strokeWidth=4f;style=Paint.Style.STROKE}
        canvas.drawLine(40f,120f,160f,120f,paint);canvas.drawRect(160f,90f,280f,150f,paint);canvas.drawLine(280f,120f,500f,120f,paint)
        canvas.drawLine(390f,120f,390f,180f,paint);canvas.drawLine(365f,180f,415f,180f,paint);canvas.drawLine(365f,194f,415f,194f,paint)
        paint.style=Paint.Style.FILL;paint.textSize=30f;canvas.drawText("R",205f,65f,paint);canvas.drawText("C",430f,195f,paint)
        val bytes=ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val asset=store.importAsset(bytes)
        val page=Page(id="fixture-page",title="RC low-pass filter",layers=listOf(
            Layer(kind=LayerKind.Text,x=80f,y=80f,width=820f,height=100f,fontSize=52f,text="A quieter signal.",textFace=TextFace.Serif),
            Layer(kind=LayerKind.Text,x=80f,y=170f,width=820f,height=150f,fontSize=28f,text="BENCH NOTES   /   17 NOV\nLet the useful frequencies through.",textFace=TextFace.Monospace,color=0xff65715e.toInt()),
            Layer(kind=LayerKind.Image,x=80f,y=360f,width=820f,height=328f,asset=asset),
            Layer(kind=LayerKind.Text,x=80f,y=780f,width=820f,height=240f,fontSize=36f,text="fc = 1 / (2πRC)\nR  = 10 kΩ\nC  = 100 nF\nfc ≈ 159 Hz",textFace=TextFace.Monospace),
            Layer(kind=LayerKind.Ink,color=0xffE0730C.toInt(),penWidth=4f,points=listOf(InkPoint(80f,1100f,0),InkPoint(270f,1090f,40),InkPoint(500f,1110f,80),InkPoint(720f,1105f,120))),
            Layer(kind=LayerKind.Text,x=80f,y=1190f,width=820f,height=100f,fontSize=38f,text="ಕಲ್ಪನೆ → ಪ್ರಯೋಗ",textFace=TextFace.Serif)
        ))
        val notebook=Notebook(id="fixture-notebook",title="Systems & signals",pages=listOf(page,Page(title="Bench log"),Page(title="Open questions")))
        val doc=Document(notebooks=listOf(notebook,Notebook(title="Field notes",pages=listOf(Page(title="Small observations")))))
        store.savePreferences(Preferences(theme=ThemeMode.Light,textFace=TextFace.Serif,reducedMotion=true))
        store.save("","main",Workspace(document=doc,lastNotebook=notebook.id,lastPage=page.id))
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            ui.waitUntil(60000) {ui.onAllNodesWithText("Your notebooks").fetchSemanticsNodes().isNotEmpty()}
            capture("01-notebooks")
            scenario.onActivity {ViewModelProvider(it)[NotebookViewModel::class.java].openNotebook(notebook.id)}
            ui.waitForIdle();capture("02-pages")
            ui.onNodeWithContentDescription("Continue last page").performClick()
            ui.onNodeWithContentDescription("Pen").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            ui.onNodeWithContentDescription("Add text").assertIsDisplayed()
            ui.waitUntil(15000) {var saved=false;scenario.onActivity {saved=!ViewModelProvider(it)[NotebookViewModel::class.java].state.value.saving};saved}
            capture("03-editor")
            scenario.onActivity {ViewModelProvider(it)[NotebookViewModel::class.java].screen("settings")}
            ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Text & typography"))
            ui.onNodeWithText("Text & typography").performScrollTo()
            ui.onNodeWithText("Serif").performClick()
            ui.onNodeWithText("The quick brown fox jumps over the lazy dog").performScrollTo();capture("04-serif-settings")
            ui.onNodeWithText("Monospace").performScrollTo().performClick()
            ui.onNodeWithText("The quick brown fox jumps over the lazy dog").performScrollTo();capture("05-monospace-settings")
            ui.onNodeWithTag("settings-list").performScrollToNode(hasContentDescription("Pen-only mode"))
            ui.onNodeWithContentDescription("Pen-only mode").performScrollTo().assertIsToggleable()
            scenario.onActivity {ViewModelProvider(it)[NotebookViewModel::class.java].screen("editor");it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            ui.waitUntil(10000) {var landscape=false;scenario.onActivity {landscape=it.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE};landscape}
            ui.waitForIdle();ui.onNodeWithContentDescription("Pen").assertIsDisplayed();capture("06-landscape-editor")
            scenario.onActivity {ViewModelProvider(it)[NotebookViewModel::class.java].preferences {p->p.copy(theme=ThemeMode.Dark)}}
            ui.waitForIdle()
            ui.waitUntil(5000) {var darkBars=false;scenario.onActivity {darkBars=!WindowCompat.getInsetsController(it.window,it.window.decorView).isAppearanceLightStatusBars};darkBars}
            capture("07-dark-editor")
            scenario.onActivity {ViewModelProvider(it)[NotebookViewModel::class.java].focus()}
            ui.onNodeWithContentDescription("Expand tools and exit focus mode").assertIsDisplayed();capture("08-focus")
        }
        // A fresh activity and ViewModel reload the acknowledged save from app storage.
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            ui.waitUntil(60000) {ui.onAllNodesWithText("Your notebooks").fetchSemanticsNodes().isNotEmpty()}
            scenario.onActivity {assertEquals(page.layers,ViewModelProvider(it)[NotebookViewModel::class.java].state.value.workspace.document.pages().first {p->p.id==page.id}.layers)}
        }
    }
}
