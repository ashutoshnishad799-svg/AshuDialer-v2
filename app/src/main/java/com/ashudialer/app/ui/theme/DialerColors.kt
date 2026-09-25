package com.ashudialer.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color


data class DialerPalette(
    val id: String,
    val displayName: String,
    val isDark: Boolean,
    val background: Brush,
    val solidBackground: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val navBackground: Color,
    val accent: Color,
    val accentSoft: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val danger: Color,
    val searchBackground: Color,
    val avatarBackground: Color,
    val callGreen: Color,
    val swatchStart: Color,
    val swatchEnd: Color,
    // True only for Pure Black: every card/chip/button across the app
    // renders as a flat solid fill (just cardBackground + a plain border,
    // no glass tint layer, no diagonal highlight brush) instead of going
    // through liquidGlass's translucent-glass look. The whole point of a
    // true-black OLED theme is maximum contrast and the most pixels
    // possible fully off - a semi-transparent glass tint sitting on top of
    // #000000 cardBackground still reads as a lighter grey wash rather
    // than pure black, which defeats that, and the soft white highlight
    // brush especially looked out of place against a flat black theme
    // that's meant to be stark rather than soft/glassy like every other
    // theme in this app intentionally is.
    val flatSurfaces: Boolean = false
)


val GradientPalette = DialerPalette(
    id = "gradient",
    displayName = "Gradient",
    isDark = false,
    // Light-theme readability rule (applies to every non-dark palette): every gradient stop must keep
    //   textSecondary >= 4.5:1 and danger >= 3.5:1 contrast. The bottom stops used to be saturated
    //   mid-tones (contrast 1.4 - 2.2) which made bottom-of-screen text unreadable. Stops are now
    //   capped at a lightness that keeps the colour but leaves room for dark text.
    // DESIGN FIX for "Gradient theme doesn't feel like a gradient": the
    // previous 4 stops (0xFFE8FBF5 -> 0xFFB9EDE0 -> 0xFF7FD8C9 ->
    // 0xFF52C4B0) were all the same teal hue at different lightness
    // levels only - visually that reads as a single flat-ish color with
    // very soft shading rather than a genuine gradient, especially across
    // a short vertical span where the difference between adjacent stops
    // barely registers. This widens the lightness range at both ends
    // (a near-white top, a noticeably deeper teal at the bottom - was
    // medium-light to medium) and adds a small hue drift toward blue at
    // the bottom stop, so the transition from top to bottom is now
    // actually visible as a gradient rather than a gentle tint - while
    // staying in the same teal/mint family so this doesn't become a
    // different-feeling theme, just a more legibly gradient one.
    background = Brush.verticalGradient(
        colors = listOf(Color(0xFFF3FDFA), Color(0xFFC3F2E3), Color(0xFFB3E5DB), Color(0xFFB0E8E3))
    ),
    solidBackground = Color(0xFFC3F2E3),
    cardBackground = Color(0xFFFFFFFF).copy(alpha = 0.92f),
    cardBorder = Color(0xFFFFFFFF).copy(alpha = 0.6f),
    navBackground = Color(0xFFFFFFFF).copy(alpha = 0.88f),
    accent = Color(0xFF0F8A7C),
    accentSoft = Color(0xFFDCF5EE),
    textPrimary = Color(0xFF0B2E28),
    textSecondary = Color(0xFF4A6460),
    danger = Color(0xFFD3351F),
    searchBackground = Color(0xFFFFFFFF).copy(alpha = 0.95f),
    avatarBackground = Color(0xFFCFEFE6),
    callGreen = Color(0xFF34C759),
    swatchStart = Color(0xFFC3F2E3),
    swatchEnd = Color(0xFF2E9E93)
)


val MidnightPalette = DialerPalette(
    id = "midnight",
    displayName = "Midnight",
    isDark = true,
    background = Brush.verticalGradient(colors = listOf(Color(0xFF0B0B0F), Color(0xFF0B0B0F))),
    solidBackground = Color(0xFF0B0B0F),
    cardBackground = Color(0xFF1C1C22),
    cardBorder = Color(0xFF2A2A32),
    navBackground = Color(0xFF17171C),
    accent = Color(0xFF32D74B),
    accentSoft = Color(0xFF173321),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFF8E8E96),
    danger = Color(0xFFFF453A),
    searchBackground = Color(0xFF1C1C22),
    avatarBackground = Color(0xFF2A2A32),
    callGreen = Color(0xFF34C759),
    swatchStart = Color(0xFF1C1C22),
    swatchEnd = Color(0xFF32D74B)
)


