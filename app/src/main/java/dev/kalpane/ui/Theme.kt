package dev.kalpane.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import dev.kalpane.R
import dev.kalpane.data.ThemeMode

val Mallige = FontFamily(Font(R.font.mallige))
val Amber = Color(0xffE0730C)
private val Light = lightColorScheme(
    primary = Color(0xff9B4800), onPrimary = Color.White,
    primaryContainer = Color(0xffFFE1C5), onPrimaryContainer = Color(0xff3A1B00),
    secondary = Color(0xff5D625C), onSecondary = Color.White,
    secondaryContainer = Color(0xffE8E9E2), onSecondaryContainer = Color(0xff222620),
    background = Color(0xffF5F4EF), onBackground = Color(0xff262824),
    surface = Color(0xffFBFAF6), onSurface = Color(0xff262824),
    surfaceVariant = Color(0xffEAEAE3), onSurfaceVariant = Color(0xff5B5E56),
    surfaceContainer = Color(0xffF0F0E9), surfaceContainerHigh = Color(0xffE9E9E1),
    outline = Color(0xff787C71), outlineVariant = Color(0xffD6D8CE),
)
private val Dark = darkColorScheme(
    primary = Color(0xffFFB775), onPrimary = Color(0xff4F2500),
    primaryContainer = Color(0xff693400), onPrimaryContainer = Color(0xffFFDFC1),
    secondary = Color(0xffC4C8BC), secondaryContainer = Color(0xff363B32),
    background = Color(0xff191B18), onBackground = Color(0xffE3E5DC),
    surface = Color(0xff20221E), onSurface = Color(0xffE3E5DC),
    surfaceVariant = Color(0xff34372F), onSurfaceVariant = Color(0xffC3C7BA),
    surfaceContainer = Color(0xff272A24), surfaceContainerHigh = Color(0xff30332C),
    outline = Color(0xff8D9284), outlineVariant = Color(0xff464B40),
)
@Composable fun isNight(mode: ThemeMode) = mode == ThemeMode.Dark || (mode == ThemeMode.System && isSystemInDarkTheme())
@Composable fun KalpaneTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    // Deliberately fixed: wallpaper-derived dynamicColor is never requested.
    val family=noteFont(dev.kalpane.data.TextFace.Sans)
    val base=Typography()
    val typography=Typography(
        displayLarge=base.displayLarge.copy(fontFamily=family),displayMedium=base.displayMedium.copy(fontFamily=family),displaySmall=base.displaySmall.copy(fontFamily=family),
        headlineLarge=base.headlineLarge.copy(fontFamily=family),headlineMedium=base.headlineMedium.copy(fontFamily=family),headlineSmall=base.headlineSmall.copy(fontFamily=family),
        titleLarge=base.titleLarge.copy(fontFamily=family),titleMedium=base.titleMedium.copy(fontFamily=family),titleSmall=base.titleSmall.copy(fontFamily=family),
        bodyLarge=base.bodyLarge.copy(fontFamily=family),bodyMedium=base.bodyMedium.copy(fontFamily=family),bodySmall=base.bodySmall.copy(fontFamily=family),
        labelLarge=base.labelLarge.copy(fontFamily=family),labelMedium=base.labelMedium.copy(fontFamily=family),labelSmall=base.labelSmall.copy(fontFamily=family),
    )
    MaterialTheme(colorScheme = if(isNight(mode)) Dark else Light,typography=typography,content = content)
}
