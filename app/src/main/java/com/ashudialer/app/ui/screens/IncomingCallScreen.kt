package com.ashudialer.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.telecom.SpamAssessment
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.components.Avatar
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.components.glassCircle
import com.ashudialer.app.ui.components.liquidGlass

/**
 * Nudges a color slightly lighter for the top of AuroraBackdrop's vertical
 * gradient - was a fixed pair of hardcoded navy shades before, now derives
 * both stops from the theme-aware backdropBase so the subtle top-to-bottom
 * gradient still exists on every theme instead of only the original navy.
 */
private fun lightenTowardBlack(color: Color, amount: Float): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(color.toArgb(), hsl)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1], (hsl[2] + amount).coerceIn(0f, 1f))))
}

/**
 * Extracts an approximate hue (0-359) from any Color by converting to HSL -
 * used to bias the aurora's colors toward the active theme's own accent
 * color rather than only the caller-name-derived hue (see
 * IncomingCallScreen's hue blend above).
 */
private fun accentHueDegrees(color: Color): Float {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(color.toArgb(), hsl)
    return hsl[0]
}

/**
 * The dark backdrop this whole aurora-glass look needs. Dark palettes
 * (Black/Dark Mode, Midnight, Violet) already have a near-black or near-navy
 * solidBackground, so that's used directly - Black theme's incoming call is
 * then genuinely black, not a fixed navy that ignored the theme. Light
 * palettes (White, Gradient, Ocean Blue, Sunset, Rose Gold) have a
 * solidBackground far too pale to hold a glow at all, so instead of losing
 * the aurora look on those themes, a deep, mostly-desaturated tint of the
 * palette's own accent color stands in - dark enough for the same white
 * glass/white text used everywhere else on this screen to stay readable,
 * while still visibly belonging to that theme's color rather than reverting
 * to one identical dark color on every light theme.
 */
private fun incomingCallBackdropColor(palette: DialerPalette): Color {
    // Dark palettes: their own solidBackground already IS a proper dark
    // backdrop (Black theme's is literally #000000), so use it directly -
    // this is what makes Black theme's incoming call screen actually
    // black rather than a generic navy.
    if (palette.isDark) return palette.solidBackground

    // Light palettes (Gradient, Ocean, Sunset, Violet, RoseGold, White,
    // Rainbow): solidBackground on every one of these is intentionally
    // pale/airy for the REST of the app, which is exactly wrong for this
    // screen - the aurora glow and white-on-dark glass text/avatar below
    // both need real contrast against something dark, not a near-white
    // fill. This was previously just `return palette.solidBackground`
    // directly, silently ignoring this exact reasoning already written
    // above in this file's class doc - the result was every light
    // theme's incoming call rendering washed-out/low-contrast instead of
    // the "deep tint derived from the palette's own accent" the comment
    // actually promised. HSV manipulation on the palette's own accent
    // color (not a fixed navy) is what keeps this genuinely per-theme:
    // Ocean Blue's accent yields a deep blue backdrop, Violet's yields
    // deep violet, Rainbow's accent (a saturated purple) yields a deep
    // purple backdrop rather than its own pale lavender solidBackground.
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(palette.accent.toArgb(), hsv)
    hsv[1] = hsv[1].coerceAtLeast(0.55f) // ensure real saturation even for a muted accent
    hsv[2] = 0.16f // fixed low value = a consistently deep, near-black-but-hued backdrop
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Entrance choreography shared by the caller identity block and the action
 * row: each element settles in slightly staggered rather than the whole
 * screen appearing at once. `delayMs` offsets when a given element starts
 * its own rise-and-fade.
 */
@Composable
private fun rememberEntranceProgress(delayMs: Int): State<Float> {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMs.toLong())
        started = true
    }
    return animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "entrance-progress"
    )
}

/**
 * Public entry point - dispatches to whichever incoming-call visual style is
 * selected in Settings > Appearance > Incoming call screen
 * (AppSettings.incomingCallStyle). Every style shares the exact same
 * caller-info props and the exact same onAccept/onDecline/onQuickMessage
 * callbacks, so InCallActivity's call site never needs to know which style
 * is active - only this dispatcher does.
 *
 *  - "aurora" (default): the original design below - a soft multi-hue glow
 *    behind frosted glass, draggable accept/decline circles.
 *  - "orbit": a calmer, more premium mood - a single slowly-rotating glass
 *    ring system around a large centered avatar disc, a soft one-hue
 *    gradient mesh backdrop (rather than aurora's 5 separate colored
 *    blobs), and accept/decline as two glass arcs you drag apart.
 *  - "pulse": the most kinetic style - concentric glass ripples radiating
 *    outward from the avatar like sonar, a slowly drifting two-tone mesh
 *    gradient backdrop, and accept/decline as glass pill buttons with their
 *    own ripple-on-press feedback.
 *
 * An unrecognized/stale style string (e.g. a future update removes a style)
 * falls back to "aurora" rather than crashing, since this value round-trips
 * through DataStore as a plain string.
 */
