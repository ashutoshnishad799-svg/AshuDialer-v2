package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.appcalls.recording.RecordingPrefs
import com.ashudialer.app.appcalls.scrcpy.ScrcpyAudioCodec
import com.ashudialer.app.appcalls.scrcpy.ScrcpyAudioSource
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette

/**
 * Every call-recording option, grouped the way Ever Dialer's recorder groups them:
 * what to record, filters, audio quality, where files go, notifications, auto-delete.
 * All values live in [RecordingPrefs] and take effect on the next call, no restart needed.
 */
@Composable
fun RecordingSettingsScreen(
    onBack: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenAppCallsSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current
    val prefs = remember { RecordingPrefs(context) }

    // One state holder per setting so the UI updates the moment a switch is flipped.
    var master by remember { mutableStateOf(prefs.callRecordingEnabled) }
    var autoIn by remember { mutableStateOf(prefs.autoRecordIncoming) }
    var autoOut by remember { mutableStateOf(prefs.autoRecordOutgoing) }
    var onAnswer by remember { mutableStateOf(prefs.recordOnAnswerOnly) }
    var ignoreAnon by remember { mutableStateOf(prefs.ignoreAnonymousIncoming) }
    var ignoreCcIn by remember { mutableStateOf(prefs.ignoreCrossCountryIncoming) }
    var ignoreCcOut by remember { mutableStateOf(prefs.ignoreCrossCountryOutgoing) }
    var contactsIn by remember { mutableStateOf(prefs.ignoreContactsModeIncoming) }
    var contactsOut by remember { mutableStateOf(prefs.ignoreContactsModeOutgoing) }
    var source by remember { mutableStateOf(prefs.audioSource) }
    var codec by remember { mutableStateOf(prefs.audioCodec) }
    var bitRate by remember { mutableStateOf(prefs.audioBitRate) }
    var storage by remember { mutableStateOf(prefs.storageMode) }
    var template by remember { mutableStateOf(prefs.fileNameTemplate) }
    var showNotif by remember { mutableStateOf(prefs.showRecordingNotification) }
    var postNotif by remember { mutableStateOf(prefs.postRecordingActionsNotification) }
    var vibrate by remember { mutableStateOf(prefs.vibrateOnStartStop) }
    var toasts by remember { mutableStateOf(prefs.showToasts) }
    var delTime by remember { mutableStateOf(prefs.autoDeleteByTimeEnabled) }
    var delTimeValue by remember { mutableStateOf(prefs.autoDeleteByTimeValue.toString()) }
    var delTimeUnit by remember { mutableStateOf(prefs.autoDeleteByTimeUnit) }
    var delSpace by remember { mutableStateOf(prefs.autoDeleteBySpaceEnabled) }
    var delSpaceValue by remember { mutableStateOf(prefs.autoDeleteBySpaceValue.toString()) }
    var delSpaceUnit by remember { mutableStateOf(prefs.autoDeleteBySpaceUnit) }
    var pickSource by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = palette.textPrimary) }
            Spacer(Modifier.width(4.dp))
            Text("Recording settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenGuide) { Icon(Icons.Filled.HelpOutline, "Guide", tint = palette.accent) }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(palette) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Record calls", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                            Text("Master switch for all call recording", fontSize = 12.sp, color = palette.textSecondary)
                        }
                        Switch(checked = master, onCheckedChange = { master = it; prefs.callRecordingEnabled = it })
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onOpenSetup, Modifier.fillMaxWidth()) { Text("Open setup checklist (Shizuku & permissions)", fontSize = 13.sp) }
                }
            }

            item { Label("What to record", palette) }
            item {
                Card(palette) {
                    Toggle("Incoming calls", null, autoIn, palette) { autoIn = it; prefs.autoRecordIncoming = it }
                    Toggle("Outgoing calls", null, autoOut, palette) { autoOut = it; prefs.autoRecordOutgoing = it }
                    Toggle("Only after the call is answered", "Skips ringing and unanswered calls", onAnswer, palette) { onAnswer = it; prefs.recordOnAnswerOnly = it }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = onOpenAppCallsSetup, Modifier.fillMaxWidth()) { Text("WhatsApp & Telegram call recording", fontSize = 13.sp) }
                }
            }

            item { Label("Filters", palette) }
            item {
                Card(palette) {
                    Toggle("Skip anonymous incoming calls", "Hidden / private numbers", ignoreAnon, palette) { ignoreAnon = it; prefs.ignoreAnonymousIncoming = it }
                    Toggle("Skip international incoming calls", null, ignoreCcIn, palette) { ignoreCcIn = it; prefs.ignoreCrossCountryIncoming = it }
                    Toggle("Skip international outgoing calls", null, ignoreCcOut, palette) { ignoreCcOut = it; prefs.ignoreCrossCountryOutgoing = it }
                    Spacer(Modifier.height(6.dp))
                    Choice("Incoming from contacts", contactsIn.label(), palette) {
                        contactsIn = contactsIn.next(); prefs.ignoreContactsModeIncoming = contactsIn
                    }
                    Choice("Outgoing to contacts", contactsOut.label(), palette) {
                        contactsOut = contactsOut.next(); prefs.ignoreContactsModeOutgoing = contactsOut
                    }
                }
            }

            item { Label("Audio", palette) }
            item {
                Card(palette) {
                    Choice("Audio source", source.label, palette) { pickSource = !pickSource }
                    if (pickSource) {
                        val sdk = android.os.Build.VERSION.SDK_INT
                        val common = ScrcpyAudioSource.phoneCallChoices.filter { it.minApi <= sdk }
                        val advancedList = ScrcpyAudioSource.advancedChoices.filter { it.minApi <= sdk }
                        common.forEach { option ->
                            SourceOption(option, option == source, palette) {
                                source = option; prefs.audioSource = option; pickSource = false
                            }
                        }
                        if (advancedList.isNotEmpty()) {
                            Choice("Advanced sources", if (showAdvanced) "Hide" else "Show ${advancedList.size} more", palette) {
                                showAdvanced = !showAdvanced
                            }
                            if (showAdvanced) {
                                advancedList.forEach { option ->
                                    SourceOption(option, option == source, palette) {
                                        source = option; prefs.audioSource = option; pickSource = false
                                    }
                                }
                            }
                        }
                    }
                    Choice("Format", codec.label, palette) {
                        val next = ScrcpyAudioCodec.entries[(codec.ordinal + 1) % ScrcpyAudioCodec.entries.size]
                        codec = next; prefs.audioCodec = next
                    }
                    Choice("Quality (bitrate)", if (bitRate <= 0) "Automatic (${codec.defaultBitRate / 1000} kbps)" else "${bitRate / 1000} kbps", palette) {
                        val steps = listOf(0, 16000, 32000, 64000, 128000)
                        bitRate = steps[(steps.indexOf(bitRate).let { if (it < 0) 0 else it } + 1) % steps.size]
                        prefs.audioBitRate = bitRate
                    }
                }
            }

            item { Label("Files", palette) }
            item {
                Card(palette) {
                    Choice(
                        "Save recordings in",
                        if (storage == RecordingPrefs.StorageMode.PUBLIC_MUSIC) "Music / Ashu Dialer (visible to other apps)" else "Private app storage (hidden)",
                        palette
                    ) {
                        storage = if (storage == RecordingPrefs.StorageMode.PUBLIC_MUSIC) RecordingPrefs.StorageMode.PRIVATE else RecordingPrefs.StorageMode.PUBLIC_MUSIC
                        prefs.storageMode = storage
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = template,
                        onValueChange = { template = it; prefs.fileNameTemplate = it.ifBlank { RecordingPrefs.DEFAULT_FILE_NAME_TEMPLATE } },
                        label = { Text("File name") },
                        supportingText = { Text("{contact_name} {phone_number} {date} {direction} {app_source} {cross_country}", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item { Label("Notifications & feedback", palette) }
            item {
                Card(palette) {
                    Toggle("Show recording notification", "Visible while a call is being recorded", showNotif, palette) { showNotif = it; prefs.showRecordingNotification = it }
                    Toggle("Open / Share / Delete after the call", null, postNotif, palette) { postNotif = it; prefs.postRecordingActionsNotification = it }
                    Toggle("Vibrate on start and stop", null, vibrate, palette) { vibrate = it; prefs.vibrateOnStartStop = it }
                    Toggle("Show toasts", null, toasts, palette) { toasts = it; prefs.showToasts = it }
                }
            }

            item { Label("Auto-delete", palette) }
            item {
                Card(palette) {
                    Toggle("Delete recordings older than...", null, delTime, palette) { delTime = it; prefs.autoDeleteByTimeEnabled = it }
                    if (delTime) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = delTimeValue,
                                onValueChange = { v -> delTimeValue = v.filter(Char::isDigit).take(4); delTimeValue.toIntOrNull()?.let { prefs.autoDeleteByTimeValue = it } },
                                singleLine = true, modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(onClick = { delTimeUnit = if (delTimeUnit == "days") "hours" else "days"; prefs.autoDeleteByTimeUnit = delTimeUnit }) { Text(delTimeUnit) }
                        }
                    }
                    Toggle("Keep total size under...", null, delSpace, palette) { delSpace = it; prefs.autoDeleteBySpaceEnabled = it }
                    if (delSpace) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = delSpaceValue,
                                onValueChange = { v -> delSpaceValue = v.filter(Char::isDigit).take(5); delSpaceValue.toIntOrNull()?.let { prefs.autoDeleteBySpaceValue = it } },
                                singleLine = true, modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(onClick = { delSpaceUnit = if (delSpaceUnit == "mb") "gb" else "mb"; prefs.autoDeleteBySpaceUnit = delSpaceUnit }) { Text(delSpaceUnit.uppercase()) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun RecordingPrefs.IgnoreContactsMode.label() = when (this) {
    RecordingPrefs.IgnoreContactsMode.NONE -> "Record everyone"
    RecordingPrefs.IgnoreContactsMode.ALL -> "Skip everyone in my contacts"
    RecordingPrefs.IgnoreContactsMode.SELECTED -> "Skip selected numbers only"
}

private fun RecordingPrefs.IgnoreContactsMode.next() =
    RecordingPrefs.IgnoreContactsMode.entries[(ordinal + 1) % RecordingPrefs.IgnoreContactsMode.entries.size]

@Composable
private fun SourceOption(option: ScrcpyAudioSource, selected: Boolean, palette: DialerPalette, onPick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) palette.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onPick)
            .padding(10.dp)
    ) {
        Text(option.label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
        Text(option.description, fontSize = 11.5.sp, color = palette.textSecondary)
    }
}

@Composable
private fun Label(text: String, palette: DialerPalette) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = palette.textSecondary, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
}

@Composable
private fun Card(palette: DialerPalette, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(14.dp), content = content)
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, palette: DialerPalette, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = palette.textPrimary)
            if (subtitle != null) Text(subtitle, fontSize = 11.5.sp, color = palette.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Choice(title: String, value: String, palette: DialerPalette, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp)) {
        Text(title, fontSize = 14.sp, color = palette.textPrimary)
        Text(value, fontSize = 12.sp, color = palette.accent)
    }
}
