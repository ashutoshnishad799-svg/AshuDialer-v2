package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.components.glassCard

private data class PolicySection(val heading: String, val body: String)

private val sections = listOf(
    PolicySection(
        "What this app accesses",
        "Ashu Dialer reads your call log, contacts, and phone state so it can " +
            "function as your dialer — showing recent calls, matching incoming numbers " +
            "to saved contacts, and letting you place calls. It requests microphone access " +
            "only if you turn on call recording."
    ),
    PolicySection(
        "Where your data lives",
        "By default, everything — call history, contacts cache, blocked numbers, and your " +
            "theme preference — stays on your device in a local, private database. Nothing is " +
            "sent anywhere unless you explicitly sign in and enable Cloud Backup."
    ),
    PolicySection(
        "Cloud Backup (optional)",
        "If you sign in with your Google account and turn on Cloud Backup, your call log, " +
            "contacts cache, blocked-number list, and settings are synced to your private " +
            "Firebase account storage so you can restore them on a new device. This is off by " +
            "default and only activates after you sign in and opt in."
    ),
    PolicySection(
        "Call recording (optional)",
        "If you enable call recording in Settings, recordings are saved locally to your " +
            "device only. They are never uploaded automatically. Call recording laws vary by " +
            "region — you're responsible for complying with local regulations, including " +
            "informing the other party where required."
    ),
    PolicySection(
        "Spam protection",
        "The Protect tab checks incoming numbers against a local block-list you control. " +
            "No call data is sent to a third-party spam-detection service."
    ),
    PolicySection(
        "What we don't do",
        "We don't sell your data. We don't share your call log or contacts with advertisers. " +
            "We don't run ads in this app."
    ),
    PolicySection(
        "Your control",
        "You can clear call history, remove blocked numbers, revoke permissions, or delete " +
            "your account and all associated cloud data at any time from Settings."
    ),
    PolicySection(
        "Contact",
        "Questions about this policy? Reach out via Instagram @ashtosh_07x."
    )
)

@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
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
            Text("Privacy Policy", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                "Last updated: August 2026",
                fontSize = 12.5.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(bottom = 18.dp)
            )

            sections.forEach { section ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(palette, 16.dp)
                        .padding(16.dp)
                ) {
                    Text(section.heading, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(section.body, fontSize = 13.5.sp, color = palette.textSecondary, lineHeight = 19.sp)
                }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
