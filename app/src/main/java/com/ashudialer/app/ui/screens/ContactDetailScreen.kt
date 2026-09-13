@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.ashudialer.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.Contact
import com.ashudialer.app.data.SystemCallLogEntry
import com.ashudialer.app.data.db.CallDirection
import com.ashudialer.app.ui.components.Avatar
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.components.glassCircle


@Composable
fun ContactDetailScreen(
    contact: Contact,
    isBlocked: Boolean,
    isFavorite: Boolean,
    videoCallAvailable: Boolean,
    hasEmail: Boolean = false,
    callHistory: List<SystemCallLogEntry>,
    noteText: String,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onWhatsApp: () -> Unit = {},
    onVideoCall: () -> Unit,
    onEmail: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onNoteChange: (String) -> Unit,
    onChangePhoto: () -> Unit,
    onShareContact: () -> Unit,
    onAddToHomeScreen: () -> Unit = {},
    onShareNumber: (String) -> Unit = {},
    onOpenCallHistory: () -> Unit = {},
    onDeleteCallHistory: () -> Unit,
    onDeleteSingleCall: (SystemCallLogEntry) -> Unit = {},
    onToggleBlock: () -> Unit,
    onOpenVibrationPattern: () -> Unit = {},
    onOpenSimRouting: () -> Unit = {},
    currentRingtoneLabel: String = "Default",
    onOpenRingtonePicker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current

    Box(modifier = modifier.fillMaxSize().background(palette.background)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                TopBar(onBack = onBack, palette = palette)
                HeaderSection(
                    contact = contact,
                    isFavorite = isFavorite,
                    videoCallAvailable = videoCallAvailable,
                    hasEmail = hasEmail,
                    palette = palette,
                    onCall = onCall,
                    onMessage = onMessage,
                    onVideoCall = onVideoCall,
                    onEmail = onEmail,
                    onChangePhoto = onChangePhoto,
                    onShareNumber = onShareNumber
                )
            }

            item {
                GlassCard(palette = palette, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    NoteField(noteText = noteText, onNoteChange = onNoteChange, palette = palette)
                }
            }

            if (callHistory.isNotEmpty()) {
                item {
                    GlassCard(palette = palette, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Column {
                            val latest = callHistory.first()
                            CallSummaryRow(entry = latest, palette = palette)
                            HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                            SimpleRow(
                                label = "View call history",
                                subtitle = "${callHistory.size} calls",
                                palette = palette,
                                onClick = onOpenCallHistory
                            )
                        }
                    }
                }
            }

            item {
                GlassCard(palette = palette, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column {
                        SimpleRow(label = "Share contact", palette = palette, onClick = onShareContact)
                        HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                        SimpleRow(label = "Add to Home Screen", palette = palette, onClick = onAddToHomeScreen)
                        HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                        SimpleRow(label = "Message on WhatsApp", palette = palette, onClick = onWhatsApp)
                        HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                        SimpleRow(
                            label = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            palette = palette,
                            onClick = onToggleFavorite
                        )
                    }
                }
            }

            item {
                GlassCard(palette = palette, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column {
                        SimpleRow(
                            label = "Ringtone",
                            subtitle = currentRingtoneLabel,
                            palette = palette,
                            onClick = onOpenRingtonePicker
                        )
                        HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                        SimpleRow(label = "Vibration pattern", palette = palette, onClick = onOpenVibrationPattern)
                        HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                        SimpleRow(label = "SIM for outgoing calls", palette = palette, onClick = onOpenSimRouting)
                    }
                }
            }

            if (callHistory.isNotEmpty()) {
                item {
                    GlassCard(palette = palette, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        SimpleRow(label = "Delete call history", palette = palette, destructive = true, onClick = onDeleteCallHistory)
                    }
                }
            }

            item {
                GlassCard(palette = palette, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SimpleRow(
                        label = if (isBlocked) "Unblock this caller" else "Block this caller",
                        palette = palette,
                        destructive = !isBlocked,
                        onClick = onToggleBlock
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, palette: DialerPalette) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(onClick = onBack, palette = palette) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(1.dp))
    }
}

