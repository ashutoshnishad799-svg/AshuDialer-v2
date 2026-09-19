package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.telecom.OemPermissionHelper
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.components.glassCard

/**
 * The post-onboarding setup checklist for the actual dialer only: phone/call
 * permissions and the default-dialer role. Call-recording/Shizuku setup is
 * deliberately kept out of this gate and lives in the dedicated recording
 * setup flow so a normal user never needs Shizuku just to use the dialer.
 *
 * Every card's status is re-read on every resume by MainActivity, the same
 * fix applied to isDefaultDialer/
 * hasPermissions in MainActivity and for the identical reason: any of
 * these can change from outside this screen entirely - granting phone
 * permissions from a system dialog, setting the default dialer from
 * Settings, flashing a root module and rebooting - and this screen must
 * reflect whichever of those actually happened by the time the person is
 * looking at it again, not a stale snapshot from when it first appeared.
 */
@Composable
fun PermissionsScreen(
    isDefaultDialer: Boolean,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onSetDefaultDialer: () -> Unit,
    onOpenDefaultAppsSettings: () -> Unit = {},
    onOpenMiuiAutostartSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current

    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "ASHU DIALER",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = palette.textSecondary
            )
            Text(
                "SETUP",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = palette.accent
            )
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(96.dp).clip(CircleShape).background(palette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = palette.accent, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Set up Ashu Dialer",
                fontSize = 26.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "A few quick steps to make Ashu Dialer your complete calling app.",
                fontSize = 14.sp, color = palette.textSecondary, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            SetupChecklistItem(
                icon = Icons.Filled.Phone,
                title = "Phone & call access",
                done = hasPermissions,
                palette = palette
            )
            Spacer(Modifier.height(12.dp))
            SetupChecklistItem(
                icon = Icons.Filled.Shield,
                title = "Default dialer",
                done = isDefaultDialer,
                palette = palette
            )
            if (hasPermissions && isDefaultDialer && OemPermissionHelper.isLikelyMiui()) {
                Spacer(Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.accentSoft)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("One more step for MIUI", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "MIUI can keep sending calls to its own Phone app even after you set Ashu Dialer as default. " +
                            "Turn on Autostart and remove battery restrictions for Ashu Dialer so calls, the lock-screen " +
                            "call UI, and missed-call notifications come from this app instead of MIUI's.",
                        fontSize = 12.5.sp, color = palette.textSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onOpenMiuiAutostartSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Autostart settings")
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)) {
            when {
                !hasPermissions -> Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) {
                    Text("Grant permissions", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                !isDefaultDialer -> Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onSetDefaultDialer,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                    ) {
                        Text("Set as default dialer", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    // Manual escape hatch: if the tap above opens a system
                    // dialog that never comes back (seen on some MIUI
                    // builds - see the comment on
                    // OemPermissionHelper.openDefaultAppsSettings for why),
                    // this is how the person gets to the exact same result
                    // without needing to force-close the app and hunt
                    // through Settings themselves.
                    OutlinedButton(
                        onClick = onOpenDefaultAppsSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Trouble with the dialog above? Open Settings", fontSize = 13.sp)
                    }
                }
                else -> Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = palette.accentSoft,
                        disabledContentColor = palette.accent
                    )
                ) {
                    Text("Already the default dialer", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun SetupChecklistItem(
    icon: ImageVector,
    title: String,
    done: Boolean,
    palette: DialerPalette
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(palette, 16.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, fontSize = 15.sp, color = palette.textPrimary, modifier = Modifier.weight(1f))
        if (done) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Done", tint = palette.accent, modifier = Modifier.size(22.dp))
        } else {
            Icon(Icons.Filled.Warning, contentDescription = "Not set up yet", tint = palette.danger, modifier = Modifier.size(20.dp))
        }
    }
}