val OceanPalette = DialerPalette(
    id = "ocean",
    displayName = "Ocean Blue",
    isDark = false,
    // The top and the bottom of the screen are clearly different colours (sky blue at the top,
    // soft lilac at the bottom, ~46 degrees of hue apart) but the WHOLE gradient stays light.
    //
    // WHY IT MUST STAY LIGHT: every screen draws its text in dark navy / slate (textPrimary /
    // textSecondary / danger) on top of this gradient. An earlier version darkened the bottom to
    // deep indigo (#5A57D6): secondary text there measured a contrast ratio of 1.08:1 (1.0 is
    // literally invisible) and "Danger zone", the Keypad/Mute/Audio labels and the bottom-of-screen
    // buttons disappeared. The original blue gradient had the same flaw (1.34:1 at its bottom stop),
    // it was just less obvious. Rule for this palette: keep every gradient stop light enough that
    //   textPrimary   >= 11:1,  textSecondary >= 4.5:1,  danger >= 3.8:1   (WCAG AA-level).
    // Measured for the stops below: primary >= 11.5, secondary >= 4.78, danger >= 3.83.
    background = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF2F8FF),   // sky white
            Color(0xFFD3ECFF),   // ice blue
            Color(0xFFD0DCFF),   // periwinkle
            Color(0xFFDED2FA)    // soft lilac
        )
    ),
    solidBackground = Color(0xFFD3ECFF),
    cardBackground = Color(0xFFFFFFFF).copy(alpha = 0.94f),
    cardBorder = Color(0xFFFFFFFF).copy(alpha = 0.65f),
    navBackground = Color(0xFFFFFFFF).copy(alpha = 0.9f),
    accent = Color(0xFF1E6FE8),
    accentSoft = Color(0xFFDCEBFF),
    textPrimary = Color(0xFF0B1E42),
    // Darkened from #5A6E93 (only 4.8:1 even on the lightest stop) so it stays >= 4.5:1 across
    // the whole gradient.
    textSecondary = Color(0xFF475B80),
    // Darkened from #E0442E (3.4:1 on the light stops) to stay readable on the lilac end.
    danger = Color(0xFFBC331F),
    searchBackground = Color(0xFFFFFFFF).copy(alpha = 0.96f),
    avatarBackground = Color(0xFFD3E7FF),
    callGreen = Color(0xFF34C759),
    swatchStart = Color(0xFF8FD3FF),
    swatchEnd = Color(0xFFB9A8F5)
)


val SunsetPalette = DialerPalette(
    id = "sunset",
    displayName = "Sunset",
    isDark = false,
    // Light-theme readability rule (applies to every non-dark palette): every gradient stop must keep
    //   textSecondary >= 4.5:1 and danger >= 3.5:1 contrast. The bottom stops used to be saturated
    //   mid-tones (contrast 1.4 - 2.2) which made bottom-of-screen text unreadable. Stops are now
    //   capped at a lightness that keeps the colour but leaves room for dark text.
    background = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFF3E4), Color(0xFFFFD9B8), Color(0xFFFFC2AD), Color(0xFFF8BAB4))
    ),
    solidBackground = Color(0xFFFFD9B8),
    cardBackground = Color(0xFFFFFFFF).copy(alpha = 0.93f),
    cardBorder = Color(0xFFFFFFFF).copy(alpha = 0.6f),
    navBackground = Color(0xFFFFFFFF).copy(alpha = 0.88f),
    accent = Color(0xFFE0523C),
    accentSoft = Color(0xFFFFE4DA),
    textPrimary = Color(0xFF3A1A12),
    textSecondary = Color(0xFF6D4D43),
    danger = Color(0xFFC12A1C),
    searchBackground = Color(0xFFFFFFFF).copy(alpha = 0.95f),
    avatarBackground = Color(0xFFFFE0CF),
    callGreen = Color(0xFF34C759),
    swatchStart = Color(0xFFFFC08C),
    swatchEnd = Color(0xFFE0523C)
)


