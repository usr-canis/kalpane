package dev.kalpane

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Walks through the main flows on a fresh install and saves screenshots for review:
 * create a notebook, write Markdown, draw a shape, navigate back, open settings, dark theme.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WalkthroughTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val shots = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(400) // let crossfades settle
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(shots, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher) =
        rule.waitUntilAtLeastOneExists(matcher, 10_000)

    @Test fun walkthrough() {
        waitFor(hasText("New notebook"))
        shot("01-home-empty")
        rule.onNode(hasText("New notebook")).performClick()
        rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("Work")
        rule.onNode(hasText("Create")).performClick()

        waitFor(hasContentDescription("Work, Page 1"))
        shot("02-notebook")
        rule.onNode(hasContentDescription("Work, Page 1")).performClick()

        waitFor(hasSetTextAction())
        rule.onNode(hasSetTextAction()).performTextInput(
            "# Kalpane\nNotes with **Markdown**, #ideas and [[Plan]].\n- [ ] Sketch the layout\n- [x] Pick fonts\n> Quote\n```kotlin\nval x = 1 // code\n```\n"
        )
        shot("03-page-text")

        rule.onNode(hasContentDescription("Shapes")).performClick()
        rule.onRoot().performTouchInput {
            swipe(Offset(width * 0.25f, height * 0.62f), Offset(width * 0.7f, height * 0.82f), 300)
        }
        shot("04-page-shape")

        Espresso.pressBack() // clear selection
        Espresso.pressBack() // leave the page
        waitFor(hasContentDescription("Work, Page 1"))
        shot("05-notebook-after")
        Espresso.pressBack()

        waitFor(hasText("Notebooks"))
        shot("06-home")
        rule.onNode(hasContentDescription("More")).performClick()
        rule.onNode(hasText("Settings")).performClick()
        waitFor(hasText("Theme"))
        shot("07-settings")
        rule.onNode(hasText("Dark")).performClick()
        shot("08-settings-dark")
        Espresso.pressBack()
        waitFor(hasText("Notebooks"))
        shot("09-home-dark")
    }
}
