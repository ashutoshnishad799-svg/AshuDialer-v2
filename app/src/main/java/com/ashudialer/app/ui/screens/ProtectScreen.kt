package com.ashudialer.app.ui.screens

import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.launch
import com.ashudialer.app.AshuDialerApp
import androidx.core.app.NotificationManagerCompat
import com.ashudialer.app.data.AppSettings
import com.ashudialer.app.data.db.BlockedNumberEntity
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.components.glassCard

@Composable
fun ProtectScreen(
    blockedNumbers: List<BlockedNumberEntity>,
    onOpenBlockedNumbers: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current
    val app = context.applicationContext as AshuDialerApp
    val settings by app.appSettingsRepository.settingsFlow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val roleManager = remember(context) { context.getSystemService(RoleManager::class.java) }
    val screeningRoleAvailable = remember(roleManager) {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true
    }
    var screeningRoleHeld by remember(roleManager) {
        mutableStateOf(screeningRoleAvailable && roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true)
    }

    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var fullScreenCallAccess by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
        )
    }

    fun refreshScreeningRole() {
        screeningRoleHeld = screeningRoleAvailable && roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
    }

    // Re-check after the system role picker closes and whenever the app returns
    // from Settings. This fixes the old UI staying stuck on "Enable" even when
    // Android had already granted ROLE_CALL_SCREENING.
    DisposableEffect(lifecycleOwner, roleManager, screeningRoleAvailable) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshScreeningRole()
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                fullScreenCallAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                    context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshScreeningRole()
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        fullScreenCallAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val screeningRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshScreeningRole()
    }

    val spamProtectionOn = settings.spamProtectionEnabled
    val silenceUnknown = settings.silenceUnknownCallers
    val flagInternational = settings.flagInternationalNumbers

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            "Protect", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
            color = palette.textPrimary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().glassCard(palette, 20.dp).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(palette.accentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = palette.accent, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (spamProtectionOn) "You're protected" else "Protection is off",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text("${blockedNumbers.size} numbers blocked", fontSize = 13.sp, color = palette.textSecondary)
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("Protection settings", palette)
            Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp)) {
                ProtectToggleRow(Icons.Filled.Shield, "Spam detection", "Flag suspicious callers automatically", spamProtectionOn, palette) { value -> scope.launch { app.appSettingsRepository.setSpamProtectionEnabled(value) } }
                HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                ProtectToggleRow(Icons.Filled.VolumeOff, "Silence unknown callers", "Numbers not in your contacts won't ring", silenceUnknown, palette) { value -> scope.launch { app.appSettingsRepository.setSilenceUnknownCallers(value) } }
                HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                ProtectToggleRow(Icons.Filled.PhoneDisabled, "Flag international numbers", "Warn on calls from unfamiliar country codes", flagInternational, palette) { value -> scope.launch { app.appSettingsRepository.setFlagInternationalNumbers(value) } }
            }

            if (screeningRoleAvailable && !screeningRoleHeld) {
                Spacer(Modifier.height(12.dp))
                Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(16.dp)) {
                    Text("Caller protection access", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("Allow Ashu Dialer to identify and silence suspicious or unknown calls before they ring.", fontSize = 12.sp, color = palette.textSecondary)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                                if (intent != null) screeningRoleLauncher.launch(intent)
                            } catch (_: Exception) {
                                refreshScreeningRole()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Enable caller protection", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (!notificationsEnabled || !fullScreenCallAccess) {
                Spacer(Modifier.height(14.dp))
                Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(16.dp)) {
                    Text("Lock-screen call alerts", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (!notificationsEnabled) {
                            "Enable Ashu Dialer notifications so incoming calls can wake the screen and show call controls."
                        } else {
                            "Allow full-screen call alerts so an incoming call can wake the display and appear above the lock screen."
                        },
                        fontSize = 12.sp,
                        color = palette.textSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = if (!notificationsEnabled) {
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                } else null
                                if (intent != null) context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                                } catch (_: Exception) { }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(if (!notificationsEnabled) "Enable notifications" else "Allow full-screen call alerts", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("Blocked numbers", palette)
            Column(
                modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp).clickable { onOpenBlockedNumbers() }.padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Block, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Manage blocked numbers", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
                        Text(if (blockedNumbers.isEmpty()) "No numbers blocked yet" else "${blockedNumbers.size} blocked", fontSize = 12.sp, color = palette.textSecondary)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ProtectToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    palette: DialerPalette,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
            Text(subtitle, fontSize = 11.5.sp, color = palette.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = palette.accent))
    }
}