val VioletPalette = DialerPalette(
    id = "violet",
    displayName = "Violet",
    isDark = true,
    background = Brush.verticalGradient(colors = listOf(Color(0xFF120D1F), Color(0xFF120D1F))),
    solidBackground = Color(0xFF120D1F),
    cardBackground = Color(0xFF211A34),
    cardBorder = Color(0xFF352A54),
    navBackground = Color(0xFF1A1428),
    accent = Color(0xFFB18CFF),
    accentSoft = Color(0xFF2E2350),
    textPrimary = Color(0xFFF3EEFF),
    textSecondary = Color(0xFF9C90BF),
    danger = Color(0xFFFF6178),
    searchBackground = Color(0xFF211A34),
    avatarBackground = Color(0xFF352A54),
    callGreen = Color(0xFF34C759),
    swatchStart = Color(0xFF211A34),
    swatchEnd = Color(0xFFB18CFF)
)


val RoseGoldPalette = DialerPalette(
    id = "rosegold",
    displayName = "Rose Gold",
    isDark = false,
    // Light-theme readability rule (applies to every non-dark palette): every gradient stop must keep
    //   textSecondary >= 4.5:1 and danger >= 3.5:1 contrast. The bottom stops used to be saturated
    //   mid-tones (contrast 1.4 - 2.2) which made bottom-of-screen text unreadable. Stops are now
    //   capped at a lightness that keeps the colour but leaves room for dark text.
    background = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFF6F3), Color(0xFFFFE1DA), Color(0xFFFFC2C2), Color(0xFFF2C5C5))
    ),
    solidBackground = Color(0xFFFFE1DA),
    cardBackground = Color(0xFFFFFFFF).copy(alpha = 0.93f),
    cardBorder = Color(0xFFFFFFFF).copy(alpha = 0.6f),
    navBackground = Color(0xFFFFFFFF).copy(alpha = 0.88f),
    accent = Color(0xFFC96B6B),
    accentSoft = Color(0xFFFFE9E5),
    textPrimary = Color(0xFF3D1F1F),
    textSecondary = Color(0xFF6C5252),
    danger = Color(0xFFC33434),
    searchBackground = Color(0xFFFFFFFF).copy(alpha = 0.95f),
    avatarBackground = Color(0xFFFFDCD5),
    callGreen = Color(0xFF34C759),
    swatchStart = Color(0xFFFFC9C0),
    swatchEnd = Color(0xFFC96B6B)
)


val DarkModePalette = DialerPalette(
    id = "darkmode",
    displayName = "Dark Mode",
    isDark = true,
    background = Brush.verticalGradient(colors = listOf(Color(0xFF000000), Color(0xFF000000))),
    solidBackground = Color(0xFF000000),
    cardBackground = Color(0xFF121212),
    // Was #232323 on a #121212 card - only 1.19:1 contrast, i.e.
    // functionally invisible. This is what made Call Insights'
    // day-over-day bar chart (DayOverDayChart in CallInsightsScreen.kt)
    // look "black on black" on this theme: a day with zero calls draws
    // its bar in cardBorder, and the chart card's own edge is stroked
    // with it too, so both effectively vanished into the card. Moved to
    // a lighter, clearly-visible grey (#3A3A3A on #121212 is ~2.7:1
    // against the card, and the chart's non-zero bars/accent elements
    // were never affected - they already used palette.accent at a solid
    // 7.4:1). Not WCAG "AA text" contrast (this is a decorative
    // border/empty-state fill, not body text), but enough to actually
    // see it, which is the bar the bug report failed to clear.
    cardBorder = Color(0xFF3A3A3A),
    navBackground = Color(0xFF0A0A0A),
    accent = Color(0xFF4FA8FF),
    accentSoft = Color(0xFF12233A),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF9A9A9A),
    danger = Color(0xFFFF5449),
    searchBackground = Color(0xFF121212),
    avatarBackground = Color(0xFF232323),
    callGreen = Color(0xFF34C759),
    swatchStart = Color(0xFF000000),
    swatchEnd = Color(0xFF4FA8FF)
)


/**
 * True OLED black: every surface that Dark Mode still lifts off pure
 * black (cardBackground #121212, navBackground #0A0A0A) is pinned to
 * #000000 here instead, so a screen showing mostly cards/nav (the common
 * case) is #000000 for its full extent rather than only the gaps between
 * cards - the actual power saving on an OLED panel comes from pixels
 * being fully off, which a #121212 card never allows. Card/chip edges
 * still need a visible border to read as separate surfaces when
 * everything behind them is identically black (glassCard's own
 * tint/highlight layers help, but the explicit border is what carries
 * legibility once glass tintAlpha is low) - cardBorder is bumped up from
 * Dark Mode's already-fixed #3A3A3A to #454545 for a touch more
 * separation given every surface here starts from literal black rather
 * than a slightly-lifted base.
 */
