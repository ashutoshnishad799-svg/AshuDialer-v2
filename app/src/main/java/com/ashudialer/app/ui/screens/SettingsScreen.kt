package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.AppSettings
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.components.glassCard


@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenIncomingCallStyle: () -> Unit,
    onToggleCallRecording: (Boolean) -> Unit,
    onToggleAutoRecordAll: (Boolean) -> Unit,
    onOpenRecordingSettings: () -> Unit = {},
    onToggleLedFlash: (Boolean) -> Unit,
    onToggleVibrateOnButton: (Boolean) -> Unit,
    onToggleKeepCallsInNotifications: (Boolean) -> Unit,
    onToggleBackEndsCall: (Boolean) -> Unit,
    onToggleDisableProximity: (Boolean) -> Unit,
    onToggleShowThumbnails: (Boolean) -> Unit,
    onToggleShowPhoneNumbers: (Boolean) -> Unit,
    onToggleRelativeDate: (Boolean) -> Unit,
    onToggleGroupRecentsByDay: (Boolean) -> Unit = {},
    onToggleShowSearchBar: (Boolean) -> Unit,
    onSelectFontSize: (Int) -> Unit,
    onSelectButtonDepth: (String) -> Unit = {},
    onExportCallHistory: () -> Unit,
    onImportCallHistory: () -> Unit,
    onOpenTelegramChannel: () -> Unit,
    onOpenHelpFeedback: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    quietHoursSubtitle: String = "Off",
    onOpenQuietHours: () -> Unit = {},
    onOpenMiuiAutostartSettings: (() -> Unit)? = null,
    onFixPermissions: () -> Unit = {},
    onFixDefaultDialer: () -> Unit = {},
    onRequestBluetooth: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                // Explicit weight(1f) here is the fix: without it, this
                // LazyColumn only had "fill remaining space inside a Column"
                // as an implicit expectation, but Column doesn't allocate
                // space to non-weighted children that way - it sizes them to
                // their own content first. A LazyColumn's "content size" with
                // many items can end up taller than the screen with no
                // explicit bound telling it otherwise, and depending on the
                // exact constraint chain from whatever Compose destination/
                // NavHost wraps this screen, that could mean the list's
                // internal scroll gesture never gets properly bounded scroll
                // range - it looks like it's "there" but stops responding to
                // swipes partway down. weight(1f) forces it to take exactly
                // "screen height minus the header row above", guaranteeing a
                // real bounded scroll container all the way to the last item.
                .weight(1f)
                .padding(horizontal = 20.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                SectionLabel("Troubleshooting", palette)
                Text(
                    "Only if something is not working, for example calls do not light up the screen. Each line shows its state and opens the right place to fix it.",
                    fontSize = 12.sp, color = palette.textSecondary, lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
                Spacer(Modifier.height(8.dp))
                SettingsCard(palette) {
                    TroubleshootingRows(palette, onFixPermissions, onFixDefaultDialer, onRequestBluetooth)
                }
                Spacer(Modifier.height(20.dp))
            }


            item {
                SectionLabel("Appearance", palette)
                SettingsCard(palette) {
                    NavRow(icon = Icons.Filled.Palette, title = "Theme", subtitle = "Choose a color palette", palette = palette, onClick = onOpenAppearance)
                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                    NavRow(
                        icon = Icons.Filled.Phone,
                        title = "Incoming call screen",
                        subtitle = incomingCallStyleDisplayName(settings.incomingCallStyle),
                        palette = palette,
                        onClick = onOpenIncomingCallStyle
                    )
                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                    ButtonDepthRow(selected = settings.buttonDepth, palette = palette, onSelect = onSelectButtonDepth)
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                SectionLabel("Notifications", palette)
                SettingsCard(palette) {
                    ToggleRow(
                        icon = Icons.Filled.FlashOn, title = "LED flash for alerts",
                        subtitle = "Flashes the device LED for incoming calls",
                        checked = settings.ledFlashForAlerts, palette = palette, onToggle = onToggleLedFlash
                    )
                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                    ToggleRow(
                        icon = Icons.Filled.RecordVoiceOver, title = "Vibrate on button press",
                        subtitle = "Haptic feedback on dialpad and buttons",
                        checked = settings.vibrateOnButtonPress, palette = palette, onToggle = onToggleVibrateOnButton
                    )
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                SectionLabel("Calls", palette)
                SettingsCard(palette) {
                    ToggleRow(
                        title = "Keep calls in notifications",
                        subtitle = "Leaves the call notification visible after it ends",
                        checked = settings.keepCallsInNotifications, palette = palette, onToggle = onToggleKeepCallsInNotifications
                    )
                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                    ToggleRow(
                        title = "Back action ends the call",
                        subtitle = "Device Back button ends the active call",
                        checked = settings.backEndsCall, palette = palette, onToggle = onToggleBackEndsCall
                    )
                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                    ToggleRow(
                        title = "Disable proximity sensor during calls",
                        subtitle = "For devices/cases with unreliable proximity detection",
                        checked = settings.disableProximitySensor, palette = palette, onToggle = onToggleDisableProximity
                    )
                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                    NavRow(
                        icon = Icons.Filled.Bedtime,
                        title = "Quiet Hours",
                        subtitle = quietHoursSubtitle,
                        palette = palette,
                        onClick = onOpenQuietHours
                    )
                }
            }

            if (com.ashudialer.app.BuildConfig.CALL_RECORDING_ENABLED) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionLabel("Call recording", palette)
                    SettingsCard(palette) {
                        ToggleRow(
                            icon = Icons.Filled.Mic, title = "Enable call recording",
                            subtitle = "Adds a Record button to the call screen. Needs a one-time Shizuku setup.",
                            checked = settings.callRecordingEnabled, palette = palette, onToggle = onToggleCallRecording
                        )
                        HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                        ToggleRow(
                            icon = Icons.Filled.RecordVoiceOver, title = "Auto-record all calls",
                            subtitle = if (settings.callRecordingEnabled)
                                "Starts recording by itself when a call is answered"
                            else
                                "Turn on \"Enable call recording\" first",
                            checked = settings.autoRecordAll, enabled = settings.callRecordingEnabled,
                            palette = palette, onToggle = onToggleAutoRecordAll
                        )
                        HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenRecordingSettings() }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Recording settings", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
                                Text(
                                    "Audio source, format, where files are saved, file names, WhatsApp / Telegram calls",
                                    fontSize = 12.sp, color = palette.textSecondary, lineHeight = 16.sp
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = palette.textSecondary)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                SectionLabel("List view", palette)
                SettingsCard(palette) {
                    ToggleRow(
                        title = "Show contact thumbnails",
                        subtitle = "Photos instead of initials in Recents and Contacts",
                        checked = settings.showContactThumbnails, palette = palette, onToggle = onToggleShowThumbnails
                    )
                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                    ToggleRow(
                        title = "Show phone numbers",
                        subtitle = "Shows the number alongside saved contact names",
                        checked = settings.showPhoneNumbers, palette = palette, onToggle = onToggleShowPhoneNumbers
                    )
                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                    ToggleRow(
                        title = "Relative date",
                        subtitle = "\"Yesterday\" instead of the full date where possible",
                        checked = settings.useRelativeDate, palette = palette, onToggle = onToggleRelativeDate
                    )
                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                    ToggleRow(
                        title = "Group calls by day",
                        subtitle = "Show one row per number per day, with a count, even if you called other people in between",
                        checked = settings.groupRecentsByDay, palette = palette, onToggle = onToggleGroupRecentsByDay
                    )
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                SectionLabel("Top app bar", palette)
                SettingsCard(palette) {
                    ToggleRow(
                        title = "Show search bar",
                        subtitle = "Search field pinned to the top of Contacts",
                        checked = settings.showSearchBar, palette = palette, onToggle = onToggleShowSearchBar
                    )
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                SectionLabel("Backups", palette)
                SettingsCard(palette) {
                    NavRow(title = "Export call history", subtitle = "Save your call log as a file", palette = palette, onClick = onExportCallHistory)
                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                    NavRow(title = "Import call history", subtitle = "Restore a previously exported call log", palette = palette, onClick = onImportCallHistory)
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                SectionLabel("General", palette)
                SettingsCard(palette) {
                    FontSizeRow(selectedIndex = settings.fontSizeIndex, palette = palette, onSelect = onSelectFontSize)
                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                    NavRow(title = "Check for updates", subtitle = "Find the latest Phone release", palette = palette, onClick = onCheckForUpdates)
                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                    NavRow(icon = Icons.Filled.Help, title = "Feedback & diagnostics", subtitle = "Report bugs, crashes, or send a diagnostic log", palette = palette, onClick = onOpenHelpFeedback)
                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                    NavRow(title = "Join our Telegram", subtitle = "Updates, new features, and support", palette = palette, onClick = onOpenTelegramChannel)
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

private fun fontSizeLabel(index: Int): String = when (index) {
    0 -> "Small"
    2 -> "Large"
    3 -> "Extra Large"
    else -> "Medium"
}

/**
 * Shows all four font sizes at once as tappable "A" previews (each drawn at
 * its own actual relative scale, matching Theme.kt's fontScaleFor), with the
 * current selection highlighted - rather than the old single row that
 * cycled through the four options on tap with no visible indication of
 * what had changed.
 */
@Composable
private fun FontSizeRow(selectedIndex: Int, palette: DialerPalette, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text("Font size", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
        Text(fontSizeLabel(selectedIndex), fontSize = 12.sp, color = palette.textSecondary)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.searchBackground),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(0 to 15.sp, 1 to 18.sp, 2 to 21.sp, 3 to 24.sp).forEach { (index, previewSize) ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .let { if (selected) it.background(palette.accent) else it }
                        .clickable { onSelect(index) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "A",
                        fontSize = previewSize,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) androidx.compose.ui.graphics.Color.White else palette.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun ButtonDepthRow(selected: String, palette: DialerPalette, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text("Button style", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
        Text(
            if (selected == "raised") "Raised - buttons have a subtle shadow and a defined edge" else "Flat - today's minimal look",
            fontSize = 12.sp, color = palette.textSecondary
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.searchBackground),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("flat" to "Flat", "raised" to "Raised").forEach { (value, label) ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .let { if (isSelected) it.background(palette.accent) else it }
                        .clickable { onSelect(value) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) androidx.compose.ui.graphics.Color.White else palette.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String, palette: DialerPalette) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
internal fun SettingsCard(palette: DialerPalette, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp)
    ) {
        content()
    }
}

@Composable
private fun NavRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String,
    palette: DialerPalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
            Text(subtitle, fontSize = 12.sp, color = palette.textSecondary)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun ToggleRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    palette: DialerPalette,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon, contentDescription = null,
                tint = if (enabled) palette.accent else palette.textSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = if (enabled) palette.textPrimary else palette.textSecondary.copy(alpha = 0.5f)
            )
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = palette.textSecondary)
            }
        }
        Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
    }
}


/**
 * Everything that is NOT needed to get started but fixes the usual "it does not work on my phone" problems, in one
 * place. Each line shows whether it is on and opens the exact page where it can be changed; the states are re-read
 * whenever the person comes back from that page.
 */
@Composable
private fun TroubleshootingRows(
    palette: DialerPalette,
    onFixPermissions: () -> Unit,
    onFixDefaultDialer: () -> Unit,
    onRequestBluetooth: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val oem = com.ashudialer.app.telecom.OemPermissionHelper
    val perms = com.ashudialer.app.telecom.DialerPermissions
    val hasPhone = remember(refresh) { perms.hasAll(context) }
    val isDefault = remember(refresh) { perms.isDefaultDialer(context) }
    val fullScreenOk = remember(refresh) { oem.canUseFullScreenIntent(context) }
    val miui = oem.isLikelyMiui()
    val miuiLock = remember(refresh) { oem.miuiShowOnLockScreenAllowed(context) }
    val miuiBg = remember(refresh) { oem.miuiBackgroundStartAllowed(context) }
    val needsBluetooth = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val bluetoothOk = remember(refresh) {
        !needsBluetooth || perms.isGranted(context, android.Manifest.permission.BLUETOOTH_CONNECT)
    }

    fun state(ok: Boolean?, on: String = "On", off: String = "Off. Tap to fix", unknown: String = "Tap to check") =
        when (ok) { true -> on; false -> off; null -> unknown }

    NavRow(Icons.Filled.Phone, "Phone and call access", state(hasPhone, on = "Allowed"), palette, onFixPermissions)
    NavRow(Icons.Filled.Call, "Default dialer", state(isDefault, on = "Ashu Dialer is the default"), palette, onFixDefaultDialer)
    NavRow(Icons.Filled.Lock, "Full screen calls", state(fullScreenOk, off = "Off. Turn on so a call can open over the lock screen"), palette) {
        oem.openFullScreenIntentSettings(context)
    }
    if (miui) {
        NavRow(Icons.Filled.Settings, "Xiaomi: Show on Lock screen", state(miuiLock), palette) { oem.openMiuiPermissionEditor(context) }
        NavRow(Icons.Filled.Settings, "Xiaomi: Open new windows while running in the background", state(miuiBg), palette) { oem.openMiuiPermissionEditor(context) }
        NavRow(Icons.Filled.Settings, "Xiaomi: Autostart", "Tap to check", palette) { oem.openMiuiAutostartSettings(context) }
    }
    if (needsBluetooth) {
        NavRow(Icons.Filled.Settings, "Bluetooth headset audio", state(bluetoothOk, on = "Allowed", off = "Off. Tap to allow"), palette, onRequestBluetooth)
    }
    NavRow(Icons.Filled.Build, "Battery: keep running in background", "Tap to check", palette) { oem.openAppBatterySettings(context) }
}
