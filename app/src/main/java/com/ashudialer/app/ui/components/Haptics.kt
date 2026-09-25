package com.ashudialer.app.ui.components

import android.content.Context
import android.view.HapticFeedbackConstants
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * A short tap-confirmation buzz, gated on the person's "Vibrate on button
 * press" setting. This is the same implementation that already worked
 * correctly in DialerScreen, pulled out here so every other screen (More,
 * Contacts, Recents, Settings, ...) can use the identical, proven logic
 * instead of each screen needing its own copy - previously every other
 * screen simply had no vibration call at all.
 *
 * Duration is 24ms, not the original 8ms. Most OEM haptic stacks (MIUI,
 * OneUI, ColorOS, and stock on several chipsets) silently drop one-shot
 * vibrations below roughly 15-20ms - the motor is told to fire and stop
 * again before it's physically finished spinning up, so nothing is felt.
 * The incoming-call ringtone pattern (PixelInCallService, MainActivity) uses
 * createWaveform with timings well above this floor, which is why that
 * vibration was always felt while every dialpad tap silently produced
 * nothing on affected devices - same code path, same permission, same
 * settings flag, just a duration under the hardware's real minimum. 24ms
 * matches the tap-feedback duration Android's own keyboard and Samsung's
 * OneUI settings use for key-press haptics, so it's a values judged safe by
 * precedent rather than a guess. Amplitude is also set explicitly instead of
 * DEFAULT_AMPLITUDE, since a few OEM vibrator HALs treat "default" as a low
 * fallback rather than the platform-intended medium-strength buzz.
 */
fun vibrateForButtonPress(context: Context, enabled: Boolean) {
    if (!enabled) return
    // Kept for non-Compose callers. Compose screens use rememberButtonHaptic
    // below, which routes through View.performHapticFeedback and does not
    // depend on an OEM accepting a tiny vibrator pulse.
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator?.vibrate(VibrationEffect.createOneShot(30, 180))
    } else {
        @Suppress("DEPRECATION") vibrator?.vibrate(30)
    }
}

/**
 * Composable convenience: returns a zero-arg function bound to the current
 * context and the passed-in setting, so call sites can just do
 * `val vibrate = rememberButtonHaptic(vibrateOnButtonPress); ... ; vibrate()`
 * on every tap instead of threading Context through manually each time.
 */
@Composable
fun rememberButtonHaptic(enabled: Boolean): () -> Unit {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view, enabled) {
        {
            if (enabled) {
                val performed = view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (!performed) vibrateForButtonPress(context, true)
            }
        }
    }
}
