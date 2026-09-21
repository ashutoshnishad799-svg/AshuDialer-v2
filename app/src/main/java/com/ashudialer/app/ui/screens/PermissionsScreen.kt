package com.ashudialer.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.LaunchedEffect
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
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlinx.coroutines.delay

/**
 * The post-onboarding setup checklist for the actual dialer only: phone/call
 * permissions and the default-dialer role. Call-recording/Shizuku setup is
 * deliberately kept out of this gate and lives in the dedicated recording
 * setup flow, so a normal user never needs Shizuku just to use the dialer.
 *
 * The lock-screen row is informational and optional: it never blocks
 * finishing setup. It only appears on Android 14+ where "Full screen
 * notifications" is a separate switch that can be off, in which case an
 * incoming call on a locked phone shows as a small banner instead of the
 * full call screen.
 *
 * Each row's status is re-read by MainActivity on every resume, so returning
 * from a system dialog or Settings updates the checkmarks immediately.
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

    // Keep this screen from being closed by an accidental back press in the
    // middle of first-time setup (there is no earlier step to return to).
    androidx.activity.compose.BackHandler(enabled = true) {}

    // Full-screen-intent state is re-read whenever the screen resumes, since
    // the switch lives in system Settings and changes outside this screen.
    var lockIssues by remember { mutableStateOf(OemPermissionHelper.lockScreenIssues(context)) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                lockIssues = OemPermissionHelper.lockScreenIssues(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Rows slide/fade in one after another instead of all appearing at once.
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    val allDone = hasPermissions && isDefaultDialer

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
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            // Header badge: a soft ring around the shield. The ring turns
            // to the "done" colour once both required steps are complete.
            val ringColor by animateColorAsState(
                targetValue = if (allDone) palette.callGreen else palette.accent,
                animationSpec = tween(500),
                label = "ring"
            )
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(ringColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(palette.accentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (allDone) Icons.Filled.CheckCircle else Icons.Filled.Shield,
                        contentDescription = null,
                        tint = ringColor,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                if (allDone) "You're all set" else "Set up Ashu Dialer",
                fontSize = 26.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (allDone) "Everything needed for calling is ready."
                else "Two quick steps and Ashu Dialer becomes your calling app.",
                fontSize = 14.sp, color = palette.textSecondary, textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(26.dp))

            StaggeredRow(visible = show, delayMillis = 0) {
                SetupChecklistItem(
                    icon = Icons.Filled.Phone,
                    title = "Phone & call access",
                    subtitle = "Lets the app place and answer calls",
                    done = hasPermissions,
                    palette = palette
                )
            }
            Spacer(Modifier.height(12.dp))
            StaggeredRow(visible = show, delayMillis = 90) {
                SetupChecklistItem(
                    icon = Icons.Filled.Shield,
                    title = "Default dialer",
                    subtitle = "Required so calls open in Ashu Dialer",
                    done = isDefaultDialer,
                    palette = palette
                )
            }

            // Calls on the lock screen: one card listing exactly what is still switched off (screen does not turn
            // on for a call, the call shows as a small notification, answering asks for the PIN). Optional - it never
            // blocks finishing setup - and it disappears by itself once everything is on.
            if (lockIssues.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                StaggeredRow(visible = show, delayMillis = 180) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(palette, 16.dp)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(38.dp).clip(CircleShape).background(palette.accentSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Calls on the lock screen", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
                                Text("Recommended", fontSize = 12.sp, color = palette.textSecondary)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Turn these on so an incoming call lights up the screen, opens full screen, and can be answered without your PIN:",
                            fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 17.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        lockIssues.forEach { issue ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(issue.title, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
                                    Text(issue.hint, fontSize = 11.5.sp, color = palette.textSecondary, lineHeight = 15.sp)
                                }
                                Spacer(Modifier.width(10.dp))
                                OutlinedButton(onClick = { OemPermissionHelper.openLockScreenIssue(context, issue.id) }) { Text("Open") }
                            }
                        }
                        if (OemPermissionHelper.isLikelyMiui() && lockIssues.any { it.id == "miui" }) {
                            Spacer(Modifier.height(4.dp))
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    OemPermissionHelper.confirmMiuiPermissions(context)
                                    lockIssues = OemPermissionHelper.lockScreenIssues(context)
                                }
                            ) { Text("I've turned the Xiaomi ones on") }
                        }
                    }
                }
            }

            if (allDone && OemPermissionHelper.isLikelyMiui()) {
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
                        Text("One more step for MIUI / HyperOS", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "MIUI can keep sending calls to its own Phone app even after you set Ashu Dialer as default. " +
                            "Turn on Autostart and remove battery restrictions for Ashu Dialer so calls, the lock-screen " +
                            "call UI, and missed-call notifications come from this app instead of MIUI's.",
                        fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 17.sp
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
            Spacer(Modifier.height(16.dp))
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
                    // Manual escape hatch: if the tap above opens a system dialog
                    // that never comes back (seen on some MIUI builds), this
                    // reaches the exact same result through Settings, which does
                    // not depend on that dialog returning a result.
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

/** Fades and slides its content up into place after [delayMillis]. */
@Composable
private fun StaggeredRow(visible: Boolean, delayMillis: Int, content: @Composable () -> Unit) {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(delayMillis.toLong())
            ready = true
        }
    }
    AnimatedVisibility(
        visible = ready,
        enter = fadeIn(tween(320)) + slideInVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            initialOffsetY = { it / 3 }
        )
    ) {
        content()
    }
}

@Composable
private fun SetupChecklistItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    done: Boolean,
    palette: DialerPalette
) {
    val badgeColor by animateColorAsState(
        targetValue = if (done) palette.callGreen.copy(alpha = 0.16f) else palette.accentSoft,
        animationSpec = tween(350),
        label = "badge"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(palette, 16.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(badgeColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (done) palette.callGreen else palette.accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
            Text(subtitle, fontSize = 12.sp, color = palette.textSecondary, lineHeight = 16.sp)
        }
        if (done) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Done", tint = palette.callGreen, modifier = Modifier.size(24.dp))
        } else {
            Icon(Icons.Filled.Warning, contentDescription = "Not set up yet", tint = palette.danger, modifier = Modifier.size(22.dp))
        }
    }
}