val PureBlackPalette = DialerPalette(
    id = "pureblack",
    displayName = "Pure Black",
    isDark = true,
    background = Brush.verticalGradient(colors = listOf(Color(0xFF000000), Color(0xFF000000))),
    solidBackground = Color(0xFF000000),
    cardBackground = Color(0xFF000000),
    cardBorder = Color(0xFF454545),
    navBackground = Color(0xFF000000),
    accent = Color(0xFF4FA8FF),
    accentSoft = Color(0xFF0D1A2B),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA0A0A0),
    danger = Color(0xFFFF5449),
    searchBackground = Color(0xFF000000),
    avatarBackground = Color(0xFF1A1A1A),
    callGreen = Color(0xFF34C759),
    swatchStart = Color(0xFF000000),
    swatchEnd = Color(0xFF2A2A2A),
    flatSurfaces = true
)


/**
 * A restrained, low-saturation look for anyone who wants the app to read
 * as a plain business tool rather than a themed consumer app - a cool
 * slate/graphite palette with a single muted steel-blue accent, no
 * gradient background (flat, like White/Dark Mode), and a slightly
 * heavier text-secondary weight than the other light themes for a
 * "documents and spreadsheets" feel rather than a soft/playful one.
 */
val ProfessionalPalette = DialerPalette(
    id = "professional",
    displayName = "Professional",
    isDark = false,
    background = Brush.verticalGradient(colors = listOf(Color(0xFFF4F5F7), Color(0xFFF4F5F7))),
    solidBackground = Color(0xFFF4F5F7),
    cardBackground = Color(0xFFFFFFFF),
    // #D4D7DC (2.1:1 on 0xFFFFFF glass card, but only ~1.4:1 once
    // flatSurfaces removes the border-alpha blending glass relied on to
    // look intentionally soft) wasn't quite firm enough once this palette
    // went flat - a flat "professional" look wants a crisp, clearly-
    // defined edge, not a barely-there one. #AEB3BC (~2.1:1 at full
    // opacity, the actual opacity flatSurfaces borders render at) reads
    // as a solid, deliberate line without being harsh against white.
    cardBorder = Color(0xFFAEB3BC),
    navBackground = Color(0xFFFFFFFF),
    accent = Color(0xFF2F5D8A),
    accentSoft = Color(0xFFE1E9F1),
    textPrimary = Color(0xFF1C222B),
    textSecondary = Color(0xFF5B6472),
    danger = Color(0xFFB3261E),
    searchBackground = Color(0xFFECEEF1),
    avatarBackground = Color(0xFFE1E9F1),
    callGreen = Color(0xFF2E7D46),
    swatchStart = Color(0xFFF4F5F7),
    swatchEnd = Color(0xFF2F5D8A),
    // Flat like Pure Black (see DialerPalette.flatSurfaces), for a
    // different reason: White theme was already updated to a soft,
    // slightly-warm near-white with the same translucent glass sheen
    // every other theme in this app uses, so Professional ended up
    // reading as "White with a different accent color" rather than its
    // own distinct look - the glass tint/highlight is exactly the "soft,
    // candy-like" character a plain business-tool theme is supposed to
    // be the alternative to. Flat cards + a crisp solid border (no
    // gradient sheen) is what actually makes "restrained, low-
    // saturation, documents-and-spreadsheets" (this palette's original
    // design intent) show up as a different *feel*, not just a different
    // accent hue next to White.
    flatSurfaces = true
)


val WhitePalette = DialerPalette(
    id = "white",
    displayName = "White",
    isDark = false,
    // THE FIX for "can't tell what's what" in White theme: background was
    // pure #FFFFFF and cardBackground was #F5F5F7 - only a 2% luminance
    // difference between "the screen" and "a card sitting on the screen".
    // liquidGlass() then layers a *white* highlight brush and a *white*
    // border on top of that (see LiquidGlass.kt - both scale off
    // Color.White, brighter for light/non-dark palettes), so on this
    // palette specifically every one of glass's three layers (tint,
    // highlight, border) was some shade of near-white stacked on a
    // near-white background - nothing to actually see. Two changes fix
    // this without turning "White" into a grey theme: the background
    // keeps a whisper of cool tint (#FAFAFC, still reads as "white" at a
    // glance) so cards/glass panels have something to separate from, and
    // cardBorder moves from a barely-there #E2E2E6 to a clearly visible
    // #C6C6CC so card and chip edges are legible even where the glass
    // tint alone doesn't provide enough contrast.
    background = Brush.verticalGradient(colors = listOf(Color(0xFFFAFAFC), Color(0xFFFAFAFC))),
    solidBackground = Color(0xFFFAFAFC),
    cardBackground = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFC6C6CC),
    navBackground = Color(0xFFFFFFFF),
    accent = Color(0xFF1C1C1E),
    accentSoft = Color(0xFFE8E8EC),
    textPrimary = Color(0xFF1C1C1E),
    textSecondary = Color(0xFF6E6E73),
    danger = Color(0xFFE0442E),
    searchBackground = Color(0xFFEFEFF2),
    avatarBackground = Color(0xFFE0E0E5),
    callGreen = Color(0xFF34C759),
    swatchStart = Color(0xFFFFFFFF),
    swatchEnd = Color(0xFFC7C7CC)
)