@Composable
fun IncomingCallScreen(
    callerName: String,
    callerNumber: String,
    spamAssessment: SpamAssessment? = null,
    callerPhotoUri: String? = null,
    isSavedContact: Boolean = false,
    style: String = "aurora",
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onQuickMessage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    when (style) {
        "orbit" -> OrbitIncomingCallScreen(
            callerName = callerName,
            callerNumber = callerNumber,
            spamAssessment = spamAssessment,
            callerPhotoUri = callerPhotoUri,
            isSavedContact = isSavedContact,
            onAccept = onAccept,
            onDecline = onDecline,
            onQuickMessage = onQuickMessage,
            modifier = modifier
        )
        "pulse" -> PulseIncomingCallScreen(
            callerName = callerName,
            callerNumber = callerNumber,
            spamAssessment = spamAssessment,
            callerPhotoUri = callerPhotoUri,
            isSavedContact = isSavedContact,
            onAccept = onAccept,
            onDecline = onDecline,
            onQuickMessage = onQuickMessage,
            modifier = modifier
        )
        else -> AuroraIncomingCallScreen(
            callerName = callerName,
            callerNumber = callerNumber,
            spamAssessment = spamAssessment,
            callerPhotoUri = callerPhotoUri,
            isSavedContact = isSavedContact,
            onAccept = onAccept,
            onDecline = onDecline,
            onQuickMessage = onQuickMessage,
            modifier = modifier
        )
    }
}

/**
 * Complete redesign matching a reference iOS-style incoming call screen:
 * a soft, multi-hue aurora blur behind frosted glass, a plain glass-circle
 * avatar (no photo needed to look intentional), and two fixed accept/decline
 * circles with subtle animated chevron swipe-hints between them, rather than
 * the previous design's separate systems (a row of small fixed buttons *and*
 * a whole independent vertical drag-puck-and-track competing for the same
 * job). The circles are still draggable a short distance for the swipe
 * gesture, just without a dedicated track/puck visual - see
 * DraggableCallCircle below.
 *
 * THE FIX for "make this match Black/White/Ocean Blue like the rest of the
 * app": this screen used to hardcode one fixed dark-navy backdrop and white
 * glass regardless of which of the app's 8 themes was active, so switching
 * to White theme (for example) still showed the exact same near-black
 * incoming-call screen. It now reads LocalDialerPalette.current, same as
 * every other screen, and adapts three things per theme rather than
 * special-casing each theme by name:
 *  - the base backdrop color comes from palette.solidBackground for dark
 *    palettes (so Black theme's incoming call is actually black, not navy)
 *    or a deep tint derived from the palette's own accent for light
 *    palettes (a light palette's solidBackground is far too pale to hold an
 *    aurora glow, so light themes get a dark backdrop in their own hue
 *    instead of losing the aurora look entirely)
 *  - the aurora blob hues bias toward palette.accent's own hue instead of a
 *    caller-name-derived hue alone, so Ocean Blue's incoming call actually
 *    reads blue, Violet reads violet, etc.
 *  - the glass tint/text colors flip between white-on-dark (works for every
 *    palette, since the backdrop is always dark per the point above) - this
 *    is why avatar rings, action circles, and text below stay White-based
 *    rather than needing a separate light-mode variant: the backdrop itself
 *    guarantees dark-enough contrast for white glass regardless of which of
 *    the 8 themes picked it.
 */
