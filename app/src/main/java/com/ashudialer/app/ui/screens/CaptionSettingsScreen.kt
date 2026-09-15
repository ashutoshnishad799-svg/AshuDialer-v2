package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.telecom.CaptionLanguage
import com.ashudialer.app.telecom.CaptionModelManager
import com.ashudialer.app.telecom.CaptionModelState
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlinx.coroutines.launch

/**
 * The one consolidated page for both live captions (seeing what the other
 * person says, as text) and type-to-talk (typing something to have it
 * spoken into the call) - the person explicitly asked for a single page
 * covering both rather than these being scattered across separate screens.
 *
 * Every toggle here starts off (see AppSettings.liveCaptionsEnabled /
 * typeToTalkEnabled) - turning a feature on is always something the person
 * does deliberately from here, never a silent default.
 */
@Composable
fun CaptionSettingsScreen(
    liveCaptionsEnabled: Boolean,
    typeToTalkEnabled: Boolean,
    captionLanguageName: String,
    captionModelManager: CaptionModelManager,
    onBack: () -> Unit,
    onSetLiveCaptionsEnabled: (Boolean) -> Unit,
    onSetTypeToTalkEnabled: (Boolean) -> Unit,
    onSetCaptionLanguage: (CaptionLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val scope = rememberCoroutineScope()

    val selectedLanguage = remember(captionLanguageName) {
        CaptionLanguage.entries.find { it.name == captionLanguageName } ?: CaptionLanguage.ENGLISH_INDIA
    }

    // One state slot PER language, not one shared slot for whichever
    // language happens to be selected. Three languages can each be
    // mid-download (or freshly deleted) independently of which one is
    // currently selected as the active caption language - selecting
    // English (India) doesn't pause a Hindi download, so the screen needs
    // to track and redraw all three concurrently. A single shared
    // `modelState` variable was the root cause of three symptoms at once:
    // a language other than the selected one showed no live progress (its
    // row had no state to read, so it looked frozen then jumped once
    // something else forced a recomposition), and deleting a
    // non-selected language didn't reset anything Compose was watching so
    // the row never redrew as removed.
    val modelStates = remember {
        CaptionLanguage.entries.associateWith { language ->
            val cached = captionModelManager.cachedModelFor(language)
            mutableStateOf<CaptionModelState>(
                when {
                    cached != null -> CaptionModelState.Ready(cached)
                    captionModelManager.isDownloaded(language) -> CaptionModelState.NotDownloaded
                    else -> CaptionModelState.NotDownloaded
                }
            )
        }
    }
    // Bumped after any delete so isDownloaded()/sizeOnDiskMb() below -
    // which read the filesystem directly and aren't state-backed - get
    // re-read this composition instead of showing a stale "Downloaded"
    // row for a file that's already gone.
    var deletionTick by remember { mutableIntStateOf(0) }

    // On the Root build, captions can also work on normal SIM calls (see
    // CallCaptionEngine's class doc for exactly why) - on the Normal build,
    // captions are scoped to data/WebRTC calls only. This is read once per
    // composition, not cached at the top of the app, because BuildConfig
    // fields are compile-time constants anyway - there's no runtime cost
    // to reading it fresh here, and doing so keeps this screen
    // self-contained rather than threading another parameter through.
    val carrierCallCaptionsSupported = com.ashudialer.app.BuildConfig.CARRIER_CALL_CAPTIONS_ENABLED

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Captions & type-to-talk", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ---- Live captions section ----
            item {
                Text("LIVE CAPTIONS", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
            }

            item {
                ToggleRow(
                    icon = Icons.Filled.Subtitles,
                    title = "Show what the other person says as text",
                    subtitle = "Turns their voice into text on screen while you're on a call.",
                    checked = liveCaptionsEnabled,
                    onCheckedChange = onSetLiveCaptionsEnabled,
                    palette = palette
                )
                Spacer(Modifier.height(16.dp))
            }

            if (liveCaptionsEnabled) {
                item {
                    // Plain-language explanation of exactly which calls this
                    // works on, written to be understandable with no prior
                    // context - this is the single most important thing on
                    // this screen to get right, since promising captions
                    // "everywhere" and then having them silently not work on
                    // a normal call would be far worse than being upfront
                    // about the one real limitation up front.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.accentSoft)
                            .padding(14.dp)
                    ) {
                        Text(
                            if (carrierCallCaptionsSupported) {
                                "Works on both video calls (through this app, over the internet) and normal SIM calls."
                            } else {
                                "Works on video calls made through this app over the internet or Wi-Fi (both people need this app). " +
                                    "It doesn't work on normal SIM calls in this version of the app — that needs deeper phone " +
                                    "access this build doesn't have."
                            },
                            fontSize = 12.5.sp, color = palette.textPrimary, lineHeight = 17.sp
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                item {
                    Text("Language", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    Spacer(Modifier.height(8.dp))
                }

                items(CaptionLanguage.entries.toList()) { language ->
                    // Reading deletionTick here (even unused) ties this
                    // row's isDownloaded/sizeOnDiskMb reads to that state,
                    // so a delete anywhere in the list forces every row to
                    // re-check the filesystem instead of only the row
                    // whose own state object changed.
                    @Suppress("UNUSED_EXPRESSION") deletionTick
                    val languageState by modelStates.getValue(language)
                    LanguageRow(
                        language = language,
                        isSelected = language == selectedLanguage,
                        state = languageState,
                        isDownloaded = captionModelManager.isDownloaded(language),
                        sizeOnDiskMb = if (captionModelManager.isDownloaded(language)) captionModelManager.sizeOnDiskMb(language) else null,
                        palette = palette,
                        onSelect = {
                            onSetCaptionLanguage(language)
                        },
                        onDownload = {
                            scope.launch {
                                captionModelManager.ensureReady(language) { state ->
                                    modelStates.getValue(language).value = state
                                }
                            }
                        },
                        onDelete = {
                            captionModelManager.deleteDownloaded(language)
                            modelStates.getValue(language).value = CaptionModelState.NotDownloaded
                            deletionTick++
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                item { Spacer(Modifier.height(8.dp)) }
            }

            // ---- Type-to-talk section ----
            item {
                Text("TYPE-TO-TALK", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
            }

            item {
                ToggleRow(
                    icon = Icons.Filled.RecordVoiceOver,
                    title = "Type instead of speaking",
                    subtitle = "Adds a button on the call screen — type a message and it's spoken into the call for you, like when you can't talk out loud.",
                    checked = typeToTalkEnabled,
                    onCheckedChange = onSetTypeToTalkEnabled,
                    palette = palette
                )
                Spacer(Modifier.height(16.dp))
            }

            if (typeToTalkEnabled) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.accentSoft)
                            .padding(14.dp)
                    ) {
                        Text(
                            "Works on every call — video calls and normal SIM calls both.",
                            fontSize = 12.5.sp, color = palette.textPrimary, lineHeight = 17.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    palette: com.ashudialer.app.ui.theme.DialerPalette
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(palette, 16.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = palette.textSecondary, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent)
        )
    }
}

@Composable
private fun LanguageRow(
    language: CaptionLanguage,
    isSelected: Boolean,
    state: CaptionModelState?,
    isDownloaded: Boolean,
    sizeOnDiskMb: Long?,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Fixed minimum height so the row doesn't visually shrink/grow
            // (and its trailing icon doesn't appear to drift off-center)
            // as its state cycles between a one-line "Not downloaded" text
            // and other single-line states - every state here is one line
            // now (see the progress text change below), so this is purely
            // a stability guard, not compensating for varying content.
            .heightIn(min = 52.dp)
            .glassCard(palette, 14.dp)
            .clickable { onSelect() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(language.displayName, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    // Fixed-width percent (padStart) so this line's length
                    // doesn't change character-by-character as the number
                    // climbs (e.g. "8%" -> "38%") - that shifting width
                    // was pushing the trailing progress spinner left/right
                    // slightly on every update, reading as a "crooked" row
                    // next to the steady rows above and below it.
                    state is CaptionModelState.Downloading -> "Downloading… ${state.progressPercent.toString().padStart(3, ' ')}%"
                    state is CaptionModelState.Unpacking -> "Setting up…"
                    state is CaptionModelState.Failed -> state.message
                    isDownloaded && sizeOnDiskMb != null -> "Downloaded — ${sizeOnDiskMb}MB on your phone"
                    else -> "Not downloaded yet — about 50MB"
                },
                fontSize = 11.5.sp,
                color = if (state is CaptionModelState.Failed) palette.danger else palette.textSecondary,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        // Fixed-size trailing slot for every state (spinner / check+delete
        // / download button) instead of each branch sizing its own Row -
        // this is what actually keeps the trailing icon pinned to the
        // same vertical center regardless of which state is showing.
        Box(
            modifier = Modifier.size(width = 72.dp, height = 36.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            when {
                state is CaptionModelState.Downloading || state is CaptionModelState.Unpacking -> {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = palette.accent)
                }
                isDownloaded -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSelected) {
                            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = palette.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove downloaded language", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                else -> {
                    IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Download, contentDescription = "Download", tint = palette.accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