/**
 * The one theme in this list whose "accha sa UI" comes from its background
 * brush rather than a flat solidBackground - background is a true
 * multi-stop rainbow gradient (not one hue tinted a few ways, like
 * Sunset/Ocean/Violet each are), while every other palette property
 * (accent, text colors, card/border colors) is still tuned as a normal,
 * legible light theme on top of it. isDark = false is deliberate even
 * though the background is colorful, not literally white: text/card
 * contrast rules (liquidGlass's cardIsNearWhite branch, WhitePalette's
 * border-darkening fix, etc.) all key off isDark to decide whether glass
 * panels/text should assume a light or dark surface underneath them, and
 * a light glass tint over saturated gradient colors is what actually
 * reads as "glass sitting on a rainbow" rather than muddying the
 * gradient with a dark tint that wasn't tuned for it.
 *
 * solidBackground (used for the plain-color needs - MaterialTheme's
 * scheme.background, the base beneath any surface that isn't rendering
 * this theme's own Brush) is fixed to a soft neutral rather than trying
 * to pick "the one rainbow color" - most solid-fill call sites in this
 * app are small chips/icons/status surfaces where a saturated color would
 * clash depending on which part of the actual gradient it's meant to
 * represent, so a calm base color is the safer default there.
 */
val RainbowPalette = DialerPalette(
    id = "rainbow",
    displayName = "Rainbow",
    isDark = false,
    // Light-theme readability rule (applies to every non-dark palette): every gradient stop must keep
    //   textSecondary >= 4.5:1 and danger >= 3.5:1 contrast. The bottom stops used to be saturated
    //   mid-tones (contrast 1.4 - 2.2) which made bottom-of-screen text unreadable. Stops are now
    //   capped at a lightness that keeps the colour but leaves room for dark text.
    background = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFADAD), // red
            Color(0xFFFFDDAD), // orange
            Color(0xFFFFEEAD), // yellow
            Color(0xFFBBF2C3), // green
            Color(0xFFB8EDF4), // cyan/blue
            Color(0xFFB9ADFF), // indigo
            Color(0xFFECB3F9)  // violet
        )
    ),
    solidBackground = Color(0xFFFFDDAD),
    cardBackground = Color(0xFFFFFFFF).copy(alpha = 0.82f),
    cardBorder = Color(0xFFFFFFFF).copy(alpha = 0.75f),
    navBackground = Color(0xFFFFFFFF).copy(alpha = 0.86f),
    accent = Color(0xFF7B2FD6),
    accentSoft = Color(0xFFF3E8FF),
    textPrimary = Color(0xFF2B1049),
    textSecondary = Color(0xFF504364),
    danger = Color(0xFFA62A19),
    searchBackground = Color(0xFFFFFFFF).copy(alpha = 0.92f),
    avatarBackground = Color(0xFFF3E8FF),
    callGreen = Color(0xFF34C759),
    // A single representative sweep for the small circular theme-picker
    // swatch (ThemePickerSheet draws swatchStart->swatchEnd as a 2-stop
    // linear gradient, not the full 7-stop brush above) - red through
    // violet reads as "this is the rainbow one" at a glance even
    // compressed into a small circle with only two gradient stops.
    swatchStart = Color(0xFFFF6B6B),
    swatchEnd = Color(0xFF9D8CFF)
)

val AllPalettes = listOf(
    GradientPalette, OceanPalette, SunsetPalette, RoseGoldPalette, WhitePalette, MidnightPalette, VioletPalette, DarkModePalette, RainbowPalette, PureBlackPalette, ProfessionalPalette
)

fun paletteById(id: String): DialerPalette = AllPalettes.find { it.id == id } ?: GradientPalette
