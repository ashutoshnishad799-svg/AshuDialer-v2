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

/**
 * "flat" (default, today's existing look) or "raised" - see
 * AppSettings.buttonDepth's doc comment. Read by liquidGlass/glassCard/
 * glassCircle in LiquidGlass.kt so every button, key, and chip across the
 * whole app picks this up automatically with no per-call-site changes,
 * the same reasoning LocalDialerPalette itself already relies on for
 * theme colors.
 */
val LocalButtonDepth = compositionLocalOf { "flat" }


const val AUTO_THEME_ID = "auto"

/**
 * The one place "auto" is turned into a real theme id. Used here, by InCallActivity's
 * pre-Compose window-background placeholder, and by CallNotificationHelper's notification
 * styling, so all three always agree on what "System" actually looks like - previously only
 * this Composable and CallNotificationHelper each had their own hand-copied version of this
 * mapping (identical, but two places to get out of sync) and InCallActivity had none at all,
 * which meant an "auto" user in dark mode got a plain Gradient-colored flash before every call.
 *
 * System light -> "professional" (the Slate look - see ProfessionalPalette's displayName;
 * the id string itself is unchanged so nobody's already-saved explicit "professional" choice
 * or DataStore value needs a migration). System dark -> "darkmode", which - unlike Pure
 * Black - keeps flatSurfaces = false and so already renders every card through the app's
 * normal frosted/translucent "glass" look (see DialerPalette.flatSurfaces's doc comment).
 */
fun resolveThemeId(themeId: String, systemIsDark: Boolean): String =
    if (themeId == AUTO_THEME_ID) {
        if (systemIsDark) "darkmode" else "professional"
    } else {
        themeId
    }

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
    buttonDepth: String = "flat",
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()
    val resolvedId = resolveThemeId(themeId, systemIsDark)
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
        LocalDensity provides scaledDensity,
        LocalButtonDepth provides buttonDepth
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = DialerTypography,
            content = content
        )
    }
}
