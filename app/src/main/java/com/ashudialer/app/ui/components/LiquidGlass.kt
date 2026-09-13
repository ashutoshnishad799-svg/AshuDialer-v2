package com.ashudialer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.ashudialer.app.ui.theme.DialerPalette

/**
 * The one glass-morphism modifier for the whole app. Every screen, card,
 * sheet, and chip should use this (or the two convenience wrappers below)
 * instead of a plain `.background(palette.cardBackground, shape)` - that's
 * how "glass everywhere" actually gets applied consistently instead of
 * living in one isolated file that only BottomNav used to reach for.
 *
 * Three stacked layers make the glass read as glass rather than just a
 * translucent card:
 *  1. A semi-transparent tint of the palette's own card color, so the
 *     effect still matches whichever theme (Ocean, Midnight, Violet, etc.)
 *     is active rather than looking like a generic frosted-white pane.
 *  2. A soft diagonal highlight brush, brighter in the top-left, mimicking
 *     light catching the top edge of real glass.
 *  3. A thin light-colored border, since real glass panels are defined by
 *     their edge as much as their fill.
 *
 * tintAlpha controls how frosted (opaque) vs. how clear (see-through) the
 * panel is. 0.55 (down from an earlier 0.78) is deliberately closer to
 * actual glass - 0.78 read as a solid, barely-translucent card once seen
 * on a real device rather than glass, and the highlight/border alphas on
 * dark palettes (this app's most common case - Ocean and most other
 * built-in themes are dark) were tuned too low as well, which is why the
 * effect looked faint even where it was technically applied everywhere.
 */
fun Modifier.liquidGlass(
    palette: DialerPalette,
    shape: Shape = RoundedCornerShape(20.dp),
    tintAlpha: Float = 0.55f,
    borderAlpha: Float = 1f
): Modifier {
    val glassTint = palette.cardBackground.copy(alpha = tintAlpha)
    val highlightBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (palette.isDark) 0.22f else 0.45f),
            Color.White.copy(alpha = if (palette.isDark) 0.07f else 0.08f),
            Color.White.copy(alpha = 0f)
        )
    )
    // On light palettes whose card color is itself very close to white
    // (White theme's #FFFFFF cardBackground being the extreme case), a
    // white highlight brush on top of an already-white tint adds nothing
    // visible - the border ends up doing all the work of separating the
    // glass panel from its background. Darkening the border in that
    // specific case (near-white card color, non-dark palette) keeps every
    // other palette's border exactly as before while giving White theme's
    // cards/chips a visible edge instead of an near-invisible white-on-white
    // seam.
    val cardIsNearWhite = !palette.isDark &&
        palette.cardBackground.red > 0.92f &&
        palette.cardBackground.green > 0.92f &&
        palette.cardBackground.blue > 0.92f
    val baseStrokeAlpha = if (palette.isDark) 0.26f else 0.65f
    val strokeColor = if (cardIsNearWhite) {
        Color.Black.copy(alpha = 0.10f * borderAlpha)
    } else {
        Color.White.copy(alpha = baseStrokeAlpha * borderAlpha)
    }

    return this
        .clip(shape)
        .background(glassTint, shape)
        .background(highlightBrush, shape)
        .border(1.dp, strokeColor, shape)
}

/** Rounded-rect glass panel - the default for cards, rows, sheets, and settings items. */
fun Modifier.glassCard(
    palette: DialerPalette,
    corner: androidx.compose.ui.unit.Dp = 18.dp,
    tintAlpha: Float = 0.55f
): Modifier = this.liquidGlass(palette, RoundedCornerShape(corner), tintAlpha)

/** Circular glass chip - for avatar rings, icon badges, and pill buttons. */
fun Modifier.glassCircle(
    palette: DialerPalette,
    tintAlpha: Float = 0.55f
): Modifier = this.liquidGlass(palette, CircleShape, tintAlpha)