@Composable
private fun AuroraIncomingCallScreen(
    callerName: String,
    callerNumber: String,
    spamAssessment: SpamAssessment? = null,
    callerPhotoUri: String? = null,
    isSavedContact: Boolean = false,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onQuickMessage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val callerHue = ((callerName.firstOrNull()?.code ?: 65) * 37) % 360
    val themeHue = accentHueDegrees(palette.accent)
    val hue = (((themeHue * 0.72f) + (callerHue * 0.28f)).toInt()).let { it.mod(360) }
    val backdropBase = incomingCallBackdropColor(palette)
    val foreground = if (palette.isDark) Color.White else palette.textPrimary
    val secondaryText = if (palette.isDark) Color.White.copy(alpha = 0.70f) else palette.textSecondary
    val panel = if (palette.isDark) Color.White.copy(alpha = 0.105f) else Color.White.copy(alpha = 0.54f)
    val border = Color.White.copy(alpha = if (palette.isDark) 0.16f else 0.62f)

    val identityEntrance by rememberEntranceProgress(0)
    val avatarEntrance by rememberEntranceProgress(90)
    val actionsEntrance by rememberEntranceProgress(170)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        AuroraBackdrop(hue = hue, backdropBase = backdropBase, isDark = palette.isDark)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayerAlphaRise(identityEntrance),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = if (palette.isDark) 0.10f else 0.48f))
                        .border(1.dp, border, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "INCOMING CALL",
                        fontSize = 11.sp,
                        letterSpacing = 1.3.sp,
                        fontWeight = FontWeight.Bold,
                        color = secondaryText
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            Column(
                modifier = Modifier.graphicsLayerAlphaRise(identityEntrance),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = callerName.ifBlank { "Unknown caller" },
                    fontSize = 35.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = foreground,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                if (callerNumber.isNotBlank() && callerNumber != callerName) {
                    Spacer(Modifier.height(6.dp))
                    Text(callerNumber, fontSize = 15.sp, color = secondaryText)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isSavedContact) "Saved contact" else "New caller",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = secondaryText.copy(alpha = 0.88f)
                )
                if (spamAssessment?.isLikelySpam == true) {
                    Spacer(Modifier.height(12.dp))
                    SpamBadge(modifier = Modifier.graphicsLayerAlphaRise(identityEntrance))
                }
            }

            Spacer(Modifier.weight(1f))

            Box(Modifier.graphicsLayerAlphaRise(avatarEntrance)) {
                GlassAvatarRings(photoUri = callerPhotoUri, callerName = callerName)
            }

            Spacer(Modifier.weight(0.80f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(34.dp))
                    .background(panel)
                    .border(1.dp, border, RoundedCornerShape(34.dp))
                    .padding(horizontal = 18.dp, vertical = 18.dp)
                    .graphicsLayerAlphaRise(actionsEntrance),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PremiumCallAction(
                    icon = Icons.Filled.CallEnd,
                    label = "Decline",
                    accent = palette.danger,
                    onClick = onDecline
                )
                PremiumCallAction(
                    icon = Icons.Filled.Phone,
                    label = "Answer",
                    accent = palette.callGreen,
                    onClick = onAccept
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Tap a button or swipe it up",
                fontSize = 12.sp,
                color = secondaryText,
                modifier = Modifier.graphicsLayerAlphaRise(actionsEntrance)
            )
            Spacer(Modifier.height(10.dp))
            QuickActionsPill(
                onQuickMessage = onQuickMessage,
                modifier = Modifier.graphicsLayerAlphaRise(actionsEntrance),
                lightMode = !palette.isDark
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PremiumCallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    var dragPx by remember { mutableStateOf(0f) }
    val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 54.dp.toPx() }
    val offset by animateFloatAsState(
        targetValue = dragPx,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "call-action-offset"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (dragPx >= threshold) onClick()
                        dragPx = 0f
                    },
                    onDragCancel = { dragPx = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragPx = (dragPx - amount.y).coerceIn(0f, threshold * 1.25f)
                    }
                )
            }
            .clickable(onClick = onClick)
            .graphicsLayer {
                translationY = -offset
                val p = (dragPx / threshold).coerceIn(0f, 1f)
                scaleX = 1f + p * 0.06f
                scaleY = 1f + p * 0.06f
            }
    ) {
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(accent)
                .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(31.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The soft multi-color glow behind frosted glass that the reference image's
 * whole mood comes from - a few large, softly-colored radial blobs (warm
 * peach, violet, cool blue) positioned off-center and blurred heavily,
 * layered over a dark base. hue shifts the palette per-caller (same idea as
 * the old design's single hue-based glow) while keeping the same
 * warm-to-cool spread the reference has, rather than a single flat tint.
 */
/**
 * The soft multi-color glow behind frosted glass that the reference image's
 * whole mood comes from - a few large, vividly-colored radial blobs (warm
 * peach/coral top-left, cool blue-violet bottom-right) positioned off-center
 * and blurred heavily, layered over a dark base. hue shifts the palette per
 * caller while keeping the same warm-to-cool diagonal spread the reference
 * has.
 *
 * Two things that made the previous version read as flat/muddy instead of
 * glowing, both fixed here:
 * 1) Lightness values around 0.4-0.55 with alpha 0.35-0.45 sit very close to
 *    the dark backdrop's own luminance, so even with blur applied the
 *    result barely lifts above the base color - it reads as a slightly
 *    different shade of dark rather than a glow. Pushing lightness up
 *    (0.62-0.72) and alpha up (0.55-0.75) gives the blur something bright
 *    enough to actually diffuse into a visible glow.
 * 2) All three blobs shared one blur pass at a uniform 90dp radius sized
 *    well below their own box size, which just softens each circle's edge
 *    rather than letting colors bleed into and mix with each other the way
 *    a real aurora does. Oversizing each blob well past its own box (blur
 *    radius large relative to a smaller box) and overlapping them more
 *    lets the colors actually blend at their boundaries.
 */

private fun paletteOverlayColor(base: Color, alpha: Float): Color = base.copy(alpha = alpha)

@Composable
private fun AuroraBackdrop(hue: Int, backdropBase: Color, isDark: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            lightenTowardBlack(backdropBase, 0.06f),
                            backdropBase
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = 130.dp)
        ) {
            // Warm coral/peach glow, upper-left - matches the reference's
            // warm corner.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-70).dp, y = 10.dp)
                    .size(340.dp)
                    .background(
                        Color.hsl(((hue + 25) % 360).toFloat(), 0.70f, 0.68f).copy(alpha = if (isDark) 0.65f else 0.26f),
                        CircleShape
                    )
            )
            // Rose/magenta transition blob, right of center-top - the
            // reference's warm-to-cool handoff.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 60.dp, y = 90.dp)
                    .size(320.dp)
                    .background(
                        Color.hsl(((hue + 320) % 360).toFloat(), 0.60f, 0.55f).copy(alpha = if (isDark) 0.6f else 0.20f),
                        CircleShape
                    )
            )
            // Deep violet, center-right - the dominant mid-tone the
            // reference's right half sits on.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 40.dp, y = (-20).dp)
                    .size(400.dp)
                    .background(
                        Color.hsl(((hue + 265) % 360).toFloat(), 0.65f, 0.48f).copy(alpha = if (isDark) 0.7f else 0.18f),
                        CircleShape
                    )
            )
            // Cool blue, lower-right - the reference's cool corner.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 30.dp, y = 40.dp)
                    .size(360.dp)
                    .background(
                        Color.hsl(((hue + 215) % 360).toFloat(), 0.70f, 0.55f).copy(alpha = if (isDark) 0.55f else 0.22f),
                        CircleShape
                    )
            )
            // Faint dark olive/green pocket, upper-left corner - the subtle
            // cool-green note visible in the reference's very top-left.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-40).dp, y = (-60).dp)
                    .size(280.dp)
                    .background(
                        Color.hsl(((hue + 130) % 360).toFloat(), 0.35f, 0.30f).copy(alpha = if (isDark) 0.5f else 0.10f),
                        CircleShape
                    )
            )
        }
        // A faint dark vignette at top/bottom keeps the status bar area and
        // the button row readable against the glow rather than the blur
        // washing all the way to the edges at full brightness.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isDark) {
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.30f),
                            0.22f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.45f)
                        )
                    } else {
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.08f),
                            0.25f to Color.Transparent,
                            0.78f to Color.Transparent,
                            1f to paletteOverlayColor(backdropBase, 0.10f)
                        )
                    }
                )
        )
    }
}

