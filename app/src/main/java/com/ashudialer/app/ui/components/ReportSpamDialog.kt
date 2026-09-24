package com.ashudialer.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.theme.LocalDialerPalette

/**
 * A confirmation step before a number is added to the person's own
 * reported-spam list. "Report spam" previously did this with no
 * confirmation at all - a single mis-tap silently blocked the number with
 * no way to see what had happened afterward. This adds the confirmation
 * that action deserves (it's flagging a real person/business for this
 * person's own future reference, not just a local mute) plus an optional
 * one-line reason, without turning a should-be-quick action into a form:
 * the reason field is entirely optional and submitting with it blank is
 * still a single tap on "Report".
 */
@Composable
fun ReportSpamDialog(
    phoneNumber: String,
    onDismiss: () -> Unit,
    onConfirm: (reason: String) -> Unit
) {
    val palette = LocalDialerPalette.current
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = palette.danger) },
        title = { Text("Report $phoneNumber as spam?") },
        text = {
            Column {
                Text(
                    "This adds it to your Reported Numbers list so you have a record of it, and so you'll be warned if it calls again. It does not block the number unless you also choose to block it.",
                    fontSize = 13.5.sp,
                    color = palette.textSecondary
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    placeholder = { Text("e.g. Fake bank call, robocall") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.cardBorder,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        cursorColor = palette.accent
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim()) }) {
                Text("Report", color = palette.danger)
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
