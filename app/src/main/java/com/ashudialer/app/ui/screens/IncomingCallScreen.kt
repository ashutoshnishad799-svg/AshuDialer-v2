package com.ashudialer.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Message
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.telecom.SpamAssessment
import com.ashudialer.app.ui.theme.LocalDialerPalette

/**
 * Entrance choreography shared by the caller identity block and the action
 * row: each element settles in slightly staggered rather than the whole
 * screen appearing at once. `delayMs` offsets when a given element starts
 * its own rise-and-fade.
 */
@Composable
internal fun rememberEntranceProgress(delayMs: Int): State<Float> {
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

/** The three incoming-call looks. Anything else (including ids saved by older versions) maps to one of them. */
object IncomingCallStyles {
    const val CLEAN = "clean"
    const val CENTER = "center"
    const val SWIPE = "swipe"
    const val DEFAULT = SWIPE

    /**
     * Older builds saved "aurora", "orbit", "pulse", "classic", "hyper" or "ios". Those styles no
     * longer exist, so a saved value is mapped to the nearest current one instead of crashing or
     * silently showing something unexpected:
     *   classic  -> clean   (both are the plain white screen)
     *   hyper    -> center  (both are the big-name-on-top layout)
     *   orbit / pulse / aurora / ios / anything unknown -> the default (clean)
     */
    fun normalize(saved: String?): String = when (saved) {
        CLEAN, CENTER, SWIPE -> saved
        "classic" -> CLEAN
        "hyper" -> CENTER
        else -> DEFAULT
    }
}

/**
 * Public entry point - dispatches to whichever incoming-call look is selected in
 * Settings > Incoming call screen (AppSettings.incomingCallStyle). Every look shares the same
 * caller-info props and the same onAccept / onDecline / onQuickMessage callbacks, so
 * InCallActivity's call site never needs to know which one is active.
 *
 *  - "clean":  white; "Incoming call", round photo, big name and number, "I'm busy | Message",
 *              Decline / Accept at the bottom.
 *  - "center": soft grey-blue; big name on top, the photo in the middle, a "Reply with message"
 *              pill, Decline / Accept at the bottom.
 *  - "swipe":  the same calm look, answered by dragging a handle up.
 *
 * On a dark theme with [glass] on, all three switch to a dark frosted-glass look; with [glass] off
 * they stay white/light on every theme.
 */
@Composable
fun IncomingCallScreen(
    callerName: String,
    callerNumber: String,
    spamAssessment: SpamAssessment? = null,
    callerPhotoUri: String? = null,
    isSavedContact: Boolean = false,
    style: String = IncomingCallStyles.DEFAULT,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onQuickMessage: () -> Unit = {},
    modifier: Modifier = Modifier,
    // Used only by the style picker to show the real screen at a small size
    // (no entrance animation, no window-inset padding, every button a no-op).
    isPreview: Boolean = false,
    // Dark-theme glass look on/off (see the class comment). Ignored on light themes.
    glass: Boolean = true,
    // When null the current theme decides (dark theme -> dark look). The picker passes a value so it
    // can show both looks side by side.
    forceDark: Boolean? = null,
    // Only the Swipe style's pulsing glow behind the photo reads this - Clean and Center have
    // no such animation, so they ignore it.
    avatarPulseEnabled: Boolean = true
) {
    val dark = forceDark ?: LocalDialerPalette.current.isDark
    when (IncomingCallStyles.normalize(style)) {
        IncomingCallStyles.CENTER -> CenterIncomingCallScreen(
            callerName = callerName, callerNumber = callerNumber, spamAssessment = spamAssessment,
            callerPhotoUri = callerPhotoUri, isSavedContact = isSavedContact,
            onAccept = onAccept, onDecline = onDecline, onQuickMessage = onQuickMessage, modifier = modifier,
            isPreview = isPreview, dark = dark, glass = glass
        )
        IncomingCallStyles.SWIPE -> SwipeIncomingCallScreen(
            callerName = callerName, callerNumber = callerNumber, spamAssessment = spamAssessment,
            callerPhotoUri = callerPhotoUri, isSavedContact = isSavedContact,
            onAccept = onAccept, onDecline = onDecline, onQuickMessage = onQuickMessage, modifier = modifier,
            isPreview = isPreview, dark = dark, glass = glass, avatarPulseEnabled = avatarPulseEnabled
        )
        else -> CleanIncomingCallScreen(
            callerName = callerName, callerNumber = callerNumber, spamAssessment = spamAssessment,
            callerPhotoUri = callerPhotoUri, isSavedContact = isSavedContact,
            onAccept = onAccept, onDecline = onDecline, onQuickMessage = onQuickMessage, modifier = modifier,
            isPreview = isPreview, dark = dark, glass = glass
        )
    }
}

@Composable
internal fun QuickActionsPill(onQuickMessage: () -> Unit, modifier: Modifier = Modifier, lightMode: Boolean = false) {
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

internal fun Modifier.graphicsLayerAlphaRise(progress: Float): Modifier = this.graphicsLayer {
    alpha = progress
    translationY = (1f - progress) * 14.dp.toPx()
}

@Composable
internal fun SpamBadge(modifier: Modifier = Modifier) {
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
