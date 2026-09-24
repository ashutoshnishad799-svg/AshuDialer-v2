package com.ashudialer.app.telecom

import android.content.Context
import android.media.AudioManager

/**
 * Best-effort audio quick actions used by the in-call UI and call notification.
 * OEM AudioManager implementations can reject changes while Telecom is
 * transitioning call state, so these helpers must never crash the call UI.
 */
object CallAudioQuickActions {

    fun isMuted(context: Context): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.isMicrophoneMute == true
        } catch (_: Exception) {
            false
        }
    }

    fun toggleMute(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager.isMicrophoneMute = !audioManager.isMicrophoneMute
        } catch (_: Exception) {
            // Ignore transient/OEM audio failures rather than crashing the
            // in-call screen or notification action.
        }
    }
}
