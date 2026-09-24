package com.ashudialer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalButtonDepth

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
@Composable
fun Modifier.liquidGlass(
    palette: DialerPalette,
    shape: Shape = RoundedCornerShape(20.dp),
    tintAlpha: Float = 0.55f,
    borderAlpha: Float = 1f
): Modifier {
    // Pure Black skips the whole glass look - see DialerPalette.flatSurfaces'
    // doc comment for why. Still respects buttonDepth's raised/flat choice
    // (a shadow + a firmer border on "raised" is orthogonal to whether the
    // fill itself is glassy or flat), just without any of the
    // translucent-tint/highlight-brush layers underneath it.
    if (palette.flatSurfaces) {
        val isRaisedFlat = LocalButtonDepth.current == "raised"
        // Unlike the glass path below (which blends its border down from
        // Color.White/Black since it's meant to read as translucent glass
        // catching light), a flat surface's border is just the palette's
        // own cardBorder at full strength - that value is already tuned
        // per-palette to be clearly visible against that palette's own
        // cardBackground (Pure Black's #454545 on #000000, Professional's
        // #D4D7DC on #FFFFFF). Alpha-blending it down further reintroduces
        // exactly the "too faint to see" problem DarkModePalette's
        // cardBorder fix (see its doc comment) already fixed once - it's
        // only more visible here at borderAlpha's default of 1f, kept as a
        // multiplier so a caller that explicitly wants a softer border
        // (borderAlpha < 1) still gets one.
        val flatBorder = palette.cardBorder.copy(alpha = (if (isRaisedFlat) 1f else 0.85f) * borderAlpha)
        return this
            .let { m ->
                if (isRaisedFlat) {
                    m.shadow(
                        elevation = 4.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.6f),
                        spotColor = Color.Black.copy(alpha = 0.6f)
                    )
                } else {
                    m
                }
            }
            .clip(shape)
            .background(palette.cardBackground, shape)
            .border(1.dp, flatBorder, shape)
    }

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
    // "raised" button depth (see AppSettings.buttonDepth / LocalButtonDepth) -
    // previously button "depth" had no setting at all, every glass surface
    // was always exactly this flat look. When raised, the border gets a
    // touch more opaque (reads as a firmer, more defined edge - a real
    // Modifier.shadow() on top of an already-translucent glass background
    // just muddies the tint rather than reading as elevation, so the edge
    // is what actually carries a "more solid/pressable" feel here) and a
    // dark offset-shadow layer is drawn underneath before the glass layers
    // themselves, giving a subtle drop-shadow read without touching the
    // translucency of the glass fill itself.
    val isRaised = LocalButtonDepth.current == "raised"
    val baseStrokeAlpha = if (palette.isDark) 0.26f else 0.65f
    val strokeColor = if (cardIsNearWhite) {
        Color.Black.copy(alpha = (if (isRaised) 0.16f else 0.10f) * borderAlpha)
    } else {
        Color.White.copy(alpha = (baseStrokeAlpha + if (isRaised) 0.12f else 0f) * borderAlpha)
    }

    return this
        .let { m ->
            if (isRaised) {
                m.shadow(
                    elevation = 4.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = if (palette.isDark) 0.5f else 0.22f),
                    spotColor = Color.Black.copy(alpha = if (palette.isDark) 0.5f else 0.22f)
                )
            } else {
                m
            }
        }
        .clip(shape)
        .background(glassTint, shape)
        .background(highlightBrush, shape)
        .border(1.dp, strokeColor, shape)
}

/** Rounded-rect glass panel - the default for cards, rows, sheets, and settings items. */
@Composable
fun Modifier.glassCard(
    palette: DialerPalette,
    corner: androidx.compose.ui.unit.Dp = 18.dp,
    tintAlpha: Float = 0.55f
): Modifier = this.liquidGlass(palette, RoundedCornerShape(corner), tintAlpha)

/** Circular glass chip - for avatar rings, icon badges, and pill buttons. */
@Composable
fun Modifier.glassCircle(
    palette: DialerPalette,
    tintAlpha: Float = 0.55f
): Modifier = this.liquidGlass(palette, CircleShape, tintAlpha)
