package com.ashudialer.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.recording.integrations.shizuku.ShizukuConnectionManager
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.theme.LocalDialerPalette

@Composable
fun AdbRecordingSetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current
    var serverRunning by remember { mutableStateOf(ShizukuConnectionManager.isAvailable()) }
    var permissionGranted by remember { mutableStateOf(ShizukuConnectionManager.hasPermission(context)) }
    var captureGranted by remember { mutableStateOf(ShizukuConnectionManager.checkServerPermission("android.permission.CAPTURE_AUDIO_OUTPUT")) }

    fun refresh() {
        serverRunning = ShizukuConnectionManager.isAvailable()
        permissionGranted = ShizukuConnectionManager.hasPermission(context)
        captureGranted = ShizukuConnectionManager.checkServerPermission("android.permission.CAPTURE_AUDIO_OUTPUT")
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
            Text("Shizuku recording", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Security, contentDescription = null, tint = palette.accent)
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 18.dp).padding(18.dp)) {
                    Text("Two-way call recording", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "Ashu Dialer now uses the same elevated audio design as the supplied Ever-Dialer recorder: Shizuku launches the audio-only scrcpy server under the shell UID, and the resulting voice-call stream is encoded directly into M4A.",
                        fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.accentSoft).padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(
                            "Normal APK: Shizuku backend. Root/Magisk APK: direct su backend, so Shizuku is optional there.",
                            fontSize = 12.sp, color = palette.textPrimary, lineHeight = 18.sp
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                StatusCard(
                    title = "Shizuku server",
                    statusLabel = if (serverRunning) "Running" else "Not running",
                    statusGood = serverRunning,
                    palette = palette
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { refresh(); if (!serverRunning) {
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))) } catch (_: Exception) {}
                            } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                        ) { Text(if (serverRunning) "Refresh" else "Open Shizuku", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp) }
                        OutlinedButton(onClick = { refresh() }, modifier = Modifier.weight(1f)) { Text("Re-check", fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            item {
                StatusCard(
                    title = "Ashu Dialer Shizuku permission",
                    statusLabel = if (permissionGranted) "Granted" else "Needs approval",
                    statusGood = permissionGranted,
                    palette = palette
                ) {
                    Button(
                        onClick = { ShizukuConnectionManager.requestPermission(); refresh() },
                        enabled = serverRunning && !permissionGranted,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                    ) { Text(if (permissionGranted) "Permission granted" else "Grant Shizuku permission", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp) }
                }
                Spacer(Modifier.height(12.dp))
            }

            item {
                StatusCard(
                    title = "Shell call-audio capability",
                    statusLabel = if (captureGranted) "CAPTURE_AUDIO_OUTPUT available" else "Not available yet",
                    statusGood = captureGranted,
                    palette = palette
                ) {
                    Text(
                        if (captureGranted)
                            "The elevated shell can access the protected call-audio source. Start a real call and use Ashu Dialer's Record button."
                        else
                            "Start Shizuku with ADB/root, grant Ashu Dialer permission, then re-check. The APK cannot grant this privileged capability to itself.",
                        fontSize = 12.sp, color = palette.textSecondary, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { refresh() }, modifier = Modifier.fillMaxWidth()) { Text("Re-check capability", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(22.dp))
            }

            item {
                Text(
                    "Recording path: Music/Ashu Dialer. Codec: AAC/M4A. If the elevated backend is unavailable, the normal APK still falls back to microphone recording and labels it as MIC instead of falsely claiming two-way capture.",
                    fontSize = 11.5.sp, color = palette.textSecondary, lineHeight = 17.sp
                )
            }
        }
    }
}
