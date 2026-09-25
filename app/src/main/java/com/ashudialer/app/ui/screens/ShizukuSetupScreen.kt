package com.ashudialer.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.ashudialer.app.appcalls.ShizukuConnectionManager
import com.ashudialer.app.appcalls.recording.RecordingPrefs
import com.ashudialer.app.telecom.RecordingSetupChecker
import com.ashudialer.app.telecom.SetupStatus
import com.ashudialer.app.telecom.SetupStep
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.theme.LocalDialerPalette
import rikka.shizuku.Shizuku

private const val SHIZUKU_PLAY_URL = "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"
private const val SHIZUKU_GITHUB_URL = "https://github.com/RikkaApps/Shizuku/releases/latest"
private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

/**
 * The whole call-recording setup in one place, modelled on Ever Dialer's permission flow:
 * a live checklist (every row turns green as it is done), a button on every row that fixes
 * exactly that step, a beginner guide, a separate path for rooted phones, and the
 * auto-start options. Status refreshes every time the screen comes back to the foreground,
 * so returning from Shizuku / Settings updates the ticks by itself.
 */
@Composable
fun ShizukuSetupScreen(
    onBack: () -> Unit,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current
    val prefs = remember { RecordingPrefs(context) }

    var status by remember { mutableStateOf<SetupStatus?>(null) }
    fun refresh() { status = RecordingSetupChecker.check(context) }

    // Re-check on every resume, and react live when Shizuku starts/stops or grants permission.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        val onBinder = Shizuku.OnBinderReceivedListener { refresh() }
        val onDead = Shizuku.OnBinderDeadListener { refresh() }
        val onPerm = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(onBinder)
            Shizuku.addBinderDeadListener(onDead)
            Shizuku.addRequestPermissionResultListener(onPerm)
        }
        refresh()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching {
                Shizuku.removeBinderReceivedListener(onBinder)
                Shizuku.removeBinderDeadListener(onDead)
                Shizuku.removeRequestPermissionResultListener(onPerm)
            }
        }
    }

    // Runtime-permission launchers (each refreshes the checklist when the dialog closes).
    val singlePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    fun openShizukuApp() {
        val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (launch != null) context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) else openUrl(SHIZUKU_GITHUB_URL)
    }

    /** What tapping a row's button does. */
    fun actFor(step: SetupStep) {
        when (step) {
            SetupStep.SHIZUKU_INSTALLED -> openUrl(SHIZUKU_GITHUB_URL)
            SetupStep.SHIZUKU_RUNNING -> openShizukuApp()
            SetupStep.SHIZUKU_PERMISSION -> {
                if (!RecordingSetupChecker.isShizukuRunning()) openShizukuApp()
                else runCatching { ShizukuConnectionManager.requestPermission() }
            }
            SetupStep.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= 33) singlePermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
            SetupStep.PHONE_STATE -> singlePermission.launch(Manifest.permission.READ_PHONE_STATE)
            SetupStep.CONTACTS -> singlePermission.launch(Manifest.permission.READ_CONTACTS)
            SetupStep.CALL_LOG -> singlePermission.launch(Manifest.permission.READ_CALL_LOG)
            SetupStep.BATTERY -> runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                )
            }.onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
            SetupStep.STORAGE -> singlePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            SetupStep.NOTIFICATION_ACCESS -> runCatching {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
    }

    fun buttonLabel(step: SetupStep, installed: Boolean): String = when (step) {
        SetupStep.SHIZUKU_INSTALLED -> "Get Shizuku"
        SetupStep.SHIZUKU_RUNNING -> if (installed) "Open Shizuku" else "Get Shizuku"
        SetupStep.SHIZUKU_PERMISSION -> "Allow"
        SetupStep.BATTERY -> "Remove limits"
        SetupStep.NOTIFICATION_ACCESS -> "Open settings"
        else -> "Grant"
    }

    var recordingOn by remember { mutableStateOf(prefs.callRecordingEnabled) }
    var autoManage by remember { mutableStateOf(prefs.shizukuAutoManage) }
    var startOnRecordOnly by remember { mutableStateOf(prefs.shizukuStartOnRecordOnly) }
    var keepAlive by remember { mutableStateOf(prefs.shizukuKeepAlive) }
    var authKey by remember { mutableStateOf(prefs.shizukuAuthKey) }
    var showRootHelp by remember { mutableStateOf(false) }

    val s = status
    val ready = s?.readyForPhoneCalls == true

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = palette.textPrimary) }
            Spacer(Modifier.width(4.dp))
            Text("Call recording setup", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenGuide) { Icon(Icons.Filled.HelpOutline, "Beginner guide", tint = palette.accent) }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ---- Headline status + master switch --------------------------------------
            item {
                Column(Modifier.fillMaxWidth().glassCard(palette, 18.dp).padding(16.dp)) {
                    Text(
                        if (ready) "Ready to record" else "Setup not finished",
                        fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        color = if (ready) palette.accent else palette.textPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (ready) "Every required step is done. Turn the switch on and calls will be recorded automatically."
                        else "Finish the required steps below (they turn green as you go). Never used Shizuku? Tap the ? button above for a step-by-step guide.",
                        fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onOpenGuide,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Beginner guide: set up Shizuku step by step", fontSize = 13.sp) }
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        title = "Record calls",
                        subtitle = if (ready) "Records incoming and outgoing calls" else "Finish the required steps first",
                        checked = recordingOn,
                        enabled = ready || recordingOn,
                        onChange = { on -> recordingOn = on; prefs.callRecordingEnabled = on },
                        palette = palette
                    )
                }
            }

            // ---- The permission checklist, in Ever's order ------------------------------
            item { ShizukuSectionLabel("Steps", palette) }
            val installed = s?.isDone(SetupStep.SHIZUKU_INSTALLED) == true
            SetupStep.entries.forEach { step ->
                item(key = step.name) {
                    val done = s?.isDone(step) == true
                    // Steps that come after Shizuku itself stay disabled until Shizuku is running, mirroring Ever.
                    StepRow(
                        title = step.title,
                        why = step.why,
                        required = step.required,
                        done = done,
                        buttonLabel = buttonLabel(step, installed),
                        onAction = { actFor(step) },
                        palette = palette
                    )
                }
            }

            // ---- Shizuku auto-start ------------------------------------------------------
            item { ShizukuSectionLabel("Shizuku auto-start (optional)", palette) }
            item {
                Column(Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(16.dp)) {
                    Text(
                        "Shizuku stops after a reboot. If you turn this on, Ashu Dialer starts it for you when a call needs recording. " +
                            "In Shizuku open Settings > 'Start via intent' and copy the authorization key here. Needs Shizuku 13.6+.",
                        fontSize = 12.sp, color = palette.textSecondary, lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    SwitchRow("Let Ashu Dialer manage Shizuku", null, autoManage, true,
                        { autoManage = it; prefs.shizukuAutoManage = it }, palette)
                    if (autoManage) {
                        SwitchRow("Start only when recording", "Otherwise starts as soon as a call begins", startOnRecordOnly, true,
                            { startOnRecordOnly = it; prefs.shizukuStartOnRecordOnly = it }, palette)
                        SwitchRow("Keep Shizuku running afterwards", "Don't stop it when the recording ends", keepAlive, true,
                            { keepAlive = it; prefs.shizukuKeepAlive = it }, palette)
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = authKey,
                            onValueChange = { authKey = it; prefs.shizukuAuthKey = it },
                            label = { Text("Shizuku authorization key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ---- Rooted phone shortcut ---------------------------------------------------
            item { ShizukuSectionLabel("Rooted phone?", palette) }
            item {
                Column(Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(16.dp)) {
                    Text("Shizuku works even better with root: it can start by itself at every boot.", fontSize = 12.5.sp, color = palette.textPrimary, lineHeight = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showRootHelp = !showRootHelp }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showRootHelp) "Hide root steps" else "Show root steps", fontSize = 13.sp)
                    }
                    if (showRootHelp) {
                        Spacer(Modifier.height(10.dp))
                        listOf(
                            "1. Install the Shizuku app (button above).",
                            "2. Open Shizuku and tap \"Start\" under \"Start via root\". Your root manager (Magisk / KernelSU) asks for permission: choose Grant.",
                            "3. Come back here. The Shizuku steps turn green.",
                            "4. Tap \"Allow\" on the Shizuku permission step. A dialog asks whether Ashu Dialer may use Shizuku: allow it.",
                            "5. If it does not appear: in Shizuku open \"Authorized applications\" and switch Ashu Dialer ON.",
                            "6. In Shizuku Settings turn on \"Start on boot (root)\" so you never have to restart it."
                        ).forEach {
                            Text(it, fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 3.dp))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ---- Small UI pieces ---------------------------------------------------------------------

@Composable
private fun ShizukuSectionLabel(text: String, palette: com.ashudialer.app.ui.theme.DialerPalette) {
    Text(
        text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
        color = palette.textSecondary, modifier = Modifier.padding(top = 10.dp, start = 4.dp)
    )
}

@Composable
private fun StepRow(
    title: String,
    why: String,
    required: Boolean,
    done: Boolean,
    buttonLabel: String,
    onAction: () -> Unit,
    palette: com.ashudialer.app.ui.theme.DialerPalette
) {
    Row(
        modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (done) "Done" else "Not done",
            tint = if (done) palette.accent else palette.textSecondary,
            modifier = Modifier.size(22.dp).padding(top = 1.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary, modifier = Modifier.weight(1f, fill = false))
                if (!required) {
                    Spacer(Modifier.width(6.dp))
                    Text("optional", fontSize = 10.sp, color = palette.textSecondary)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(why, fontSize = 12.sp, color = palette.textSecondary, lineHeight = 16.sp)
            if (!done) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onAction, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(buttonLabel, fontSize = 12.5.sp)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    palette: com.ashudialer.app.ui.theme.DialerPalette
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = palette.textPrimary)
            if (subtitle != null) Text(subtitle, fontSize = 11.5.sp, color = palette.textSecondary)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
