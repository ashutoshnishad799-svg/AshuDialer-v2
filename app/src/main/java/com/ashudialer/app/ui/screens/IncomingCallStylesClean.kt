package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.telecom.SpamAssessment
import com.ashudialer.app.ui.components.Avatar

/*
 * Three plain, non-animated-background incoming-call styles:
 *
 *   "classic" - white, minimal, like a stock Google/Samsung dialer.
 *   "hyper"   - soft light-grey/blue, big centered name, two round buttons
 *               (the layout of Xiaomi HyperOS / MIUI's own incoming screen).
 *   "ios"     - dark, big name at the top, Decline / Accept as two round buttons
 *               with labels, plus Remind Me / Message on top (iPhone layout).
 *
 * All three take exactly the same caller info and the same onAccept / onDecline /
 * onQuickMessage callbacks as the older styles, so InCallActivity never needs to know
 * which one is active. Colours are fixed per style (not taken from the app theme) on
 * purpose: each is meant to look like that system's screen, not like an app theme.
 *
 * [isPreview] is used by the style picker to show the exact same composable at a
 * small size: it turns off entrance animation and makes every button a no-op, so the
 * example in the picker is the real screen, not a separate mock that could drift.
 */

private val AcceptGreen = Color(0xFF34C759)
private val DeclineRed = Color(0xFFFF3B30)

/** "Mobile" / "Unknown number" line under the name, shared by the three styles. */
private fun subtitleFor(callerNumber: String, callerName: String, isSavedContact: Boolean): String =
    when {
        callerNumber.isBlank() -> "Unknown number"
        isSavedContact && callerName != callerNumber -> callerNumber
        else -> "Mobile"
    }

// ---------------------------------------------------------------------------------
// CLASSIC (white)
// ---------------------------------------------------------------------------------
@Composable
fun ClassicIncomingCallScreen(
    callerName: String,
    callerNumber: String,
    spamAssessment: SpamAssessment?,
    callerPhotoUri: String?,
    isSavedContact: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onQuickMessage: () -> Unit,
    modifier: Modifier = Modifier,
    isPreview: Boolean = false
) {
    val rise by rememberEntranceProgress(if (isPreview) 0 else 60)
    val p = if (isPreview) 1f else rise
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFFFFFF))
            .then(if (isPreview) Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars))
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.9f))
        Text("Incoming call", fontSize = 14.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.Medium,
            modifier = Modifier.graphicsLayerAlphaRise(p))
        Spacer(Modifier.height(20.dp))
        Avatar(name = callerName, photoUri = callerPhotoUri, size = 112.dp)
        Spacer(Modifier.height(18.dp))
        Text(
            callerName, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827),
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayerAlphaRise(p)
        )
        Spacer(Modifier.height(4.dp))
        Text(subtitleFor(callerNumber, callerName, isSavedContact), fontSize = 15.sp, color = Color(0xFF6B7280),
            modifier = Modifier.graphicsLayerAlphaRise(p))
        if (spamAssessment?.isLikelySpam == true) {
            Spacer(Modifier.height(14.dp))
            SpamBadge()
        }
        Spacer(Modifier.weight(1.1f))

        if (!isPreview) {
            QuickActionsPill(onQuickMessage = onQuickMessage, lightMode = true)
            Spacer(Modifier.height(28.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RoundCallButton(DeclineRed, Icons.Filled.CallEnd, "Decline", Color(0xFF111827), onDecline, size = 68.dp)
            RoundCallButton(AcceptGreen, Icons.Filled.Phone, "Accept", Color(0xFF111827), onAccept, size = 68.dp)
        }
        Spacer(Modifier.height(if (isPreview) 14.dp else 48.dp))
    }
}