/**
 * The plain glass-circle person icon from the reference - a translucent ring
 * plus a frosted inner circle with a simple person glyph, and one slow
 * breathing halo behind it. Deliberately simple (no photo, no initials) to
 * match the reference exactly; ContactDetail-style photo avatars are used
 * everywhere else in the app, but this screen's whole visual language is
 * "frosted glass over a blurred glow," which a photo would interrupt.
 */
@Composable
private fun GlassAvatarRings(photoUri: String?, callerName: String) {
    val transition = rememberInfiniteTransition(label = "avatar-breathe")
    val haloScale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "halo-scale"
    )
    val haloAlpha by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.12f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "halo-alpha"
    )
    // A slow, subtle in-and-out pulse on the whole avatar stack (rings +
    // icon together), separate from the halo's own scale animation - this
    // is what makes the avatar itself feel alive rather than just the glow
    // behind it breathing while the glass rings sit perfectly static.
    val corePulse by transition.animateFloat(
        initialValue = 1f, targetValue = 1.035f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "core-pulse"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(184.dp)
                .graphicsLayer { scaleX = haloScale; scaleY = haloScale; alpha = haloAlpha }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.5f))
        )
        Box(
            modifier = Modifier
                .size(148.dp)
                .graphicsLayer { scaleX = corePulse; scaleY = corePulse }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
        )
        Box(
            modifier = Modifier
                .size(126.dp)
                .graphicsLayer { scaleX = corePulse; scaleY = corePulse }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Avatar(name = callerName, photoUri = photoUri, size = 108.dp)
        }
    }
}

/**
 * The fixed Decline/Accept row, with animated chevron swipe-hints between
 * the two circles - the ">>"/"<<" motif from the reference, nudging
 * outward from center in a slow loop to hint that these can be dragged, not
 * just tapped. Each circle is independently draggable a short distance
 * toward the other (DraggableCallCircle) as an alternate gesture to a tap,
 * without a separate track/puck system to visually manage.
 */
