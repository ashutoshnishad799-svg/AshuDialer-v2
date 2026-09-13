package com.ashudialer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

val LocalDialerPalette = compositionLocalOf { GradientPalette }


const val AUTO_THEME_ID = "auto"

private val DialerTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.5.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.5.sp)
)

/**
 * fontSizeIndex -> a fontScale multiplier, the same idea as Android's own
 * Settings > Display > Font size slider (which also works by scaling a
 * density's fontScale, not by changing individual text sizes). 1.0 (index 1,
 * "Medium") is unscaled - today's default look is preserved exactly for
 * anyone who never touches this setting.
 */
private fun fontScaleFor(index: Int): Float = when (index) {
    0 -> 0.88f   // Small
    2 -> 1.15f   // Large
    3 -> 1.3f    // Extra Large
    else -> 1f   // Medium (default)
}

@Composable
fun AshuDialerTheme(
    themeId: String,
    fontSizeIndex: Int = 1,
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()
    val resolvedId = if (themeId == AUTO_THEME_ID) {
        if (systemIsDark) "darkmode" else "gradient"
    } else {
        themeId
    }
    val palette = paletteById(resolvedId)

    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent,
            background = palette.solidBackground,
            surface = palette.cardBackground,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            background = palette.solidBackground,
            surface = palette.cardBackground,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary
        )
    }

    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, fontSizeIndex) {
        Density(density = baseDensity.density, fontScale = fontScaleFor(fontSizeIndex))
    }

    CompositionLocalProvider(
        LocalDialerPalette provides palette,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = DialerTypography,
            content = content
        )
    }
}
