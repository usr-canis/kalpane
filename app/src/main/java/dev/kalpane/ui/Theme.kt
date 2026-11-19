package dev.kalpane.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.kalpane.R
import dev.kalpane.ThemeMode
import android.graphics.fonts.FontFamily as PlatformFontFamily
import androidx.compose.ui.text.font.Font as ComposeFont

val Amber = Color(0xFFE0730C)

/** The Kannada wordmark face. */
val Mallige = FontFamily(ComposeFont(R.font.mallige))

private val LightColors = lightColorScheme(
    primary = Amber, onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE8D4), onPrimaryContainer = Color(0xFF4A2200),
    secondary = Color(0xFF6B6A66), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0EEEA), onSecondaryContainer = Color(0xFF1C1B19),
    background = Color(0xFFFAFAF8), onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFFAFAF8), onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFF0EEEA), onSurfaceVariant = Color(0xFF6B6A66),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F4F1),
    surfaceContainer = Color(0xFFF1F0EC), surfaceContainerHigh = Color(0xFFECEAE6),
    surfaceContainerHighest = Color(0xFFE6E4DF),
    outline = Color(0xFFA3A19B), outlineVariant = Color(0xFFE6E4DF),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF08A2C), onPrimary = Color(0xFF2B1400),
    primaryContainer = Color(0xFF4A2600), onPrimaryContainer = Color(0xFFFFDCC0),
    secondary = Color(0xFFA3A19B), onSecondary = Color(0xFF1C1B19),
    secondaryContainer = Color(0xFF292927), onSecondaryContainer = Color(0xFFECEAE6),
    background = Color(0xFF151514), onBackground = Color(0xFFECEAE6),
    surface = Color(0xFF151514), onSurface = Color(0xFFECEAE6),
    surfaceVariant = Color(0xFF262523), onSurfaceVariant = Color(0xFFA3A19B),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF0F0F0E), surfaceContainerLow = Color(0xFF1A1A19),
    surfaceContainer = Color(0xFF1F1F1D), surfaceContainerHigh = Color(0xFF292927),
    surfaceContainerHighest = Color(0xFF33332F),
    outline = Color(0xFF6E6C67), outlineVariant = Color(0xFF2E2D2B),
    error = Color(0xFFF2B8B5),
)

/** Roboto / JetBrains Mono, both falling back to Mallige for Kannada. */
object Typefaces {
    private val cache = mutableMapOf<Int, Typeface>()

    @Synchronized
    fun get(context: Context, font: Int): Typeface = cache.getOrPut(font) {
        fun family(id: Int) = PlatformFontFamily.Builder(Font.Builder(context.resources, id).build()).build()
        Typeface.CustomFallbackBuilder(family(font))
            .addCustomFallback(family(R.font.mallige))
            .setSystemFallback(if (font == R.font.jetbrains_mono) "monospace" else "sans-serif")
            .build()
    }
}

@Composable
fun appFont(mono: Boolean = false): FontFamily {
    val context = LocalContext.current
    val id = if (mono) R.font.jetbrains_mono else R.font.roboto
    return remember(id) { FontFamily(Typefaces.get(context, id)) }
}

@Composable
fun isDark(mode: ThemeMode) = mode == ThemeMode.Dark || (mode == ThemeMode.System && isSystemInDarkTheme())

@Composable
fun KalpaneTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val sans = appFont()
    val base = Typography()
    fun TextStyle.f() = copy(fontFamily = sans)
    val typography = Typography(
        displayLarge = base.displayLarge.f(), displayMedium = base.displayMedium.f(), displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(), headlineMedium = base.headlineMedium.f(), headlineSmall = base.headlineSmall.f(),
        titleLarge = base.titleLarge.f().copy(fontWeight = FontWeight.Medium, fontSize = 20.sp),
        titleMedium = base.titleMedium.f(), titleSmall = base.titleSmall.f(),
        bodyLarge = base.bodyLarge.f(), bodyMedium = base.bodyMedium.f(), bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(), labelMedium = base.labelMedium.f(), labelSmall = base.labelSmall.f(),
    )
    MaterialTheme(colorScheme = if (isDark(mode)) DarkColors else LightColors, typography = typography, content = content)
}
