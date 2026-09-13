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

    // Re-derived whenever the selected language changes, not cached once -
    // switching from an already-downloaded language to one that isn't
    // downloaded yet (or back) needs this to reflect the newly selected
    // language's actual on-disk state, not the previous one's.
    var modelState by remember(selectedLanguage) {
        val cached = captionModelManager.cachedModelFor(selectedLanguage)
        mutableStateOf<CaptionModelState>(
            when {
                cached != null -> CaptionModelState.Ready(cached)
                else -> CaptionModelState.NotDownloaded
            }
        )
    }

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
                    LanguageRow(
                        language = language,
                        isSelected = language == selectedLanguage,
                        state = if (language == selectedLanguage) modelState else null,
                        isDownloaded = captionModelManager.isDownloaded(language),
                        sizeOnDiskMb = if (captionModelManager.isDownloaded(language)) captionModelManager.sizeOnDiskMb(language) else null,
                        palette = palette,
                        onSelect = {
                            onSetCaptionLanguage(language)
                        },
                        onDownload = {
                            scope.launch {
                                captionModelManager.ensureReady(language) { state -> modelState = state }
                            }
                        },
                        onDelete = {
                            captionModelManager.deleteDownloaded(language)
                            modelState = CaptionModelState.NotDownloaded
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
            .glassCard(palette, 14.dp)
            .clickable { onSelect() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(language.displayName, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    state is CaptionModelState.Downloading -> "Downloading… ${state.progressPercent}%"
                    state is CaptionModelState.Unpacking -> "Setting up…"
                    state is CaptionModelState.Failed -> state.message
                    isDownloaded && sizeOnDiskMb != null -> "Downloaded — ${sizeOnDiskMb}MB on your phone"
                    else -> "Not downloaded yet — about 50MB"
                },
                fontSize = 11.5.sp,
                color = if (state is CaptionModelState.Failed) palette.danger else palette.textSecondary
            )
        }
        Spacer(Modifier.width(8.dp))
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
