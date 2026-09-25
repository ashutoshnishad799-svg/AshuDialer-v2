package com.ashudialer.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.BuildConfig
import com.ashudialer.app.data.UpdateCheckResult
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.theme.LocalDialerPalette

@Composable
fun UpdateScreen(
    result: UpdateCheckResult?,
    busy: Boolean,
    installing: Boolean = false,
    // 0..100 while the APK downloads; -1 = the server did not say how big the file is.
    downloadPercent: Int = 0,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onInstallUpdate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = palette.textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("App updates", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text("Ashu Dialer", fontSize = 12.sp, color = palette.textSecondary)
            }
            IconButton(onClick = onCheck, enabled = !busy && !installing) {
                Icon(Icons.Filled.Refresh, "Check for updates", tint = palette.textPrimary)
            }
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).glassCard(palette, 28.dp).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(78.dp).clip(RoundedCornerShape(24.dp)).glassCard(palette, 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.SystemUpdate, null, tint = palette.accent, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("Ashu Dialer", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                Text("Current version ${result?.currentVersion ?: BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = palette.textSecondary)
                Spacer(Modifier.height(18.dp))

                when {
                    installing -> {
                        Text("Downloading update", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Spacer(Modifier.height(14.dp))
                        if (downloadPercent in 0..100) {
                            // Determinate bar: the person sees it moving and knows it is not stuck.
                            LinearProgressIndicator(
                                progress = downloadPercent / 100f,
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                                color = palette.accent,
                                trackColor = palette.cardBorder
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("$downloadPercent%", fontSize = 13.sp, color = palette.textSecondary)
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                                color = palette.accent,
                                trackColor = palette.cardBorder
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Keep the app open until it finishes", fontSize = 12.sp, color = palette.textSecondary)
                    }
                    busy -> {
                        CircularProgressIndicator(color = palette.accent)
                        Spacer(Modifier.height(10.dp))
                        Text("Checking for updates…", color = palette.textSecondary)
                    }
                    result?.error != null -> {
                        Icon(Icons.Filled.Warning, null, tint = palette.danger, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Couldn't check for updates", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        Text(result.error, fontSize = 13.sp, color = palette.textSecondary, modifier = Modifier.padding(top = 5.dp))
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = onCheck) { Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(7.dp)); Text("Try again") }
                    }
                    result?.updateAvailable == true -> {
                        Icon(Icons.Filled.SystemUpdate, null, tint = palette.accent, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Update available", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        Text("Version ${result.latestVersion}", fontSize = 14.sp, color = palette.textSecondary)
                        result.releaseNotes?.let {
                            Spacer(Modifier.height(12.dp))
                            Text("What's new", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                            Text(it, fontSize = 13.sp, color = palette.textSecondary, modifier = Modifier.padding(top = 4.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onInstallUpdate, enabled = !result.apkDownloadUrl.isNullOrBlank()) {
                            Icon(Icons.Filled.SystemUpdate, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Download & install")
                        }
                    }
                    result != null -> {
                        Icon(Icons.Filled.CheckCircle, null, tint = palette.callGreen, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("You're up to date", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        Text("Latest version ${result.latestVersion}", fontSize = 13.sp, color = palette.textSecondary)
                    }
                    else -> {
                        Text("Ready to check for updates", color = palette.textSecondary, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(22.dp))
                HorizontalDivider(color = palette.cardBorder)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudDone, null, tint = palette.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Official releases", fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                        Text("Downloaded from this app's GitHub Releases page", fontSize = 12.sp, color = palette.textSecondary)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Only official Ashu Dialer APK updates are shown here.", fontSize = 12.sp, color = palette.textSecondary)
        }
    }
}
