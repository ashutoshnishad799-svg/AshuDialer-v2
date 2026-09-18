package com.ashudialer.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.theme.LocalDialerPalette

@Composable
fun RecordingGuideScreen(
    onBack: () -> Unit,
    onConfirmEnable: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary) }
            Spacer(Modifier.width(4.dp))
            Text("Recording guide", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                GuideCard("What this APK uses", "Ashu Dialer uses Shizuku to start a privileged shell service and a bundled scrcpy audio server. The app-side recorder receives the audio stream and writes an M4A file. The old Magisk/root recording path has been removed from this build.", palette)
                Spacer(Modifier.height(12.dp))
            }
            item {
                GuideCard("1 — Install and start Shizuku", "Beginner path: Settings → About phone → tap Build number 7 times → Developer options → Wireless debugging → Pair device with pairing code. Open Shizuku → Start via Wireless debugging → complete pairing/start. If your phone does not support that route, use the ADB-start instructions shown inside Shizuku. On a rooted phone, you may use Shizuku's Root-start option and approve its superuser request; Ashu Dialer itself still has no root or Magisk module dependency.", palette)
                Spacer(Modifier.height(12.dp))
            }
            item {
                GuideCard("2 — Authorize Ashu Dialer", "Open Shizuku → Authorized applications → Ashu Dialer → Allow. The Recording screen also checks whether Shizuku exposes system audio capture (CAPTURE_AUDIO_OUTPUT), because the scrcpy VOICE_CALL/OUTPUT pipeline needs that system-level capability.", palette)
                Spacer(Modifier.height(12.dp))
            }
            item {
                GuideCard("3 — Finish Android permissions", "Keep Ashu Dialer as the default phone app and grant the normal phone/call/microphone/notification permissions Android requests. For WhatsApp/Telegram recording, also grant Notification access so the app can detect their calls.", palette)
                Spacer(Modifier.height(12.dp))
            }
            item {
                GuideCard("4 — Background reliability", "On phones with aggressive battery management, set Ashu Dialer to unrestricted / not optimized battery use and keep notifications enabled. This prevents the dialer or call-notification listener from being suspended during a call.", palette)
                Spacer(Modifier.height(12.dp))
            }
            item {
                GuideCard("5 — Enable recording", "Open More → Recordings → Recording settings. Turn on Enable call recording after Shizuku is ready. Auto-record can then start recordings for supported active calls. The recorder uses the Shizuku audio pipeline instead of relying on a root module.", palette)
                Spacer(Modifier.height(12.dp))
            }
            item {
                GuideCard("WhatsApp / Telegram", "Open the dedicated WhatsApp / Telegram recording page. Turn on Notification access, make sure Shizuku remains authorized, then enable each app. Their audio is captured through the privileged output-audio pipeline when supported by the device and app audio policy.", palette)
                Spacer(Modifier.height(12.dp))
            }
            item {
                GuideCard("Important reality check", "Shizuku provides the privileged execution path, but Android/OEM audio policy still controls which audio sources are exposed on a particular device. The APK now follows the same class of Shizuku + scrcpy pipeline used by the supplied Ever-Dialer source, but no app can honestly guarantee identical two-way capture on every phone model without testing that device's audio policy.", palette)
                Spacer(Modifier.height(24.dp))
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Need to revisit Shizuku? Open the setup screen from Recording settings.", fontSize = 12.5.sp, color = palette.textSecondary)
                }
                if (onConfirmEnable != null) {
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onConfirmEnable, modifier = Modifier.fillMaxWidth()) {
                        Text("Enable call recording")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun GuideCard(title: String, body: String, palette: com.ashudialer.app.ui.theme.DialerPalette) {
    Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(16.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(body, fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 19.sp)
    }
}
