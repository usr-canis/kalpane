package dev.kalpane

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kalpane.ui.Amber
import dev.kalpane.ui.Mallige
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.runtime.CompositionLocalProvider
import java.io.File

/** Renders the 1024×500 Play Store feature graphic, so the Kannada title is shaped by Android itself. */
@RunWith(AndroidJUnit4::class)
class StoreGraphicTest {
    @get:Rule val rule = createComposeRule()

    @Test fun featureGraphic() {
        rule.setContent {
            // 1 dp = 1 px, so the layout is exactly 1024×500 pixels.
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                Row(
                    Modifier.testTag("graphic").requiredSize(1024.dp, 500.dp).background(Color(0xFFFAFAF8)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(24.dp).fillMaxHeight().background(Amber))
                    Spacer(Modifier.width(120.dp))
                    Image(painterResource(R.drawable.kalpane_logo), null, Modifier.size(220.dp))
                    Spacer(Modifier.width(64.dp))
                    Column {
                        Text("ಕಲ್ಪನೆ", fontFamily = Mallige, fontSize = 120.sp, color = Color(0xFF1C1B19))
                        Text("Kalpane", fontSize = 44.sp, color = Color(0xFF6B6A66), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        val bitmap = rule.onNodeWithTag("graphic").captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "play-feature-graphic.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
