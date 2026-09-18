package com.ashudialer.app.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ashudialer.app.appcalls.ShizukuConnectionManager
import com.ashudialer.app.telecom.RecordingSetupChecker
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.theme.LocalDialerPalette

@Composable
fun ShizukuRecordingSetupScreen(
    onBack: () -> Unit,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalDialerPalette.current
    var installed by remember { mutableStateOf(RecordingSetupChecker.isShizukuInstalled(context)) }
    var running by remember { mutableStateOf(RecordingSetupChecker.isShizukuRunning()) }
    var authorized by remember { mutableStateOf(RecordingSetupChecker.hasShizukuPermission()) }
    var audioCapture by remember { mutableStateOf(RecordingSetupChecker.hasShizukuAudioCapturePermission()) }

    fun refresh() {
        installed = RecordingSetupChecker.isShizukuInstalled(context)
        running = RecordingSetupChecker.isShizukuRunning()
        authorized = RecordingSetupChecker.hasShizukuPermission()
        audioCapture = RecordingSetupChecker.hasShizukuAudioCapturePermission()
    }

    fun openShizuku() {
        val managerPackage = ShizukuConnectionManager.getPackageName(context) ?: "moe.shizuku.manager"
        runCatching {
            context.startActivity(context.packageManager.getLaunchIntentForPackage(managerPackage)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary) }
            Spacer(Modifier.width(4.dp))
            Text("Shizuku setup", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.accentSoft).clickable { onOpenGuide() }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.MenuBook, contentDescription = null, tint = palette.accent, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Beginner guide", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        Spacer(Modifier.height(3.dp))
                        Text("Shows every step for wireless debugging / ADB and authorization.", fontSize = 12.sp, color = palette.textSecondary)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = palette.textSecondary)
                }
                Spacer(Modifier.height(18.dp))
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(16.dp)) {
                    Text("Setup status", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    StatusLine(installed, "Shizuku app installed", "Shizuku is not installed", palette)
                    Spacer(Modifier.height(8.dp))
                    StatusLine(running, "Shizuku server is running", "Start Shizuku", palette)
                    Spacer(Modifier.height(8.dp))
                    StatusLine(authorized, "Ashu Dialer is authorized", "Authorize Ashu Dialer", palette)
                    Spacer(Modifier.height(8.dp))
                    StatusLine(audioCapture, "System audio capture is available", "System audio capture is not available to Shizuku yet", palette)
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = { openShizuku() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (installed) "Open Shizuku" else "Get Shizuku", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { refresh() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Re-check", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            item {
                StepCard(
                    number = "1",
                    title = "Install and start Shizuku",
                    body = "Install Shizuku from its official release page. Beginner path: Settings → About phone → tap Build number 7 times → Developer options → Wireless debugging → Pair device with pairing code. Open Shizuku, choose Start via Wireless debugging, pair when asked, and start the server. If your ROM does not provide that path, use Shizuku's ADB-start instructions. On a rooted phone, Shizuku can instead be started with Root and the normal superuser prompt; Ashu Dialer itself still has no root or Magisk dependency.",
                    palette = palette,
                    buttonText = "Open Shizuku",
                    onButton = { openShizuku() }
                )
                Spacer(Modifier.height(12.dp))
            }

            item {
                StepCard(
                    number = "2",
                    title = "Authorize Ashu Dialer",
                    body = "In Shizuku, open Authorized applications and switch Ashu Dialer ON. Return to Ashu Dialer and tap Re-check.",
                    palette = palette,
                    buttonText = "Open Shizuku",
                    onButton = { openShizuku() }
                )
                Spacer(Modifier.height(12.dp))
            }

            item {
                StepCard(
                    number = "3",
                    title = "Allow reliable background operation",
                    body = "Keep dialer notifications enabled and remove battery restrictions for Ashu Dialer if your phone aggressively stops background apps. This is especially important for app-call notification detection.",
                    palette = palette,
                    buttonText = "Open app settings",
                    onButton = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }
                    }
                )
                Spacer(Modifier.height(18.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (installed && running && authorized) palette.accentSoft else palette.cardBackground).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (installed && running && authorized) Icons.Filled.CheckCircle else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (installed && running && authorized && audioCapture) "Shizuku is ready. Phone-call recording can now start from the normal APK."
                        else "Finish the steps above. If the audio-capture row stays red on your ROM, start Shizuku using its root-start method; the APK itself still uses no root module.",
                        fontSize = 12.5.sp,
                        color = palette.textPrimary,
                        lineHeight = 18.sp
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StatusLine(good: Boolean, ok: String, bad: String, palette: com.ashudialer.app.ui.theme.DialerPalette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (good) Icons.Filled.CheckCircle else Icons.Filled.Error, contentDescription = null, tint = if (good) palette.accent else palette.textSecondary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (good) ok else bad, fontSize = 12.5.sp, color = if (good) palette.textPrimary else palette.textSecondary)
    }
}

@Composable
private fun StepCard(
    number: String,
    title: String,
    body: String,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    buttonText: String,
    onButton: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(palette.accentSoft), contentAlignment = Alignment.Center) {
                Text(number, fontWeight = FontWeight.Bold, color = palette.accent, fontSize = 13.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }
        Spacer(Modifier.height(9.dp))
        Text(body, fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 19.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onButton, modifier = Modifier.fillMaxWidth()) { Text(buttonText, fontSize = 13.sp) }
    }
}