// ---------------------------------------------------------------------------------
// HYPER (HyperOS / MIUI-like)
// ---------------------------------------------------------------------------------
@Composable
fun HyperIncomingCallScreen(
    callerName: String,
    callerNumber: String,
    spamAssessment: SpamAssessment?,
    callerPhotoUri: String?,
    isSavedContact: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onQuickMessage: () -> Unit,
    modifier: Modifier = Modifier,
    isPreview: Boolean = false
) {
    val rise by rememberEntranceProgress(if (isPreview) 0 else 60)
    val p = if (isPreview) 1f else rise
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFF4F6FA), Color(0xFFE6EBF5))))
            .then(if (isPreview) Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars))
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.7f))
        Text(
            callerName, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15181E),
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayerAlphaRise(p)
        )
        Spacer(Modifier.height(6.dp))
        Text(subtitleFor(callerNumber, callerName, isSavedContact), fontSize = 16.sp, color = Color(0xFF5B6270),
            modifier = Modifier.graphicsLayerAlphaRise(p))
        Spacer(Modifier.height(4.dp))
        Text("Incoming voice call", fontSize = 13.sp, color = Color(0xFF8A91A0), modifier = Modifier.graphicsLayerAlphaRise(p))
        if (spamAssessment?.isLikelySpam == true) {
            Spacer(Modifier.height(14.dp))
            SpamBadge()
        }
        Spacer(Modifier.weight(0.8f))
        Avatar(name = callerName, photoUri = callerPhotoUri, size = 132.dp)
        Spacer(Modifier.weight(1.4f))

        if (!isPreview) {
            // HyperOS puts the quick-reply chip above the two round buttons.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .clickable(onClick = onQuickMessage)
                    .padding(horizontal = 20.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Message, contentDescription = null, tint = Color(0xFF3B4252), modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reply with message", fontSize = 14.sp, color = Color(0xFF23283A), fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(28.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RoundCallButton(DeclineRed, Icons.Filled.CallEnd, "Decline", Color(0xFF23283A), onDecline, size = 72.dp)
            RoundCallButton(AcceptGreen, Icons.Filled.Phone, "Accept", Color(0xFF23283A), onAccept, size = 72.dp)
        }
        Spacer(Modifier.height(if (isPreview) 14.dp else 52.dp))
    }
}

// ---------------------------------------------------------------------------------
// IOS (iPhone-like)
// ---------------------------------------------------------------------------------
@Composable
fun IosIncomingCallScreen(
    callerName: String,
    callerNumber: String,
    spamAssessment: SpamAssessment?,
    callerPhotoUri: String?,
    isSavedContact: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onQuickMessage: () -> Unit,
    modifier: Modifier = Modifier,
    isPreview: Boolean = false
) {
    val rise by rememberEntranceProgress(if (isPreview) 0 else 60)
    val p = if (isPreview) 1f else rise
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3A3F4B), Color(0xFF14161B))))
            .then(if (isPreview) Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars))
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.55f))
        Avatar(name = callerName, photoUri = callerPhotoUri, size = 92.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            callerName, fontSize = 36.sp, fontWeight = FontWeight.Medium, color = Color.White,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayerAlphaRise(p)
        )
        Spacer(Modifier.height(4.dp))
        Text(subtitleFor(callerNumber, callerName, isSavedContact), fontSize = 17.sp, color = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.graphicsLayerAlphaRise(p))
        if (spamAssessment?.isLikelySpam == true) {
            Spacer(Modifier.height(14.dp))
            SpamBadge()
        }
        Spacer(Modifier.weight(1.6f))

        if (!isPreview) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IosTopAction(Icons.Filled.Notifications, "Remind Me", onQuickMessage)
                IosTopAction(Icons.Filled.Message, "Message", onQuickMessage)
            }
            Spacer(Modifier.height(36.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RoundCallButton(DeclineRed, Icons.Filled.CallEnd, "Decline", Color.White, onDecline, size = 72.dp)
            RoundCallButton(AcceptGreen, Icons.Filled.Phone, "Accept", Color.White, onAccept, size = 72.dp)
        }
        Spacer(Modifier.height(if (isPreview) 14.dp else 52.dp))
    }
}

// ---------------------------------------------------------------------------------
// shared bits
// ---------------------------------------------------------------------------------
@Composable
private fun RoundCallButton(
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    labelColor: Color,
    onClick: () -> Unit,
    size: Dp
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(size * 0.42f))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, color = labelColor.copy(alpha = 0.85f), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun IosTopAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
    }
}
