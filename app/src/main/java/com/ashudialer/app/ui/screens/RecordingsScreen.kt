@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.ashudialer.app.ui.screens

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ashudialer.app.ui.components.glassCard


@Composable
fun RecordingsScreen(
    recordings: List<File>,
    onBack: () -> Unit,
    onPlay: (File) -> Unit,
    onShare: (File) -> Unit,
    onDelete: (File) -> Unit,
    onMoveToPrivateSpace: (List<File>) -> Unit = {},
    onOpenRecordingGuide: () -> Unit = {},
    onOpenRecordingSettings: () -> Unit = {},
    onOpenRecordingSetup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    // Which recording (if any) is currently expanded with the in-app player.
    // Only one at a time, matching how a mini-player normally behaves.
    var expandedFile by remember { mutableStateOf<File?>(null) }

    // Multi-select state: empty set means "not in selection mode" at all -
    // the row long-press handler enters selection mode by adding the first
    // file, and clearing back to empty (via the X in the selection app bar,
    // or removing the last selected item) exits it. This means selection
    // mode is fully derived from selectedFiles rather than needing its own
    // separate boolean that could get out of sync with the set's contents.
    var selectedFiles by remember { mutableStateOf(setOf<File>()) }
    val inSelectionMode = selectedFiles.isNotEmpty()

    // Separate pages for normal calls, WhatsApp, Telegram, Instagram and Snapchat (plus "All").
    var kind by remember { mutableStateOf(RecordingKind.ALL) }
    val visibleRecordings = remember(recordings, kind) {
        if (kind == RecordingKind.ALL) recordings else recordings.filter { kindOf(it) == kind }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (inSelectionMode) {
            SelectionAppBar(
                selectedCount = selectedFiles.size,
                onClose = { selectedFiles = emptySet() },
                onMoveToPrivateSpace = {
                    onMoveToPrivateSpace(selectedFiles.toList())
                    selectedFiles = emptySet()
                },
                onDeleteSelected = {
                    if (expandedFile in selectedFiles) expandedFile = null
                    selectedFiles.forEach { onDelete(it) }
                    selectedFiles = emptySet()
                },
                palette = palette
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
                }
                Spacer(Modifier.width(4.dp))
                Text("My Recordings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenRecordingSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Recording settings", tint = palette.textSecondary)
                }
            }
        }

        if (!inSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.accentSoft)
                    .clickable { onOpenRecordingSetup() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.HelpOutline, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Recording not working? Open the setup checklist (Shizuku & permissions). New to Shizuku? The ? button explains every step.",
                    fontSize = 12.5.sp, color = palette.textPrimary, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenRecordingGuide, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.HelpOutline, contentDescription = "Beginner guide", tint = palette.accent, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecordingKind.entries.forEach { k ->
                    val count = if (k == RecordingKind.ALL) recordings.size else recordings.count { kindOf(it) == k }
                    Text(
                        "${k.title} ($count)",
                        fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                        color = if (kind == k) palette.solidBackground else palette.textPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (kind == k) palette.accent else palette.accentSoft)
                            .clickable { kind = k }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (visibleRecordings.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text("No recordings yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (kind == RecordingKind.WHATSAPP || kind == RecordingKind.TELEGRAM || kind == RecordingKind.INSTAGRAM || kind == RecordingKind.SNAPCHAT)
                        "${kind.title} calls are recorded automatically once they are turned on in Recording settings."
                    else "Calls are recorded automatically once setup is finished. You can also tap Record during a call.",
                    fontSize = 13.sp, color = palette.textSecondary
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 12.dp)) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().glassCard(palette, 18.dp)
                    ) {
                        visibleRecordings.forEachIndexed { index, file ->
                            RecordingRow(
                                file = file,
                                expanded = expandedFile == file,
                                inSelectionMode = inSelectionMode,
                                selected = file in selectedFiles,
                                onToggleExpand = {
                                    expandedFile = if (expandedFile == file) null else file
                                },
                                onToggleSelect = {
                                    selectedFiles = if (file in selectedFiles) selectedFiles - file else selectedFiles + file
                                },
                                onLongPress = {
                                    if (!inSelectionMode) selectedFiles = setOf(file)
                                },
                                onShare = { onShare(file) },
                                onDelete = {
                                    if (expandedFile == file) expandedFile = null
                                    onDelete(file)
                                }
                            )
                            if (index != visibleRecordings.lastIndex) {
                                HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Replaces the normal title bar while one or more recordings are selected -
 * the standard Android "contextual action bar" pattern (Gmail, Photos,
 * Files all do this): a close button to cancel the selection, a count, and
 * the action(s) that apply to the current selection. Only one action exists
 * today (move to Private Space), but this bar is the natural place future
 * batch actions (e.g. bulk delete) would go too.
 */
@Composable
private fun SelectionAppBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onMoveToPrivateSpace: () -> Unit,
    onDeleteSelected: () -> Unit,
    palette: com.ashudialer.app.ui.theme.DialerPalette
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel selection", tint = palette.textPrimary)
        }
        Spacer(Modifier.width(4.dp))
        Text("$selectedCount selected", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
        IconButton(onClick = onMoveToPrivateSpace) {
            Icon(Icons.Filled.Lock, contentDescription = "Move to Private Space", tint = palette.accent)
        }
        IconButton(onClick = { showDeleteConfirm = true }) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete selected", tint = palette.danger)
        }
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (selectedCount == 1) "Delete recording?" else "Delete $selectedCount recordings?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteSelected()
                }) {
                    Text("Delete", color = palette.danger)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RecordingRow(
    file: File,
    expanded: Boolean,
    inSelectionMode: Boolean,
    selected: Boolean,
    onToggleExpand: () -> Unit,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalDialerPalette.current
    val label = remember(file.name) { parseRecordingLabel(file.name) }
    // Lifted out of InlineAudioPlayer so the row's own leading icon can
    // reflect real playback state instead of just "is this row expanded."
    // Previously the leading icon showed Pause the instant a row expanded -
    // regardless of whether audio had actually started - because it was
    // driven by `expanded`, a completely different piece of state than
    // InlineAudioPlayer's internal `isPlaying` (which starts false and only
    // flips true once MediaPlayer's prepareAsync() finishes). That's why
    // both icons could show "Pause" simultaneously right after tapping play:
    // the row said "expanded = true" instantly, while actual playback was
    // still a beat behind.
    var isPlaying by remember(file) { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (inSelectionMode) onToggleSelect() else onToggleExpand() },
                    onLongClick = onLongPress
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // In selection mode, every row's leading icon becomes a
            // selection checkmark instead of its own play button - tapping
            // anywhere on the row toggles selection rather than starting
            // playback, so the play affordance would be misleading here.
            if (inSelectionMode) {
                IconButton(onClick = onToggleSelect, modifier = Modifier.size(38.dp)) {
                    Icon(
                        if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (selected) "Selected" else "Not selected",
                        tint = if (selected) palette.accent else palette.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(palette.accentSoft)
                ) {
                    Icon(
                        if (expanded && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (expanded && isPlaying) "Pause" else "Play",
                        tint = palette.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label.first, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary, maxLines = 1)
                Text(label.second, fontSize = 12.5.sp, color = palette.textSecondary)
            }
            if (!inSelectionMode) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = "Share", tint = palette.textSecondary, modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = palette.danger, modifier = Modifier.size(19.dp))
                }
            }
        }

        AnimatedVisibility(visible = expanded && !inSelectionMode, enter = expandVertically(), exit = shrinkVertically()) {
            InlineAudioPlayer(
                file = file,
                palette = palette,
                isPlaying = isPlaying,
                onIsPlayingChange = { isPlaying = it }
            )
        }
    }
}

