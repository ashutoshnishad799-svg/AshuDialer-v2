package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneLocked
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.RecentCall
import com.ashudialer.app.data.db.CallDirection
import com.ashudialer.app.data.db.LockedNumberEntity
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette
import java.io.File
import com.ashudialer.app.ui.components.glassCircle

private enum class PrivateSpaceTab { NUMBERS, HISTORY, RECORDINGS }

@Composable
fun PrivateSpaceHomeScreen(
    lockedNumbers: List<LockedNumberEntity>,
    callHistory: List<RecentCall>,
    recordings: List<File>,
    onBack: () -> Unit,
    onCall: (String) -> Unit,
    onAddNumber: (phoneNumber: String, label: String) -> Unit,
    onRemoveNumber: (String) -> Unit,
    onShareRecording: (File) -> Unit,
    onDeleteRecording: (File) -> Unit,
    onMoveRecordingOut: (File) -> Unit,
    showRecordings: Boolean = true,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    var tab by remember { mutableStateOf(PrivateSpaceTab.NUMBERS) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.Lock, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Private Space", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Private Space settings", tint = palette.textSecondary, modifier = Modifier.size(20.dp))
            }
        }

        // A small, self-contained tab switcher rather than reusing the main
        // app's bottom nav - Private Space is deliberately a compact, separate
        // area (per request: "alag chhota call manager ho aur history ho"),
        // not a re-skin of the main Recents/Dialer/Contacts tab set.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TabChip("Numbers", Icons.Filled.PhoneLocked, tab == PrivateSpaceTab.NUMBERS, palette) { tab = PrivateSpaceTab.NUMBERS }
            TabChip("History", Icons.Filled.History, tab == PrivateSpaceTab.HISTORY, palette) { tab = PrivateSpaceTab.HISTORY }
            if (showRecordings) {
                TabChip("Recordings", Icons.Filled.FiberManualRecord, tab == PrivateSpaceTab.RECORDINGS, palette) { tab = PrivateSpaceTab.RECORDINGS }
            }
        }

        Spacer(Modifier.height(4.dp))

        when (tab) {
            PrivateSpaceTab.NUMBERS -> NumbersTab(
                lockedNumbers = lockedNumbers,
                palette = palette,
                onCall = onCall,
                onRemove = onRemoveNumber,
                onAddClick = { showAddDialog = true }
            )
            PrivateSpaceTab.HISTORY -> HistoryTab(callHistory = callHistory, palette = palette)
            PrivateSpaceTab.RECORDINGS -> if (showRecordings) RecordingsTab(
                recordings = recordings,
                palette = palette,
                onShare = onShareRecording,
                onDelete = onDeleteRecording,
                onMoveOut = onMoveRecordingOut
            ) else NumbersTab(
                lockedNumbers = lockedNumbers,
                palette = palette,
                onCall = onCall,
                onRemove = onRemoveNumber,
                onAddClick = { showAddDialog = true }
            )
        }
    }

    if (showAddDialog) {
        AddLockedNumberDialog(
            palette = palette,
            onDismiss = { showAddDialog = false },
            onConfirm = { number, label ->
                onAddNumber(number, label)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun TabChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, palette: DialerPalette, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) palette.accent else palette.cardBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) palette.solidBackground else palette.textSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = if (selected) palette.solidBackground else palette.textPrimary)
    }
}

@Composable
private fun NumbersTab(
    lockedNumbers: List<LockedNumberEntity>,
    palette: DialerPalette,
    onCall: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAddClick: () -> Unit
) {
    if (lockedNumbers.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.PhoneLocked,
            title = "No locked numbers yet",
            subtitle = "Add a number here to require this password before anyone can call it.",
            palette = palette,
            actionLabel = "Add a number",
            onAction = onAddClick
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.accentSoft)
                    .clickable(onClick = onAddClick)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("+ Add a number", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.accent)
            }
        }
        items(lockedNumbers, key = { it.phoneNumber }) { entry ->
            LockedNumberRow(entry = entry, palette = palette, onCall = { onCall(entry.phoneNumber) }, onRemove = { onRemove(entry.phoneNumber) })
            HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun LockedNumberRow(entry: LockedNumberEntity, palette: DialerPalette, onCall: () -> Unit, onRemove: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).glassCircle(palette),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.displayLabel.ifBlank { entry.phoneNumber },
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary, maxLines = 1
            )
            if (entry.displayLabel.isNotBlank()) {
                Text(entry.phoneNumber, fontSize = 12.5.sp, color = palette.textSecondary)
            }
        }
        IconButton(onClick = onCall) {
            Icon(Icons.Filled.Phone, contentDescription = "Call", tint = palette.accent, modifier = Modifier.size(19.dp))
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = palette.textSecondary, modifier = Modifier.size(19.dp))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Remove lock", color = palette.danger) },
                    leadingIcon = { Icon(Icons.Filled.LockOpen, contentDescription = null, tint = palette.danger) },
                    onClick = { menuExpanded = false; onRemove() }
                )
            }
        }
    }
}

