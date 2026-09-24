package com.ashudialer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.theme.LocalDialerPalette

/**
 * The default set of quick-reply texts shown when declining a call with a
 * message - "Reply with message" (the pill/panel on the incoming-call
 * screen) used to be entirely decorative: tapping it always sent the exact
 * same hardcoded "Can't talk right now, will call you back." regardless of
 * which of the two quick-action labels ("I'm busy" / "Message") the
 * person actually tapped, and there was no way to choose a different one.
 * Four short, genuinely distinct options rather than a longer list -
 * this is a screen shown for a few seconds while a call is still ringing,
 * so picking fast matters more than exhaustive coverage. Not user-
 * configurable (yet) to keep this fix scoped to "the feature actually
 * works" rather than growing into a full settings surface, but the list
 * lives in one place so that's a small follow-up if wanted later.
 */
val DefaultQuickReplyMessages = listOf(
    "Can't talk right now, will call you back.",
    "In a meeting, call you soon.",
    "Driving right now, will call back.",
    "On my way, will call you back."
)

/**
 * Shown when the person taps "Reply with message" / "I'm busy" on the
 * incoming-call screen. Picking a message declines the call and sends
 * that message (see InCallActivity's onQuickMessage) - dismissing the
 * dialog (back press, tapping outside) leaves the call ringing exactly as
 * it was, so a person who opens this by mistake can still just answer or
 * decline normally instead of being forced into one of the four options.
 */
@Composable
fun QuickReplyPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    messages: List<String> = DefaultQuickReplyMessages
) {
    val palette = LocalDialerPalette.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Message, contentDescription = null, tint = palette.accent) },
        title = { Text("Reply with message") },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Text(
                    "The call will be declined and this message sent to the caller.",
                    fontSize = 13.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                messages.forEach { message ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.accentSoft)
                            .clickable { onSelect(message) }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(message, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textSecondary) }
        },
        containerColor = palette.cardBackground,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary
    )
}
