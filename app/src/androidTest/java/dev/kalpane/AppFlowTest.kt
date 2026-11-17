package dev.kalpane

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        ui.onNodeWithContentDescription("Add text").performClick()
        ui.onNodeWithText("Text",useUnmergedTree=true).performTextInput("Voltage = current × resistance")
        ui.onNodeWithText("Save").performClick()
        ui.onNodeWithContentDescription("Undo").assertIsEnabled().performClick()
        ui.onNodeWithContentDescription("More tools").performClick()
        ui.onNodeWithContentDescription("Redo").assertIsEnabled().performClick()
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("Appearance").assertIsDisplayed()
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
    @Test fun settingsControlsRemainReachable() {
        library();ui.onNodeWithContentDescription("Settings").performClick()
        ui.onNodeWithText("Appearance").assertIsDisplayed()
        ui.onNodeWithText("GitHub backup").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Private repository").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Create branch").performScrollTo().assertIsDisplayed()
    }
}
