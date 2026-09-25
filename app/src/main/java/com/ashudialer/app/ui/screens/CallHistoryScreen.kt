package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.Contact
import com.ashudialer.app.data.SystemCallLogEntry
import com.ashudialer.app.data.db.CallDirection
import com.ashudialer.app.ui.components.Avatar
import com.ashudialer.app.ui.theme.LocalDialerPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallHistoryScreen(
    contact: Contact,
    history: List<SystemCallLogEntry>,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onDeleteEntry: (SystemCallLogEntry) -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    Column(modifier = modifier.fillMaxSize().background(palette.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Call history", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text(contact.displayName, fontSize = 13.sp, color = palette.textSecondary)
            }
            if (history.isNotEmpty()) {
                IconButton(onClick = onDeleteAll) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete call history", tint = palette.danger)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(name = contact.displayName, photoUri = contact.photoUri, size = 54.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.displayName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Text(contact.phoneNumber, fontSize = 13.sp, color = palette.textSecondary)
            }
            FilledTonalButton(onClick = onCall, shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Call")
            }
        }

        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No call history found", color = palette.textSecondary, fontSize = 15.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history.size) { index ->
                    val entry = history[index]
                    val directionIcon = when (entry.direction) {
                        CallDirection.INCOMING, CallDirection.MISSED -> Icons.Filled.CallReceived
                        else -> Icons.Filled.CallMade
                    }
                    val directionText = when (entry.direction) {
                        CallDirection.INCOMING -> "Incoming"
                        CallDirection.OUTGOING -> "Outgoing"
                        CallDirection.MISSED -> "Missed"
                        CallDirection.REJECTED -> "Rejected"
                    }
                    Surface(
                        color = palette.cardBackground,
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(directionIcon, contentDescription = null, tint = if (entry.direction == CallDirection.MISSED) palette.danger else palette.accent, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(directionText, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                                Text(formatHistoryDate(entry.timestampMillis), fontSize = 12.5.sp, color = palette.textSecondary)
                            }
                            Text(formatDuration(entry.durationSeconds), fontSize = 13.sp, color = palette.textSecondary)
                            IconButton(onClick = { onDeleteEntry(entry) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete call", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatHistoryDate(timestamp: Long): String =
    SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(timestamp))

private fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "—"
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