@Composable
private fun SwipeUpCallCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onTrigger: () -> Unit
) {
    var dragPx by remember { mutableStateOf(0f) }
    val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.toPx() }
    val offset by animateFloatAsState(
        targetValue = dragPx,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "swipe-up-offset"
    )
    val progress = (dragPx / threshold).coerceIn(0f, 1f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .graphicsLayer {
                    translationY = -offset
                    scaleX = 1f + progress * 0.08f
                    scaleY = 1f + progress * 0.08f
                }
                .clip(CircleShape)
                .background(color.copy(alpha = 0.92f))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            if (dragPx >= threshold) onTrigger()
                            dragPx = 0f
                        },
                        onDragCancel = { dragPx = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            // Only upward movement arms the action.
                            dragPx = (dragPx - amount.y).coerceIn(0f, threshold * 1.35f)
                        }
                    )
                }
                .clickable(onClick = onTrigger),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Swipe up", tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(18.dp))
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The bottom "I'm busy" / "Message" pill from the reference - two compact
 * actions sharing one translucent capsule rather than the previous design's
 * separate full-size QuickAction (icon-over-label) button used for Message.
 * "I'm busy" declines with a canned response; onQuickMessage covers actual
 * text-reply, matching the existing public API this screen already exposed.
 */
@Composable
private fun QuickActionsPill(onQuickMessage: () -> Unit, modifier: Modifier = Modifier, lightMode: Boolean = false) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (lightMode) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.12f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PillAction(icon = Icons.Filled.Block, label = "I'm busy", onClick = onQuickMessage, lightMode = lightMode)
        Box(modifier = Modifier.size(1.dp, 22.dp).background(if (lightMode) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.18f)))
        PillAction(icon = Icons.Filled.Message, label = "Message", onClick = onQuickMessage, lightMode = lightMode)
    }
}

@Composable
private fun PillAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, lightMode: Boolean) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (lightMode) Color(0xFF33415C).copy(alpha = 0.82f) else Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 13.5.sp, color = if (lightMode) Color(0xFF23324D).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Medium)
    }
}

/**
 * Fades a composable in while rising slightly from below - the entrance
 * motion used for the identity block, spam badge, and action row.
 */
private fun Modifier.graphicsLayerAlphaRise(progress: Float): Modifier = this.graphicsLayer {
    alpha = progress
    translationY = (1f - progress) * 14.dp.toPx()
}

/**
 * The "Likely spam" pill - a slow breathing glow so it reads as an active
 * warning rather than static text.
 */
@Composable
private fun SpamBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "spam-badge-pulse")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "spam-glow"
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFE0442E).copy(alpha = glowAlpha * 0.85f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text("Likely spam", fontSize = 12.5.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

// ============================================================================
// STYLE: "Orbit" - calmer/premium mood. A large centered avatar disc inside
// slowly-rotating concentric glass rings (the "orbit"), a soft single-hue
// mesh gradient backdrop (deliberately calmer than Aurora's 5-blob glow),
// and accept/decline as two wide glass arcs anchored to the bottom corners
// that the person drags toward center to answer/decline - a different
// physical gesture from Aurora's vertical swipe-up, so the two styles don't
// just look different, they feel different to use.
// ============================================================================

/**
 * Orbit's backdrop: a still, single-hue radial mesh rather than Aurora's
 * five separately-colored blobs - two overlapping soft radial gradients in
 * the same hue family (a lighter core, a darker outer wash) plus one very
 * slow drifting highlight, so it reads as calm and premium rather than
 * energetic. Uses the same incomingCallBackdropColor/accentHueDegrees
 * per-theme derivation Aurora uses, so Orbit is just as theme-aware.
 */
@Composable
private fun OrbitMeshBackdrop(hue: Int, backdropBase: Color, isDark: Boolean) {
    val transition = rememberInfiniteTransition(label = "orbit-mesh-drift")
    val driftX by transition.animateFloat(
        initialValue = -30f, targetValue = 30f,
        animationSpec = infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbit-drift-x"
    )
    val driftY by transition.animateFloat(
        initialValue = -18f, targetValue = 22f,
        animationSpec = infiniteRepeatable(tween(11000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbit-drift-y"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(lightenTowardBlack(backdropBase, 0.05f), backdropBase)
                    )
                )
        )
        // One slow-drifting soft highlight, well off-center, heavily
        // blurred - the only moving light source in this style, which is
        // what keeps Orbit feeling calm rather than static.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = 160.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = driftX.dp, y = driftY.dp)
                    .size(460.dp)
                    .background(
                        Color.hsl(hue.toFloat(), 0.55f, 0.50f).copy(alpha = if (isDark) 0.42f else 0.16f),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-60).dp)
                    .size(320.dp)
                    .background(
                        Color.hsl(((hue + 20) % 360).toFloat(), 0.45f, 0.58f).copy(alpha = if (isDark) 0.30f else 0.12f),
                        CircleShape
                    )
            )
        }
        // Same readability vignette treatment as Aurora, kept identical so
        // the status bar and bottom action area stay legible.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isDark) {
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.28f),
                            0.24f to Color.Transparent,
                            0.72f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.48f)
                        )
                    } else {
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.08f),
                            0.25f to Color.Transparent,
                            0.78f to Color.Transparent,
                            1f to backdropBase.copy(alpha = 0.10f)
                        )
                    }
                )
        )
    }
}

