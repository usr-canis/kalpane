package dev.kalpane.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily as AndroidFontFamily
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import dev.kalpane.R
import dev.kalpane.data.TextFace

fun TextFace.displayName() = when(this) {TextFace.Sans->"Roboto";TextFace.Serif->"Source Serif 4";TextFace.Monospace->"JetBrains Mono"}
object NoteTypefaces {
    private val cache=mutableMapOf<TextFace,Typeface>()
    @Synchronized fun get(context: Context,face: TextFace): Typeface = cache.getOrPut(face) {
        val kannada=AndroidFontFamily.Builder(Font.Builder(context.resources,R.font.mallige).build()).build()
        val primary=when(face) {
            TextFace.Sans->AndroidFontFamily.Builder(Font.Builder(context.resources,R.font.roboto).build()).build()
            TextFace.Serif->AndroidFontFamily.Builder(Font.Builder(context.resources,R.font.source_serif).build()).build()
            TextFace.Monospace->AndroidFontFamily.Builder(Font.Builder(context.resources,R.font.jetbrains_mono).build()).build()
        }
        Typeface.CustomFallbackBuilder(primary).apply {addCustomFallback(kannada)}
            .setSystemFallback(when(face){TextFace.Sans->"sans-serif";TextFace.Serif->"serif";TextFace.Monospace->"monospace"}).build()
    }
}
@Composable fun noteFont(face: TextFace): FontFamily {
    val context=LocalContext.current
    return remember(face) {FontFamily(NoteTypefaces.get(context,face))}
}
