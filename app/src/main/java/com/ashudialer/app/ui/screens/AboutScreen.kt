package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.components.glassCard

private data class FeatureHighlight(val icon: ImageVector, val title: String, val description: String)

/**
 * Feature summary shown on the About screen. Includes the contact-detail
 * tap behavior explicitly per request - tapping a contact's avatar/name
 * anywhere in the app (Contacts, Recents, a call's caller ID) opens that
 * full contact detail screen (call/message/video/email shortcuts, notes,
 * call history, block/favorite) rather than just being a static photo.
 */
private val featureHighlights = listOf(
    FeatureHighlight(
        Icons.Filled.Contacts,
        "Tap any contact photo",
        "Opens their full profile — call, message, notes, call history, and more — from anywhere a name or photo appears."
    ),
    FeatureHighlight(
        Icons.Filled.Shield,
        "Spam protection",
        "Suspicious and unknown callers are flagged automatically, with an option to block."
    ),
    FeatureHighlight(
        Icons.Filled.FiberManualRecord,
        "Call recording",
        "Record calls on demand or automatically, with playback and sharing built in."
    ),
    FeatureHighlight(
        Icons.Filled.Palette,
        "Themes",
        "Gradient, solid, and dark palettes to match how you like your phone to look."
    )
)

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    versionName: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("About", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(palette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Phone, contentDescription = null, tint = palette.accent, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("Ashu Dialer", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Version $versionName", fontSize = 13.sp, color = palette.textSecondary)
            Spacer(Modifier.height(6.dp))
            Text("Created by Ashutosh Nishad", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.accent)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "WHAT'S INSIDE",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .glassCard(palette, 18.dp)
        ) {
            featureHighlights.forEachIndexed { index, feature ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(feature.icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(feature.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Spacer(Modifier.height(2.dp))
                        Text(feature.description, fontSize = 13.sp, color = palette.textSecondary, lineHeight = 18.sp)
                    }
                }
                if (index != featureHighlights.lastIndex) {
                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            "Made for calls, contacts, and everything around them.",
            fontSize = 12.sp,
            color = palette.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp)
        )
    }
}
