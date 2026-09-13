package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.telecom.RecordingSetupChecker
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.components.glassCard

/**
 * Everything reachable from this hub is either a link to a place the person
 * already controls (Magisk's app, Android's own Accessibility settings, the
 * Shizuku app) or a genuine runtime check - never a button that silently
 * roots the device or installs anything by itself. Rooting/flashing a
 * Magisk module happens entirely inside Magisk's own app, outside this one;
 * this screen only detects whether that has already happened.
 */
@Composable
fun RecordingSettingsScreen(
    onBack: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenRootSetup: () -> Unit,
    onOpenAccessibilitySetup: () -> Unit,
    onOpenAdbSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current

    val isPrivApp = remember { RecordingSetupChecker.isRunningAsPrivApp(context) }
    val accessibilityOn = remember { RecordingSetupChecker.isAccessibilityServiceEnabled(context) }
    val shizukuInstalled = remember { RecordingSetupChecker.isShizukuInstalled(context) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Recording setup", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.accentSoft)
                        .clickable { onOpenGuide() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.MenuBook, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "New here? Read the full guide first — it explains why this exists and what each option below actually does.",
                        fontSize = 12.5.sp, color = palette.textPrimary, modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.danger.copy(alpha = 0.10f)).padding(14.dp)
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = palette.danger, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "This app cannot root your device and never will. It also can't cause a bootloop by itself — rooting/flashing only happens inside Magisk's own app, which you control, and a Magisk module can always be disabled from Magisk's app or its recovery menu if something looks wrong. Everything below only detects status or opens settings; nothing here flashes anything on its own.",
                        fontSize = 11.5.sp, color = palette.textPrimary, lineHeight = 16.sp
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            item {
                MethodNavRow(
                    icon = Icons.Filled.Code,
                    title = "Root",
                    subtitle = "Best option — you have root. Full setup, checks, and status here.",
                    statusLabel = if (isPrivApp) "Active" else "Not set up",
                    statusGood = isPrivApp,
                    palette = palette,
                    onClick = onOpenRootSetup
                )
                Spacer(Modifier.height(12.dp))
            }

            // Root already active (the priv-app module is genuinely
            // installed and running) is the strongest, most complete
            // setup this app supports - ADB/Shizuku and Accessibility are
            // both weaker fallbacks meant for people without root at all.
            // Showing them anyway once root is already confirmed working
            // just adds two dead-end options to tap through and two more
            // permission surfaces (Shizuku, Accessibility Service) that
            // don't need to exist for this person's setup at all - not
            // "harmless extra info" but literally unnecessary access this
            // app would otherwise be asking for. Once isPrivApp is true,
            // this entire block collapses to a single line confirming
            // that, and Root above already carries all the real controls.
            if (!isPrivApp) {
                // A fresh, non-rooted ("Normal APK") install with neither
                // ADB/Shizuku nor Accessibility already active gets a
                // straightforward recommendation instead of two setup
                // paths that are both weaker than root and easy to get
                // stuck halfway through. Real two-way call recording on
                // this app only works reliably via the priv-app root
                // path (see Root above) - so for someone without root,
                // walking them through ADB or Accessibility setup for a
                // worse result than a purpose-built recorder isn't the
                // most honest thing to spend their time on. If either
                // path is ALREADY active, this app respects that existing
                // choice and keeps showing the normal rows below instead -
                // this block only replaces them for a genuinely fresh,
                // nothing-set-up-yet install.
                if (!shizukuInstalled && !accessibilityOn) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(palette, 14.dp)
                                .padding(16.dp)
                        ) {
                            Icon(Icons.Filled.MenuBook, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "No root on this device",
                                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Reliable two-way call recording needs the level of system access only root " +
                                        "provides. Without it, Android and most phone makers deliberately restrict " +
                                        "apps from capturing call audio, so this app can't offer a dependable " +
                                        "recorder here.\n\n" +
                                        "If you're on a custom ROM or have root, use the Root option above to set " +
                                        "this app up as a privileged system app for full call recording.\n\n" +
                                        "Otherwise, a trusted third-party call recorder — such as BCR (Basic Call " +
                                        "Recorder), which is open-source and well regarded for this — is a better " +
                                        "fit than the workarounds this app could offer without root.",
                                    fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 17.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                } else {
                    item {
                        MethodNavRow(
                            icon = Icons.Filled.Code,
                            title = "ADB / Shizuku",
                            subtitle = "Non-root path using ADB-granted permissions.",
                            statusLabel = if (shizukuInstalled) "Shizuku found" else "Not installed",
                            statusGood = shizukuInstalled,
                            palette = palette,
                            onClick = onOpenAdbSetup
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    item {
                        MethodNavRow(
                            icon = Icons.Filled.Person,
                            title = "Accessibility",
                            subtitle = "Background reliability only — not audio access.",
                            statusLabel = if (accessibilityOn) "On" else "Off",
                            statusGood = accessibilityOn,
                            palette = palette,
                            onClick = onOpenAccessibilitySetup
                        )
                        Spacer(Modifier.height(28.dp))
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.accent.copy(alpha = 0.10f))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Root setup is active, so ADB/Shizuku and Accessibility aren't needed and are hidden here.",
                            fontSize = 12.sp, color = palette.textPrimary, lineHeight = 16.sp
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun MethodNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    statusLabel: String,
    statusGood: Boolean,
    palette: DialerPalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(palette, 18.dp)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = palette.textSecondary, lineHeight = 16.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (statusGood) Icons.Filled.CheckCircle else Icons.Filled.Error,
                    contentDescription = null,
                    tint = if (statusGood) palette.accent else palette.textSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(statusLabel, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (statusGood) palette.accent else palette.textSecondary)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
    }
}
