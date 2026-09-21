package com.ashudialer.app.telecom

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.telecom.Call
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.lifecycleScope
import com.ashudialer.app.AshuDialerApp
import com.ashudialer.app.R
import com.ashudialer.app.data.AppSettings
import com.ashudialer.app.data.db.CallDirection
import com.ashudialer.app.ui.screens.CallScreen
import com.ashudialer.app.ui.screens.IncomingCallScreen
import com.ashudialer.app.ui.screens.NativeVideoCallScreen
import com.ashudialer.app.ui.theme.AshuDialerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


/**
 * The correct video state to answer() an incoming call with, shared by
 * every answer path across this Activity (notification-tap auto-answer,
 * and the ringing screen's own Accept button - PixelInCallService.answer()
 * has its own equivalent for the third path, the notification's action
 * button, since it doesn't share this file's scope).
 *
 * See PixelInCallService.answer()'s doc comment for the full reasoning -
 * in short: this only ever answers as STATE_BIDIRECTIONAL when the
 * incoming call itself already requested video (a real VoLTE video call),
 * preserving STATE_AUDIO_ONLY for every ordinary voice call exactly as
 * before. VideoProfile.isAudioOnly(int) is used rather than a plain ==
 * comparison against STATE_AUDIO_ONLY (which is 0, so == would miss any
 * state that also has the paused bit set) per the platform's own
 * documented guidance for this check.
 */
private fun answerVideoStateFor(call: Call): Int {
    val videoState = call.details?.videoState ?: android.telecom.VideoProfile.STATE_AUDIO_ONLY
    return if (android.telecom.VideoProfile.isAudioOnly(videoState)) {
        android.telecom.VideoProfile.STATE_AUDIO_ONLY
    } else {
        android.telecom.VideoProfile.STATE_BIDIRECTIONAL
    }
}

@Composable
private fun CallLoadingScreen(title: String, subtitle: String) {
    val palette = com.ashudialer.app.ui.theme.LocalDialerPalette.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(color = palette.accent)
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
        }
    }
}

class InCallActivity : ComponentActivity() {

    // Set when this Activity was launched specifically to answer a call on
    // the person's behalf (see CallActionReceiver.ACTION_ANSWER) rather than
    // just to show the ringing screen and wait for a manual tap on the
    // in-app Answer button. A mutableStateOf here - not a plain var - is
    // what lets the Composable tree below actually react to it, including
    // the onNewIntent case where the value changes after setContent has
    // already run once.
    //
    // consumeAutoAnswer() (not a second boolean flag, and not immediately
    // clearing this in onNewIntent) is what current.answer() is gated on
    // inside setContent's LaunchedEffect(call, pendingAutoAnswer) - answering
    // requires an actual Call object, which is not guaranteed to exist yet
    // the instant this Activity starts (see the existing call==null retry
    // loop a little further down in this file), so the flag has to survive
    // until that LaunchedEffect can act on it, then be consumed exactly
    // once so a later recomposition can never answer a second, unrelated
    // call using a stale true value.
    private var pendingAutoAnswer by mutableStateOf(false)

