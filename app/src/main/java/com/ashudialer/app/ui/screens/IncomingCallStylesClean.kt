package com.ashudialer.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.telecom.SpamAssessment
import com.ashudialer.app.ui.components.Avatar

/*
 * The three incoming-call screens.
 *
 *   "clean"  - white. "Incoming call" label, round photo, big name + number, an "I'm busy | Message"
 *              row, Decline (left) and Accept (right) at the bottom. (Reference screen 1.)
 *   "center" - soft grey-blue. Big name + number + "Incoming voice call" at the top, the photo in
 *              the middle, a "Reply with message" pill, Decline / Accept at the bottom. (Reference
 *              screen 2.)
 *   "swipe"  - the same calm look, but answering / declining is a swipe: drag the green handle up to
 *              answer or the red one up to decline, with a breathing halo and drifting chevrons.
 *
 * Positions are FRACTIONS of the screen height (measured from the reference screenshots), not fixed
 * dp, so the screen keeps the same proportions on a small phone, a tall phone and a tablet.
 *
 * DARK MODE. On a dark theme the screens switch to a dark glass look (frosted translucent panels
 * over a dark gradient) when [glass] is true; with [glass] false they stay white/light exactly like
 * the references, on every theme. [dark] only picks the colours; the caller decides it.
 *
 * All three take the same caller info and callbacks as before, so InCallActivity is unchanged.
 * [isPreview] is used by the style picker to draw the very same composable small: no entrance
 * animation, no window-inset padding, and every button is a no-op.
 */

private val AcceptGreen = Color(0xFF34C759)
private val DeclineRed = Color(0xFFFF3B30)

/** Colours for one look (light or dark glass). */
private class CallColors(
    val background: Brush,
    val title: Color,
    val subtitle: Color,
    val label: Color,
    val pill: Color,
    val pillText: Color,
    val divider: Color
)

private fun lightColors(tinted: Boolean) = CallColors(
    background = if (tinted) Brush.verticalGradient(listOf(Color(0xFFF4F6FA), Color(0xFFE6EBF5)))
    else Brush.verticalGradient(listOf(Color.White, Color.White)),
    title = Color(0xFF111827),
    subtitle = Color(0xFF6B7280),
    label = Color(0xFF3B4252),
    pill = Color.White,
    pillText = Color(0xFF23283A),
    divider = Color(0x1F000000)
)

private fun darkGlassColors() = CallColors(
    background = Brush.verticalGradient(listOf(Color(0xFF1B2030), Color(0xFF0B0E16))),
    title = Color.White,
    subtitle = Color.White.copy(alpha = 0.66f),
    label = Color.White.copy(alpha = 0.86f),
    // Frosted panel: translucent white over the dark gradient, with a hairline border added by the caller.
    pill = Color.White.copy(alpha = 0.10f),
    pillText = Color.White.copy(alpha = 0.92f),
    divider = Color.White.copy(alpha = 0.16f)
)

private fun colorsFor(dark: Boolean, glass: Boolean, tinted: Boolean): CallColors =
    if (dark && glass) darkGlassColors() else lightColors(tinted)

/** "Mobile" / "Unknown number" line under the name. */
private fun numberLine(callerNumber: String, callerName: String, isSavedContact: Boolean): String = when {
    callerNumber.isBlank() -> "Unknown number"
    isSavedContact && callerName != callerNumber -> callerNumber
    else -> "Mobile"
}

