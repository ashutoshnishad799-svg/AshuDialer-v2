package com.ashudialer.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.ReconnectSuggestion
import com.ashudialer.app.ui.theme.DialerPalette

/**
 * "You two used to talk a lot - haven't connected in N days" card at the top
 * of Recents. Surfaces ReconnectRepository's output (see its own doc
 * comment for the two-window comparison this is built from).
 *
 * Deliberately ONE suggestion, not a list: a single well-chosen prompt reads
 * as a considerate nudge; three or four stacked would read as the app
 * nagging, which cuts against what this is meant to do (make someone want to
 * open the app, not make them want to dismiss it). onDismiss persists past
 * this app session (see RecentsScreen's call site) so a "not this one"
 * answer sticks rather than reappearing every time Recents reopens.
 */
@Composable
fun ReconnectSuggestionCard(
    suggestion: ReconnectSuggestion,
    palette: DialerPalette,
    onCall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(palette, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name = suggestion.displayName, photoUri = suggestion.photoUri, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Haven't talked in ${suggestion.daysSinceLastCall} days",
                fontSize = 11.5.sp, color = palette.textSecondary
            )
            Text(
                suggestion.displayName,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onCall, modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)) {
            Icon(Icons.Filled.Call, contentDescription = "Call ${suggestion.displayName}", tint = palette.accent)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
        }
    }
}