    private fun consumeAutoAnswer(): Boolean {
        val value = pendingAutoAnswer
        pendingAutoAnswer = false
        return value
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_AUTO_ANSWER, false)) {
            pendingAutoAnswer = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_AUTO_ANSWER, false)) {
            pendingAutoAnswer = true
        }
        // Apply lock-screen visibility/wake behavior immediately, before the
        // first Compose frame. This is especially important when Telecom
        // launches us from a locked, sleeping device. The manifest flags are
        // kept as a second layer, but doing it here removes the small race
        // where the activity could be created before those window attributes
        // were reflected in the live window.
        setupLockScreenAndWakeFlags(false)
        val app = application as AshuDialerApp
        // Same reasoning as MainActivity: without this, the status and
        // navigation bars are opaque system-default bars the call screen's
        // own gradient/aurora background can never show through, no matter
        // what the insets-controller icon-color logic further down does.
        enableEdgeToEdge()
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        // THE FIX for "brief blue flash placing a call on Black theme (or
        // any theme other than Ocean)": Theme.AshuDialer.Call's static XML
        // windowBackground used to be call_window_background.xml, a single
        // hardcoded light-blue color (Ocean's own background) - the color
        // painted by the system compositor for the brief gap before this
        // window's first Compose frame draws. Every theme other than Ocean
        // (Black/Dark Mode included) flashed that wrong, unrelated color for
        // a fraction of a second before snapping to the real theme.
        //
        // Setting the window background here at runtime, using the actual
        // active theme's own solid background color, replaces that one
        // hardcoded placeholder - the compositor now paints the *correct*
        // theme's color for that same brief gap, so there's nothing to
        // "snap away from" once Compose's first frame lands. Deliberately
        // reads ThemePreference's synchronous, NEVER-BLOCKING SharedPreferences
        // mirror (peekLastKnownThemeId) rather than themeIdFlow: the DataStore
        // flow is async and collecting it here would mean either blocking
        // this thread (an earlier version of this code did exactly that with
        // a runBlocking fallback, and it's what caused intermittent
        // open/answer-time hangs - see peekLastKnownThemeId's doc comment) or
        // drawing this first frame with no theme info at all. Worst case here
        // (mirror not populated yet) is a one-frame "ocean" placeholder
        // instead of a hang - the ColorDrawable set below is only ever
        // visible for that same brief pre-first-frame gap; setContent's own
        // Compose tree (using the real themeIdFlow value, which can only ever
        // be equal or more current) draws over it immediately after.
        val syncedThemeId = com.ashudialer.app.data.ThemePreference.peekLastKnownThemeId(this)
        val placeholderColor = com.ashudialer.app.ui.theme.paletteById(syncedThemeId).solidBackground
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(placeholderColor.toArgb())
        )

        lifecycleScope.launch {
            val disableProximitySensor = runCatching {
                app.appSettingsRepository.settingsFlow.first().disableProximitySensor
            }.getOrDefault(false)
            if (disableProximitySensor) setupLockScreenAndWakeFlags(true)
        }


        val backPressCallback = object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                PixelInCallService.currentCall?.disconnect()
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
        onBackPressedDispatcher.addCallback(this, backPressCallback)

        // Fast, subtle activity entrance. The old implementation disabled all
        // platform transitions, so even a perfectly smooth Compose tree still
        // arrived as a hard cut. The preview window is disabled in the theme,
        // so this animation starts from the previous screen without exposing
        // the old blue application preview.
        applyCallEnterTransition()

        setContent {
            // "ocean" here (not "gradient") to match ThemePreference's own
            // steady-state default - this is only the placeholder shown for
            // the brief instant before the real DataStore value loads, but
            // an in-call screen is exactly the moment a themed flash is
            // most visible, so it should match the app's actual default
            // rather than a different, unrelated palette.
            val themeId by app.themePreference.themeIdFlow.collectAsState(initial = "ocean")
            val settings by app.appSettingsRepository.settingsFlow.collectAsState(initial = AppSettings())

            // Keep the in-call Activity opaque for its whole lifetime. The
            // correctly themed window background remains underneath Compose,
            // preventing Telecom/OEM transitions from exposing the previous
            // Activity or a system-colored surface.
            LaunchedEffect(settings.backEndsCall) {
                backPressCallback.isEnabled = settings.backEndsCall
            }

            var call by remember { mutableStateOf(PixelInCallService.currentCall) }
            var callState by remember { mutableStateOf(call?.state) }
            // True once a call object has been on this screen. When the call later disappears it has ENDED, which
            // must close the screen at once. Without this it fell into the "Connecting call... waiting for the phone
            // service" placeholder (meant only for the first moments of a NEW call) and sat there until the hand-off
            // timeout ran out - the "Connecting screen shows when I end a call" bug.
            var everHadCall by remember { mutableStateOf(call != null) }
            var hasSecondCall by remember { mutableStateOf(PixelInCallService.hasMultipleCalls) }
            var canMergeCalls by remember { mutableStateOf(PixelInCallService.canMergeCalls()) }
            var canSwapCalls by remember { mutableStateOf(PixelInCallService.canSwapCalls()) }
            // Set the instant the person taps "End call", before Telecom's
            // own STATE_DISCONNECTED has actually arrived - purely a local,
            // optimistic UI flag so the end-call button gives immediate
            // visual feedback (disabling itself / showing "Ending...")
            // rather than looking unresponsive for however long the real
            // Telecom/carrier-side disconnect takes to come back. Does not
            // skip or fake the real disconnect - current.disconnect() is
            // still called normally right after, and callState/finish()
            // still only act on the real STATE_DISCONNECTED once it lands.
            var isEndingCall by remember { mutableStateOf(false) }
            var isRecording by remember { mutableStateOf(com.ashudialer.app.BuildConfig.CALL_RECORDING_ENABLED && app.callRecorder.isRecording) }
            var recordingMode by remember { mutableStateOf(app.callRecorder.currentMode()) }
            // True only when the person tapped "Record call" themselves during this call.
            // A recording that is running while this is false and the Auto-record setting
            // is on was started by auto-record, and the menu says so.
            var manualRecordStarted by remember { mutableStateOf(false) }
            var recordingSeconds by remember { mutableStateOf(app.callRecorder.elapsedSeconds()) }
            // True once recording has run silent (no getMaxAmplitude signal
            // above the noise floor) for several consecutive polls in a
            // row. A single quiet poll doesn't set this - normal calls have
            // real pauses in speech - only a sustained run of silence does,
            // which is what actually indicates the audio path died rather
            // than someone just not talking for a second.
            var recordingLooksSilent by remember { mutableStateOf(false) }
            // Surfaces AudioManager.isMicrophoneMute() while recording is
            // active - confirmed directly against a real silent recording
            // (VOICE_UPLINK and even plain MIC both producing 13+ seconds
            // of digital silence on a 14-second call, with real audio
            // only appearing in roughly the last second as the call tore
            // down) that this device's telephony stack holds exclusive
            // control of the microphone path for the call's entire
            // duration, independent of which AudioSource this app's own
            // MediaRecorder requests. isMicrophoneMute() reflects that
            // system-level state directly rather than this app having to
            // infer it indirectly from amplitude alone - if this reads
            // true while recording is running, that is the actual
            // explanation, not a bug in source selection or retry logic.
            var micIsSystemMuted by remember { mutableStateOf(false) }
            var showNoteDialog by remember { mutableStateOf(false) }
            var showAddCallDialog by remember { mutableStateOf(false) }
            var addCallContacts by remember { mutableStateOf(emptyList<com.ashudialer.app.data.Contact>()) }
            val loggedOutgoingNumbers = remember { mutableSetOf<String>() }

            LaunchedEffect(showAddCallDialog) {
                if (showAddCallDialog) {
                    addCallContacts = runCatching { app.contactsRepository.loadAllContacts() }
                        .getOrDefault(emptyList())
                        .sortedBy { it.displayName.lowercase() }
                }
            }

            val audioRouteController = remember { AudioRouteController(this@InCallActivity) }
            // Reactive, Telecom-confirmed state (see PixelInCallService's
            // onCallAudioStateChanged) rather than an optimistic snapshot
            // read right after requesting a route change or only refreshed
            // as a side effect of unrelated call-state events. This is what
            // fixes the speaker icon sometimes not matching the real route.
            val currentRoute by PixelInCallService.currentAudioRouteFlow.collectAsState()
            val availableRoutes by PixelInCallService.availableAudioRoutesFlow.collectAsState()
            // Same StateFlow pattern as currentRoute above, and driven by
            // the exact same onCallAudioStateChanged callback - mute and
            // speaker now update from one shared source at the same
            // moment, instead of mute's old separate synchronous
            // AudioManager read racing against route's async Telecom
            // callback (that race was the actual cause of the two
            // buttons visibly falling out of sync with each other).
            val isMuted by PixelInCallService.isMutedFlow.collectAsState()
            // See NativeVideoCallScreen's own doc comment for what this
            // actually renders once true, and onUpgradeToNativeVideo's doc
            // comment (below, in this same file) for the request that
            // sets it. Camera permission for the screen this drives is
            // requested just below, the same registerForActivityResult
            // pattern VideoCallActivity already uses for its own (separate,
            // WebRTC) camera permission - this app already asks for camera
            // access before recording video anywhere, so this isn't a new
            // category of permission prompt for the person to have never
            // seen before.
            val nativeVideoActive by PixelInCallService.nativeVideoUpgradeActiveFlow.collectAsState()
            var nativeVideoCameraPermission by remember {
                mutableStateOf<Boolean?>(
                    if (ContextCompat.checkSelfPermission(this@InCallActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) true else null
                )
            }
            val requestNativeVideoCameraPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted -> nativeVideoCameraPermission = granted }
            // Only actually prompts once the upgrade has genuinely
            // succeeded (nativeVideoActive true) rather than pre-emptively
            // on every call - most calls never become video calls, so
            // asking every time would be a permission prompt for a
            // capability most calls never use.
            LaunchedEffect(nativeVideoActive) {
                if (nativeVideoActive && nativeVideoCameraPermission == null) {
                    requestNativeVideoCameraPermission.launch(Manifest.permission.CAMERA)
                }
            }


            val dtmfPlayer = remember { DtmfPlayer() }
            DisposableEffect(Unit) { onDispose { dtmfPlayer.release() } }
            fun sendDtmf(digit: Char) {
                dtmfPlayer.play(digit)
                val active = PixelInCallService.currentCall ?: return
                try {
                    active.playDtmfTone(digit)
                    active.stopDtmfTone()
                } catch (_: Exception) {


                }
            }

            DisposableEffect(Unit) {
                val listener: () -> Unit = {
                    call = PixelInCallService.currentCall
                    callState = call?.state
                    hasSecondCall = PixelInCallService.hasMultipleCalls
                    canMergeCalls = PixelInCallService.canMergeCalls()
                    canSwapCalls = PixelInCallService.canSwapCalls()
                    // isMuted is no longer set here - it now comes solely
                    // from PixelInCallService.isMutedFlow (see above),
                    // which is driven by Telecom's own onCallAudioStateChanged.
                    // Writing to it here too, from a direct AudioManager
                    // read, was the second half of the same race that made
                    // mute and speaker fall out of sync: this listener and
                    // the Telecom callback could fire in either order and
                    // stomp on each other's value.
                }
                PixelInCallService.addCallListener(listener)
                onDispose { PixelInCallService.removeCallListener(listener) }
            }


            LaunchedEffect(call) {
                // Safety-net polling for when Call.Callback's event-driven
                // onStateChanged (see PixelInCallService.notifyListeners)
                // doesn't fire promptly - normally it does, so this loop
                // rarely does real work, but it's the fallback that catches
                // a state change if that callback is ever delayed. Interval
                // tightened from 500ms to 150ms: on "End call", the person
                // taps immediately and any residual gap between Telecom
                // actually disconnecting and this activity noticing (and
                // closing) reads as call-end lag - a worst-case 500ms
                // fallback-detection window was a meaningful chunk of that
                // perceived delay by itself, on top of whatever genuine
                // carrier/Telecom-side latency exists and can't be
                // controlled from here.
                while (call != null) {
                    kotlinx.coroutines.delay(150)
                    val liveCall = PixelInCallService.currentCall
                    val liveState = liveCall?.state
                    if (liveCall !== call) call = liveCall
                    if (liveState != null && liveState != callState) callState = liveState
                    hasSecondCall = PixelInCallService.hasMultipleCalls
                    canMergeCalls = PixelInCallService.canMergeCalls()
                    canSwapCalls = PixelInCallService.canSwapCalls()
                }
            }


            // NOTE: there used to be a "dialing timeout" watchdog here that called
            // Call.disconnect() after 7.5 s if the call was still DIALING/CONNECTING.
            // That was the cause of "I place a call, it rings 2-4 seconds and then
            // cuts by itself": for OUTGOING calls Android keeps the Call in
            // STATE_DIALING for the whole time the far end is ringing (STATE_RINGING
            // is incoming-only), so the watchdog could not tell "ringing normally"
            // from "stuck" and hung up live calls. The carrier/Telecom already ends
            // a genuinely unanswered call by itself (typically after 30-60 s), so the
            // watchdog is gone; the person can always tap End call themselves.

            LaunchedEffect(callState) {
                if (callState == Call.STATE_DISCONNECTED || callState == Call.STATE_DISCONNECTING) {
                    // THE ACTUAL FIX for the black screen after a call ends:
                    // this used to call app.callRecorder.stop() and AWAIT it
                    // (a plain blocking function, not a suspend function)
                    // before calling finish() on the same line. MediaRecorder
                    // .stop() is well documented to block for a noticeable
                    // moment while it flushes/finalizes the encoder - worse
                    // on short recordings (confirmed via AOSP's own
                    // MediaRecorder CTS test, which had to add a manual delay
                    // after stop() specifically for short-duration clips).
                    // Since this whole block runs as a coroutine on
                    // Dispatchers.Main (LaunchedEffect's default), that
                    // blocking call froze the *entire* UI thread - not just
                    // recording logic - for however long stop() took,
                    // directly explaining the 1-3+ second gap between the
                    // DISCONNECTED state arriving and finish() actually
                    // running (confirmed in logcat: DISCONNECTED at
                    // 08:33:54.749, but the activity's CLOSE window
                    // transition didn't start until 08:33:57.059 - a 2.3s
                    // freeze, not an animation or windowBackground issue).
                    // finish() and the transition override now run first and
                    // immediately, so the screen closes the instant the call
                    // actually ends; the recorder is stopped and saved on a
                    // background dispatcher afterward, fully decoupled from
                    // this activity's lifecycle so a slow encoder flush can
                    // never block anything the person can see again.
                    //
                    // If the black screen is STILL happening after both this
                    // fix and the fade_out transition fix, the remaining
                    // candidate is a layer neither of those touches: this
                    // activity uses showWhenLocked/turnScreenOn (see
                    // setupLockScreenAndWakeFlags) so it can appear over the
                    // lock screen. When it finishes while the device is
                    // still actually locked, handing back to the real lock
                    // screen is a SEPARATE transition owned by SystemUI/
                    // WindowManagerService, not by this activity's own
                    // window - overridePendingTransitionCompat() only
                    // controls this activity's own close animation, it has
                    // no influence over whatever SystemUI paints while
                    // re-presenting the keyguard underneath. MIUI's keyguard
                    // transition is heavier than stock AOSP's and a brief
                    // black frame during that specific hand-off would look
                    // identical to what's already been fixed here, while
                    // having a completely different, OS-level cause this
                    // app can't directly control.
                    // isKeyguardLockedNow is logged so a fresh logcat can
                    // confirm or rule this out directly: if the black screen
                    // only happens on calls where this logs true, that's the
                    // keyguard hand-off, not this activity. setShowWhenLocked
                    // (false) right before finish() is a low-risk, reversible
                    // attempt at a smoother hand-off - it tells WindowManager
                    // this window no longer needs special keyguard treatment
                    // a moment before it closes, rather than closing a
                    // still-showWhenLocked window and leaving WindowManager
                    // to sort out the keyguard state after the fact.
                    Log.i("InCallActivity", "DISCONNECTED at ${SystemClock.elapsedRealtime()} - closing UI immediately")
                    closeCallUiImmediately()
                    if (app.callRecorder.isRecording) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            app.callRecorder.stop()
                        }
                    }
                }
            }

            LaunchedEffect(call) {
                // Telecom can deliver onCallAdded and the activity launch on
                // adjacent main-thread turns. Do not finish the call UI just
                // because the first composition happened before the service
                // listener populated currentCall; that race made the custom
                // screen disappear and left the stock dialer UI on some OEMs.
                if (call != null) {
                    everHadCall = true
                } else if (everHadCall) {
                    Log.i("InCallActivity", "call object is gone after being shown - the call ended, closing UI immediately")
                    closeCallUiImmediately()
                } else {
                    // The call Activity is only a presentation surface; it is
                    // never allowed to become a permanent waiting screen. If
                    // Telecom has not handed us a Call inside this short OEM
                    // hand-off window, close the Activity and leave the live
                    // Telecom/notification path alone. This guarantees a fast
                    // return instead of the white "Connecting call" screen
                    // sitting there indefinitely.
                    repeat((CALL_OBJECT_HANDOFF_TIMEOUT_MS / 100).toInt()) {
                        kotlinx.coroutines.delay(100)
                        val pending = PixelInCallService.currentCall
                        if (pending != null) {
                            call = pending
                            callState = pending.state
                            return@LaunchedEffect
                        }
                    }
                    Log.i("InCallActivity", "call object handoff timeout at ${SystemClock.elapsedRealtime()} - closing UI immediately")
                    closeCallUiImmediately()
                }
            }

            // Performs the actual answer requested by CallActionReceiver's
            // notification-tap path (see EXTRA_AUTO_ANSWER / pendingAutoAnswer
            // above). Keyed on both `call` and `pendingAutoAnswer` - not just
            // one of them - because either can arrive first: a fresh launch
            // usually has the flag set before `call` is populated (the retry
            // loop above fills `call` in a moment), while onNewIntent firing
            // on an Activity that was already showing the ringing screen has
            // `call` populated well before the new intent (and therefore the
            // flag) arrives. Only fires while callState is actually
            // STATE_RINGING - if the caller hung up in the moment between the
            // notification tap and this Activity resolving a live Call, there
            // is nothing left to answer, and answer()ing a call that already
            // moved to another state is not a meaningful action.
            LaunchedEffect(call, pendingAutoAnswer, callState) {
                if (pendingAutoAnswer && call != null && callState == Call.STATE_RINGING) {
                    val current = call ?: return@LaunchedEffect
                    consumeAutoAnswer()
                    current.answer(answerVideoStateFor(current))
                    logCallAsync(
                        app,
                        current.details?.handle?.schemeSpecificPart ?: "Unknown",
                        current.details?.callerDisplayName ?: current.details?.handle?.schemeSpecificPart ?: "Unknown",
                        CallDirection.INCOMING
                    )
                }
            }

            LaunchedEffect(isRecording, callState) {
                if (!isRecording) return@LaunchedEffect
                var quietPollsInARow = 0
                while (app.callRecorder.isRecording) {
                    recordingSeconds = app.callRecorder.elapsedSeconds()
                    recordingMode = app.callRecorder.currentMode()
                    isRecording = true
                    // Only start judging silence once elapsedSeconds > 2 -
                    // the very first couple of polls can legitimately read
                    // near-zero simply because the encoder hasn't flushed
                    // its first real audio frame yet, which isn't the same
                    // failure this is meant to catch.
                    if (recordingSeconds > 2) {
                        val amplitude = app.callRecorder.currentAmplitude()
                        if (amplitude <= 15) {
                            quietPollsInARow++
                        } else {
                            quietPollsInARow = 0
                        }
                        // ~6 seconds of continuous silence (500ms poll x 12)
                        // before flagging - long enough that a normal pause
                        // in conversation won't trip it, short enough that
                        // the person still finds out mid-call rather than
                        // only after listening back afterward.
                        recordingLooksSilent = quietPollsInARow >= 12

                        // Check the actual system mic-mute state once
                        // silence has run long enough to be worth
                        // investigating, rather than every single poll -
                        // this is a real system call (AudioManager),
                        // cheap but no reason to run it every 500ms when
                        // there's real signal.
                        if (quietPollsInARow >= 8) {
                            micIsSystemMuted = try {
                                val am = getSystemService(AUDIO_SERVICE) as? android.media.AudioManager
                                am?.isMicrophoneMute == true
                            } catch (_: Throwable) {
                                false
                            }
                        } else {
                            micIsSystemMuted = false
                        }

                        // At ~4 seconds (500ms poll x 8), attempt one
                        // capped mid-call restart onto the next fallback
                        // source - confirmed necessary on at least one
                        // real device where VOICE_UPLINK opens and starts
                        // cleanly (so it isn't caught by any upfront
                        // probe) but produces genuine silence for the
                        // whole call, with real audio only appearing in
                        // roughly the last second as the call tears down.
                        // Lowered from the original 8-second threshold
                        // (poll x 16): on a short call - confirmed
                        // directly on a ~15-second test call - the old
                        // threshold left less than a second of margin
                        // before the call could end first, so the
                        // recording captured silence start-to-finish with
                        // real audio only in literally the last 1-2
                        // seconds during teardown. 4 seconds still safely
                        // clears the >2-second startup grace period above
                        // and the required run of quiet polls, while
                        // leaving enough of even a short call to actually
                        // benefit from the switch. restartOnSustainedSilence()
                        // itself caps this to once per call and only acts
                        // if the source has produced literally zero signal
                        // since it started (not just this poll window), so
                        // a real quiet moment in an actual conversation
                        // can't trigger it - only a source that has
                        // carried no audio at all so far can. NOTE:
                        // confirmed on a real device that this restart
                        // alone is not sufficient when the block is
                        // system-wide (MIC also came back silent after a
                        // restart from VOICE_UPLINK) - micIsSystemMuted
                        // above is what actually explains that case; the
                        // restart still helps on devices where only the
                        // specific privileged source (not MIC) is the
                        // problem. Beyond this call, CallRecorder.start()
                        // now also remembers a confirmed-silent source
                        // permanently (see markSourceKnownSilent) so
                        // future calls skip straight past it instead of
                        // re-losing time to it on every call.
                        if (quietPollsInARow >= 8) {
                            val switchedTo = app.callRecorder.restartOnSustainedSilence()
                            if (switchedTo != null) {
                                recordingMode = switchedTo
                                quietPollsInARow = 0
                                recordingLooksSilent = false
                                micIsSystemMuted = false
                            }
                        }
                    }
                    kotlinx.coroutines.delay(500)
                }
                isRecording = false
                recordingMode = null
                recordingLooksSilent = false
                micIsSystemMuted = false
            }

            fun startRecording(callerLabel: String) {
                // Never touch the microphone while the call is ringing, dialing,
                // ended, or otherwise not actually connected.
                if (callState != Call.STATE_ACTIVE) return
                manualRecordStarted = true
                if (app.callRecorder.isRecording) {
                    isRecording = true
                    recordingMode = app.callRecorder.currentMode()
                    recordingSeconds = app.callRecorder.elapsedSeconds()
                    return
                }
                // CallRecorder.start() calls MediaRecorder.prepare()/start()
                // and a short SystemClock.sleep() per candidate audio source
                // (it can try up to 3 sources before giving up), which is
                // genuinely blocking I/O - up to several hundred ms. Running
                // it directly from this click handler used to block the main
                // thread, so the record button (and the whole call screen)
                // would freeze/lag right when tapped. Moving the blocking
                // work to Dispatchers.IO and only touching Compose state
                // afterwards (back on the main thread automatically once the
                // launched block resumes) keeps the tap responsive.
                //
                // Recording must not silently change the user's audio route.
                // CallRecorder first tries protected/communication call-audio
                // sources and only then falls back to the normal microphone.
                // Android does not provide a public API for an ordinary app to
                // force two-way cellular capture, so we never fake that by
                // switching speaker on automatically.
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val mode = app.callRecorder.start(callerLabel)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        recordingMode = mode.takeIf { it != RecordingMode.FAILED }
                        isRecording = mode != RecordingMode.FAILED
                        recordingSeconds = app.callRecorder.elapsedSeconds()
                    }
                }
            }

            fun stopRecording() {
                manualRecordStarted = false
                // stop() also does blocking I/O (MediaRecorder.stop() plus
                // copying the file into public storage) - same reasoning as
                // startRecording() above.
                isRecording = false
                recordingMode = null
                recordingSeconds = 0
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    app.callRecorder.stop()
                }
            }

            AshuDialerTheme(themeId = themeId, fontSizeIndex = settings.fontSizeIndex) {
                // Same theme-follows-status-bar-icon-color fix as
                // MainActivity - previously InCallActivity had no status bar
                // styling at all (Theme.AshuDialer.Call's XML didn't set
                // statusBarColor/windowLightStatusBar either, see
                // themes.xml), so the call screen's status bar area looked
                // mismatched/inconsistent against whichever theme was active
                // rather than blending into it like the rest of the app.
                val palette = com.ashudialer.app.ui.theme.LocalDialerPalette.current
                val view = LocalView.current
                // IncomingCallScreen (rendered when call?.state ==
                // STATE_RINGING) always uses its own dark aurora background
                // regardless of which app theme is active - its whole
                // design (see IncomingCallScreen.kt) doesn't follow
                // palette.isDark the way the rest of the app does. Using
                // palette.isDark here for that screen specifically would
                // mean a Light/Gradient theme selection flips the status
                // bar to dark icons on top of that always-dark background,
                // making them unreadable. Every other call state
                // (CallScreen, for an active/dialing call) still follows
                // the theme as before.
                val isRingingScreen = call?.state == android.telecom.Call.STATE_RINGING
                LaunchedEffect(palette.isDark, isRingingScreen) {
                    androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                        val useLightIcons = if (isRingingScreen) false else !palette.isDark
                        isAppearanceLightStatusBars = useLightIcons
                        isAppearanceLightNavigationBars = useLightIcons
                    }
                }

                val current = call
                if (current == null) {
                    CallLoadingScreen(
                        title = if (pendingAutoAnswer) "Connecting incoming call…" else "Connecting call…",
                        subtitle = "Waiting for the phone service"
                    )
                } else {
                    val number = current.details?.handle?.schemeSpecificPart ?: "Unknown"
                    val rawCallerDisplayName = current.details?.callerDisplayName?.takeIf { it.isNotBlank() }


                    var localContactMatch by remember(number) {
                        mutableStateOf<com.ashudialer.app.data.Contact?>(null)
                    }
                    // Tri-state: null = lookup not finished yet, so callers that
                    // need to wait for a stable name (e.g. logging the call
                    // below) can tell "still resolving" apart from "resolved,
                    // and genuinely isn't a saved contact" - Contact?'s own null
                    // can't distinguish those two cases by itself.
                    var contactLookupDone by remember(number) { mutableStateOf(false) }
                    LaunchedEffect(number) {
                        localContactMatch = if (number.isNotBlank() && number != "Unknown") {
                            app.contactsRepository.lookupNameForNumber(number)
                        } else {
                            null
                        }
                        contactLookupDone = true
                    }

                    // A saved contact's name should always win once the lookup
                    // resolves - the carrier-provided callerDisplayName (SIM/
                    // network CNAP data) is only a placeholder shown while that
                    // lookup is still in flight, or a fallback when the number
                    // truly isn't saved. Previously this was the other way
                    // around (carrier name always won if present at all), so
                    // an incoming call from a saved contact still showed
                    // whatever label the SIM/carrier reported instead of the
                    // name actually saved for that number.
                    val resolvedName = localContactMatch?.displayName ?: rawCallerDisplayName
                    val displayName = resolvedName ?: number
                    val secondary = PixelInCallService.secondaryCall
                    val secondaryNumber = secondary?.details?.handle?.schemeSpecificPart.orEmpty()
                    val secondaryName = secondary?.details?.callerDisplayName?.takeIf { it.isNotBlank() }
                        ?: secondaryNumber.takeIf { it.isNotBlank() }
                    val secondaryState = secondary?.state


                    // Specifically whether this matched a locally saved contact -
                    // not just "is there any name string at all", since a
                    // carrier-supplied name isn't a saved contact and previously
                    // caused isSavedContact (which drives photo/name display
                    // elsewhere) to be true for numbers that were never actually
                    // saved.
                    val isSavedContact = localContactMatch != null

                    // Recents previously relied entirely on Android's own system
                    // call log getting a finished entry, then this app's own
                    // periodic syncFromSystem() eventually pulling that in - so a
                    // freshly placed outgoing call didn't appear in Recents until
                    // some later sync, not immediately. This logs the outgoing
                    // call directly into this app's own call log the moment it's
                    // known to be a real outgoing call, the same way an accepted
                    // incoming call already is on the RINGING branch below.
                    // Keyed on `number` (not `call`) with a per-call guard so
                    // this fires exactly once per distinct outgoing call even
                    // though this composable recomposes many times as the call
                    // progresses through DIALING -> ACTIVE etc.
                    val isOutgoing = current.details?.callDirection == Call.Details.DIRECTION_OUTGOING
                    LaunchedEffect(number, isOutgoing, contactLookupDone) {
                        if (isOutgoing && contactLookupDone && number.isNotBlank() && number != "Unknown" && loggedOutgoingNumbers.add(number)) {
                            logCallAsync(app, number, displayName, CallDirection.OUTGOING)
                        }
                    }

                    // Auto-record previously only ever fired from
                    // IncomingCallScreen's onAccept - an outgoing call (the
                    // person dialing out) never had any auto-record trigger
                    // at all, regardless of the setting. This starts
                    // recording the moment an outgoing call actually
                    // connects (STATE_ACTIVE - not STATE_DIALING, since
                    // there's no call audio to capture yet while it's still
                    // ringing on the other end). Keyed on callState so it
                    // only evaluates on real state transitions, and guarded
                    // on !isRecording so it can't try to start a second
                    // recording on top of one already running (e.g. if the
                    // person manually started one from the record button
                    // before this effect re-runs).
                    //
                    // Automatic recording is now owned by CallRecordingCoordinator (:appcalls),
                    // which is driven by the system PHONE_STATE broadcast and applies every
                    // user filter (incoming/outgoing, anonymous, international, ignored
                    // contacts, "only after answered"). Starting a second recording from here
                    // as well would race it, so this screen only tells the coordinator the
                    // exact moment the call becomes ACTIVE - that is the precise "answered"
                    // signal, which the broadcast alone cannot provide.
                    LaunchedEffect(callState, isOutgoing) {
                        if (callState == Call.STATE_ACTIVE && settings.callRecordingEnabled) {
                            com.ashudialer.app.appcalls.recording.CallRecordingCoordinator
                                .getInstance(applicationContext)
                                .notifyCallAnswered(number = number, isIncoming = !isOutgoing)
                        }
                    }

                    when (callState) {
                        Call.STATE_RINGING -> {
                            val spamAssessment = remember(number) { PendingSpamFlags.consume(number) }
                            IncomingCallScreen(
                                callerName = displayName,
                                callerNumber = number,
                                callerPhotoUri = localContactMatch?.photoUri,
                                isSavedContact = isSavedContact,
                                spamAssessment = spamAssessment,
                                style = settings.incomingCallStyle,
                                glass = settings.incomingCallGlass,
                                onAccept = {
                                    current.answer(answerVideoStateFor(current))
                                    logCallAsync(app, number, displayName, CallDirection.INCOMING)
                                },
                                onDecline = {
                                    runCatching { current.reject(false, null) }
                                    closeCallUiImmediately()
                                },
                                onQuickMessage = {
                                    runCatching { current.reject(true, "Can't talk right now, will call you back.") }
                                    closeCallUiImmediately()
                                }
                            )
                        }
                        else -> {
                            // NativeVideoCallScreen only once the upgrade
                            // has actually succeeded (nativeVideoActive -
                            // never on a merely-requested-but-not-yet-
                            // answered upgrade), Telecom has genuinely
                            // attached a VideoCall to this call, and camera
                            // permission is granted. Any one of those being
                            // false falls back to the normal CallScreen -
                            // including "permission not granted yet/denied",
                            // which correctly leaves the call as a normal
                            // audio call in this app's own UI even though
                            // it may already be bidirectional video at the
                            // network level, rather than showing a video
                            // screen with no camera feed. current.videoCall
                            // itself (not just nativeVideoActive) is
                            // checked here because the two are set by
                            // different callbacks arriving through
                            // different objects (PixelInCallService's
                            // static flow vs. this Call's own videoCall
                            // property) - both being ready is what this
                            // screen actually needs.
                            val nativeVideoCall = current.videoCall
                            if (nativeVideoActive && nativeVideoCall != null && nativeVideoCameraPermission == true) {
                                NativeVideoCallScreen(
                                    videoCall = nativeVideoCall,
                                    callerName = displayName,
                                    isConnected = callState == Call.STATE_ACTIVE,
                                    onEndCall = {
                                        isEndingCall = true
                                        runCatching { current.disconnect() }
                                    }
                                )
                            } else {
                            CallScreen(
                                callerName = displayName,
                                callerNumber = number,
                                isSavedContact = isSavedContact,
                                callerPhotoUri = localContactMatch?.photoUri,
                                isConnected = callState == Call.STATE_ACTIVE,
                                isOnHold = callState == Call.STATE_HOLDING,
                                // Telecom's own wall-clock connect timestamp -
                                // see CallScreen's connectTimeMillis doc
                                // comment for why this (not a local counter)
                                // is the fix for the duration resetting when
                                // this activity gets recreated. Only read
                                // once actually ACTIVE: Telecom can return 0
                                // (or a stale value from a previous call on
                                // the same Call object) before then, and
                                // CallScreen's own fallback covers the rare
                                // case where this is still 0 despite
                                // isConnected being true.
                                connectTimeMillis = if (callState == Call.STATE_ACTIVE) {
                                    current.details?.connectTimeMillis ?: 0L
                                } else {
                                    0L
                                },
                                canMerge = canMergeCalls,
                                canSwap = canSwapCalls,
                                secondaryCallerName = secondaryName,
                                secondaryCallerNumber = secondaryNumber,
                                secondaryCallState = secondaryState,
                                recordingAvailable = com.ashudialer.app.BuildConfig.CALL_RECORDING_ENABLED && settings.callRecordingEnabled && callState == Call.STATE_ACTIVE,
                                isRecording = isRecording,
                                autoRecordActive = isRecording && !manualRecordStarted && settings.autoRecordAll,
                                recordingMode = recordingMode,
                                recordingSeconds = recordingSeconds,
                                recordingLooksSilent = recordingLooksSilent,
                                micIsSystemMuted = micIsSystemMuted,
                                availableAudioRoutes = availableRoutes,
                                currentAudioRoute = currentRoute,
                                onDtmfDigit = { digit -> sendDtmf(digit) },
                                onOpenNote = { showNoteDialog = true },
                                onOpenVideoCall = if (app.authRepository.isSignedIn()) {
                                    {
                                        val intent = VideoCallActivity.callerIntent(this@InCallActivity, number, displayName)
                                        startActivity(intent)
                                    }
                                } else null,
                                // DEEP FIX (native/VoLTE video calling, part
                                // 2 of 2 - part 1 is the answer()
                                // STATE_BIDIRECTIONAL fix in
                                // PixelInCallService/CallActionReceiver/this
                                // file's own answer paths): offering an
                                // in-call *upgrade* to video, the same
                                // feature a stock carrier dialer's in-call
                                // video button provides. Only offered when
                                // Telecom itself already reports both
                                // directions are actually supported for
                                // THIS specific call - CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL
                                // (this device/carrier connection can send
                                // and receive video) AND
                                // CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL
                                // (the other party's device/network can
                                // too) - both bits come from the live
                                // Call.Details.callCapabilities the carrier's
                                // own ConnectionService reports, not
                                // something this app can turn on for a call
                                // that doesn't genuinely support it. A call
                                // over a non-VoLTE/2G-3G-only connection, or
                                // to a phone/carrier that doesn't support
                                // video, correctly never shows this option -
                                // there's no bypass, since the underlying
                                // network capability itself has to exist.
                                //
                                // Call.videoCall (the InCallService.VideoCall
                                // handle for this call) is what's actually
                                // used to send the upgrade request -
                                // sendSessionModifyRequest(VideoProfile) asks
                                // Telecom/the carrier to renegotiate this
                                // same call as bidirectional video. Once the
                                // carrier confirms (Call.Callback.
                                // onVideoCallChanged / a videoState change on
                                // this same Call), rendering the two live
                                // video surfaces via that VideoProvider is a
                                // separate, larger UI surface of its own -
                                // not yet built here. This wiring's scope is
                                // specifically the capability check and
                                // issuing the correct request so it's never
                                // offered/sent for a call that can't
                                // actually support it; the call keeps
                                // running as a normal (audio) call in this
                                // app's own UI after the request is sent,
                                // rather than this button silently doing
                                // nothing or claiming to show video it
                                // doesn't yet render.
                                onUpgradeToNativeVideo = run {
                                    val caps = current.details?.callCapabilities ?: 0
                                    val localOk = (caps and Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL) != 0
                                    val remoteOk = (caps and Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL) != 0
                                    if (localOk && remoteOk && current.videoCall != null) {
                                        {
                                            runCatching {
                                                current.videoCall?.sendSessionModifyRequest(
                                                    android.telecom.VideoProfile(android.telecom.VideoProfile.STATE_BIDIRECTIONAL)
                                                )
                                            }
                                        }
                                    } else null
                                },
                                isMuted = isMuted,
                                onToggleMute = {
                                    // Request through Telecom (same pattern as
                                    // onSelectAudioRoute below) rather than
                                    // writing AudioManager directly and reading
                                    // it straight back. The real value always
                                    // arrives via isMutedFlow once Telecom
                                    // confirms it, which is what keeps this in
                                    // lockstep with the speaker button instead
                                    // of the two settling at different times.
                                    val service = PixelInCallService.instance
                                    if (service != null) {
                                        service.requestMuted(!isMuted)
                                    } else {
                                        // No InCallService instance - same rare
                                        // fallback case as onSelectAudioRoute's
                                        // else branch below, with no Telecom
                                        // callback to report back through.
                                        runCatching { CallAudioQuickActions.toggleMute(this@InCallActivity) }
                                    }
                                    runCatching { CallNotificationHelper.refreshOngoing(this@InCallActivity) }
                                },
                                onToggleRecording = {
                                    if (com.ashudialer.app.BuildConfig.CALL_RECORDING_ENABLED) {
                                        if (isRecording) stopRecording() else startRecording(displayName)
                                    }
                                },
                                onSelectAudioRoute = { route ->


                                    val service = PixelInCallService.instance
                                    if (service != null) {
                                        val telecomRoute = when (route) {
                                            com.ashudialer.app.telecom.AudioRoute.SPEAKER -> android.telecom.CallAudioState.ROUTE_SPEAKER
                                            com.ashudialer.app.telecom.AudioRoute.EARPIECE -> android.telecom.CallAudioState.ROUTE_EARPIECE
                                            com.ashudialer.app.telecom.AudioRoute.BLUETOOTH -> android.telecom.CallAudioState.ROUTE_BLUETOOTH
                                            com.ashudialer.app.telecom.AudioRoute.WIRED_HEADSET -> android.telecom.CallAudioState.ROUTE_WIRED_HEADSET
                                        }
                                        // Do NOT set currentRoute here - it now comes from
                                        // PixelInCallService.currentAudioRouteFlow, which
                                        // updates itself once Telecom's onCallAudioStateChanged
                                        // fires with the real, confirmed route. Setting it here
                                        // too would reintroduce the exact stale-read bug this
                                        // was fixed for.
                                        service.setAudioRoute(telecomRoute)
                                    } else {
                                        // No InCallService instance (rare - only if the
                                        // activity somehow launched outside a real Telecom
                                        // call). This fallback controller has no Telecom
                                        // callback to report back through, so it's the one
                                        // case where we still trust its own read of the route.
                                        audioRouteController.selectRoute(route)
                                    }
                                },
                                onMerge = { PixelInCallService.instance?.mergeCalls() },
                                onSwap = { PixelInCallService.instance?.swapCalls() },
                                onToggleHold = { PixelInCallService.instance?.toggleHold() },
                                onAddCall = { showAddCallDialog = true },
                                isEndingCall = isEndingCall,
                                onEndCall = {
                                    // Register the tap immediately, then ask Telecom to
                                    // disconnect. Do not stop MediaRecorder before the
                                    // disconnect request: releasing the capture path first
                                    // can add avoidable work to the same OEM call-end window.
                                    isEndingCall = true
                                    runCatching { current.disconnect() }
                                    // Do not make the person wait for Telecom/carrier
                                    // propagation. The UI is a presentation layer; the
                                    // disconnect request continues in Telecom after we
                                    // immediately return to the dialer.
                                    closeCallUiImmediately()
                                }
                            )
                            }

                            if (showNoteDialog) {
                                com.ashudialer.app.ui.components.InCallNoteDialog(
                                    callerLabel = displayName,
                                    onDismiss = { showNoteDialog = false },
                                    onSave = { text ->
                                        // applicationScope, not lifecycleScope: if the person
                                        // saves a note then immediately ends the call (a very
                                        // natural sequence), finish() cancels lifecycleScope
                                        // and could cut the write off mid-flight before Room
                                        // actually persists it.
                                        app.applicationScope.launch {
                                            app.callNoteRepository.addNote(
                                                phoneNumber = number,
                                                callerLabel = displayName,
                                                text = text
                                            )
                                        }
                                        showNoteDialog = false
                                    }
                                )
                            }

                            if (showAddCallDialog) {
                                com.ashudialer.app.ui.components.AddCallDialog(
                                    contacts = addCallContacts,
                                    onDismiss = { showAddCallDialog = false },
                                    onCall = { secondNumber ->
                                        showAddCallDialog = false
                                        // Match stock two-call behaviour: ask Telecom to put the
                                        // current ACTIVE call on hold before creating the new
                                        // outgoing call. Telecom may do this itself, but making
                                        // the request explicit gives the UI a deterministic
                                        // ACTIVE -> HOLDING -> DIALING sequence on OEMs that
                                        // otherwise leave both calls visually ambiguous.
                                        PixelInCallService.currentCall?.let { existing ->
                                            if (existing.state == Call.STATE_ACTIVE) {
                                                runCatching { existing.hold() }
                                            }
                                        }
                                        DialerPermissions.placeCall(this@InCallActivity, secondNumber)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }


    /**
     * Close the call Activity without a platform fade. A fade can expose the
     * Activity window background during the hand-off to the main dialer,
     * producing a visible blue/blank flash on some OEM builds.
     */
    private fun applyCallEnterTransition() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(
                    OVERRIDE_TRANSITION_OPEN,
                    R.anim.call_enter,
                    0
                )
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.call_enter, 0)
            }
        } catch (_: Throwable) {
        }
    }

    /** Close the call Activity with no visual transition or blank-frame gap. */
    private fun closeCallUiImmediately() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                runCatching { setShowWhenLocked(false) }
            }
            finish()
            // Deliberately zero-duration: the requested behaviour is that the
            // call surface disappears directly to the dialer with no black,
            // white, fade or scale frame in between.
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        } catch (_: Throwable) {
            runCatching { finish() }
        }
    }

    // NOTE: there used to be a dismissKeyguardForCall() here that called KeyguardManager.requestDismissKeyguard()
    // when the person answered. On a phone with a PIN / pattern / fingerprint lock that call OPENS the unlock
    // screen, which is exactly the "I answer and it asks for my password" bug. A stock dialer never does this: the
    // call screen is drawn ABOVE the lock screen (setShowWhenLocked + setTurnScreenOn, set in
    // setupLockScreenAndWakeFlags) and the call is answered and talked through without unlocking anything.

    private fun setupLockScreenAndWakeFlags(disableProximitySensor: Boolean) {
        // These flags are deliberately applied to the real call activity, not
        // only to the notification. This makes the UI eligible to appear over
        // the keyguard when Telecom launches/reuses the activity while the
        // phone is locked.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
            if (disableProximitySensor) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    private fun logCallAsync(
        app: AshuDialerApp,
        number: String,
        name: String,
        direction: CallDirection
    ) {
        // Same reasoning as the note save above - this must survive the
        // Activity finishing right after the call ends.
        app.applicationScope.launch {
            app.callLogRepository.logCall(number = number, name = name, direction = direction)
        }
    }

    override fun onResume() {
        super.onResume()
        CallNotificationHelper.isCallScreenVisible = true


        CallNotificationHelper.refreshOngoing(this)
    }

    override fun onPause() {
        super.onPause()
        Log.i("InCallActivity", "onPause at ${SystemClock.elapsedRealtime()}")
        CallNotificationHelper.isCallScreenVisible = false


        CallNotificationHelper.refreshOngoing(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("InCallActivity", "onDestroy at ${SystemClock.elapsedRealtime()}")
    }

    companion object {
        // Never leave the user sitting on an otherwise empty "Connecting call"
        // surface. Telecom normally supplies a Call almost immediately; this
        // is only a defensive hand-off ceiling for OEM/dual-SIM races.
        private const val CALL_OBJECT_HANDOFF_TIMEOUT_MS = 1800L

        /**
         * Boolean intent extra: when true, this Activity answers the ringing
         * call itself as soon as it has a live Call object, instead of just
         * opening the ringing screen and waiting for a manual tap on Answer.
         * Set by CallActionReceiver when the person answers from the
         * notification action - see the ACTION_ANSWER branch there for the
         * full reasoning on why answering happens here rather than directly
         * inside that BroadcastReceiver.
         */
        const val EXTRA_AUTO_ANSWER = "com.ashudialer.app.EXTRA_AUTO_ANSWER"
    }
}
