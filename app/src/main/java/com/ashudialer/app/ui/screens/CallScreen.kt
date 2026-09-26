package com.ashudialer.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate as rotateCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.telecom.AudioRoute
import com.ashudialer.app.telecom.RecordingMode
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import com.ashudialer.app.ui.components.glassCard

enum class CallUiState { CONNECTING, RINGING, ACTIVE, ON_HOLD, ENDED }

@Composable
fun CallScreen(
    callerName: String,
    callerNumber: String,
    isSavedContact: Boolean = false,
    callerPhotoUri: String? = null,
    isConnected: Boolean = false,
    isOnHold: Boolean = false,
    // Wall-clock time (System.currentTimeMillis()-based, same epoch as
    // Telecom's own Call.Details.connectTimeMillis) the call actually went
    // ACTIVE. 0L means "not connected yet / unknown" - see the seconds
    // derivation below for why this replaces a simple incrementing counter.
    connectTimeMillis: Long = 0L,
    canMerge: Boolean = false,
    canSwap: Boolean = false,
    secondaryCallerName: String? = null,
    secondaryCallerNumber: String? = null,
    secondaryCallState: Int? = null,
    recordingAvailable: Boolean = false,
    isRecording: Boolean = false,
    // True for the window between tapping Record and the recorder actually
    // starting (see InCallActivity's isRecordingStarting) - shows a spinner
    // on the record button so a tap gets immediate visible feedback instead
    // of the button looking unresponsive for up to several hundred ms, and
    // disables the button for that window so a second tap can't race in.
    isRecordingStarting: Boolean = false,
    // True when the running recording was started by the Auto-record setting
    // (not by tapping Record). Only changes the wording of the menu row.
    autoRecordActive: Boolean = false,
    recordingMode: RecordingMode? = null,
    recordingSeconds: Int = 0,
    recordingLooksSilent: Boolean = false,
    micIsSystemMuted: Boolean = false,
    availableAudioRoutes: List<AudioRoute> = listOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER),
    currentAudioRoute: AudioRoute = AudioRoute.EARPIECE,
    onDtmfDigit: (Char) -> Unit = {},
    initialDialedDigits: String = "",
    onOpenNote: () -> Unit = {},
    onOpenVideoCall: (() -> Unit)? = null,
    // Non-null only when this call's own carrier/network already
    // reported both-directions video support (Call.Details capability
    // bits CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL and
    // CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL - see InCallActivity
    // for exactly where that's checked) - i.e. this is a real native/VoLTE
    // video upgrade, the same feature a stock carrier dialer (e.g. the
    // Realme stock dialer's in-call "वीडियो कॉल" option) offers, and
    // entirely separate from onOpenVideoCall above (this app's own
    // internet/WebRTC video calling, which works between any two phones
    // regardless of carrier support). Both can be offered at once when
    // both are available - see MoreActionsSheet below for how they're
    // distinguished in the More menu.
    onUpgradeToNativeVideo: (() -> Unit)? = null,
    onToggleRecording: () -> Unit = {},
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    onSelectAudioRoute: (AudioRoute) -> Unit = {},
    onMerge: () -> Unit = {},
    onSwap: () -> Unit = {},
    onToggleHold: () -> Unit = {},
    onAddCall: () -> Unit = {},
    onEndCall: () -> Unit,
    // True from the instant the person taps End call until the real
    // Telecom disconnect actually lands - purely visual (see
    // InCallActivity's isEndingCall declaration for the full reasoning).
    // Defaulted so every other existing call site of this composable keeps
    // compiling unchanged.
    isEndingCall: Boolean = false,
    // Frosted-glass look: when true, the root background is drawn at reduced opacity so the
    // blurred wallpaper set up by InCallActivity (Window.setBackgroundBlurRadius, Android 12+)
    // shows through softly instead of this screen being fully opaque. Defaults to false so every
    // existing call site - and every device below Android 12, where InCallActivity never enables
    // the blur in the first place - keeps the current, fully-opaque screen. See InCallActivity's
    // call site of CallScreen for where this is threaded from Settings.
    frostedGlassEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current

    var state by remember { mutableStateOf(CallUiState.CONNECTING) }
    var showKeypad by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    // Digits the person has typed on the in-call keypad (sent as tones), shown above the keys for the WHOLE call, from the
    // first digit to the last. Closing and reopening the keypad does not clear them (they used to vanish on close); the
    // full history lives in PixelInCallService, so it also survives the screen being rebuilt.
    var dialedDigits by remember { mutableStateOf(initialDialedDigits) }

    // THE FIX for "call duration resets to 0 when returning to the app
    // after switching to another task": seconds used to be a plain
    // `remember { mutableStateOf(0) }` counter that only ever incremented
    // from inside this composable's own coroutine loop. `remember` only
    // survives while this composable stays in the same composition - it
    // does NOT survive InCallActivity itself being recreated, which is
    // exactly what can happen when the person leaves the call screen for
    // another app (or another screen inside this app) and the system
    // reclaims/recreates the activity before they come back to it. On
    // return, this composable started fresh, `seconds` came back as its
    // initial 0, and the real call - genuinely still active in the
    // background the entire time via Telecom - looked like it had just
    // connected.
    //
    // connectTimeMillis is passed in from InCallActivity as Telecom's own
    // Call.Details.connectTimeMillis - a wall-clock timestamp the Telecom
    // framework maintains for the call itself, completely independent of
    // this screen, this activity, or any composable's lifecycle. Deriving
    // seconds from "now minus connectTimeMillis" instead of counting up
    // from a remembered 0 means the very first frame after any recreation
    // already shows the call's real elapsed duration, not a reset one -
    // there's nothing to lose because nothing about the duration was ever
    // stored locally in the first place.
    //
    // Fallback: connectTimeMillis is 0L on every normal path until Telecom
    // actually reports STATE_ACTIVE (see InCallActivity - it only reads
    // current.details?.connectTimeMillis once isConnected is true), so
    // "no connect time yet" and "not connected yet" are the same moment in
    // practice and showing 0 is correct there. The only scenario where the
    // gap matters is a stale/misbehaving Telecom implementation reporting
    // isConnected=true while still returning connectTimeMillis=0 - to keep
    // the counter from silently freezing at 0 for the rest of a real call
    // in that situation, fallBackStartMillis anchors to the first moment
    // *this composable* observed an active call, exactly once (remembered,
    // not reset on recomposition), and is only ever used as a last resort
    // when Telecom's own timestamp isn't available.
    var fallBackStartMillis by remember { mutableStateOf(0L) }
    if (connectTimeMillis <= 0L && isConnected && !isOnHold && fallBackStartMillis == 0L) {
        fallBackStartMillis = System.currentTimeMillis()
    }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val effectiveConnectMillis = if (connectTimeMillis > 0L) connectTimeMillis else fallBackStartMillis
    val seconds = if (effectiveConnectMillis > 0L) {
        ((nowMillis - effectiveConnectMillis) / 1000L).toInt().coerceAtLeast(0)
    } else {
        0
    }

    // Frame-driven entrance choreography: the old in-call screen was mostly
    // static, so opening it looked like a hard cut even though the rest of
    // Ashu Dialer has motion. This is deliberately short and starts after
    // the first frame so it never delays first render.
    var entranceStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        entranceStarted = true
    }
    val entranceProgress by animateFloatAsState(
        targetValue = if (entranceStarted) 1f else 0f,
        animationSpec = tween(260, easing = LinearEasing),
        label = "call-screen-entrance"
    )
    val screenAlpha = 0.96f + 0.04f * entranceProgress
    val screenTranslationY = (1f - entranceProgress) * 18f

    // Reflects the real Telecom call state (isConnected, sourced from
    // Call.STATE_ACTIVE upstream) rather than guessing with a fixed delay.
    // The previous version switched to ACTIVE - and started the seconds
    // counter - after a hardcoded 1.8s regardless of whether the other
    // person had actually answered yet, so "Dialing"/ringing time was
    // counted as if the call were already connected.
    //
    // secondaryCallState (call-waiting/second-line updates) and even
    // isConnected/isOnHold themselves can emit repeatedly from Telecom
    // during a perfectly normal active call without the *value* actually
    // changing. This effect still needs to re-run on every emission so it
    // never misses a real transition, so it intentionally keys on the raw
    // upstream signals rather than on `state`.
    LaunchedEffect(isConnected, isOnHold, secondaryCallState) {
        state = when {
            isOnHold -> CallUiState.ON_HOLD
            isConnected -> CallUiState.ACTIVE
            else -> CallUiState.CONNECTING
        }
    }

    // Bug fix: this used to key on (state, isOnHold). Because the effect
    // above can reassign `state = ACTIVE` again on every upstream update
    // even when the call was already ACTIVE (same value, new assignment),
    // keying the ticker on `state` restarted this loop on every one of
    // those no-op updates - each restart re-suspends on a fresh delay(1000)
    // before the first tick, so on a noisy connection the seconds counter
    // (and anything reading `isCallActive`) could stall/flicker repeatedly
    // during an otherwise normal active call. Deriving a plain Boolean and
    // keying on that instead means the effect only restarts when the call
    // actually transitions in or out of "ticking" - not on every repeat
    // emission of the same state.
    //
    // This loop no longer increments a counter - seconds above is now
    // purely derived from connectTimeMillis and the current wall clock.
    // All this loop does now is refresh nowMillis once a second so that
    // derivation actually recomputes and the displayed number keeps
    // ticking - the source of truth moved to connectTimeMillis, this is
    // just what makes the UI repaint.
    val isCallActive = state == CallUiState.ACTIVE && !isOnHold
    LaunchedEffect(isCallActive) {
        while (isCallActive) {
            delay(1000)
            nowMillis = System.currentTimeMillis()
        }
    }

    // Accumulates the total upward drag distance across a single gesture, so
    // the threshold check (see pointerInput below) only fires once per swipe
    // rather than repeatedly as the finger moves.
    var accumulatedDragUp by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background, alpha = if (frostedGlassEnabled) 0.92f else 1f)
            .graphicsLayer {
                alpha = screenAlpha
                translationY = screenTranslationY
            }
            // A swipe-up anywhere on the call screen also opens the More
            // sheet, in addition to the existing More button - matching the
            // gesture people expect from the stock in-call screen, where the
            // controls panel responds to both a tap and a swipe. Only
            // upward drags matter (downward is left alone, since that's a
            // reasonable way to nudge the sheet back closed once it's open,
            // handled by MoreActionsSheet's own dismiss/drag-down if it has
            // one). detectVerticalDragGestures resets the accumulator on
            // every gesture start so a slow multi-part swipe still adds up
            // correctly rather than only counting the last movement.
            //
            // Threshold was 60f, which is a very small movement (well under
            // half a centimeter on most screens) - a quick, small flick of
            // the thumb anywhere on the call screen was enough to trigger
            // it, which felt like it was opening by accident. Raised to a
            // deliberately larger distance so only a real, sustained swipe
            // opens the sheet, not an incidental brush of the screen.
            .pointerInput(showMore) {
                if (showMore) return@pointerInput
                // ONLY a long, deliberate swipe up opens More: the finger has to travel 30% of the screen
                // height (about 270dp on a normal phone). The old 180 PIXELS was ~65dp, so an ordinary tap that
                // drifted a little, or a short flick, opened it by accident. Moving back down takes progress
                // away again, so a wobbling finger cannot add up to a trigger either.
                val threshold = size.height * 0.30f
                detectVerticalDragGestures(
                    onDragStart = { accumulatedDragUp = 0f },
                    onDragEnd = { accumulatedDragUp = 0f },
                    onDragCancel = { accumulatedDragUp = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        accumulatedDragUp = (accumulatedDragUp - dragAmount).coerceAtLeast(0f)
                        if (accumulatedDragUp > threshold) {
                            accumulatedDragUp = 0f
                            showMore = true
                            showKeypad = false
                        }
                        change.consume()
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Union with displayCutout, not statusBarsPadding() alone -
                // this is the active-call screen where the caller's avatar
                // sits near the top (see the avatar Column just below),
                // which is exactly the element that was rendering partly
                // under the physical camera cutout on punch-hole phones.
                // statusBarsPadding() covers the status bar's own height
                // but the cutout can protrude further on some devices/OEM
                // skins, so it needs to be accounted for explicitly rather
                // than assumed to already be inside the status bar inset.
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
        ) {
            // Keep the caller identity anchored near the top. The recording
            // indicator is a separate element, so starting a recording can
            // never push the number/name into the middle of the screen.
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isSavedContact) callerName else callerNumber,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    maxLines = 1
                )
                if (isSavedContact && callerNumber.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = callerNumber,
                        fontSize = 14.sp,
                        color = palette.textSecondary,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ScallopedAvatar(
                    name = callerName,
                    isSavedContact = isSavedContact,
                    photoUri = callerPhotoUri,
                    onHold = isOnHold,
                    palette = palette
                )
            }

            Spacer(Modifier.height(24.dp))

            AnimatedVisibility(
                visible = isRecording,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                RecordingWaveformBar(
                    seconds = recordingSeconds,
                    mode = recordingMode,
                    looksSilent = recordingLooksSilent,
                    micIsSystemMuted = micIsSystemMuted
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when {
                        isEndingCall -> "Ending call…"
                        state == CallUiState.CONNECTING -> "Dialing…"
                        isOnHold -> "on hold"
                        else -> formatDuration(seconds)
                    },
                    fontSize = 15.sp,
                    color = palette.textSecondary,
                    fontWeight = FontWeight.Medium
                )
            }

            if (!secondaryCallerName.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.cardBackground.copy(alpha = 0.55f))
                        .border(1.dp, palette.cardBorder.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = when (secondaryCallState) {
                                android.telecom.Call.STATE_HOLDING -> "On hold"
                                android.telecom.Call.STATE_DIALING, android.telecom.Call.STATE_CONNECTING -> "Calling…"
                                android.telecom.Call.STATE_RINGING -> "Ringing…"
                                android.telecom.Call.STATE_ACTIVE -> "Active"
                                else -> "Second call"
                            },
                            fontSize = 11.sp, color = palette.textSecondary, fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = secondaryCallerName ?: secondaryCallerNumber.orEmpty(),
                            fontSize = 15.sp, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1
                        )
                    }
                    if (canSwap) {
                        Text("Swap", fontSize = 12.sp, color = palette.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ONE fixed slot for the keypad and the More sheet.
            //
            // They used to be two separate AnimatedVisibility blocks stacked in this Column. A block that is
            // animating OUT keeps its full height until its animation ends, so switching keypad <-> More made
            // the Column briefly taller than the screen: Keypad / Mute / Audio / More and the End button were
            // shoved off the bottom, and the panel was pushed up the screen and then slid back down (the
            // "screen jumps up and down" bug, and "press the keypad after swiping up and the keypad shoots
            // up"). Now this slot's size is fixed by weight(1f) and the panels fade / slide INSIDE it, so
            // nothing else can move. If a panel is taller than the slot (a short phone, or a huge system font)
            // it scrolls inside the slot instead of overflowing.
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.BottomCenter
            ) {
                val slotMaxHeight = maxHeight
                PanelSlot(visible = showKeypad) {
                    Box(Modifier.heightIn(max = slotMaxHeight).verticalScroll(rememberScrollState())) {
                        InCallKeypad(
                            palette = palette,
                            digits = dialedDigits,
                            onDigit = { d ->
                                dialedDigits = (dialedDigits + d).takeLast(60)
                                onDtmfDigit(d)
                            }
                        )
                    }
                }
                PanelSlot(visible = showMore) {
                    Box(Modifier.heightIn(max = slotMaxHeight).verticalScroll(rememberScrollState())) {
                        MoreActionsSheet(
                            palette = palette,
                            canMerge = canMerge,
                            canSwap = canSwap,
                            isOnHold = isOnHold,
                            recordingAvailable = recordingAvailable,
                            isRecording = isRecording,
                            isRecordingStarting = isRecordingStarting,
                            autoRecordActive = autoRecordActive,
                            availableAudioRoutes = availableAudioRoutes,
                            currentAudioRoute = currentAudioRoute,
                            onToggleRecording = onToggleRecording,
                            onSelectAudioRoute = onSelectAudioRoute,
                            onMerge = onMerge,
                            onSwap = onSwap,
                            onToggleHold = onToggleHold,
                            onAddCall = onAddCall,
                            onOpenNote = onOpenNote,
                            onOpenVideoCall = onOpenVideoCall,
                            onUpgradeToNativeVideo = onUpgradeToNativeVideo,
                            onDismiss = { showMore = false }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CallControlButton(Icons.Filled.Dialpad, "Keypad", active = showKeypad, palette = palette) {
                    showKeypad = !showKeypad
                    if (showKeypad) showMore = false
                }
                CallControlButton(
                    if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    if (isMuted) "Unmute" else "Mute",
                    active = isMuted,
                    palette = palette
                ) { onToggleMute() }
                var showAudioRouteMenu by remember { mutableStateOf(false) }
                Box {
                    CallControlButton(
                        icon = iconForRoute(currentAudioRoute),
                        label = if (availableAudioRoutes.size > 2) "Audio" else if (currentAudioRoute == AudioRoute.SPEAKER) "Speaker" else "Audio",
                        active = showAudioRouteMenu || currentAudioRoute == AudioRoute.SPEAKER || currentAudioRoute == AudioRoute.BLUETOOTH,
                        palette = palette
                    ) {
                        // With only Earpiece/Speaker available, keep the original simple
                        // toggle. Once a third route becomes available (Bluetooth or a
                        // wired headset connects), a two-way toggle can no longer express
                        // every option, so this opens a small picker instead - reusing the
                        // exact same route list already used in the More sheet below.
                        if (availableAudioRoutes.size > 2) {
                            showAudioRouteMenu = true
                        } else {
                            val next = if (currentAudioRoute == AudioRoute.SPEAKER) AudioRoute.EARPIECE else AudioRoute.SPEAKER
                            onSelectAudioRoute(next)
                        }
                    }
                    DropdownMenu(expanded = showAudioRouteMenu, onDismissRequest = { showAudioRouteMenu = false }) {
                        availableAudioRoutes.forEach { route ->
                            DropdownMenuItem(
                                text = { Text(routeLabel(route) + if (route == currentAudioRoute) " ✓" else "") },
                                leadingIcon = { Icon(iconForRoute(route), contentDescription = null) },
                                onClick = {
                                    onSelectAudioRoute(route)
                                    showAudioRouteMenu = false
                                }
                            )
                        }
                    }
                }
                CallControlButton(Icons.Filled.MoreHoriz, "More", active = showMore, palette = palette) {
                    showMore = !showMore
                    if (showMore) showKeypad = false
                }
            }

            Spacer(Modifier.height(8.dp))


            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                val endCallInteractionSource = remember { MutableInteractionSource() }
                val endCallPressed by endCallInteractionSource.collectIsPressedAsState()
                val endCallScale by animateFloatAsState(
                    // isEndingCall keeps the button in its pressed-down scale
                    // rather than springing back up, on top of disabling
                    // taps below - both are the same "this already
                    // registered, don't invite a second tap" signal.
                    targetValue = if (endCallPressed || isEndingCall) 0.90f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "endCallScale"
                )
                val endCallAlpha by animateFloatAsState(
                    targetValue = if (isEndingCall) 0.6f else 1f,
                    label = "endCallAlpha"
                )
                IconButton(
                    onClick = onEndCall,
                    // Prevents a second tap from calling onEndCall (and so
                    // current.disconnect()) again while the first tap's
                    // disconnect is still in flight - harmless on its own,
                    // but pointless and exactly the kind of thing that made
                    // the button feel unresponsive enough to tap again in
                    // the first place.
                    enabled = !isEndingCall,
                    interactionSource = endCallInteractionSource,
                    modifier = Modifier
                        .width(112.dp)
                        .height(60.dp)
                        .graphicsLayer { scaleX = endCallScale; scaleY = endCallScale; alpha = endCallAlpha }
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color(0xFFE53E3E))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(30.dp))
                ) {
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = "End call",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp).rotate(135f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScallopedAvatar(
    name: String,
    isSavedContact: Boolean,
    photoUri: String? = null,
    onHold: Boolean,
    palette: DialerPalette
) {


    val transition = rememberInfiniteTransition(label = "avatar-gradient-spin")
    val rotationDegrees by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "avatar-gradient-angle"
    )

    Box(
        modifier = Modifier
            .size(112.dp)
            .scale(if (onHold) 0.95f else 1f)
            .drawScallopBackground(palette, rotationDegrees),
        contentAlignment = Alignment.Center
    ) {
        if (isSavedContact && !photoUri.isNullOrBlank()) {
            // Same photo the person set for this contact (including a fresh
            // crop) shows up here too, not just initials - this reuses Coil's
            // AsyncImage the same way every other avatar in the app already
            // loads a contact photo, clipped to the circular frame.
            coil.compose.AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(112.dp).clip(CircleShape)
            )
        } else if (isSavedContact) {
            Text(
                text = name.take(1).uppercase(),
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary
            )
        } else {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = palette.textPrimary,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}


private fun Modifier.drawScallopBackground(palette: DialerPalette, rotationDegrees: Float): Modifier = this.then(
    Modifier.drawWithCache {
        val path = buildScallopPath(size.width, size.height, petals = 16)


        val brush = Brush.sweepGradient(
            colors = listOf(
                palette.swatchStart,
                palette.accent,
                palette.swatchEnd,
                palette.accent,
                palette.swatchStart
            ),
            center = Offset(size.width / 2f, size.height / 2f)
        )
        onDrawBehind {
            rotateCanvas(degrees = rotationDegrees, pivot = Offset(size.width / 2f, size.height / 2f)) {
                drawPath(path, brush = brush)
            }
        }
    }
)

private fun buildScallopPath(width: Float, height: Float, petals: Int): Path {
    val path = Path()
    val cx = width / 2f
    val cy = height / 2f
    val outerR = width / 2f
    val step = (2 * Math.PI / petals).toFloat()

    for (i in 0 until petals) {
        val angle = i * step
        val midAngle = angle + step / 2f
        val outerX = cx + outerR * cos(angle)
        val outerY = cy + outerR * sin(angle)
        val bulgeX = cx + (outerR * 1.04f) * cos(midAngle)
        val bulgeY = cy + (outerR * 1.04f) * sin(midAngle)
        val nextX = cx + outerR * cos(angle + step)
        val nextY = cy + outerR * sin(angle + step)

        if (i == 0) path.moveTo(outerX, outerY) else path.lineTo(outerX, outerY)
        path.quadraticBezierTo(bulgeX, bulgeY, nextX, nextY)
    }
    path.close()
    return path
}

@Composable
private fun RecordingWaveformBar(seconds: Int, mode: RecordingMode?, looksSilent: Boolean = false, micIsSystemMuted: Boolean = false) {
    val bars = remember { List(28) { Random.nextFloat() * 0.7f + 0.15f } }
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "waveform-phase"
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.12f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(10.dp).background(Color(0xFFE53E3E), CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(text = formatDuration(seconds), color = Color.Black.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(12.dp))
            Row(
                modifier = Modifier.weight(1f).height(20.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bars.forEachIndexed { i, base ->
                    val animatedHeight = (base + 0.25f * kotlin.math.sin(phase * 6.28f + i * 0.5f)).coerceIn(0.1f, 1f)
                    Box(
                        modifier = Modifier
                            .width(2.5.dp)
                            .fillMaxHeight(animatedHeight)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                    )
                }
            }
        }

        if (micIsSystemMuted) {
            // Confirmed directly against a real recording: even plain
            // MICROPHONE (no special permission needed at all) came back
            // as 13+ seconds of digital silence on a 14-second call, with
            // real audio only in roughly the last second as the call
            // tore down - the telephony stack itself holds the
            // microphone path exclusively for the call's duration on
            // this device, independent of which AudioSource this app
            // requests. There is no in-app fix for this - it's a
            // system/OEM-level restriction - so this message says so
            // plainly instead of implying a retry or a different source
            // would help, and suggests the one thing that can actually
            // work around it: speakerphone lets the other party's voice
            // (and, acoustically, some of your own) reach the
            // microphone through the air instead of over the exclusive
            // internal call-audio path.
            Text(
                text = "Android/this device is blocking microphone access to any app during " +
                    "calls — not something this app can override. Try turning on speakerphone: " +
                    "audio reaching the mic through the air can still be picked up even when the " +
                    "internal call-audio path itself is blocked.",
                color = Color(0xFFB91C1C),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
            )
        } else if (looksSilent) {
            // Surfaced from CallRecorder.currentAmplitude() via InCallActivity
            // (see recordingLooksSilent there) - this is the mid-call
            // counterpart to CallRecorder's own startup silence probe: a
            // source that passed the startup check but has produced no real
            // signal for several seconds running. Told to the person while
            // the call is still happening, since finding out only after
            // hanging up (by listening to a silent file) is much less
            // useful than being able to react - hang up and call back on
            // speaker, switch to a different recording setup, etc.
            Text(
                text = "No audio detected in the last few seconds — this recording may be silent",
                color = Color(0xFFB91C1C),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
            )
        } else if (mode == RecordingMode.MICROPHONE) {
            Text(
                text = "Recording via microphone — call audio access is restricted by Android/OEM",
                color = Color.Black.copy(alpha = 0.55f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
            )
        } else if (mode == RecordingMode.VOICE_COMMUNICATION) {
            Text(
                text = "Recording via communication audio",
                color = Color.Black.copy(alpha = 0.55f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
            )
        }
    }
}

/** Fades and slides one panel in or out INSIDE the fixed slot; never changes the layout around it. */
@Composable
private fun PanelSlot(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(180)) +
            slideInVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)) { it / 6 },
        exit = fadeOut(animationSpec = tween(110)) +
            slideOutVertically(animationSpec = tween(150)) { it / 6 }
    ) {
        content()
    }
}

@Composable
private fun InCallKeypad(
    palette: DialerPalette,
    digits: String,
    onDigit: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf("123", "456", "789", "*0#")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp)
            .glassCard(palette, 20.dp)
            .padding(top = 10.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // What has been typed so far. The height is fixed, so the panel never changes size as digits are added.
        Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            if (digits.isEmpty()) {
                Text("Tap keys to send tones", fontSize = 13.sp, color = palette.textSecondary.copy(alpha = 0.7f))
            } else {
                // Everything typed is shown; a long sequence shrinks and wraps to a second line instead of being cut off.
                Text(
                    digits,
                    fontSize = when {
                        digits.length <= 12 -> 26.sp
                        digits.length <= 24 -> 19.sp
                        else -> 14.sp
                    },
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.5.sp,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    maxLines = 2
                )
            }
        }
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { digit -> KeypadDigit(digit, palette, onDigit, Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One in-call keypad key. The digit is sent the moment the finger goes DOWN (not when it lifts), so the
 * tone starts at once like a real keypad, and the key answers with a quick shrink plus a soft glass
 * highlight instead of the old slow, bouncy spring.
 */
@Composable
private fun KeypadDigit(digit: Char, palette: DialerPalette, onDigit: (Char) -> Unit, modifier: Modifier = Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = tween(if (pressed) 60 else 140, easing = FastOutSlowInEasing),
        label = "keypadDigitScale"
    )
    val glow by animateFloatAsState(
        targetValue = if (pressed) 0.22f else 0f,
        animationSpec = tween(if (pressed) 40 else 220),
        label = "keypadDigitGlow"
    )
    // The touch area is the whole cell (an equal share of the row wide, 64dp tall) and the cells touch, so a tap that lands
    // a little beside a digit still presses that digit. The round highlight is only the visual.
    Box(
        modifier = modifier
            .height(64.dp)
            .pointerInput(digit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onDigit(digit)
                        tryAwaitRelease()
                        pressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = glow)),
            contentAlignment = Alignment.Center
        ) {
            Text(digit.toString(), color = palette.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun MoreActionsSheet(
    palette: DialerPalette,
    canMerge: Boolean,
    canSwap: Boolean,
    isOnHold: Boolean,
    recordingAvailable: Boolean,
    isRecording: Boolean,
    isRecordingStarting: Boolean = false,
    autoRecordActive: Boolean,
    availableAudioRoutes: List<AudioRoute>,
    currentAudioRoute: AudioRoute,
    onToggleRecording: () -> Unit,
    onSelectAudioRoute: (AudioRoute) -> Unit,
    onMerge: () -> Unit,
    onSwap: () -> Unit,
    onToggleHold: () -> Unit,
    onAddCall: () -> Unit,
    onOpenNote: () -> Unit,
    onOpenVideoCall: (() -> Unit)?,
    onUpgradeToNativeVideo: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp)
            .glassCard(palette, 20.dp)
            .padding(vertical = 8.dp)
    ) {
        MoreActionRow(Icons.Filled.NoteAlt, "Note", palette) { onOpenNote(); onDismiss() }
        // Fully hidden (not just disabled) when recording is off in
        // Settings - a visible-but-greyed-out "Record call" row invites
        // taps and confusion about why it doesn't work, when the real
        // answer is "you haven't turned this on, and here's why it might
        // not fully work anyway" - that conversation belongs in the
        // Settings guide, not as a disabled button mid-call.
        if (recordingAvailable) {
            MoreActionRow(
                icon = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                label = when {
                    isRecordingStarting -> "Starting recording…"
                    isRecording && autoRecordActive -> "Auto call recording active - tap to stop"
                    isRecording -> "Stop recording"
                    else -> "Record call"
                },
                palette = palette,
                enabled = !isRecordingStarting,
                loading = isRecordingStarting,
                onClick = { onToggleRecording(); onDismiss() }
            )
        }
        when {
            canMerge -> MoreActionRow(Icons.Filled.CallMerge, "Conference / Merge", palette) { onMerge(); onDismiss() }
            canSwap -> MoreActionRow(Icons.Filled.SwapCalls, "Swap calls", palette) { onSwap(); onDismiss() }
            else -> MoreActionRow(
                if (isOnHold) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                if (isOnHold) "Resume call" else "Hold call",
                palette
            ) { onToggleHold(); onDismiss() }
        }
        MoreActionRow(Icons.Filled.PersonAdd, "Add call", palette) { onAddCall(); onDismiss() }
        // Two separate video-calling rows, shown independently based on
        // what's actually available for this specific call - see
        // onUpgradeToNativeVideo's doc comment on CallScreen's signature
        // for the distinction:
        //
        // - Native/VoLTE upgrade: only offered when THIS call's own
        //   carrier connection already reported bidirectional video
        //   support (checked once, in InCallActivity, from the live
        //   Call.Details capability bits - never assumed available just
        //   because the button exists). Labeled "Switch to video call"
        //   since it upgrades the exact same ongoing call in place,
        //   matching what a stock carrier dialer's in-call video option
        //   does.
        // - This app's own internet video calling: offered whenever
        //   video calling is configured (signed in + own number saved -
        //   see AshuDialerApp/AuthRepository), completely independent of
        //   carrier support, since it starts a new WebRTC call over data
        //   rather than upgrading the carrier connection itself.
        //
        // Both can show at once on a call where both happen to be true;
        // neither replaces the other, since they're genuinely different
        // mechanisms with different requirements on the other side.
        if (onUpgradeToNativeVideo != null) {
            MoreActionRow(Icons.Filled.Videocam, "Switch to video call", palette, enabled = true) { onUpgradeToNativeVideo(); onDismiss() }
        }
        if (onOpenVideoCall != null) {
            // Reuses the same Videocam icon as the native row above rather
            // than a second, unverified icon name (Icons.Filled.VideoCall
            // isn't confirmed to exist in this project's Material Icons
            // version, while Videocam is already used successfully
            // elsewhere in this exact file) - the two rows are
            // distinguished by their labels ("Switch to video call" vs
            // "Internet video call"), which is unambiguous either way.
            MoreActionRow(Icons.Filled.Videocam, "Internet video call", palette, enabled = true) { onOpenVideoCall(); onDismiss() }
        }
        // UX FIX: this used to fall back to a permanently-disabled "Video
        // call - Coming soon" row whenever neither video option was
        // available. That row could never actually turn on by itself for
        // anyone whose Firebase project isn't fully configured (missing
        // google-services.json at build time, or signed out with no
        // network to complete the app's own silent sign-in) - meaning
        // some real installs would see a dead, unexplained "Coming soon"
        // button in the More menu on every single call, forever, which
        // reads as a broken/unfinished feature rather than an honest
        // absence. Showing nothing at all when there's genuinely nothing
        // to offer is the same pattern this app already uses for
        // recordingAvailable/canMerge/canSwap above (each row is either
        // real and tappable, or not shown) - a row that can never do
        // anything doesn't belong in this menu.
        if (availableAudioRoutes.size > 2) {
            availableAudioRoutes.forEach { route ->
                MoreActionRow(
                    icon = iconForRoute(route),
                    label = routeLabel(route),
                    palette = palette,
                    trailing = if (route == currentAudioRoute) "✓" else null,
                    onClick = { onSelectAudioRoute(route); onDismiss() }
                )
            }
        }
    }
}

@Composable
private fun MoreActionRow(
    icon: ImageVector,
    label: String,
    palette: DialerPalette,
    enabled: Boolean = true,
    loading: Boolean = false,
    trailing: String? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (enabled) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = interactionSource,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = palette.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                icon, contentDescription = label,
                tint = if (enabled) palette.textPrimary else palette.textSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            label, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            color = if (enabled) palette.textPrimary else palette.textSecondary.copy(alpha = 0.4f),
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            val isCheck = trailing == "✓"
            Text(
                trailing,
                fontSize = if (isCheck) 15.sp else 11.sp,
                color = if (isCheck) palette.accent else palette.textSecondary.copy(alpha = 0.6f),
                fontWeight = if (isCheck) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

private fun iconForRoute(route: AudioRoute): ImageVector = when (route) {
    AudioRoute.EARPIECE -> Icons.Filled.PhoneInTalk
    AudioRoute.SPEAKER -> Icons.Filled.VolumeUp
    AudioRoute.BLUETOOTH -> Icons.Filled.BluetoothAudio
    AudioRoute.WIRED_HEADSET -> Icons.Filled.Headset
}

private fun routeLabel(route: AudioRoute): String = when (route) {
    AudioRoute.EARPIECE -> "Phone"
    AudioRoute.SPEAKER -> "Speaker"
    AudioRoute.BLUETOOTH -> "Bluetooth"
    AudioRoute.WIRED_HEADSET -> "Headset"
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    palette: DialerPalette,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "callControlButtonScale"
    )
    val fillColor by animateColorAsState(
        targetValue = if (active) palette.accent else palette.cardBackground,
        animationSpec = tween(200),
        label = "callControlButtonFill"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (active) 0.35f else 0.16f,
        animationSpec = tween(200),
        label = "callControlButtonBorder"
    )
    val tintColor by animateColorAsState(
        targetValue = if (active) Color.White else palette.textPrimary,
        animationSpec = tween(200),
        label = "callControlButtonTint"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                // Glassy look: a translucent white layer over the active/
                // inactive fill (rather than a flat, opaque
                // palette.cardBackground/accent circle), plus a hairline
                // border to read as glass catching light rather than a flat
                // painted disc. copy(alpha) keeps this working correctly
                // across every theme (light, dark, gradient) since it's
                // layered relative to whatever's underneath rather than a
                // fixed white-on-dark assumption.
                .background(fillColor)
                .background(Color.White.copy(alpha = if (active) 0.16f else 0.08f))
                .border(1.dp, Color.White.copy(alpha = borderAlpha), CircleShape)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = tintColor,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, color = palette.textSecondary, fontWeight = FontWeight.Medium)
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
