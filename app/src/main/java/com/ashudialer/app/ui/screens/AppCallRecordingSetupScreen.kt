package com.ashudialer.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ashudialer.app.appcalls.recording.RecordingPrefs
import com.ashudialer.app.telecom.RecordingSetupChecker
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.theme.LocalDialerPalette

/**
 * WhatsApp / Telegram call recording. Both need the same two things as normal call recording
 * (Shizuku running + allowed) plus Notification Access, which is how the app notices that an
 * app call is in progress. Each app has its own on/off switch.
 */
@Composable
fun AppCallRecordingSetupScreen(
    onBack: () -> Unit,
    onOpenSetup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current
    val prefs = remember { RecordingPrefs(context) }

    var whatsapp by remember { mutableStateOf(prefs.recordWhatsApp) }
    var telegram by remember { mutableStateOf(prefs.recordTelegram) }
    var instagram by remember { mutableStateOf(prefs.recordInstagram) }
    var snapchat by remember { mutableStateOf(prefs.recordSnapchat) }
    var shizukuOk by remember { mutableStateOf(false) }
    var notifAccess by remember { mutableStateOf(false) }

    fun refresh() {
        shizukuOk = RecordingSetupChecker.isShizukuRunning() && RecordingSetupChecker.hasShizukuPermission(context)
        notifAccess = RecordingSetupChecker.hasNotificationAccess(context)
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(obs)
        refresh()
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = palette.textPrimary) }
            Spacer(Modifier.width(4.dp))
            Text("App calls", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Voice and video calls inside WhatsApp, Telegram, Instagram and Snapchat never go through the phone network, so Android gives no direct " +
                        "way to record them. Ashu Dialer records what comes out of the phone's speaker while the call is on, using Shizuku. " +
                        "For the best result use the loudspeaker or headphones and keep the volume up.",
                    fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 18.sp
                )
            }

            item {
                Column(Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(14.dp)) {
                    Text("Requirements", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    Req("Shizuku running and allowed", shizukuOk, palette) {
                        Button(onClick = onOpenSetup, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) { Text("Set up", fontSize = 12.sp) }
                    }
                    Req("Notification access", notifAccess, palette) {
                        Button(
                            onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                        ) { Text("Open", fontSize = 12.sp) }
                    }
                }
            }

            item {
                Column(Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(14.dp)) {
                    AppSwitch("Record WhatsApp calls", "Also covers WhatsApp Business", whatsapp, palette) {
                        whatsapp = it; prefs.recordWhatsApp = it
                    }
                    AppSwitch("Record Telegram calls", "Telegram and Telegram X", telegram, palette) {
                        telegram = it; prefs.recordTelegram = it
                    }
                    AppSwitch("Record Instagram calls", "Instagram and Instagram Lite", instagram, palette) {
                        instagram = it; prefs.recordInstagram = it
                    }
                    AppSwitch("Record Snapchat calls", "Voice and video calls in Snapchat", snapchat, palette) {
                        snapchat = it; prefs.recordSnapchat = it
                    }
                }
            }

            item {
                Text(
                    "Recordings appear in the Recordings tab and are saved in a folder named after the app (Music/Ashu Dialer/WhatsApp, Telegram, Instagram, Snapchat). " +
                        "Instagram and Snapchat do not label their call notification the way WhatsApp and Telegram do, so those two are detected from the ongoing call notification; if one is not picked up on your phone, tell me on Telegram from Help & feedback. " +
                        "The master \"Record calls\" switch in Recording settings must be on as well.",
                    fontSize = 12.sp, color = palette.textSecondary, lineHeight = 17.sp
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Req(text: String, done: Boolean, palette: com.ashudialer.app.ui.theme.DialerPalette, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
            tint = if (done) palette.accent else palette.textSecondary, modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.5.sp, color = palette.textPrimary, modifier = Modifier.weight(1f))
        if (!done) action()
    }
}

@Composable
private fun AppSwitch(title: String, subtitle: String, checked: Boolean, palette: com.ashudialer.app.ui.theme.DialerPalette, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = palette.textPrimary)
            Text(subtitle, fontSize = 11.5.sp, color = palette.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
