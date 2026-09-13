package com.ashudialer.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
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
import com.ashudialer.app.telecom.OemPermissionHelper
import com.ashudialer.app.telecom.RecordingSetupChecker
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.components.glassCard

@Composable
fun AccessibilityRecordingSetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current
    var accessibilityOn by remember { mutableStateOf(RecordingSetupChecker.isAccessibilityServiceEnabled(context)) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Accessibility setup", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.accentSoft).padding(14.dp)
                ) {
                    Text(
                        "This does not add two-way call recording. Google's Play policy forbids using Accessibility to capture call audio, and on almost every modern phone this channel only captures silence anyway. The only real effect here is helping some phones (Xiaomi/MIUI especially) avoid closing Ashu Dialer in the background while a call is active.",
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
                            if (accessibilityOn) Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = null,
                            tint = if (accessibilityOn) palette.accent else palette.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (accessibilityOn) "On" else "Off",
                            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                            color = if (accessibilityOn) palette.accent else palette.textSecondary
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Exception) {
                                OemPermissionHelper.openAppBatterySettings(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Accessibility settings", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { accessibilityOn = RecordingSetupChecker.isAccessibilityServiceEnabled(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Check", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            item {
                Text("If Ashu Dialer doesn't appear in the list", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This only happens for apps installed outside the Play Store (a direct link, Telegram, or another website):\n\n1. Settings → Apps → Ashu Dialer\n2. Tap the ⋮ menu in the top corner\n3. Choose \"Allow restricted settings\"\n4. Come back to Accessibility settings — Ashu Dialer should now be selectable\n\nThe exact wording can vary a little by phone brand.",
                    fontSize = 12.5.sp, color = palette.textPrimary, lineHeight = 19.sp
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}