/**
 * The centered avatar with two slowly counter-rotating glass rings around
 * it - the "orbit" the style is named for. Each ring has a small brighter
 * arc segment (simulated with an uneven alpha sweep via two stacked
 * semicircle-ish boxes) so the rotation is actually visible rather than a
 * uniformly-lit ring spinning invisibly in place.
 */
@Composable
private fun OrbitAvatar(photoUri: String?, callerName: String, palette: DialerPalette) {
    val transition = rememberInfiniteTransition(label = "orbit-rings")
    val outerRotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "orbit-outer-rotation"
    )
    val innerRotation by transition.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing)),
        label = "orbit-inner-rotation"
    )
    val breathe by transition.animateFloat(
        initialValue = 1f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbit-breathe"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer ring - thin, slowly rotating clockwise.
        Box(
            modifier = Modifier
                .size(216.dp)
                .rotate(outerRotation)
                .clip(CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.14f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(216.dp)
                .rotate(outerRotation)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(46.dp, 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.55f))
            )
        }
        // Inner ring - slightly thicker, rotating the other direction.
        Box(
            modifier = Modifier
                .size(178.dp)
                .rotate(innerRotation)
                .clip(CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.12f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(178.dp)
                .rotate(innerRotation)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(3.dp, 34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.5f))
            )
        }
        // Large glass avatar disc, breathing gently.
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = breathe; scaleY = breathe }
                .size(146.dp)
                .glassCircle(palette, tintAlpha = 0.30f),
            contentAlignment = Alignment.Center
        ) {
            Avatar(name = callerName, photoUri = photoUri, size = 118.dp)
        }
    }
}

/**
 * Orbit's accept/decline: two wide glass arcs pinned to the bottom-left and
 * bottom-right, each draggable *horizontally toward center* to trigger -
 * a deliberately different gesture from Aurora's vertical swipe-up, so the
 * two styles feel distinct to use, not just to look at. Tapping still works
 * as the fast path.
 */
