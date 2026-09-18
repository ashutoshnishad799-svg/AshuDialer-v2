package com.ashudialer.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.ashudialer.app.AshuDialerApp
import com.ashudialer.app.telecom.RecordingSetupChecker
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlinx.coroutines.launch

@Composable
fun RecordingSettingsScreen(
    onBack: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenShizukuSetup: () -> Unit,
    onOpenAppCallsSetup: () -> Unit,
    onOpenPrivateSpace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current
    val app = context.applicationContext as AshuDialerApp
    val scope = rememberCoroutineScope()
    val settings by app.appSettingsRepository.settingsFlow.collectAsState(initial = com.ashudialer.app.data.AppSettings())

    var shizukuInstalled by remember { mutableStateOf(RecordingSetupChecker.isShizukuInstalled(context)) }
    var shizukuRunning by remember { mutableStateOf(RecordingSetupChecker.isShizukuRunning()) }
    var shizukuAuthorized by remember { mutableStateOf(RecordingSetupChecker.hasShizukuPermission()) }
    var shizukuAudioCapture by remember { mutableStateOf(RecordingSetupChecker.hasShizukuAudioCapturePermission()) }
    var notificationAccess by remember { mutableStateOf(RecordingSetupChecker.isAppCallNotificationAccessGranted(context)) }

    fun refresh() {
        shizukuInstalled = RecordingSetupChecker.isShizukuInstalled(context)
        shizukuRunning = RecordingSetupChecker.isShizukuRunning()
        shizukuAuthorized = RecordingSetupChecker.hasShizukuPermission()
        shizukuAudioCapture = RecordingSetupChecker.hasShizukuAudioCapturePermission()
        notificationAccess = RecordingSetupChecker.isAppCallNotificationAccessGranted(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val shizukuReady = shizukuInstalled && shizukuRunning && shizukuAuthorized && shizukuAudioCapture
    val appCallsReady = shizukuReady && notificationAccess

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Call recording", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.accentSoft)
                        .clickable { onOpenGuide() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.MenuBook, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("New here? Open the setup guide", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        Spacer(Modifier.height(3.dp))
                        Text("Step-by-step Shizuku setup, authorization and troubleshooting.", fontSize = 12.sp, color = palette.textSecondary)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = palette.textSecondary)
                }
                Spacer(Modifier.height(18.dp))
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(16.dp)) {
                    Text("Shizuku", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    SetupStatusRow(shizukuInstalled, "Shizuku installed", "Install Shizuku first", palette)
                    Spacer(Modifier.height(7.dp))
                    SetupStatusRow(shizukuRunning, "Shizuku server running", "Start Shizuku", palette)
                    Spacer(Modifier.height(7.dp))
                    SetupStatusRow(shizukuAuthorized, "Ashu Dialer authorized", "Allow Ashu Dialer in Shizuku → Authorized applications", palette)
                    Spacer(Modifier.height(7.dp))
                    SetupStatusRow(shizukuAudioCapture, "Shizuku recording bridge ready", "Authorize Ashu Dialer in Shizuku first", palette)
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = onOpenShizukuSetup, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (shizukuReady) "Shizuku settings & status" else "Set up Shizuku", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { refresh() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Re-check status", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(16.dp)) {
                    Text("Phone call recording", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Ashu Dialer uses the Shizuku shell service and bundled scrcpy audio pipeline for call recording. The APK no longer has a root or Magisk recording path.",
                        fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    RecordingToggleRow(
                        label = "Enable call recording",
                        checked = settings.callRecordingEnabled,
                        enabled = shizukuReady,
                        palette = palette,
                        onCheckedChange = { checked -> scope.launch { app.appSettingsRepository.setCallRecordingEnabled(checked) } }
                    )
                    Spacer(Modifier.height(10.dp))
                    RecordingToggleRow(
                        label = "Auto-record calls",
                        checked = settings.autoRecordAll,
                        enabled = shizukuReady && settings.callRecordingEnabled,
                        palette = palette,
                        onCheckedChange = { checked -> scope.launch { app.appSettingsRepository.setAutoRecordAll(checked) } }
                    )
                    Spacer(Modifier.height(10.dp))
                    RecordingToggleRow(
                        label = "Announce recording",
                        checked = settings.announceRecording,
                        enabled = shizukuReady && settings.callRecordingEnabled,
                        palette = palette,
                        onCheckedChange = { checked -> scope.launch { app.appSettingsRepository.setAnnounceRecording(checked) } }
                    )
                    Spacer(Modifier.height(10.dp))
                    PhoneRecordingSourceSelector(
                        sourceKey = settings.recordingAudioSource,
                        enabled = shizukuReady && settings.callRecordingEnabled,
                        palette = palette,
                        onSourceSelected = { source -> scope.launch { app.appSettingsRepository.setRecordingAudioSource(source) } }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(16.dp)) {
                    Text("WhatsApp / Telegram", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    SetupStatusRow(notificationAccess, "Notification access", "Required to detect supported app calls", palette)
                    Spacer(Modifier.height(6.dp))
                    SetupStatusRow(appCallsReady, "Shizuku + detection ready", "Finish the setup above", palette)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onOpenAppCallsSetup, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open app-call recording settings", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(16.dp)) {
                    Text("Recording storage", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("Normal recordings are saved under Music/Ashu Dialer. Private Space keeps selected recordings inside Ashu Dialer's protected app storage instead of the public music collection.", fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 19.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onOpenPrivateSpace, modifier = Modifier.fillMaxWidth()) {
                        Text("Open Private Space", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(16.dp)) {
                    Text("Android permissions & reliability", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Keep Ashu Dialer notifications enabled, allow microphone permission when Android asks, and remove aggressive battery restrictions so the dialer and notification listener can stay alive during calls.",
                        fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                })
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Ashu Dialer notification settings", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open battery optimization settings", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SetupStatusRow(good: Boolean, goodText: String, badText: String, palette: com.ashudialer.app.ui.theme.DialerPalette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (good) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (good) palette.accent else palette.textSecondary,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (good) goodText else badText,
            fontSize = 12.5.sp,
            color = if (good) palette.textPrimary else palette.textSecondary
        )
    }
}

@Composable
private fun PhoneRecordingSourceSelector(
    sourceKey: String,
    enabled: Boolean,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    onSourceSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        "voice-call" to "Voice call — both call legs (recommended)",
        "voice-call-downlink" to "Voice call downlink — remote side",
        "voice-call-uplink" to "Voice call uplink — microphone side",
        "mic-voice-communication" to "Voice communication — device voice path",
        "output" to "Output mix — final device audio"
    )
    val current = options.firstOrNull { it.first == sourceKey } ?: options.first()

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.cardBackground)
                .clickable(enabled = enabled) { expanded = true }.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text("Audio source", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            Spacer(Modifier.height(3.dp))
            Text(current.second, fontSize = 11.5.sp, color = palette.textSecondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label, fontSize = 12.5.sp) },
                    onClick = { expanded = false; onSourceSelected(key) },
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
private fun RecordingToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.cardBackground).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = palette.accent, checkedTrackColor = palette.accent.copy(alpha = 0.55f))
        )
    }
}
