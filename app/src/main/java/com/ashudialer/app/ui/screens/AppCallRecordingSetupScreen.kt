package com.ashudialer.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.telecom.RecordingSetupChecker
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.components.glassCard
import kotlinx.coroutines.launch

/**
 * Setup and toggles for recording WhatsApp/Telegram voice and video calls - a separate
 * capability from native phone-call recording, since a VoIP call inside a third-party app
 * has no modem-level VOICE_CALL tap to read from at all (see AppCallRecordingEngine's own doc
 * comment in the :appcalls module for the full explanation of why OUTPUT/REMOTE_SUBMIX via
 * Shizuku is the one technique that actually works here).
 *
 * Two separate system permissions are needed, independently of each other: Shizuku (for the
 * privileged audio capture itself) and Notification Access (so this app can detect a WhatsApp/
 * Telegram call is happening at all, since there's no broadcast for that). Both are checked and
 * requested from here.
 */
@Composable
fun AppCallRecordingSetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current
    val app = context.applicationContext as com.ashudialer.app.AshuDialerApp
    val scope = rememberCoroutineScope()

    var shizukuInstalled by remember { mutableStateOf(RecordingSetupChecker.isShizukuInstalled(context)) }
    var notificationAccessGranted by remember { mutableStateOf(RecordingSetupChecker.isAppCallNotificationAccessGranted(context)) }
    val settings by app.appSettingsRepository.settingsFlow.collectAsState(initial = com.ashudialer.app.data.AppSettings())

    val bothPermissionsReady = shizukuInstalled && notificationAccessGranted

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("WhatsApp / Telegram calls", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Text(
                    "Why this needs its own setup",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "A WhatsApp or Telegram call is internet-based, not a phone-network call, so there's no " +
                        "modem-level audio to tap into the way there is for a normal call. This app instead " +
                        "captures the audio your speaker is already playing, through the same kind of privileged " +
                        "access Shizuku grants for normal call recording - it just needs to actually detect that " +
                        "one of these calls is happening first, which needs Notification access.",
                    fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 19.sp
                )
                Spacer(Modifier.height(24.dp))
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(16.dp)) {
                    Text("1. Shizuku", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    StatusRow(shizukuInstalled, "Shizuku is installed", "Shizuku not found on this device", palette)
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (shizukuInstalled) "Open Shizuku's page" else "Get Shizuku", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { shizukuInstalled = RecordingSetupChecker.isShizukuInstalled(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Re-check", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(16.dp)) {
                    Text("2. Notification access", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    StatusRow(notificationAccessGranted, "Notification access granted", "Not granted yet", palette)
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open notification access settings", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { notificationAccessGranted = RecordingSetupChecker.isAppCallNotificationAccessGranted(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Re-check", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            if (!bothPermissionsReady) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.accentSoft).padding(14.dp)
                    ) {
                        Text(
                            "Both steps above need to be ready before the toggles below will do anything.",
                            fontSize = 12.5.sp, color = palette.textPrimary, lineHeight = 19.sp
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            item {
                Text("Record calls from", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Spacer(Modifier.height(10.dp))
                AppToggleRow(
                    label = "WhatsApp",
                    checked = settings.recordWhatsAppCallsEnabled,
                    enabled = bothPermissionsReady,
                    palette = palette,
                    onCheckedChange = { checked ->
                        scope.launch { app.appSettingsRepository.setRecordWhatsAppCallsEnabled(checked) }
                    }
                )
                Spacer(Modifier.height(10.dp))
                AppToggleRow(
                    label = "Telegram",
                    checked = settings.recordTelegramCallsEnabled,
                    enabled = bothPermissionsReady,
                    palette = palette,
                    onCheckedChange = { checked ->
                        scope.launch { app.appSettingsRepository.setRecordTelegramCallsEnabled(checked) }
                    }
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun StatusRow(good: Boolean, goodLabel: String, badLabel: String, palette: com.ashudialer.app.ui.theme.DialerPalette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (good) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (good) palette.accent else palette.textSecondary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (good) goodLabel else badLabel,
            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
            color = if (good) palette.accent else palette.textSecondary
        )
    }
}

@Composable
private fun AppToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().glassCard(palette, 14.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked && enabled,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = palette.accent, checkedTrackColor = palette.accent.copy(alpha = 0.5f))
        )
    }
}
