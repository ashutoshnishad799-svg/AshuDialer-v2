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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.components.glassCard

private data class FaqItem(val question: String, val answer: String)

private const val INSTAGRAM_USER = "ashutosh_07x"
private const val TELEGRAM_USER = "ashutosh_07x"
private const val TELEGRAM_CHANNEL = "ashuapps_07x"

/**
 * Opens [webUrl] in the dedicated app when possible ([appPackage] + [appUri]),
 * otherwise in the browser. Going through ACTION_VIEW with an explicit package
 * avoids the blank/black screen some phones show when a generic openUri() hands
 * an instagram.com link to a half-initialised in-app browser tab.
 */
private fun openExternal(context: android.content.Context, appPackage: String, appUri: String, webUrl: String) {
    val nativeIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(appUri))
        .setPackage(appPackage)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(webUrl))
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(nativeIntent)
    } catch (_: Throwable) {
        try {
            context.startActivity(webIntent)
        } catch (_: Throwable) {
            android.widget.Toast.makeText(context, "Couldn't open the link", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun SupportLinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val palette = LocalDialerPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .glassCard(palette, 14.dp)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
            Text(subtitle, fontSize = 12.5.sp, color = palette.textSecondary)
        }
    }
}

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
        "Tap \"Send feedback\" below, describe what happened, and tap Send - your app version, phone model, Android version and your most recent crash report (if the app has crashed) are attached automatically, and it goes straight to the developer with no extra app to pick."
    )
)

@Composable
fun HelpFeedbackScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current
    var showFeedbackDialog = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    if (showFeedbackDialog.value) {
        com.ashudialer.app.ui.components.SendFeedbackDialog(onDismiss = { showFeedbackDialog.value = false })
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
            SupportLinkRow(
                icon = Icons.Filled.BugReport,
                title = "Send feedback",
                subtitle = "Type what happened - sent directly, no app to pick"
            ) {
                showFeedbackDialog.value = true
            }
            SupportLinkRow(
                icon = Icons.Filled.Send,
                title = "Message me on Telegram",
                subtitle = "@$TELEGRAM_USER - fastest reply"
            ) {
                openExternal(context, "org.telegram.messenger", "tg://resolve?domain=$TELEGRAM_USER", "https://t.me/$TELEGRAM_USER")
            }
            SupportLinkRow(
                icon = Icons.Filled.CameraAlt,
                title = "Instagram",
                subtitle = "@$INSTAGRAM_USER"
            ) {
                openExternal(context, "com.instagram.android", "https://instagram.com/_u/$INSTAGRAM_USER", "https://instagram.com/$INSTAGRAM_USER")
            }
            SupportLinkRow(
                icon = Icons.Filled.Campaign,
                title = "Telegram support channel",
                subtitle = "Updates, releases and help - t.me/$TELEGRAM_CHANNEL"
            ) {
                openExternal(context, "org.telegram.messenger", "tg://resolve?domain=$TELEGRAM_CHANNEL", "https://t.me/$TELEGRAM_CHANNEL")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
