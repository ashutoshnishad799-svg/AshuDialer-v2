/*
 * Ashu Phone
 * Copyright (C) 2026 Ashutosh Nishad
 *
 * This file is part of Ashu Phone, licensed under the GNU General Public
 * License, version 3 or (at your option) any later version.
 * See the LICENSE and NOTICE files in the project root.
 * This program comes with ABSOLUTELY NO WARRANTY.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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
