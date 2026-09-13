package com.ashudialer.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.telecom.RecordingSetupChecker
import com.ashudialer.app.telecom.SetupCheckResult
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ashudialer.app.ui.components.glassCard

/**
 * Root/Superuser access (su) alone does NOT unlock two-way call recording -
 * that's a common misunderstanding worth correcting up front. VOICE_CALL is
 * gated by the signature|privileged permission CAPTURE_AUDIO_OUTPUT, which
 * Android's audio policy service checks based on whether the app is
 * installed as a priv-app with that permission explicitly allow-listed -
 * not based on su/shell access. Having su lets someone install a system
 * app (which is what the Magisk module below does), but su by itself,
 * without becoming a priv-app, will not make VOICE_CALL work. This page
 * says that plainly rather than implying "grant root = done".
 */
@Composable
fun RootRecordingSetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isPrivApp by remember { mutableStateOf(RecordingSetupChecker.isRunningAsPrivApp(context)) }
    var voiceCallResult by remember { mutableStateOf<SetupCheckResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var sourceProbeResult by remember {
        mutableStateOf<com.ashudialer.app.telecom.AudioSourceProbeResult?>(null)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Root setup", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Text(
                    "Superuser access by itself is not enough",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Granting Ashu Dialer Superuser (su) access — the kind you manage from your Superuser/Magisk app — does not turn on two-way recording by itself. Android gates the two-way call-audio channel behind a separate check: whether the app is installed as a privileged system app with one specific permission explicitly allowed. Su gives shell access; it doesn't change where the app is installed. The Magisk module below is what actually does that.",
                    fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 19.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Some devices go further and block microphone access to every app (not just this one) for the entire duration of a call, regardless of which recording permission is granted — confirmed directly on at least one device, where even the plain unprivileged microphone recorded complete silence for almost the whole call. If recording still comes back silent after everything below shows granted, that's the likely cause, and it isn't fixable from inside any app. Turning on speakerphone during the call is the one thing that can still work around it, since audio traveling through the air can reach the mic even when the internal call-audio path is blocked.",
                    fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 19.sp
                )
                Spacer(Modifier.height(24.dp))
            }

            item {
                Text("Setup steps", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary)
                Spacer(Modifier.height(10.dp))
                Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(16.dp)) {
                    StepRow(1, "Download or build the Ashu Dialer Magisk module (a .zip) from the module's release page.")
                    StepRow(2, "Open Magisk → Modules → Install from storage → pick the downloaded .zip.")
                    StepRow(3, "Reboot when Magisk asks you to.")
                    StepRow(4, "Come back to this screen and press Check below.")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Important: the module's own .zip must be rebuilt from the exact same " +
                        "APK you have installed as Ashu Dialer. If you update or reinstall the " +
                        "app afterward (sideload, Play Store, or a fresh build) without " +
                        "reflashing a matching module, the priv-app permission grant silently " +
                        "stops applying to your actual running app - the \"CAPTURE_AUDIO_OUTPUT " +
                        "permission\" check below is what catches that mismatch.",
                    fontSize = 11.5.sp, color = palette.textSecondary, lineHeight = 17.sp
                )
                Spacer(Modifier.height(20.dp))
            }

            item {
                StatusCard(
                    title = "Installed as system app",
                    statusLabel = if (isPrivApp) "Yes — module is active" else "No — module not installed/active yet",
                    statusGood = isPrivApp,
                    palette = palette
                ) {
                    OutlinedButton(
                        onClick = {
                            val opened = try {
                                val launch = context.packageManager.getLaunchIntentForPackage("com.topjohnwu.magisk")
                                if (launch != null) { context.startActivity(launch); true } else false
                            } catch (_: Exception) { false }
                            if (!opened) {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/topjohnwu/Magisk/releases"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Magisk", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { isPrivApp = RecordingSetupChecker.isRunningAsPrivApp(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Re-check", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                var hasCaptureAudioOutput by remember {
                    mutableStateOf(RecordingSetupChecker.hasCaptureAudioOutputPermission(context))
                }
                StatusCard(
                    title = "CAPTURE_AUDIO_OUTPUT permission",
                    statusLabel = if (hasCaptureAudioOutput) "Granted by PackageManager" else "Not granted - this is the actual blocker",
                    statusGood = hasCaptureAudioOutput,
                    palette = palette
                ) {
                    // THE authoritative check, deliberately separate from
                    // "Installed as system app" above: that one only
                    // confirms a file sits at a /priv-app/ path, which
                    // Magisk's Magic Mount can report as successful even
                    // when PackageManager itself still refuses to treat
                    // the install as privileged (a signature mismatch, or
                    // the APK actually running being a newer sideload/
                    // Play-installed build than whatever is bundled
                    // inside the currently-flashed Magisk module zip, are
                    // the two most common real causes). If this card
                    // shows "Not granted" while "Installed as system app"
                    // above shows green, that gap - not anything the app's
                    // own recording code does - is what's actually
                    // stopping VOICE_CALL/VOICE_UPLINK/VOICE_DOWNLINK: no
                    // amount of retrying the recorder can substitute for a
                    // permission PackageManager itself hasn't granted.
                    if (!hasCaptureAudioOutput) {
                        Text(
                            "If \"Installed as system app\" above is green but this is still " +
                                "not granted, the file is mounted at the right path but " +
                                "PackageManager isn't actually treating this install as " +
                                "privileged. The single most common cause: the APK bundled " +
                                "inside the currently-flashed Magisk module .zip is an older " +
                                "build than whatever is actually running as Ashu Dialer right " +
                                "now (e.g. you've since sideloaded/updated the app separately " +
                                "from Play Store or a direct APK, without rebuilding and " +
                                "reflashing the Magisk module to match). Rebuild the Magisk " +
                                "module from the exact same APK you have installed, reflash it, " +
                                "and reboot - a mismatched APK inside the module is invisible to " +
                                "\"Installed as system app\" but is exactly what this permission " +
                                "check catches.",
                            fontSize = 11.5.sp, color = palette.textSecondary, lineHeight = 17.sp
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedButton(
                        onClick = { hasCaptureAudioOutput = RecordingSetupChecker.hasCaptureAudioOutputPermission(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Re-check", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                StatusCard(
                    title = "Two-way audio source test",
                    statusLabel = when (voiceCallResult) {
                        SetupCheckResult.GRANTED -> "Working — call-audio source is available"
                        SetupCheckResult.NOT_GRANTED -> "Not available — module may need a reboot, or isn't installed"
                        else -> "Not tested yet"
                    },
                    statusGood = voiceCallResult == SetupCheckResult.GRANTED,
                    palette = palette
                ) {
                    Button(
                        onClick = {
                            isChecking = true
                            scope.launch {
                                val (result, sources) = withContext(Dispatchers.IO) {
                                    RecordingSetupChecker.checkVoiceCallSourceAvailable(context) to
                                        RecordingSetupChecker.checkAllAudioSources(context)
                                }
                                voiceCallResult = result
                                sourceProbeResult = sources
                                isChecking = false
                            }
                        },
                        enabled = !isChecking,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isChecking) "Testing…" else "Test now", color = Color.White)
                    }
                    Text(
                        "Runs a one-second silent test, not a real call — safe to run any time.",
                        fontSize = 11.sp, color = palette.textSecondary, modifier = Modifier.padding(top = 8.dp)
                    )
                    // Shown once a test has actually run, so it's clear
                    // exactly which underlying MediaRecorder source (not
                    // just VOICE_CALL) this device's audio policy grants -
                    // "not available" on VOICE_CALL alone doesn't say
                    // whether uplink/downlink work instead, which is the
                    // difference between "recording will capture nothing"
                    // and "recording will only capture your own mic side".
                    sourceProbeResult?.let { probe ->
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Per-source result",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        SourceResultRow("VOICE_CALL (both sides, combined)", probe.voiceCall, palette)
                        SourceResultRow("VOICE_UPLINK (your mic only)", probe.voiceUplink, palette)
                        SourceResultRow("VOICE_DOWNLINK (other side only)", probe.voiceDownlink, palette)
                        SourceResultRow("VOICE_COMMUNICATION (fallback, mic-only)", probe.voiceCommunication, palette)
                        if (!probe.voiceCommunication) {
                            // VOICE_COMMUNICATION needs no special
                            // permission at all - just RECORD_AUDIO,
                            // which this screen can only be showing if
                            // hasPermissions was already true. This
                            // failing too (not just VOICE_CALL/UPLINK/
                            // DOWNLINK) means the problem isn't the
                            // priv-app/CAPTURE_AUDIO_OUTPUT grant at all -
                            // something more basic is blocking audio
                            // capture for this app across the board.
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "VOICE_COMMUNICATION failing too is unusual - that source needs " +
                                    "no special permission, only ordinary microphone access. This " +
                                    "points at something blocking audio capture for this app " +
                                    "generally, not specifically the call-audio permission: " +
                                    "another app currently holding the microphone, microphone " +
                                    "access disabled for this app in system Settings, or a " +
                                    "device-wide microphone mute/restriction. Close other apps " +
                                    "that might be using the mic and check Settings → Apps → Ashu " +
                                    "Phone → Permissions → Microphone, then try again.",
                                fontSize = 11.5.sp, color = palette.textSecondary, lineHeight = 17.sp
                            )
                        } else if (!probe.voiceCall && !probe.voiceUplink && !probe.voiceDownlink) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "This test checks each source with no call active, so it can " +
                                    "only tell you whether the device's audio policy lets this " +
                                    "app open the source at all - not whether a real call " +
                                    "actually sounds right on it. If these are still unavailable " +
                                    "after installing the module and rebooting, this device's " +
                                    "vendor audio policy is blocking that source for this app " +
                                    "entirely (not fixable from inside the app). Start an actual " +
                                    "call to see which source recording really uses - VOICE_" +
                                    "COMMUNICATION can pass this idle test purely from ambient " +
                                    "room noise even when it won't be the source used during a " +
                                    "real call. Until a source is granted, recording falls back " +
                                    "to microphone-only.",
                                fontSize = 11.5.sp, color = palette.textSecondary, lineHeight = 17.sp
                            )
                        }
                        if (probe.failureReasons.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Technical detail",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary
                            )
                            Spacer(Modifier.height(4.dp))
                            val sourceNames = mapOf(
                                android.media.MediaRecorder.AudioSource.VOICE_CALL to "VOICE_CALL",
                                android.media.MediaRecorder.AudioSource.VOICE_UPLINK to "VOICE_UPLINK",
                                android.media.MediaRecorder.AudioSource.VOICE_DOWNLINK to "VOICE_DOWNLINK",
                                android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION"
                            )
                            probe.failureReasons.forEach { (source, reason) ->
                                Text(
                                    "${sourceNames[source] ?: source.toString()}: $reason",
                                    fontSize = 10.5.sp, color = palette.textSecondary, lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    val palette = LocalDialerPalette.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("$number.", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = palette.accent, modifier = Modifier.width(20.dp))
        Text(text, fontSize = 12.5.sp, color = palette.textPrimary, lineHeight = 18.sp)
    }
}

@Composable
private fun SourceResultRow(label: String, available: Boolean, palette: com.ashudialer.app.ui.theme.DialerPalette) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (available) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (available) palette.accent else palette.textSecondary,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.5.sp, color = palette.textSecondary)
    }
}

@Composable
private fun StatusCard(
    title: String,
    statusLabel: String,
    statusGood: Boolean,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(16.dp)) {
        Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (statusGood) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                tint = if (statusGood) palette.accent else palette.textSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(statusLabel, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (statusGood) palette.accent else palette.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}
