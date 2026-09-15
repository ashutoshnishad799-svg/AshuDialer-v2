package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.components.rememberButtonHaptic
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.components.glassCard

private data class MenuEntry(val label: String, val icon: ImageVector)

private val baseMenuItems = listOf(
    MenuEntry("Account", Icons.Filled.AccountCircle),
    MenuEntry("Backup", Icons.Filled.CloudUpload),
    MenuEntry("Local Backup", Icons.Filled.SdStorage),
    MenuEntry("Notes", Icons.Filled.NoteAlt),
    MenuEntry("Recordings", Icons.Filled.FiberManualRecord),
    MenuEntry("Call Insights", Icons.Filled.BarChart),
    MenuEntry("Vibration Patterns", Icons.Filled.Vibration),
    MenuEntry("Appearance", Icons.Filled.Palette),
    MenuEntry("Quiet Hours", Icons.Filled.Bedtime),
    MenuEntry("Settings", Icons.Filled.Settings),
    MenuEntry("Blocked numbers", Icons.Filled.Block),
    MenuEntry("Private Space", Icons.Filled.Lock),
    MenuEntry("Voicemail", Icons.Filled.Voicemail),
    MenuEntry("Set as default dialer", Icons.Filled.PhonelinkSetup),
    MenuEntry("Help & feedback", Icons.Filled.Help),
    MenuEntry("Privacy Policy", Icons.Filled.PrivacyTip),
    MenuEntry("About", Icons.Filled.Info)
)

@Composable
fun MoreScreen(
    onItemClick: (String) -> Unit,
    showSimRouting: Boolean = false,
    vibrateOnButtonPress: Boolean = true,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val vibrate = rememberButtonHaptic(vibrateOnButtonPress)


    val menuItems = remember(showSimRouting, com.ashudialer.app.BuildConfig.CALL_RECORDING_ENABLED) {
        val filtered = baseMenuItems.filter { it.label != "Recordings" || com.ashudialer.app.BuildConfig.CALL_RECORDING_ENABLED }.toMutableList()
        if (showSimRouting) {
            filtered.add(filtered.indexOfFirst { it.label == "Voicemail" } + 1, MenuEntry("SIM Routing", Icons.Filled.SimCard))
        }
        filtered.add(MenuEntry("Check for updates", Icons.Filled.SystemUpdate))
        filtered
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // The header ("More" title) stays outside this scroll, but the
            // whole rest of the screen used to be one plain, non-scrolling
            // Column - fine while the menu list was short, but this list has
            // grown to 15 items (Account, Backup, Local Backup, Notes,
            // Recordings, Call Insights, Vibration Patterns, Appearance,
            // Settings, Blocked numbers, Private Space, Voicemail, Set as
            // default dialer, Help & feedback, Privacy Policy), taller than
            // most screens, and a plain Column has no scrolling behavior of
            // its own - content past the bottom of the screen was simply
            // clipped and unreachable, which is why Privacy Policy at the
            // bottom couldn't be scrolled to at all.
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "More", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
            color = palette.textPrimary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .glassCard(palette, 18.dp)
        ) {
            menuItems.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vibrate()
                            onItemClick(entry.label)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(entry.icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = entry.label,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                }
                if (index != menuItems.lastIndex) {
                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
