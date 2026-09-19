package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.db.QuietHoursEntity
import com.ashudialer.app.ui.theme.LocalDialerPalette

/**
 * Smart Quiet Hours: a scheduled window where incoming calls silently go to
 * voicemail unless the caller is a starred favorite, or calls again within
 * a short window (repeat-caller bypass, in case it's urgent).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietHoursScreen(
    schedule: QuietHoursEntity,
    onBack: () -> Unit,
    onSave: (QuietHoursEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    var local by remember(schedule) { mutableStateOf(schedule) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    fun update(transform: (QuietHoursEntity) -> QuietHoursEntity) {
        local = transform(local)
        onSave(local)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Quiet Hours", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp)) {
            item {
                Spacer(Modifier.height(8.dp))
                Icon(Icons.Filled.Bedtime, contentDescription = null, tint = palette.accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Calls during this window quietly go to voicemail. Favorites still ring through, and anyone who calls twice in a row gets through too - just in case.",
                    fontSize = 13.5.sp, color = palette.textSecondary, lineHeight = 19.sp
                )
                Spacer(Modifier.height(18.dp))
            }

            item {
                SettingsCard(palette) {
                    ToggleRow(
                        title = "Enable Quiet Hours",
                        subtitle = null,
                        checked = local.enabled,
                        palette = palette,
                        onToggle = { update { it.copy(enabled = !it.enabled) } }
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            item {
                SectionLabel("Schedule", palette)
                SettingsCard(palette) {
                    TimeRow(
                        label = "Starts",
                        hour = local.startHour,
                        minute = local.startMinute,
                        palette = palette,
                        onClick = { showStartPicker = true }
                    )
                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                    TimeRow(
                        label = "Ends",
                        hour = local.endHour,
                        minute = local.endMinute,
                        palette = palette,
                        onClick = { showEndPicker = true }
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            item {
                SectionLabel("Who can still reach you", palette)
                SettingsCard(palette) {
                    ToggleRow(
                        title = "Favorites always ring through",
                        subtitle = "Starred contacts bypass Quiet Hours",
                        checked = local.allowFavorites,
                        palette = palette,
                        onToggle = { update { it.copy(allowFavorites = !it.allowFavorites) } }
                    )
                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                    ToggleRow(
                        title = "Let repeat callers through",
                        subtitle = "A second call within ${local.repeatCallerWindowMinutes} min rings through, in case it's urgent",
                        checked = local.allowRepeatCallerBypass,
                        palette = palette,
                        onToggle = { update { it.copy(allowRepeatCallerBypass = !it.allowRepeatCallerBypass) } }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showStartPicker) {
        TimePickerDialogHost(
            initialHour = local.startHour,
            initialMinute = local.startMinute,
            onDismiss = { showStartPicker = false },
            onConfirm = { h, m ->
                update { it.copy(startHour = h, startMinute = m) }
                showStartPicker = false
            }
        )
    }
    if (showEndPicker) {
        TimePickerDialogHost(
            initialHour = local.endHour,
            initialMinute = local.endMinute,
            onDismiss = { showEndPicker = false },
            onConfirm = { h, m ->
                update { it.copy(endHour = h, endMinute = m) }
                showEndPicker = false
            }
        )
    }
}

@Composable
private fun TimeRow(
    label: String,
    hour: Int,
    minute: Int,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
        Text(formatClock(hour, minute), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.accent)
    }
}

private fun formatClock(hour: Int, minute: Int): String {
    val period = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "%d:%02d %s".format(displayHour, minute, period)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialogHost(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = {
            TimePicker(state = state)
        }
    )
}
