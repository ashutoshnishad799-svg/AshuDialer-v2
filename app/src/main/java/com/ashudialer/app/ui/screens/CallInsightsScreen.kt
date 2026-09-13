package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.CallInsights
import com.ashudialer.app.data.DailyCallSummary
import com.ashudialer.app.data.ContactTimeShare
import com.ashudialer.app.data.InsightsPeriod
import com.ashudialer.app.ui.components.Avatar
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.components.glassCard

@Composable
fun CallInsightsScreen(
    insights: CallInsights?,
    period: InsightsPeriod,
    onPeriodChange: (InsightsPeriod) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    var selectedDay by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<DailyCallSummary?>(null) }

    if (selectedDay != null) {
        DayInsightDetailScreen(summary = selectedDay!!, onBack = { selectedDay = null }, modifier = modifier)
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = palette.textPrimary) }
            Column(Modifier.weight(1f)) {
                Text("Call Insights", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                Text("Your calling pattern at a glance", fontSize = 12.5.sp, color = palette.textSecondary)
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodChip("Week", period == InsightsPeriod.WEEK, palette) { onPeriodChange(InsightsPeriod.WEEK) }
            PeriodChip("Month", period == InsightsPeriod.MONTH, palette) { onPeriodChange(InsightsPeriod.MONTH) }
        }

        if (insights == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = palette.accent) }
            return@Column
        }

        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Spacer(Modifier.height(12.dp))
                // Large hero card: the visual hierarchy now matches a full-screen analytics page instead of a stack of small cards.
                Column(Modifier.fillMaxWidth().glassCard(palette, 24.dp).padding(20.dp)) {
                    Text("${insights.totalCalls} calls", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                    Text("${formatDurationCompact(insights.totalTalkTimeSeconds)} total talk time", fontSize = 14.sp, color = palette.textSecondary)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniMetric("Incoming", insights.incomingCalls.toString(), Icons.Filled.CallReceived, palette.accent, palette, Modifier.weight(1f))
                        MiniMetric("Outgoing", insights.outgoingCalls.toString(), Icons.Filled.CallMade, palette.accent, palette, Modifier.weight(1f))
                        MiniMetric("Missed", insights.missedCalls.toString(), Icons.Filled.CallMissed, if (insights.missedCalls > 0) palette.danger else palette.textSecondary, palette, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Average call", formatDurationCompact(insights.averageCallSeconds), palette, modifier = Modifier.weight(1f))
                    StatCard("Longest call", formatDurationCompact(insights.longestCallSeconds), palette, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                SectionTitle("Daily activity", "Tap a day for the complete breakdown", palette)
                Spacer(Modifier.height(8.dp))
                DayOverDayChart(insights.dailySummaries, palette) { selectedDay = it }
                Spacer(Modifier.height(12.dp))
            }

            item {
                Column(Modifier.fillMaxWidth().glassCard(palette, 20.dp).padding(vertical = 4.dp)) {
                    insights.dailySummaries.asReversed().forEachIndexed { index, summary ->
                        DaySummaryRow(summary, palette) { selectedDay = summary }
                        if (index != insights.dailySummaries.lastIndex) HorizontalDivider(color = palette.cardBorder)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (insights.topContactsByTime.isNotEmpty()) {
                item {
                    SectionTitle("People you talk to most", "Ranked by total talk time", palette)
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.fillMaxWidth().glassCard(palette, 20.dp)) {
                        val maxSeconds = insights.topContactsByTime.maxOf { it.totalSeconds }
                        insights.topContactsByTime.forEachIndexed { index, contact ->
                            TopContactRow(contact, maxSeconds, palette)
                            if (index != insights.topContactsByTime.lastIndex) HorizontalDivider(color = palette.cardBorder)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (insights.longestCallWith != null) {
                item {
                    Column(Modifier.fillMaxWidth().glassCard(palette, 20.dp).padding(18.dp)) {
                        Text("Longest conversation", fontSize = 12.5.sp, color = palette.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(name = insights.longestCallWith, photoUri = null, size = 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(insights.longestCallWith, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                                Text(formatDurationCompact(insights.longestCallSeconds), fontSize = 13.sp, color = palette.accent)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (insights.oneSidedCallers.isNotEmpty()) {
                item {
                    SectionTitle("One-sided calling", "People where calls only go one way", palette)
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.fillMaxWidth().glassCard(palette, 20.dp)) {
                        insights.oneSidedCallers.forEachIndexed { index, caller ->
                            OneSidedCallerRow(caller, palette)
                            if (index != insights.oneSidedCallers.lastIndex) HorizontalDivider(color = palette.cardBorder)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, palette: DialerPalette) {
    Column {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Text(subtitle, fontSize = 11.5.sp, color = palette.textSecondary)
    }
}

@Composable
private fun MiniMetric(label: String, value: String, icon: ImageVector, tint: Color, palette: DialerPalette, modifier: Modifier = Modifier) {
    Column(modifier.glassCard(palette, 16.dp).padding(11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(5.dp))
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
        Text(label, fontSize = 10.5.sp, color = palette.textSecondary)
    }
}

@Composable
private fun DaySummaryRow(summary: DailyCallSummary, palette: DialerPalette, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(summary.label, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            Text("${summary.totalCalls} calls · ${formatDurationCompact(summary.talkTimeSeconds)} talk time", fontSize = 11.5.sp, color = palette.textSecondary)
        }
        Text("${summary.incomingCalls} ↓  ${summary.outgoingCalls} ↑  ${summary.missedCalls} ✕", fontSize = 11.sp, color = palette.textSecondary)
    }
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, palette: DialerPalette, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) palette.accent else palette.cardBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else palette.textPrimary)
    }
}

@Composable
private fun StatCard(label: String, value: String, palette: DialerPalette, accentColor: Color? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.glassCard(palette, 16.dp).padding(16.dp)
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = accentColor ?: palette.textPrimary)
        Text(label, fontSize = 12.5.sp, color = palette.textSecondary)
    }
}

@Composable
private fun DirectionStat(
    icon: ImageVector,
    count: Int,
    label: String,
    tint: Color,
    palette: DialerPalette,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.glassCard(palette, 16.dp).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(6.dp))
        Text(count.toString(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Text(label, fontSize = 11.sp, color = palette.textSecondary)
    }
}

@Composable
private fun DayOverDayChart(summaries: List<DailyCallSummary>, palette: DialerPalette, onDayClick: (DailyCallSummary) -> Unit) {
    val maxCount = (summaries.maxOfOrNull { it.totalCalls } ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(palette, 16.dp)
            .padding(horizontal = 14.dp, vertical = 18.dp)
            .height(120.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        summaries.forEach { summary ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clickable { onDayClick(summary) }) {
                val count = summary.totalCalls
                val fraction = count.toFloat() / maxCount.toFloat()
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .fillMaxHeight(fraction.coerceAtLeast(0.04f))
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (count > 0) palette.accent else palette.cardBorder)
                )
                Spacer(Modifier.height(6.dp))
                Text(summary.label.substringBefore(','), fontSize = 9.sp, color = palette.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TopContactRow(contact: ContactTimeShare, maxSeconds: Int, palette: DialerPalette) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name = contact.displayName, photoUri = null, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.displayName, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary, maxLines = 1)
                Text("${contact.callCount} calls", fontSize = 11.5.sp, color = palette.textSecondary)
            }
            Text(formatDurationCompact(contact.totalSeconds), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = palette.accent)
        }
        Spacer(Modifier.height(8.dp))
        val fraction = if (maxSeconds > 0) contact.totalSeconds.toFloat() / maxSeconds.toFloat() else 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.cardBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(palette.accent)
            )
        }
    }
}

@Composable
private fun OneSidedCallerRow(caller: com.ashudialer.app.data.OneSidedCaller, palette: DialerPalette) {
    val isYouCalling = caller.direction == com.ashudialer.app.data.OneSidedDirection.YOU_ALWAYS_CALL
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name = caller.displayName, photoUri = null, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(caller.displayName, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary, maxLines = 1)
            Text(
                if (isYouCalling) "You've called ${caller.outgoingCount}× · they've never called back"
                else "They've called ${caller.incomingCount}× · you've never called back",
                fontSize = 11.5.sp, color = palette.textSecondary
            )
        }
        Icon(
            if (isYouCalling) Icons.Filled.CallMade else Icons.Filled.CallReceived,
            contentDescription = null,
            tint = palette.textSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun DayInsightDetailScreen(summary: DailyCallSummary, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalDialerPalette.current
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.Close, "Back", tint = palette.textPrimary) }
            Column(Modifier.weight(1f)) {
                Text(summary.label, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                Text("Complete daily call breakdown", fontSize = 12.5.sp, color = palette.textSecondary)
            }
        }

        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().glassCard(palette, 24.dp).padding(20.dp)) {
                    Text("${summary.totalCalls}", fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                    Text("Total calls", fontSize = 13.sp, color = palette.textSecondary)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Talk time", formatDurationCompact(summary.talkTimeSeconds), palette, modifier = Modifier.weight(1f))
                        StatCard("Missed", summary.missedCalls.toString(), palette, accentColor = if (summary.missedCalls > 0) palette.danger else null, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                Column(Modifier.fillMaxWidth().glassCard(palette, 20.dp).padding(8.dp)) {
                    LargeDirectionRow(Icons.Filled.CallReceived, "Incoming calls", summary.incomingCalls, palette.accent, palette)
                    HorizontalDivider(color = palette.cardBorder)
                    LargeDirectionRow(Icons.Filled.CallMade, "Outgoing calls", summary.outgoingCalls, palette.accent, palette)
                    HorizontalDivider(color = palette.cardBorder)
                    LargeDirectionRow(Icons.Filled.CallMissed, "Missed calls", summary.missedCalls, palette.danger, palette)
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                Column(Modifier.fillMaxWidth().glassCard(palette, 20.dp).padding(18.dp)) {
                    Text("Day snapshot", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    Text("Incoming + outgoing = ${summary.incomingCalls + summary.outgoingCalls} connected attempts", fontSize = 12.5.sp, color = palette.textSecondary)
                    Text("Missed = ${summary.missedCalls}", fontSize = 12.5.sp, color = palette.textSecondary, modifier = Modifier.padding(top = 5.dp))
                    Text("Talk time = ${formatDurationCompact(summary.talkTimeSeconds)}", fontSize = 12.5.sp, color = palette.textSecondary, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
    }
}

@Composable
private fun LargeDirectionRow(icon: ImageVector, label: String, count: Int, tint: Color, palette: DialerPalette) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(palette.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
        Text(count.toString(), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
    }
}

private fun formatDurationCompact(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}