@Composable
private fun OrbitActionArc(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    alignEnd: Boolean,
    onTrigger: () -> Unit
) {
    var dragPx by remember { mutableStateOf(0f) }
    val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 46.dp.toPx() }
    val offset by animateFloatAsState(
        targetValue = dragPx,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 400f),
        label = "orbit-arc-offset"
    )
    val progress = (dragPx / threshold).coerceIn(0f, 1f)
    val direction = if (alignEnd) -1f else 1f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                translationX = offset * direction
                scaleX = 1f + progress * 0.05f
                scaleY = 1f + progress * 0.05f
            }
            .pointerInput(alignEnd) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragPx >= threshold) onTrigger()
                        dragPx = 0f
                    },
                    onDragCancel = { dragPx = 0f },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        val towardCenter = if (alignEnd) -amount else amount
                        dragPx = (dragPx + towardCenter).coerceIn(0f, threshold * 1.3f)
                    }
                )
            }
            .clickable(onClick = onTrigger)
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .liquidGlass(
                    palette = LocalDialerPalette.current,
                    shape = CircleShape,
                    tintAlpha = 0.28f
                )
                .background(accent.copy(alpha = 0.30f + progress * 0.35f), CircleShape)
                .border(1.5.dp, accent.copy(alpha = 0.65f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.88f), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OrbitIncomingCallScreen(
    callerName: String,
    callerNumber: String,
    spamAssessment: SpamAssessment? = null,
    callerPhotoUri: String? = null,
    isSavedContact: Boolean = false,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onQuickMessage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val hue = accentHueDegrees(palette.accent).toInt()
    val backdropBase = incomingCallBackdropColor(palette)
    val secondaryText = Color.White.copy(alpha = 0.70f)

    val identityEntrance by rememberEntranceProgress(0)
    val avatarEntrance by rememberEntranceProgress(100)
    val actionsEntrance by rememberEntranceProgress(190)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        OrbitMeshBackdrop(hue = hue, backdropBase = backdropBase, isDark = palette.isDark)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .graphicsLayerAlphaRise(identityEntrance)
                    .glassCard(palette, corner = 18.dp, tintAlpha = 0.24f)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "INCOMING CALL",
                    fontSize = 10.5.sp,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.Bold,
                    color = secondaryText
                )
            }

            Spacer(Modifier.weight(0.55f))

            Column(
                modifier = Modifier.graphicsLayerAlphaRise(avatarEntrance),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OrbitAvatar(photoUri = callerPhotoUri, callerName = callerName, palette = palette)
                Spacer(Modifier.height(26.dp))
                Text(
                    text = callerName.ifBlank { "Unknown caller" },
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                if (callerNumber.isNotBlank() && callerNumber != callerName) {
                    Spacer(Modifier.height(6.dp))
                    Text(callerNumber, fontSize = 14.sp, color = secondaryText)
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    text = if (isSavedContact) "Saved contact" else "New caller",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = secondaryText.copy(alpha = 0.85f)
                )
                if (spamAssessment?.isLikelySpam == true) {
                    Spacer(Modifier.height(12.dp))
                    SpamBadge()
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayerAlphaRise(actionsEntrance)
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                OrbitActionArc(
                    icon = Icons.Filled.CallEnd,
                    label = "Decline",
                    accent = palette.danger,
                    alignEnd = false,
                    onTrigger = onDecline
                )
                OrbitActionArc(
                    icon = Icons.Filled.Phone,
                    label = "Answer",
                    accent = palette.callGreen,
                    alignEnd = true,
                    onTrigger = onAccept
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Tap or drag inward to respond",
                fontSize = 11.5.sp,
                color = secondaryText,
                modifier = Modifier.graphicsLayerAlphaRise(actionsEntrance)
            )
            Spacer(Modifier.height(12.dp))
            QuickActionsPill(
                onQuickMessage = onQuickMessage,
                modifier = Modifier.graphicsLayerAlphaRise(actionsEntrance)
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ============================================================================
// STYLE: "Pulse" - the most kinetic/energetic style. Concentric glass rings
// radiate continuously outward from the avatar like sonar, a slowly
// drifting two-tone mesh gradient backdrop keeps the whole screen feeling
// alive, and accept/decline are glass pill buttons that ripple outward on
// press. Full-width vertical stack (avatar higher up, name below it, pills
// stacked full-width at the bottom) so it also reads visually distinct from
// both Aurora's and Orbit's centered-column layouts, not just animation-wise.
// ============================================================================

/**
 * Pulse's backdrop: two softly-colored wide bands (rather than Aurora's 5
 * blobs or Orbit's 1 still core) that slowly drift past each other
 * diagonally, giving the whole screen a gentle "always moving" quality that
 * matches the ripples below without being as busy as Aurora's multi-blob
 * glow.
 */
@Composable
private fun PulseMeshBackdrop(hue: Int, backdropBase: Color, isDark: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse-mesh-drift")
    val bandOffset1 by transition.animateFloat(
        initialValue = -60f, targetValue = 60f,
        animationSpec = infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse-band-1"
    )
    val bandOffset2 by transition.animateFloat(
        initialValue = 50f, targetValue = -50f,
        animationSpec = infiniteRepeatable(tween(8600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse-band-2"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(lightenTowardBlack(backdropBase, 0.07f), backdropBase))
                )
        )
        Box(modifier = Modifier.fillMaxSize().blur(radius = 140.dp)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = bandOffset1.dp, y = (-40).dp)
                    .size(380.dp)
                    .background(
                        Color.hsl(hue.toFloat(), 0.65f, 0.55f).copy(alpha = if (isDark) 0.5f else 0.20f),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = bandOffset2.dp, y = 30.dp)
                    .size(420.dp)
                    .background(
                        Color.hsl(((hue + 200) % 360).toFloat(), 0.6f, 0.5f).copy(alpha = if (isDark) 0.48f else 0.18f),
                        CircleShape
                    )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isDark) {
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.26f),
                            0.24f to Color.Transparent,
                            0.72f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.46f)
                        )
                    } else {
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.08f),
                            0.25f to Color.Transparent,
                            0.78f to Color.Transparent,
                            1f to backdropBase.copy(alpha = 0.10f)
                        )
                    }
                )
        )
    }
}

/**
 * The sonar effect: 3 rings continuously expand outward from the avatar and
 * fade out, staggered so a new one starts before the previous finishes -
 * the same "signal going out" motif a incoming-call screen wants, done with
 * plain glass rings instead of a photo/video effect.
 */
@Composable
private fun PulseRing(delayMs: Int, baseSize: Dp, maxSize: Dp, color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse-ring-$delayMs")
    val progress by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2200, delayMillis = delayMs, easing = FastOutSlowInEasing)
        ),
        label = "pulse-ring-progress-$delayMs"
    )
    val size = baseSize + (maxSize - baseSize) * progress
    val alpha = (1f - progress) * 0.55f

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.5.dp, color.copy(alpha = alpha), CircleShape)
    )
}

