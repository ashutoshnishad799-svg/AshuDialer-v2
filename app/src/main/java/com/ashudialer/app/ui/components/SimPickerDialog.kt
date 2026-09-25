package com.ashudialer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.telecom.SimAccount
import com.ashudialer.app.ui.theme.LocalDialerPalette

/**
 * Shown before placing an outgoing call on a dual-SIM device when the
 * number being dialed has no saved routing rule (SimRoutingScreen) telling
 * the app which SIM to use. Previously there was no picker on this path at
 * all - placeCallDirect's `handle` resolved straight to null whenever no
 * rule existed, silently handing the choice to whichever SIM the OS
 * defaults to, with the person never seeing a prompt or getting a say.
 * This is that missing prompt: pick a SIM, place the call with it, and
 * optionally remember the choice as this number's routing rule (via
 * "Always use for this number") or as the device-wide default SIM (via
 * "Use as default, don't ask again") so this doesn't have to be repeated
 * every single time for someone who'd rather set it once.
 */
@Composable
fun SimPickerDialog(
    phoneNumber: String,
    sims: List<SimAccount>,
    preselectedHandleId: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (sim: SimAccount, rememberForNumber: Boolean, setAsDefault: Boolean) -> Unit
) {
    val palette = LocalDialerPalette.current
    var selected by remember {
        mutableStateOf(sims.firstOrNull { it.handle.id == preselectedHandleId } ?: sims.firstOrNull())
    }
    var rememberForNumber by remember { mutableStateOf(false) }
    var setAsDefault by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.SimCard, contentDescription = null, tint = palette.accent) },
        title = { Text("Call with which SIM?") },
        text = {
            Column {
                Text(
                    "Calling $phoneNumber",
                    fontSize = 13.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                sims.forEach { sim ->
                    val isSelected = selected?.handle == sim.handle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) palette.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable {
                                selected = sim
                                // A previously-remembered choice for a different SIM no
                                // longer makes sense once the person picks a different one.
                                rememberForNumber = false
                                setAsDefault = false
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.SimCard, contentDescription = null, tint = if (isSelected) palette.accent else palette.textSecondary, modifier = Modifier.size(18.dp))
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 10.dp))
                        Text(sim.label, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary, modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = palette.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
                CheckRow(
                    label = "Always use this SIM for $phoneNumber",
                    checked = rememberForNumber,
                    onCheckedChange = { rememberForNumber = it; if (it) setAsDefault = false },
                    palette = palette
                )
                CheckRow(
                    label = "Use as my default SIM, don't ask again",
                    checked = setAsDefault,
                    onCheckedChange = { setAsDefault = it; if (it) rememberForNumber = false },
                    palette = palette
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let { onConfirm(it, rememberForNumber, setAsDefault) } },
                enabled = selected != null
            ) {
                Text("Call", color = palette.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textSecondary) }
        },
        containerColor = palette.cardBackground,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary
    )
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    palette: com.ashudialer.app.ui.theme.DialerPalette
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = palette.accent)
        )
        Text(label, fontSize = 12.5.sp, color = palette.textSecondary, modifier = Modifier.weight(1f))
    }
}
