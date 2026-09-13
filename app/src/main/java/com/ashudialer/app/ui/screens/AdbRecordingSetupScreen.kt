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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/**
 * Shizuku genuinely can enable two-way recording on non-root devices (by
 * granting the app the ADB shell's own permission set, which does include
 * CAPTURE_AUDIO_OUTPUT) - this is a real, working technique used by other
 * open-source call recorders. It requires binding to Shizuku's AIDL
 * service and requesting its runtime permission, which is a genuine
 * feature to build, not a toggle - so this page is honest that it isn't
 * wired up yet rather than showing a button that would silently do
 * nothing.
 */
@Composable
fun AdbRecordingSetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current
    var shizukuInstalled by remember { mutableStateOf(RecordingSetupChecker.isShizukuInstalled(context)) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("ADB / Shizuku setup", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Text(
                    "What Shizuku actually does",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Shizuku is a separate app that gives other apps a slice of the ADB shell's own permissions, without needing full root. Since the shell user is allowed to capture call audio, this can genuinely unlock two-way recording without rooting the device.",
                    fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 19.sp
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.accentSoft).padding(14.dp)
                ) {
                    Text(
                        "Honestly: this app doesn't yet talk to Shizuku. Making that work needs a real integration (binding to Shizuku's service and requesting its permission), which is planned but not built yet — so nothing on this page will enable two-way recording today. This page exists so you can get Shizuku installed and ready for when that support ships.",
                        fontSize = 12.5.sp, color = palette.textPrimary, lineHeight = 19.sp
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(16.dp)) {
                    Text("Status", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (shizukuInstalled) Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = null,
                            tint = if (shizukuInstalled) palette.accent else palette.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (shizukuInstalled) "Shizuku is installed" else "Shizuku not found on this device",
                            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                            color = if (shizukuInstalled) palette.accent else palette.textSecondary
                        )
                    }
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
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}