@Composable
private fun PulseAvatar(photoUri: String?, callerName: String, palette: DialerPalette) {
    Box(contentAlignment = Alignment.Center) {
        PulseRing(delayMs = 0, baseSize = 128.dp, maxSize = 232.dp, color = Color.White)
        PulseRing(delayMs = 700, baseSize = 128.dp, maxSize = 232.dp, color = Color.White)
        PulseRing(delayMs = 1400, baseSize = 128.dp, maxSize = 232.dp, color = Color.White)
        Box(
            modifier = Modifier
                .size(128.dp)
                .glassCircle(palette, tintAlpha = 0.34f),
            contentAlignment = Alignment.Center
        ) {
            Avatar(name = callerName, photoUri = photoUri, size = 100.dp)
        }
    }
}

/**
 * Full-width glass pill button with its own small ripple-on-press flourish
 * (a ring that briefly expands from the tap point's general area and fades)
 * - Pulse's own equivalent of Aurora's swipe-up / Orbit's drag-inward, kept
 * as a straightforward tap here since a full-width pill has less room for a
 * drag gesture to feel natural than a compact circle/arc does.
 */
@Composable
private fun PulsePillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    palette: DialerPalette,
    onClick: () -> Unit
) {
    var pressedTick by remember { mutableStateOf(0) }
    val rippleProgress by animateFloatAsState(
        targetValue = pressedTick.toFloat(),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "pulse-pill-ripple"
    )
    val ripplePhase = rippleProgress - rippleProgress.toInt()
    val rippleAlpha = if (pressedTick > 0) (1f - ripplePhase) * 0.35f else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .liquidGlass(palette, shape = RoundedCornerShape(32.dp), tintAlpha = 0.30f)
            .background(accent.copy(alpha = 0.34f), RoundedCornerShape(32.dp))
            .clickable {
                pressedTick += 1
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = 1f + ripplePhase * 0.5f; scaleY = 1f + ripplePhase * 0.5f; alpha = rippleAlpha }
                .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PulseIncomingCallScreen(
    callerName: String,
    callerNumber: String,
    spamAssessment: SpamAssessment? = null,
    callerPhotoUri: String? = null,
    isSavedContact: Boolean = false,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onQuickMessage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val callerHue = ((callerName.firstOrNull()?.code ?: 65) * 41) % 360
    val themeHue = accentHueDegrees(palette.accent)
    val hue = (((themeHue * 0.65f) + (callerHue * 0.35f)).toInt()).let { it.mod(360) }
    val backdropBase = incomingCallBackdropColor(palette)
    val secondaryText = Color.White.copy(alpha = 0.72f)

    val identityEntrance by rememberEntranceProgress(0)
    val avatarEntrance by rememberEntranceProgress(80)
    val actionsEntrance by rememberEntranceProgress(160)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        PulseMeshBackdrop(hue = hue, backdropBase = backdropBase, isDark = palette.isDark)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .graphicsLayerAlphaRise(identityEntrance)
                    .glassCard(palette, corner = 18.dp, tintAlpha = 0.24f)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "INCOMING CALL",
                    fontSize = 10.5.sp,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.Bold,
                    color = secondaryText
                )
            }

            Spacer(Modifier.height(30.dp))

            Box(
                modifier = Modifier.graphicsLayerAlphaRise(avatarEntrance),
                contentAlignment = Alignment.Center
            ) {
                PulseAvatar(photoUri = callerPhotoUri, callerName = callerName, palette = palette)
            }

            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier.graphicsLayerAlphaRise(identityEntrance),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = callerName.ifBlank { "Unknown caller" },
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                if (callerNumber.isNotBlank() && callerNumber != callerName) {
                    Spacer(Modifier.height(6.dp))
                    Text(callerNumber, fontSize = 14.sp, color = secondaryText)
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    text = if (isSavedContact) "Saved contact" else "New caller",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = secondaryText.copy(alpha = 0.85f)
                )
                if (spamAssessment?.isLikelySpam == true) {
                    Spacer(Modifier.height(12.dp))
                    SpamBadge()
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayerAlphaRise(actionsEntrance),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PulsePillButton(
                    icon = Icons.Filled.Phone,
                    label = "Answer",
                    accent = palette.callGreen,
                    palette = palette,
                    onClick = onAccept
                )
                PulsePillButton(
                    icon = Icons.Filled.CallEnd,
                    label = "Decline",
                    accent = palette.danger,
                    palette = palette,
                    onClick = onDecline
                )
            }

            Spacer(Modifier.height(12.dp))
            QuickActionsPill(
                onQuickMessage = onQuickMessage,
                modifier = Modifier.graphicsLayerAlphaRise(actionsEntrance)
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}