// ---------------------------------------------------------------------------------------------
// CLEAN  (reference screen 1)
// ---------------------------------------------------------------------------------------------
@Composable
fun CleanIncomingCallScreen(
    callerName: String,
    callerNumber: String,
    spamAssessment: SpamAssessment?,
    callerPhotoUri: String?,
    isSavedContact: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onQuickMessage: () -> Unit,
    modifier: Modifier = Modifier,
    isPreview: Boolean = false,
    dark: Boolean = false,
    glass: Boolean = true
) {
    val c = colorsFor(dark, glass, tinted = false)
    val rise by rememberEntranceProgress(if (isPreview) 0 else 60)
    val p = if (isPreview) 1f else rise

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .then(if (isPreview) Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars))
    ) {
        val h = maxHeight
        val w = maxWidth
        // Positions are measured from the reference screenshot as fractions of the screen height.
        val photo = (h * 0.119f).coerceIn(72.dp, 132.dp)
        val circle = (h * 0.073f).coerceIn(54.dp, 78.dp)
        val sideMargin = w * 0.09f

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.482f))
            Text("Incoming call", fontSize = 15.sp, color = c.subtitle, fontWeight = FontWeight.Medium,
                modifier = Modifier.graphicsLayerAlphaRise(p))
            Spacer(Modifier.height(h * 0.02f))
            Avatar(name = callerName, photoUri = callerPhotoUri, size = photo)
            Spacer(Modifier.height(h * 0.028f))
            Text(
                callerName, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = c.title,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp).graphicsLayerAlphaRise(p)
            )
            Spacer(Modifier.height(4.dp))
            Text(numberLine(callerNumber, callerName, isSavedContact), fontSize = 15.sp, color = c.subtitle,
                modifier = Modifier.graphicsLayerAlphaRise(p))
            if (spamAssessment?.isLikelySpam == true) {
                Spacer(Modifier.height(12.dp))
                SpamBadge()
            }
            Spacer(Modifier.weight(0.518f))

            // "I'm busy | Message"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.then(if (isPreview) Modifier else Modifier.graphicsLayerAlphaRise(p))
            ) {
                QuickText(Icons.Filled.Block, "I'm busy", c, if (isPreview) {{}} else onQuickMessage)
                Box(Modifier.padding(horizontal = 14.dp).size(1.dp, 28.dp).background(c.divider))
                QuickText(Icons.Filled.Message, "Message", c, if (isPreview) {{}} else onQuickMessage)
            }
            Spacer(Modifier.height(h * 0.032f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = sideMargin),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RoundAction(DeclineRed, Icons.Filled.CallEnd, "Decline", c.label, if (isPreview) {{}} else onDecline, circle)
                RoundAction(AcceptGreen, Icons.Filled.Phone, "Accept", c.label, if (isPreview) {{}} else onAccept, circle)
            }
            Spacer(Modifier.height(h * 0.05f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// CENTER  (reference screen 2)
// ---------------------------------------------------------------------------------------------
@Composable
fun CenterIncomingCallScreen(
    callerName: String,
    callerNumber: String,
    spamAssessment: SpamAssessment?,
    callerPhotoUri: String?,
    isSavedContact: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onQuickMessage: () -> Unit,
    modifier: Modifier = Modifier,
    isPreview: Boolean = false,
    dark: Boolean = false,
    glass: Boolean = true
) {
    val c = colorsFor(dark, glass, tinted = true)
    val rise by rememberEntranceProgress(if (isPreview) 0 else 60)
    val p = if (isPreview) 1f else rise

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .then(if (isPreview) Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars))
    ) {
        val h = maxHeight
        val w = maxWidth
        val photo = (h * 0.142f).coerceIn(84.dp, 152.dp)
        val circle = (h * 0.077f).coerceIn(56.dp, 82.dp)
        val sideMargin = w * 0.103f

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.276f))
            Text(
                callerName, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = c.title,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp).graphicsLayerAlphaRise(p)
            )
            Spacer(Modifier.height(6.dp))
            Text(numberLine(callerNumber, callerName, isSavedContact), fontSize = 16.sp, color = c.subtitle,
                modifier = Modifier.graphicsLayerAlphaRise(p))
            Spacer(Modifier.height(4.dp))
            Text("Incoming voice call", fontSize = 14.sp, color = c.subtitle.copy(alpha = 0.85f),
                modifier = Modifier.graphicsLayerAlphaRise(p))
            if (spamAssessment?.isLikelySpam == true) {
                Spacer(Modifier.height(12.dp))
                SpamBadge()
            }
            Spacer(Modifier.weight(0.263f))
            Avatar(name = callerName, photoUri = callerPhotoUri, size = photo)
            Spacer(Modifier.weight(0.461f))

            Panel(c, dark && glass, if (isPreview) {{}} else onQuickMessage) {
                Icon(Icons.Filled.Message, contentDescription = null, tint = c.pillText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Reply with message", fontSize = 15.sp, color = c.pillText, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(h * 0.028f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = sideMargin),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RoundAction(DeclineRed, Icons.Filled.CallEnd, "Decline", c.label, if (isPreview) {{}} else onDecline, circle)
                RoundAction(AcceptGreen, Icons.Filled.Phone, "Accept", c.label, if (isPreview) {{}} else onAccept, circle)
            }
            Spacer(Modifier.height(h * 0.05f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// SWIPE  (the third look: same calm layout, answered with a swipe)
// ---------------------------------------------------------------------------------------------
@Composable
fun SwipeIncomingCallScreen(
    callerName: String,
    callerNumber: String,
    spamAssessment: SpamAssessment?,
    callerPhotoUri: String?,
    isSavedContact: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onQuickMessage: () -> Unit,
    modifier: Modifier = Modifier,
    isPreview: Boolean = false,
    dark: Boolean = false,
    glass: Boolean = true,
    avatarPulseEnabled: Boolean = true
) {
    val c = colorsFor(dark, glass, tinted = true)
    val rise by rememberEntranceProgress(if (isPreview) 0 else 60)
    val p = if (isPreview) 1f else rise

    // A slow "breathing" halo behind the photo (so the screen feels alive without being
    // busy) - this is the one animation in this screen that also runs in the settings
    // picker's own small preview (rather than being forced static via isPreview like the
    // entrance-fade animations above), so turning "Pulsing glow around photo" on or off in
    // Settings is visible right there without waiting for a real call. When turned off the
    // infinite animation is never created at all (not just visually pinned to 1f), so a
    // disabled toggle actually stops the animation loop instead of leaving it running unseen.
    val halo: Float = if (avatarPulseEnabled) {
        val breathe = rememberInfiniteTransition(label = "swipe-breathe")
        val animatedHalo by breathe.animateFloat(
            initialValue = 0.94f, targetValue = 1.10f,
            animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Reverse),
            label = "halo"
        )
        animatedHalo
    } else {
        1f
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .then(if (isPreview) Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars))
    ) {
        val h = maxHeight
        val w = maxWidth
        val photo = (h * 0.135f).coerceIn(80.dp, 148.dp)
        val handle = (h * 0.080f).coerceIn(58.dp, 84.dp)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.26f))
            Text(
                callerName, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = c.title,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp).graphicsLayerAlphaRise(p)
            )
            Spacer(Modifier.height(6.dp))
            Text(numberLine(callerNumber, callerName, isSavedContact), fontSize = 16.sp, color = c.subtitle,
                modifier = Modifier.graphicsLayerAlphaRise(p))
            Spacer(Modifier.height(4.dp))
            Text("Incoming voice call", fontSize = 14.sp, color = c.subtitle.copy(alpha = 0.85f),
                modifier = Modifier.graphicsLayerAlphaRise(p))
            if (spamAssessment?.isLikelySpam == true) {
                Spacer(Modifier.height(12.dp))
                SpamBadge()
            }
            Spacer(Modifier.weight(0.24f))

            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(photo * 1.22f)
                        .graphicsLayer { scaleX = halo; scaleY = halo }
                        .clip(CircleShape)
                        .background(AcceptGreen.copy(alpha = 0.14f))
                )
                Avatar(name = callerName, photoUri = callerPhotoUri, size = photo)
            }
            Spacer(Modifier.weight(0.5f))

            if (!isPreview) {
                Text("Swipe up to answer or decline", fontSize = 13.sp, color = c.subtitle)
                Spacer(Modifier.height(10.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = w * 0.14f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                SwipeHandle(DeclineRed, Icons.Filled.CallEnd, "Decline", c.label, if (isPreview) {{}} else onDecline, handle, isPreview)
                SwipeHandle(AcceptGreen, Icons.Filled.Phone, "Answer", c.label, if (isPreview) {{}} else onAccept, handle, isPreview)
            }
            Spacer(Modifier.height(h * 0.02f))
            // Message shortcut kept, so text-reply is never lost in the swipe style.
            Box(Modifier.clickable(enabled = !isPreview, onClick = onQuickMessage).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Message, contentDescription = null, tint = c.subtitle, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reply with message", fontSize = 13.sp, color = c.subtitle)
                }
            }
            Spacer(Modifier.height(h * 0.035f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// shared pieces
// ---------------------------------------------------------------------------------------------

/** Filled round Decline / Accept button with its label underneath. */
@Composable
private fun RoundAction(
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    labelColor: Color,
    onClick: () -> Unit,
    size: Dp
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(color).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(size * 0.42f))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 14.sp, color = labelColor, fontWeight = FontWeight.Medium)
    }
}

