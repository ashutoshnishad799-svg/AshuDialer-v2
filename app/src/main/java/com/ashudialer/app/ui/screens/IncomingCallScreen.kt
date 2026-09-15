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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.telecom.SpamAssessment
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.components.Avatar

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
fun IncomingCallScreen(
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
    // Blend the caller-derived hue with the active theme's own accent hue so
    // every caller still looks visually distinct (as before) while the
    // overall palette clearly belongs to whichever theme is active - a 65/35
    // bias toward the theme's hue is enough to make Ocean Blue read blue and
    // Violet read violet without making every caller on that theme look
    // identical.
    val themeHue = accentHueDegrees(palette.accent)
    val hue = (((themeHue * 0.65f) + (callerHue * 0.35f)).toInt()).let { if (it < 0) it + 360 else it % 360 }
    val backdropBase = incomingCallBackdropColor(palette)

    val identityEntrance by rememberEntranceProgress(delayMs = 0)
    val badgeEntrance by rememberEntranceProgress(delayMs = 140)
    val actionsEntrance by rememberEntranceProgress(delayMs = 220)

    val foreground = if (palette.isDark) Color.White else palette.textPrimary
    val secondaryText = if (palette.isDark) Color.White.copy(alpha = 0.72f) else palette.textSecondary
    val glassFill = if (palette.isDark) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.48f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        AuroraBackdrop(hue = hue, backdropBase = backdropBase, isDark = palette.isDark)

        Column(
            modifier = Modifier
                .fillMaxSize()
                // Same cutout-aware fix as CallScreen/ContactDetailScreen/
                // AddContactScreen - this screen's caller avatar
                // (GlassAvatarRings below) is the top-anchored element most
                // at risk of sitting under a punch-hole cutout, so
                // statusBarsPadding() alone isn't assumed sufficient here
                // either.
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
        ) {
            Spacer(Modifier.weight(0.36f))

            Column(
                modifier = Modifier.fillMaxWidth().graphicsLayerAlphaRise(identityEntrance),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Incoming call", fontSize = 15.sp, color = secondaryText, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Text(callerName, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = foreground, textAlign = TextAlign.Center, maxLines = 1)
                Spacer(Modifier.height(5.dp))
                if (callerNumber.isNotBlank() && callerNumber != callerName) {
                    Text(callerNumber, fontSize = 15.sp, color = secondaryText)
                }
                if (!isSavedContact) {
                    Spacer(Modifier.height(4.dp))
                    Text("Not saved contact", fontSize = 12.sp, color = secondaryText.copy(alpha = 0.9f))
                }
                if (spamAssessment?.isLikelySpam == true) {
                    Spacer(Modifier.height(10.dp))
                    SpamBadge(modifier = Modifier.graphicsLayerAlphaRise(badgeEntrance))
                }
            }

            Spacer(Modifier.weight(0.28f))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GlassAvatarRings(photoUri = callerPhotoUri, callerName = callerName)
            }
            Spacer(Modifier.weight(0.48f))

            // A single frosted action area keeps the incoming screen visually
            // consistent with the rest of Ashu Dialer. Both actions use the
            // same gesture: swipe the button upward to trigger it.
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(34.dp))
                    .background(glassFill)
                    .padding(horizontal = 22.dp, vertical = 16.dp)
                    .graphicsLayerAlphaRise(actionsEntrance),
                horizontalArrangement = Arrangement.spacedBy(42.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SwipeUpCallCircle(Icons.Filled.CallEnd, "Decline", Color(0xFFE0442E), onDecline)
                SwipeUpCallCircle(Icons.Filled.Phone, "Answer", palette.callGreen, onAccept)
            }

            Spacer(Modifier.height(10.dp))
            Text("Swipe up to answer or decline", fontSize = 12.sp, color = secondaryText, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            QuickActionsPill(onQuickMessage = onQuickMessage, modifier = Modifier.align(Alignment.CenterHorizontally).graphicsLayerAlphaRise(actionsEntrance), lightMode = !palette.isDark)
            Spacer(Modifier.height(22.dp))
        }
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