@Composable
private fun HeaderSection(
    contact: Contact,
    isFavorite: Boolean,
    videoCallAvailable: Boolean,
    hasEmail: Boolean,
    palette: DialerPalette,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onVideoCall: () -> Unit,
    onEmail: () -> Unit,
    onChangePhoto: () -> Unit,
    onShareNumber: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    // Shows the "Share" chip for a few seconds right after a copy, then
    // auto-hides - a copy confirmation that doesn't need to be dismissed by
    // hand, matching how most system copy-toast affordances behave. Keyed
    // so re-copying the same number (e.g. copy, dismiss, copy again) still
    // restarts the timer instead of the second copy silently doing nothing
    // because the chip was "already showing."
    var copyConfirmationToken by remember { mutableStateOf(0) }
    var showShareChip by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(copyConfirmationToken) {
        if (copyConfirmationToken > 0) {
            showShareChip = true
            kotlinx.coroutines.delay(4000)
            showShareChip = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(modifier = Modifier.clickable(onClick = onChangePhoto)) {
                Avatar(name = contact.displayName, photoUri = contact.photoUri, size = 116.dp)
            }


            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(palette.accent)
                    .clickable(onClick = onChangePhoto),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            contact.displayName,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        // The phone number itself was never actually rendered here before -
        // only the name and the row of action icons below it - so there was
        // no way to see, let alone copy, the number from this screen at all.
        // combinedClickable's onLongClick (rather than a plain onClick) is
        // what triggers the copy, matching the long-press-to-copy gesture
        // used everywhere else on Android (e.g. copying a link from a
        // browser's address bar) rather than a single tap doing something
        // as consequential as writing to the clipboard.
        if (contact.phoneNumber.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                contact.phoneNumber,
                fontSize = 14.5.sp,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            clipboard.setText(AnnotatedString(contact.phoneNumber))
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            copyConfirmationToken += 1
                        }
                    )
            )

            AnimatedVisibility(
                visible = showShareChip,
                enter = fadeIn() + scaleIn(initialScale = 0.85f) + slideInVertically(initialOffsetY = { -it / 2 }),
                exit = fadeOut() + scaleOut(targetScale = 0.85f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.accentSoft)
                        .clickable {
                            showShareChip = false
                            onShareNumber(contact.phoneNumber)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = palette.accent, modifier = Modifier.size(12.dp))
                    Text(
                        "Copied",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.accent,
                        modifier = Modifier.padding(start = 5.dp, end = 10.dp)
                    )
                    Box(modifier = Modifier.size(1.dp, 12.dp).background(palette.accent.copy(alpha = 0.3f)))
                    Icon(
                        Icons.Filled.Share, contentDescription = "Share number", tint = palette.accent,
                        modifier = Modifier.size(12.dp).padding(start = 10.dp)
                    )
                    Text(
                        "Share",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.accent,
                        modifier = Modifier.padding(start = 5.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            GlassActionCircle(icon = Icons.Filled.Message, label = "Message", palette = palette, onClick = onMessage)
            GlassActionCircle(icon = Icons.Filled.Call, label = "Call", palette = palette, onClick = onCall)
            GlassActionCircle(
                icon = Icons.Filled.Videocam, label = "Video", palette = palette,
                enabled = videoCallAvailable, onClick = onVideoCall
            )
            GlassActionCircle(
                icon = Icons.Filled.Email, label = "Email", palette = palette,
                enabled = hasEmail, onClick = onEmail
            )
        }
    }
}

@Composable
private fun GlassActionCircle(
    icon: ImageVector,
    label: String,
    palette: DialerPalette,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.35f
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(palette.cardBackground.copy(alpha = palette.cardBackground.alpha * alpha))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = palette.textPrimary.copy(alpha = alpha), modifier = Modifier.size(22.dp))
    }
}


@Composable
internal fun GlassCard(palette: DialerPalette, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(palette, 20.dp)
    ) {
        content()
    }
}

@Composable
private fun GlassIconButton(onClick: () -> Unit, palette: DialerPalette, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .glassCircle(palette)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
internal fun NoteField(noteText: String, onNoteChange: (String) -> Unit, palette: DialerPalette) {
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text("Note", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.textSecondary)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = noteText,
            onValueChange = onNoteChange,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = palette.textPrimary),
            cursorBrush = Brush.verticalGradient(listOf(palette.accent, palette.accent)),
            decorationBox = { inner ->
                if (noteText.isEmpty()) {
                    Text("Add a note about this person…", fontSize = 15.sp, color = palette.textSecondary.copy(alpha = 0.7f))
                }
                inner()
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun SimpleRow(label: String, palette: DialerPalette, destructive: Boolean = false, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                label, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = if (destructive) palette.danger else palette.textPrimary
            )
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.5.sp, color = palette.textSecondary)
            }
        }
        Icon(
            Icons.Filled.ChevronRight, contentDescription = null,
            tint = (if (destructive) palette.danger else palette.textSecondary).copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun CallSummaryRow(entry: SystemCallLogEntry, palette: DialerPalette) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(directionLabel(entry.direction), fontSize = 14.sp, color = palette.textPrimary, fontWeight = FontWeight.Medium)
        Text(formatRelative(entry.timestampMillis), fontSize = 13.sp, color = palette.textSecondary)
    }
}

@Composable
internal fun CallHistoryRow(entry: SystemCallLogEntry, palette: DialerPalette, onDelete: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, tint) = when (entry.direction) {
            CallDirection.INCOMING -> Icons.Filled.CallReceived to palette.callGreen
            CallDirection.OUTGOING -> Icons.Filled.CallMade to palette.accent
            CallDirection.MISSED, CallDirection.REJECTED -> Icons.Filled.CallMissed to palette.danger
        }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(directionLabel(entry.direction), fontSize = 14.sp, color = palette.textPrimary)
            Text(formatRelative(entry.timestampMillis), fontSize = 12.sp, color = palette.textSecondary)
        }
        if (entry.durationSeconds > 0) {
            Text(formatCallDuration(entry.durationSeconds), fontSize = 12.sp, color = palette.textSecondary)
            Spacer(Modifier.width(10.dp))
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Delete this call",
                tint = palette.textSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun directionLabel(direction: CallDirection): String = when (direction) {
    CallDirection.INCOMING -> "Incoming call"
    CallDirection.OUTGOING -> "Outgoing call"
    CallDirection.MISSED -> "Missed call"
    CallDirection.REJECTED -> "Declined call"
}

private fun formatCallDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}


private fun formatRelative(millis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)

    val dayLabel = when {
        sameYear && dayDiff == 0 -> "Today"
        sameYear && dayDiff == 1 -> "Yesterday"
        sameYear && dayDiff in 2..6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(millis))
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
    }
    return "$dayLabel · $timeStr"
}
