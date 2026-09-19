package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.components.glassCard

private data class FaqItem(val question: String, val answer: String)

private val faqs = listOf(
    FaqItem(
        "Why do I need to set this as my default dialer?",
        "Android requires a default dialer app to handle system-level calling features like showing the in-call screen and screening incoming numbers. Without it, calling won't work."
    ),
    FaqItem(
        "Why didn't an incoming call appear full screen?",
        "Make sure Ashu Dialer is your default phone app and that full-screen call notifications are allowed. Some phone brands also require background/autostart permission."
    ),
    FaqItem(
        "Where is my data stored?",
        "Your call history, contacts-related app data, notes and settings stay on the device by default. Optional cloud backup only runs when you enable it."
    ),
    FaqItem(
        "How do I report a problem?",
        "Send the app version, phone model and a short description of what happened. Screenshots or a short screen recording can make troubleshooting much faster."
    )
)

@Composable
fun HelpFeedbackScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val uriHandler = LocalUriHandler.current

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Help & Support", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 8.dp)
        ) {
            Text("FAQ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
            faqs.forEach { faq ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .glassCard(palette, 14.dp)
                        .padding(14.dp)
                ) {
                    Text(faq.question, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(faq.answer, fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 17.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Support", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(palette, 14.dp)
                    .clickable { uriHandler.openUri("https://instagram.com/ashtosh_07x") }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Contact support", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
                    Text("@ashtosh_07x", fontSize = 12.5.sp, color = palette.textSecondary)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
