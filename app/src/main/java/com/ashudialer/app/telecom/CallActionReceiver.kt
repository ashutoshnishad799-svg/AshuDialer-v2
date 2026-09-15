package com.ashudialer.app.telecom

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent


class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                ACTION_ANSWER -> {
                    // THE FIX for "tapping Answer on the notification while the
                    // phone is locked/screen-off asks for the unlock PIN/pattern
                    // instead of just connecting the call": this used to call
                    // Call.answer() directly from this BroadcastReceiver. A
                    // BroadcastReceiver has no window or Activity token at all,
                    // so it has no legitimate way to ask WindowManager/Keyguard
                    // "let this happen over the lock screen" - on stock AOSP the
                    // answer usually still went through, but on stricter OEM
                    // telecom stacks (MIUI/Xiaomi in particular - this same file
                    // already special-cases those OEMs elsewhere) an answer()
                    // issued from a context with no window is silently deferred
                    // until the device is actually unlocked, which is exactly
                    // the "presses Answer, nothing happens, then gets an unlock
                    // prompt" symptom.
                    //
                    // InCallActivity already does this correctly for the
                    // call-UI-launch case (setShowWhenLocked(true) +
                    // FLAG_TURN_SCREEN_ON, applied before the first frame - see
                    // setupLockScreenAndWakeFlags) and PixelInCallService's own
                    // launchInCallUi() already falls back to launching that same
                    // Activity when the device is locked, for the identical
                    // reason. Routing the notification's Answer tap through the
                    // real, correctly-flagged Activity - instead of trying to
                    // answer blind from a windowless receiver - reuses that
                    // already-working path rather than adding a second, less
                    // reliable way to bypass the keyguard.
                    //
                    // EXTRA_AUTO_ANSWER tells InCallActivity to call answer()
                    // itself the moment it has a live Call object, rather than
                    // just opening the ringing screen and waiting for a second
                    // manual tap - the person already expressed intent to answer
                    // by tapping the notification action once.
                    val answerIntent = Intent(context, InCallActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NO_USER_ACTION
                        putExtra(InCallActivity.EXTRA_AUTO_ANSWER, true)
                    }
                    try {
                        context.startActivity(answerIntent)
                    } catch (e: Exception) {
                        // Last-resort fallback: still attempt the direct answer
                        // rather than doing nothing, in case startActivity itself
                        // is what failed (e.g. a very locked-down OEM background-
                        // activity-start restriction). This matches the previous
                        // behavior exactly, so this path is never worse than what
                        // shipped before - only the common case above is better.
                        android.util.Log.w("CallActionReceiver", "Answer Activity launch failed, falling back to direct answer()", e)
                        PixelInCallService.currentCall?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
                    }
                }
                ACTION_DECLINE -> {
                    PixelInCallService.currentCall?.reject(false, null)
                    CallNotificationHelper.clear(context)
                }
                ACTION_END -> {
                    // Let Telecom report STATE_DISCONNECTED before the service clears
                    // the notification. Clearing it immediately here can race the
                    // in-call Activity hand-off on some OEM/SystemUI builds and leave
                    // a transient blue/blank frame after ending from the notification.
                    PixelInCallService.currentCall?.disconnect()
                }
                ACTION_TOGGLE_SPEAKER -> {
                    // 3-way cycle (Earpiece -> Speaker -> Bluetooth -> back
                    // to Earpiece) instead of a plain Speaker<->Earpiece
                    // toggle - but Bluetooth only enters the cycle when a
                    // Bluetooth (or wired headset) audio device is actually
                    // connected right now, so someone with nothing else
                    // connected still gets the simple, fast 2-way toggle
                    // they're used to rather than cycling through a state
                    // that isn't available. AudioRouteController.
                    // availableRoutes() is the single source of truth for
                    // what's connected on both the modern (API 31+) and
                    // legacy code paths, so the same cycle order applies
                    // however the current route is actually being read.
                    val controller = AudioRouteController(context)
                    val available = controller.availableRoutes()
                    // EARPIECE, SPEAKER always first (fixed, predictable
                    // positions); any further connected routes
                    // (BLUETOOTH, WIRED_HEADSET) follow in whatever order
                    // availableRoutes() reports them.
                    val cycleOrder = (listOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER) +
                        available.filter { it != AudioRoute.EARPIECE && it != AudioRoute.SPEAKER }).distinct()

                    val service = PixelInCallService.instance
                    val current = if (service != null) {
                        when (service.callAudioState?.route) {
                            android.telecom.CallAudioState.ROUTE_SPEAKER -> AudioRoute.SPEAKER
                            android.telecom.CallAudioState.ROUTE_BLUETOOTH -> AudioRoute.BLUETOOTH
                            android.telecom.CallAudioState.ROUTE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
                            else -> AudioRoute.EARPIECE
                        }
                    } else {
                        controller.currentRoute()
                    }
                    val currentIndex = cycleOrder.indexOf(current).let { if (it < 0) 0 else it }
                    val next = cycleOrder[(currentIndex + 1) % cycleOrder.size]

                    if (service != null) {
                        val nextTelecomRoute = when (next) {
                            AudioRoute.SPEAKER -> android.telecom.CallAudioState.ROUTE_SPEAKER
                            AudioRoute.BLUETOOTH -> android.telecom.CallAudioState.ROUTE_BLUETOOTH
                            AudioRoute.WIRED_HEADSET -> android.telecom.CallAudioState.ROUTE_WIRED_HEADSET
                            AudioRoute.EARPIECE -> android.telecom.CallAudioState.ROUTE_EARPIECE
                        }
                        service.setAudioRoute(nextTelecomRoute)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            runCatching { CallNotificationHelper.refreshOngoing(context) }
                        }, 180L)
                    } else {
                        controller.selectRoute(next)
                        CallNotificationHelper.refreshOngoing(context)
                    }
                }
                ACTION_TOGGLE_MUTE -> {
                    // Same request-through-Telecom + settle-then-refresh
                    // pattern as ACTION_TOGGLE_SPEAKER above, not a direct
                    // synchronous AudioManager write. The old version here
                    // refreshed the notification with zero delay while
                    // speaker's handler above waited 180ms for Telecom to
                    // actually finish - so tapping both close together
                    // showed mute update first/instantly and speaker catch
                    // up visibly later, which read as the two controls
                    // being out of sync with each other.
                    val service = PixelInCallService.instance
                    if (service != null) {
                        val currentlyMuted = service.callAudioState?.isMuted == true
                        service.requestMuted(!currentlyMuted)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            runCatching { CallNotificationHelper.refreshOngoing(context) }
                        }, 180L)
                    } else {
                        CallAudioQuickActions.toggleMute(context)
                        CallNotificationHelper.refreshOngoing(context)
                    }
                }
                ACTION_CALL_BACK -> {
                    val number = intent.getStringExtra(EXTRA_CALL_BACK_NUMBER)
                    if (!number.isNullOrBlank()) {
                        CallNotificationHelper.clearMissedCallCount(context)
                        try {
                            if (DialerPermissions.isDefaultDialer(context)) {
                                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                                telecomManager.placeCall(android.net.Uri.fromParts("tel", number, null), null)
                            } else {
                                val callIntent = Intent(Intent.ACTION_CALL, android.net.Uri.fromParts("tel", number, null)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(callIntent)
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("CallActionReceiver", "Call back failed", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {


            android.util.Log.e("CallActionReceiver", "Action failed: ${intent.action}", e)
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.ashudialer.app.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE = "com.ashudialer.app.ACTION_DECLINE_CALL"
        const val ACTION_END = "com.ashudialer.app.ACTION_END_CALL"
        const val ACTION_TOGGLE_SPEAKER = "com.ashudialer.app.ACTION_TOGGLE_SPEAKER"
        const val ACTION_TOGGLE_MUTE = "com.ashudialer.app.ACTION_TOGGLE_MUTE"
        const val ACTION_CALL_BACK = "com.ashudialer.app.ACTION_CALL_BACK"
        const val EXTRA_CALL_BACK_NUMBER = "call_back_number"

        fun answerIntent(context: Context): PendingIntent = broadcastFor(context, ACTION_ANSWER, 10)
        fun declineIntent(context: Context): PendingIntent = broadcastFor(context, ACTION_DECLINE, 11)
        fun endIntent(context: Context): PendingIntent = broadcastFor(context, ACTION_END, 12)
        fun toggleSpeakerIntent(context: Context): PendingIntent = broadcastFor(context, ACTION_TOGGLE_SPEAKER, 13)
        fun toggleMuteIntent(context: Context): PendingIntent = broadcastFor(context, ACTION_TOGGLE_MUTE, 14)

        private fun broadcastFor(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, CallActionReceiver::class.java).apply { this.action = action }
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
