package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.SystemCallLogEntry
import com.ashudialer.app.data.db.CallDirection
import com.ashudialer.app.ui.components.Avatar
import com.ashudialer.app.ui.components.glassCircle
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette

/**
 * Detail screen for a phone number with no matching saved contact -
 * previously, tapping an unsaved number's avatar in Recents jumped straight
 * to the "New Contact" form dialog, with no intermediate page. This mirrors
 * ContactDetailScreen's visual language but adds a few things that are
 * specifically useful for a number you *haven't* saved yet, using only data
 * already on-device (call history, notes) rather than a network caller-ID
 * lookup, which would mean sending the person's call activity to a third
 * party and a dependency this app doesn't otherwise have:
 *
 * - A call-pattern insight card: flags the "many short/missed calls, never
 *   answered" shape that's a much stronger real-world spam signal than any
 *   single call being missed once.
 * - A quick note, saved against the raw number (not a contact record), so
 *   "delivery guy", "wrong number - bank" etc. can be jotted down without
 *   committing to saving them as a full contact.
 */
@Composable
fun UnknownNumberDetailScreen(
    phoneNumber: String,
    displayLabel: String,
    isBlocked: Boolean,
    callHistory: List<SystemCallLogEntry>,
    noteText: String,
    onNoteChange: (String) -> Unit,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onAddToContacts: () -> Unit,
    onToggleBlock: () -> Unit,
    onReportSpam: () -> Unit,
    onDeleteSingleCall: (SystemCallLogEntry) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val insight = remember(callHistory) { callPatternInsight(callHistory) }

    Column(modifier = modifier.fillMaxSize().background(palette.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Avatar(name = "", photoUri = null, size = 116.dp)

                    Spacer(Modifier.height(14.dp))
                    Text(
                        displayLabel,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary,
                        maxLines = 1
                    )

                    Spacer(Modifier.height(4.dp))
                    Surface(color = palette.accentSoft, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            "Not saved",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.accent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        GlassActionCircle(icon = Icons.Filled.Message, label = "Message", palette = palette, onClick = onMessage)
                        GlassActionCircle(icon = Icons.Filled.Call, label = "Call", palette = palette, onClick = onCall)
                        GlassActionCircle(icon = Icons.Filled.PersonAdd, label = "Add", palette = palette, onClick = onAddToContacts)
                    }
                }
            }

            if (insight != null) {
                item {
                    CallPatternInsightCard(insight = insight, palette = palette, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
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
                            Text(
                                text = "Call History (${callHistory.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textSecondary,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                            )
                            callHistory.forEachIndexed { index, entry ->
                                CallHistoryRow(entry = entry, palette = palette, onDelete = { onDeleteSingleCall(entry) })
                                if (index != callHistory.lastIndex) {
                                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }

            item {
                GlassCard(palette = palette, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SimpleRow(label = "Add to contacts", palette = palette, onClick = onAddToContacts)
                }
            }

            item {
                GlassCard(palette = palette, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column {
                        SimpleRow(
                            label = if (isBlocked) "Unblock this number" else "Block this number",
                            palette = palette,
                            destructive = !isBlocked,
                            onClick = onToggleBlock
                        )
                        HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                        SimpleRow(label = "Report spam", palette = palette, destructive = true, onClick = onReportSpam)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private data class CallPatternInsight(
    val severity: InsightSeverity,
    val headline: String,
    val detail: String
)

private enum class InsightSeverity { CAUTION, INFO }

/**
 * Looks only at calls *from* this number (incoming/missed), not calls the
 * person made to it - a pattern of many short, unanswered calls from a
 * number is a much more reliable everyday spam signal than any single
 * missed call, and it's something this app can compute entirely from call
 * history already stored on-device, with no external reputation service or
 * network request involved.
 */
private fun callPatternInsight(history: List<SystemCallLogEntry>): CallPatternInsight? {
    val incoming = history.filter { it.direction == CallDirection.INCOMING || it.direction == CallDirection.MISSED || it.direction == CallDirection.REJECTED }
    if (incoming.size < 3) return null

    val missed = incoming.count { it.direction == CallDirection.MISSED || it.direction == CallDirection.REJECTED }
    val neverAnswered = incoming.none { it.direction == CallDirection.INCOMING && it.durationSeconds > 5 }
    val missedRatio = missed.toFloat() / incoming.size

    return when {
        neverAnswered && missedRatio >= 0.8f && incoming.size >= 3 -> CallPatternInsight(
            severity = InsightSeverity.CAUTION,
            headline = "Repeated missed calls, never answered",
            detail = "This number has called ${incoming.size} times and hasn't been answered for more than a few seconds - a common pattern for spam or robocalls."
        )
        incoming.size >= 5 -> CallPatternInsight(
            severity = InsightSeverity.INFO,
            headline = "Frequent caller",
            detail = "This number has called ${incoming.size} times."
        )
        else -> null
    }
}

@Composable
private fun CallPatternInsightCard(insight: CallPatternInsight, palette: DialerPalette, modifier: Modifier = Modifier) {
    val (bg, fg, icon) = when (insight.severity) {
        InsightSeverity.CAUTION -> Triple(palette.danger.copy(alpha = 0.12f), palette.danger, Icons.Filled.WarningAmber)
        InsightSeverity.INFO -> Triple(palette.accentSoft, palette.accent, Icons.Filled.Shield)
    }
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = bg) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(insight.headline, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg)
                Spacer(Modifier.height(2.dp))
                Text(insight.detail, fontSize = 12.5.sp, color = fg.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun GlassActionCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    palette: DialerPalette,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .glassCircle(palette)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = palette.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, color = palette.textSecondary)
    }
}
