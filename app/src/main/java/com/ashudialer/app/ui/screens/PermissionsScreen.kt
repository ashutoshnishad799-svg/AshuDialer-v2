package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.telecom.OemPermissionHelper
import com.ashudialer.app.telecom.RecordingSetupChecker
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ashudialer.app.ui.components.glassCard

/**
 * The post-onboarding setup checklist: three concrete, checkable items
 * (phone/call permissions, default-dialer role, and whether call recording
 * can actually work on this device right now) rather than one generic
 * "grant permissions" button - so the person always sees exactly which
 * step is still outstanding, not just a single pass/fail state.
 *
 * Every card's status is re-read on every resume (see the ON_RESUME
 * DisposableEffect below), the same fix applied to isDefaultDialer/
 * hasPermissions in MainActivity and for the identical reason: any of
 * these can change from outside this screen entirely - granting phone
 * permissions from a system dialog, setting the default dialer from
 * Settings, changing system or device settings outside this screen - and this screen must
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
    val context = LocalContext.current

    // THE FIX for "app sometimes closes right when I open it" (the same
    // class of bug as OnboardingScreen's BackHandler - see that screen's
    // comment for the full explanation): this screen previously had no
    // BackHandler either, so a back press here fell through to the system
    // default and finished the Activity outright, right at the point a
    // fresh install is trying to get set up. Deliberately a no-op rather
    // than stepping back through anything (there's no previous step to
    // return to here - this is a flat single screen, not a multi-page
    // flow like Onboarding) - the person can still leave the app via the
    // home/recents gesture as normal, this only stops an accidental back
    // press from silently closing the whole app mid-setup.
    androidx.activity.compose.BackHandler(enabled = true) {}

    var recordingReady by remember { mutableStateOf<Boolean?>(null) }

    suspend fun refreshRecordingStatus() {
        recordingReady = withContext(Dispatchers.IO) {
            RecordingSetupChecker.isShizukuReady()
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch { refreshRecordingStatus() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        coroutineScope.launch { refreshRecordingStatus() }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            Spacer(Modifier.height(12.dp))
            SetupChecklistItem(
                icon = Icons.Filled.FiberManualRecord,
                title = "Call recordings ready",
                // Genuinely unknown until the probe above finishes, rather
                // than defaulting to either state - a false checkmark here
                // would tell someone recording works when it might not.
                done = recordingReady == true,
                pending = recordingReady == null,
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
    palette: DialerPalette,
    pending: Boolean = false
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
        when {
            pending -> Icon(Icons.Filled.Warning, contentDescription = "Checking", tint = palette.textSecondary, modifier = Modifier.size(20.dp))
            done -> Icon(Icons.Filled.CheckCircle, contentDescription = "Done", tint = palette.accent, modifier = Modifier.size(22.dp))
            else -> Icon(Icons.Filled.Warning, contentDescription = "Not set up yet", tint = palette.danger, modifier = Modifier.size(20.dp))
        }
    }
}