/** One "icon + text" item of the "I'm busy | Message" row. */
@Composable
private fun QuickText(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    c: CallColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = c.label, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 15.sp, color = c.label, fontWeight = FontWeight.Medium)
    }
}

/**
 * The rounded "Reply with message" pill. In dark-glass mode it becomes a frosted translucent panel
 * with a hairline border (the "glass" look); otherwise it is a plain white pill as in the reference.
 */
@Composable
private fun Panel(c: CallColors, glassLook: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(26.dp))
            .background(c.pill)
            .then(if (glassLook) Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(26.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * A drag handle: pull it UP past a threshold to trigger, or just tap it. It springs back when
 * released early, grows slightly while dragged, and a chevron above it nudges upward in a loop to
 * show the direction.
 */
@Composable
private fun SwipeHandle(
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    labelColor: Color,
    onTrigger: () -> Unit,
    size: Dp,
    isPreview: Boolean
) {
    var dragPx by remember { mutableStateOf(0f) }
    val threshold = with(LocalDensity.current) { 84.dp.toPx() }
    val offset by animateFloatAsState(
        targetValue = dragPx,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "handle-offset"
    )
    val progress = (dragPx / threshold).coerceIn(0f, 1f)

    val nudge = rememberInfiniteTransition(label = "chevron-nudge")
    val chevronShift by nudge.animateFloat(
        initialValue = 0f, targetValue = -8f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "chevron"
    )
    val chevronAlpha by nudge.animateFloat(
        initialValue = 0.9f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "chevron-alpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (!isPreview) {
            Icon(
                Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = color,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { translationY = chevronShift.dp.toPx(); alpha = chevronAlpha * (1f - progress) }
            )
        }
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    translationY = -offset
                    val s = 1f + progress * 0.10f
                    scaleX = s; scaleY = s
                }
                .clip(CircleShape)
                .background(color)
                .pointerInput(isPreview) {
                    if (!isPreview) detectDragGestures(
                        onDragEnd = {
                            if (dragPx >= threshold) onTrigger()
                            dragPx = 0f
                        },
                        onDragCancel = { dragPx = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            // Only upward movement arms the action.
                            dragPx = (dragPx - amount.y).coerceIn(0f, threshold * 1.3f)
                        }
                    )
                }
                .clickable(enabled = !isPreview, onClick = onTrigger),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(size * 0.42f))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 14.sp, color = labelColor, fontWeight = FontWeight.Medium)
    }
}