/**
 * A real, self-contained in-app player for a single recording - play/pause,
 * a scrubbable progress bar, and elapsed/total time - instead of handing
 * the file off to whatever external audio app the person happens to have
 * installed. Backed by a plain MediaPlayer scoped to this composable's
 * lifetime, released whenever the row collapses or the screen leaves.
 *
 * isPlaying is owned by the parent RecordingRow (not local state here
 * anymore) so the row's leading play/pause icon and this player's own
 * play/pause button always agree - see the comment on RecordingRow's
 * isPlaying for why that split used to let the two icons disagree.
 */
@Composable
private fun InlineAudioPlayer(
    file: File,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    isPlaying: Boolean,
    onIsPlayingChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var player by remember(file) { mutableStateOf<MediaPlayer?>(null) }
    var positionMs by remember(file) { mutableStateOf(0) }
    var durationMs by remember(file) { mutableStateOf(0) }
    var isSeeking by remember(file) { mutableStateOf(false) }
    var loadError by remember(file) { mutableStateOf(false) }

    DisposableEffect(file) {
        // Belt-and-braces alongside the AudioAttributes fix above: even
        // with USAGE_MEDIA set on the player itself, the shared
        // AudioManager can still be left in AudioManager.MODE_IN_COMMUNICATION
        // (Telecom sets this while a call is active and it isn't always
        // guaranteed to have been reset back to MODE_NORMAL by the time this
        // screen is opened, e.g. opening Recordings right after ending a
        // call). In that mode, audio can get routed toward the earpiece at
        // call-volume levels rather than the normal media/speaker path -
        // which sounds exactly like "no audio" if the phone isn't held to
        // an ear. Forcing MODE_NORMAL before playback starts, and restoring
        // whatever mode was active before once this player is torn down,
        // guarantees this screen's playback always uses the same audio path
        // a music or voice-message app would, regardless of what the rest
        // of the app was doing right before.
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        val previousMode = audioManager?.mode
        try {
            audioManager?.mode = android.media.AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }

        val mp = MediaPlayer().apply {
            try {
                // Explicitly setting AudioAttributes with USAGE_MEDIA here is
                // the actual fix for recordings playing back completely
                // silent - MediaPlayer's *default* audio attributes without
                // this call are supposed to already be USAGE_MEDIA, but this
                // app spends a lot of its lifetime routing call audio via
                // Telecom (which manipulates the shared AudioManager's mode
                // and active audio stream while a call is up). Once that
                // happens, a MediaPlayer created without an explicit
                // AudioAttributes call can inherit stale routing from
                // whatever stream was last active - it "plays" (no error,
                // progress bar moves, duration is correct) but the actual
                // audio never reaches the speaker/earpiece at an audible
                // level. Setting this explicitly on every playback, every
                // time, forces it onto the normal media stream regardless of
                // whatever audio state the rest of the app left behind.
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                val recordingUri = resolveRecordingUri(context, file)
                if (recordingUri != null) {
                    setDataSource(context, recordingUri)
                } else {
                    setDataSource(file.absolutePath)
                }
                setOnPreparedListener {
                    durationMs = duration
                    // Belt-and-braces alongside the AudioAttributes fix
                    // above: on some hardware, a MediaPlayer playing a
                    // mono, low-sample-rate stream (this app's recordings
                    // are 16kHz or 8kHz mono - see CallRecorder.kt) has
                    // been observed to leave its own internal playback
                    // volume at effectively zero until setVolume is called
                    // explicitly, independent of the device's system media
                    // volume slider. Setting it to full here costs nothing
                    // when it was already fine, and fixes it on the
                    // hardware where it wasn't.
                    try {
                        @Suppress("DEPRECATION")
                        setVolume(1f, 1f)
                    } catch (_: Exception) {
                    }
                    start()
                    onIsPlayingChange(true)
                }
                setOnCompletionListener {
                    onIsPlayingChange(false)
                    positionMs = 0
                }
                setOnErrorListener { _, _, _ ->
                    // A recording that fell back to MIC-only capture with
                    // near-silent audio can still fail to *decode* if the
                    // file itself is malformed (e.g. the app was killed
                    // mid-recording before stop() finished writing the MPEG-4
                    // container's trailer). Surfacing this explicitly as
                    // "Couldn't play this recording" is more honest than
                    // leaving the row looking like it's silently doing
                    // nothing when tapped.
                    loadError = true
                    onIsPlayingChange(false)
                    true
                }
                prepareAsync()
            } catch (_: Exception) {
                loadError = true
            }
        }
        player = mp
        onDispose {
            try {
                mp.stop()
            } catch (_: Exception) {
            }
            mp.release()
            player = null
            try {
                if (previousMode != null) audioManager?.mode = previousMode
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(player, isPlaying) {
        while (isPlaying && !isSeeking) {
            val current = try { player?.currentPosition } catch (_: Exception) { null }
            if (current != null) positionMs = current
            delay(200)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (loadError) {
            Text(
                "Couldn't play this recording — the file may be incomplete.",
                fontSize = 12.sp,
                color = palette.danger,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            return@Column
        }
        Slider(
            value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
            onValueChange = { fraction ->
                isSeeking = true
                positionMs = (fraction * durationMs).toInt()
            },
            onValueChangeFinished = {
                try {
                    player?.seekTo(positionMs)
                } catch (_: Exception) {
                }
                isSeeking = false
            },
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.cardBorder
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatMs(positionMs), fontSize = 11.5.sp, color = palette.textSecondary)
            IconButton(
                onClick = {
                    val mp = player ?: return@IconButton
                    try {
                        if (isPlaying) {
                            mp.pause()
                            onIsPlayingChange(false)
                        } else {
                            mp.start()
                            onIsPlayingChange(true)
                        }
                    } catch (_: Exception) {
                    }
                },
                modifier = Modifier.size(34.dp).clip(CircleShape).background(palette.accentSoft)
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(formatMs(durationMs), fontSize = 11.5.sp, color = palette.textSecondary)
        }
    }
}

private fun resolveRecordingUri(context: Context, file: File): Uri? {
    return try {
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATA)
        val selection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
        } else {
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
        }
        val args = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            arrayOf(file.name, "Music/Ashu Dialer/")
        } else arrayOf(file.name)
        context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                return MediaStore.Audio.Media.getContentUri(
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) MediaStore.VOLUME_EXTERNAL_PRIMARY else "external"
                ).buildUpon().appendPath(id.toString()).build()
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}

private fun formatMs(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}


private fun parseRecordingLabel(fileName: String): Pair<String, String> {
    val withoutExt = fileName.substringBeforeLast(".")
    val parts = withoutExt.split("_").toMutableList()

    // Optional trailing direction token added by the new file-name template.
    var direction: String? = null
    if (parts.size >= 4 && parts.last().lowercase() in setOf("in", "out")) {
        direction = if (parts.removeAt(parts.lastIndex).lowercase() == "in") "Incoming" else "Outgoing"
    }
    if (parts.size < 3) return withoutExt to (direction ?: "")

    val caller = parts.dropLast(2).joinToString("_").ifBlank { "Unknown" }
    val datePart = parts[parts.size - 2]
    val timePart = parts[parts.size - 1]
    return try {
        val parsed = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse("${datePart}_$timePart")
        val formatted = if (parsed != null) SimpleDateFormat("MMM d, yyyy \u2022 h:mm a", Locale.getDefault()).format(parsed) else ""
        caller to listOfNotNull(direction, formatted.ifBlank { null }).joinToString(" \u2022 ")
    } catch (_: Exception) {
        caller to (direction ?: "")
    }
}

/** The pages of the Recordings list: everything, phone calls, and one page per supported calling app. */
private enum class RecordingKind(val title: String) {
    ALL("All"), PHONE("Phone"), WHATSAPP("WhatsApp"), TELEGRAM("Telegram"), INSTAGRAM("Instagram"), SNAPCHAT("Snapchat")
}

/**
 * Which tab a recording belongs in.
 *
 * The FOLDER is checked first (Music/Ashu Dialer/WhatsApp/... - see RecordingStorage.subFolderFor),
 * because that is always right, including when the person edited the file-name template and the
 * app's name no longer appears in the file name. The file name is the fallback, which is what
 * recordings made by older versions (saved directly in "Ashu Dialer/") rely on.
 */
private fun kindOf(file: File): RecordingKind {
    val folder = file.parentFile?.name?.lowercase().orEmpty()
    val n = file.name.lowercase()
    return when {
        folder == "whatsapp" -> RecordingKind.WHATSAPP
        folder == "telegram" -> RecordingKind.TELEGRAM
        folder == "instagram" -> RecordingKind.INSTAGRAM
        folder == "snapchat" -> RecordingKind.SNAPCHAT
        folder == "phone" -> RecordingKind.PHONE
        n.startsWith("whatsapp") -> RecordingKind.WHATSAPP
        n.startsWith("telegram") -> RecordingKind.TELEGRAM
        n.startsWith("instagram") -> RecordingKind.INSTAGRAM
        n.startsWith("snapchat") -> RecordingKind.SNAPCHAT
        else -> RecordingKind.PHONE
    }
}