@Composable
private fun HistoryTab(callHistory: List<RecentCall>, palette: DialerPalette) {
    if (callHistory.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.History,
            title = "No call history yet",
            subtitle = "Calls to and from your locked numbers will show up here.",
            palette = palette
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(callHistory, key = { "${it.phoneNumber}-${it.timestampMillis}" }) { call ->
            HistoryRow(call = call, palette = palette)
            HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun HistoryRow(call: RecentCall, palette: DialerPalette) {
    val (icon, tint) = when (call.direction) {
        CallDirection.INCOMING -> Icons.Filled.CallReceived to palette.textSecondary
        CallDirection.OUTGOING -> Icons.Filled.CallMade to palette.textSecondary
        CallDirection.MISSED -> Icons.Filled.CallMissed to palette.danger
        CallDirection.REJECTED -> Icons.Filled.CallMissed to palette.danger
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(call.displayName.ifBlank { call.phoneNumber }, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
            Text(formatHistoryTimestamp(call.timestampMillis), fontSize = 12.sp, color = palette.textSecondary)
        }
    }
}

@Composable
private fun RecordingsTab(
    recordings: List<File>,
    palette: DialerPalette,
    onShare: (File) -> Unit,
    onDelete: (File) -> Unit,
    onMoveOut: (File) -> Unit
) {
    if (recordings.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.FiberManualRecord,
            title = "No recordings here yet",
            subtitle = "Select recordings in My Recordings and move them here to keep them private.",
            palette = palette
        )
        return
    }

    // Only one recording is open (and playing) at a time, so two never play over each other. A file that
    // disappears from the list (deleted, or moved out) closes itself: expandedPath simply stops matching.
    var expandedPath by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var shareCandidate by remember { mutableStateOf<File?>(null) }

    shareCandidate?.let { file ->
        // Sharing hands the audio to another app, which is exactly what Private Space exists to prevent,
        // so it is never one tap away.
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { shareCandidate = null },
            title = { Text("Share this recording?") },
            text = { Text("It will leave Private Space and go to the app you pick. That app may keep a copy.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { shareCandidate = null; onShare(file) }) { Text("Share") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { shareCandidate = null }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(recordings, key = { it.absolutePath }) { file ->
            val expanded = expandedPath == file.absolutePath
            PrivateRecordingRow(
                file = file,
                palette = palette,
                expanded = expanded,
                isPlaying = expanded && isPlaying,
                onIsPlayingChange = { isPlaying = it },
                // Tapping opens the built-in player right here. It never starts another app.
                onToggle = {
                    if (expanded) { expandedPath = null; isPlaying = false }
                    else { expandedPath = file.absolutePath; isPlaying = false }
                },
                onShare = { shareCandidate = file },
                onDelete = { if (expanded) { expandedPath = null; isPlaying = false }; onDelete(file) },
                onMoveOut = { if (expanded) { expandedPath = null; isPlaying = false }; onMoveOut(file) }
            )
            HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun PrivateRecordingRow(
    file: File,
    palette: DialerPalette,
    expanded: Boolean,
    isPlaying: Boolean,
    onIsPlayingChange: (Boolean) -> Unit,
    onToggle: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onMoveOut: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val displayName = remember(file.name) {
        // Recording filenames look like "<label>_<yyyyMMdd_HHmmss>.m4a" (see CallRecorder.start) - strip the
        // extension and timestamp suffix for a cleaner row title.
        file.nameWithoutExtension.substringBeforeLast('_').ifBlank { file.nameWithoutExtension }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(palette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (expanded && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (expanded) "Close player" else "Play",
                    tint = palette.accent, modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(displayName, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Share", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = palette.textSecondary, modifier = Modifier.size(19.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Move out of Private Space") },
                        leadingIcon = { Icon(Icons.Filled.LockOpen, contentDescription = null, tint = palette.textPrimary) },
                        onClick = { menuExpanded = false; onMoveOut() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = palette.danger) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = palette.danger) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
        if (expanded) {
            // The same built-in player the Recordings screen uses (seek bar, play / pause, duration).
            // The file plays straight from Private Space's own folder and is never handed to another app.
            InlineAudioPlayer(
                file = file,
                palette = palette,
                isPlaying = isPlaying,
                onIsPlayingChange = onIsPlayingChange
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    palette: DialerPalette,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(64.dp).glassCircle(palette),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, fontSize = 13.sp, color = palette.textSecondary, textAlign = TextAlign.Center, lineHeight = 18.sp)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.accent)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(actionLabel, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = palette.solidBackground)
            }
        }
    }
}

@Composable
private fun AddLockedNumberDialog(
    palette: DialerPalette,
    onDismiss: () -> Unit,
    onConfirm: (phoneNumber: String, label: String) -> Unit
) {
    var number by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add locked number") },
        text = {
            Column {
                Text("Anyone calling this number will need your Private Space password first.", fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 17.sp)
                Spacer(Modifier.height(14.dp))
                DialogField(value = number, onValueChange = { number = it.filter { c -> c.isDigit() || c == '+' } }, placeholder = "Phone number", keyboardType = KeyboardType.Phone, palette = palette)
                Spacer(Modifier.height(8.dp))
                DialogField(value = label, onValueChange = { label = it }, placeholder = "Label (optional)", keyboardType = KeyboardType.Text, palette = palette)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (number.isNotBlank()) onConfirm(number, label) },
                enabled = number.isNotBlank()
            ) { Text("Add", color = palette.accent) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DialogField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    palette: DialerPalette
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.searchBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = palette.textSecondary, fontSize = 13.5.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = palette.textPrimary, fontSize = 13.5.sp),
            cursorBrush = SolidColor(palette.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatHistoryTimestamp(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}
