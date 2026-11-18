package dev.kalpane

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.ViewModelProvider
import dev.kalpane.data.LayerKind
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import java.io.File
import org.junit.Assert.assertTrue
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun library() {ui.waitUntil(15000) {ui.onAllNodesWithText("Your notebooks").fetchSemanticsNodes().isNotEmpty()}}
    @Test fun createNotebookWriteTextUndoAndOpenSettings() {
        library()
        ui.onNodeWithContentDescription("New notebook").performClick()
        ui.onNodeWithText("Name").performTextInput("Device test ${System.nanoTime()}")
        ui.onNodeWithText("Save").performClick()
        ui.onNodeWithContentDescription("Continue last page").performClick()
        // A finger tap starts typing even while the pen is the active tool.
        ui.onNodeWithTag("document-scroll").performTouchInput { click(center) }
        ui.onNode(hasSetTextAction()).performTextInput("Voltage = current × resistance")
        ui.onNodeWithText("Done").assertDoesNotExist()
        ui.onNodeWithContentDescription("Eraser").assertIsDisplayed()
        ui.waitUntil(10000) {
            val insets=ui.activity.window.decorView.rootWindowInsets
            insets!=null && WindowInsetsCompat.toWindowInsetsCompat(insets).isVisible(WindowInsetsCompat.Type.ime())
        }
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val screenshot=instrumentation.uiAutomation.takeScreenshot()
        if(screenshot!=null) {
            val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"screenshots").apply {mkdirs()}
            File(dir,"typing-with-keyboard.png").outputStream().use {screenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
            screenshot.recycle()
        }
        ui.waitUntil(10000) {ViewModelProvider(ui.activity)[NotebookViewModel::class.java].state.value.page?.layers?.any {it.kind==LayerKind.Text && it.text.contains("Voltage = current")} == true}
        ui.onNodeWithContentDescription("Pen").performClick()
        ui.onNodeWithContentDescription("Undo").assertIsEnabled().performClick()
        ui.onNodeWithContentDescription("More tools").performClick()
        ui.onNodeWithContentDescription("Redo").assertIsEnabled().performClick()
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Appearance"))
        ui.onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Dark").performClick()
        ui.onNodeWithText("Light").performClick()
        ui.onNodeWithText("System").performClick()
    }
    @Test fun focusModeExposesOneAccessibleTool() {
        library()
        ui.onNodeWithContentDescription("New notebook").performClick()
        ui.onNodeWithText("Name").performTextInput("Focus ${System.nanoTime()}")
        ui.onNodeWithText("Save").performClick()
        ui.onNodeWithContentDescription("Continue last page").performClick()
        ui.onNodeWithContentDescription("Enter focus mode").performClick()
        ui.onNodeWithContentDescription("Add text").assertDoesNotExist()
        ui.onNodeWithContentDescription("Expand tools and exit focus mode").assertIsDisplayed().performClick()
        ui.onNodeWithContentDescription("Pen").assertIsDisplayed()
    }
    @Test fun eraserTapUsesLastSelectedMode() {
        library()
        ui.onNodeWithContentDescription("New notebook").performClick()
        ui.onNodeWithText("Name").performTextInput("Eraser ${System.nanoTime()}")
        ui.onNodeWithText("Save").performClick()
        ui.onNodeWithContentDescription("Continue last page").performClick()
        ui.onNodeWithContentDescription("Eraser").performTouchInput { longClick() }
        ui.onNodeWithText("Erase part").performClick()
        ui.onNodeWithContentDescription("Pen").performClick()
        ui.onNodeWithContentDescription("Eraser").performClick()
        ui.waitUntil(5000) {ViewModelProvider(ui.activity)[NotebookViewModel::class.java].state.value.tool==Tool.PartialEraser}
    }
    @Test fun newPageAcceptsDirectTypingWithoutChangingPreviousPage() {
        library()
        ui.onNodeWithContentDescription("New notebook").performClick()
        ui.onNodeWithText("Name").performTextInput("Pages ${System.nanoTime()}")
        ui.onNodeWithText("Save").performClick()
        ui.onNodeWithContentDescription("Continue last page").performClick()
        ui.onNodeWithTag("document-scroll").performTouchInput {click(center)}
        ui.onNode(hasSetTextAction()).performTextInput("First page")
        ui.onNodeWithContentDescription("Pen").performClick()
        ui.onNodeWithContentDescription("New page").performClick()
        val model=ViewModelProvider(ui.activity)[NotebookViewModel::class.java]
        ui.waitUntil(5000) {model.state.value.notebook?.pages?.size==2}
        ui.onNodeWithTag("document-scroll").performTouchInput {click(center)}
        ui.onNode(hasSetTextAction()).performTextInput("Second page")
        ui.waitUntil(5000) {model.state.value.page?.layers?.any {it.text=="Second page"}==true}
        val pages=model.state.value.notebook!!.pages
        assertTrue(pages[0].layers.any {it.text=="First page"})
        assertTrue(pages[1].layers.any {it.text=="Second page"})
    }
    @Test fun fingerSwipeScrollsPaperWithoutCreatingInk() {
        library()
        ui.onNodeWithContentDescription("New notebook").performClick()
        ui.onNodeWithText("Name").performTextInput("Scroll ${System.nanoTime()}")
        ui.onNodeWithText("Save").performClick()
        ui.onNodeWithContentDescription("Continue last page").performClick()
        val model=ViewModelProvider(ui.activity)[NotebookViewModel::class.java]
        ui.onNodeWithTag("document-scroll").performTouchInput {swipeUp()}
        ui.waitUntil(5000) {ui.onNodeWithTag("document-scroll").fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke()?.let {it>0f} == true}
        assertTrue(model.state.value.page!!.layers.none {it.kind==LayerKind.Ink})
        ui.onNodeWithContentDescription("Eraser").assertIsDisplayed()
    }
    @Test fun syntheticStylusDrawsOverActiveText() {
        library()
        ui.onNodeWithContentDescription("New notebook").performClick()
        ui.onNodeWithText("Name").performTextInput("Mixed input ${System.nanoTime()}")
        ui.onNodeWithText("Save").performClick()
        ui.onNodeWithContentDescription("Continue last page").performClick()
        ui.onNodeWithTag("document-scroll").performTouchInput {click(center)}
        ui.onNode(hasSetTextAction()).performTextInput("Text and ink")
        ui.waitForIdle()
        val bounds=ui.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val down=SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP).forEachIndexed {i,action->
            ui.runOnUiThread {
                val prop=MotionEvent.PointerProperties().apply {id=0;toolType=MotionEvent.TOOL_TYPE_STYLUS}
                val coords=MotionEvent.PointerCoords().apply {x=bounds.left+20f+i*8f;y=bounds.center.y;pressure=.5f}
                MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,1,arrayOf(prop),arrayOf(coords),0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,0).also {ui.activity.dispatchTouchEvent(it);it.recycle()}
            }
            Thread.sleep(40)
        }
        val model=ViewModelProvider(ui.activity)[NotebookViewModel::class.java]
        ui.waitUntil(10000) {model.state.value.page?.layers?.any {it.kind==LayerKind.Ink}==true}
        assertTrue(model.state.value.page!!.layers.any {it.kind==LayerKind.Text && it.text=="Text and ink"})
    }
    @Test fun settingsControlsRemainReachable() {
        library();ui.onNodeWithContentDescription("Settings").performClick()
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Appearance"))
        ui.onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Git remote"))
        ui.onNodeWithText("Git remote").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Private repository"))
        ui.onNodeWithText("Private repository").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Create branch"))
        ui.onNodeWithText("Create branch").performScrollTo().assertIsDisplayed()
    }
}
