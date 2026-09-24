package com.ashudialer.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.db.ReportedSpamEntity
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.components.glassCircle
import com.ashudialer.app.ui.theme.LocalDialerPalette
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The other half of "Report spam" actually doing something - a place to
 * see everything reported, when, and why, and to undo a report or also
 * block the number from the same row. Reporting and blocking stay
 * independent lists (see ReportedSpamEntity's doc comment), so a number
 * here isn't necessarily also on Blocked Numbers, and this screen's
 * "Block" action is an explicit opt-in per number rather than an implied
 * side effect of reporting.
 */
@Composable
fun ReportedSpamScreen(
    reportedNumbers: List<ReportedSpamEntity>,
    isBlocked: (String) -> Boolean,
    onBack: () -> Unit,
    onUnreport: (ReportedSpamEntity) -> Unit,
    onBlock: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.glassCircle(palette)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Reported Numbers", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
            if (reportedNumbers.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(72.dp).glassCircle(palette, tintAlpha = 0.55f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = palette.accent, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("No reported numbers", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Numbers you report as spam from a call or contact's detail screen show up here",
                        fontSize = 12.5.sp,
                        color = palette.textSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Text(
                    "${reportedNumbers.size} reported", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = palette.textSecondary, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
                Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp)) {
                    reportedNumbers.forEachIndexed { index, entry ->
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.phoneNumber, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
                                    if (entry.reason.isNotBlank()) {
                                        Text(entry.reason, fontSize = 12.5.sp, color = palette.textSecondary)
                                    }
                                    Text(
                                        dateFormat.format(java.util.Date(entry.reportedAtMillis)),
                                        fontSize = 11.5.sp,
                                        color = palette.textSecondary.copy(alpha = 0.8f)
                                    )
                                }
                                if (!isBlocked(entry.phoneNumber)) {
                                    IconButton(onClick = { onBlock(entry.phoneNumber) }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Filled.Block, contentDescription = "Also block", tint = palette.danger, modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(onClick = { onUnreport(entry) }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove report", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        if (index != reportedNumbers.lastIndex) {
                            HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}
