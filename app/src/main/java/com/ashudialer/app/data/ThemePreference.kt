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
package com.ashudialer.app.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.ashudialer.app.ui.theme.AUTO_THEME_ID

private val Context.dataStore by preferencesDataStore(name = "pixel_dialer_prefs")

// THE FIX for "blue flash when placing a call on Black theme (or any theme
// other than Ocean)": InCallActivity's window needs *some* color painted by
// the system compositor for the brief gap before its first Compose frame
// draws (see call_window_background.xml's history - windowBackground is the
// attribute that actually paints during that gap). That XML drawable was a
// single hardcoded color (Ocean's light blue), so every other theme's
// in-call screen flashed that wrong color for a fraction of a second before
// snapping to the real theme.
//
// DataStore (themeIdFlow below) is async-only by design - there's no
// synchronous read, and blocking the UI thread on one during onCreate to
// paint a window background would itself cause jank. This SharedPreferences
// mirror exists purely to give InCallActivity.onCreate a synchronous,
// same-process read of "whatever theme was last active" so it can paint the
// *correct* placeholder color at window-creation time, before DataStore's
// async flow has even started collecting - see InCallActivity's onCreate.
private const val SYNC_PREFS_NAME = "pixel_dialer_theme_sync"
private const val SYNC_PREFS_KEY = "last_known_theme_id"

class ThemePreference(private val context: Context) {
    private val key = stringPreferencesKey("theme_id")

    // THE FIX for the sync cache staying stale/empty forever on an existing
    // install: setTheme() only ever WROTE to the sync cache, so a theme
    // that was already chosen before this cache existed (e.g. Black theme
    // picked on an older build) left lastKnownThemeIdSync() permanently
    // falling back to "ocean" - the exact wrong placeholder color this
    // whole mechanism exists to avoid. themeIdFlow is the one thing every
    // install eventually reads regardless of when the theme was chosen
    // (app launch collects it to actually render the UI), so mirroring the
    // cache here too - on every read, not just every write - means the
    // cache self-heals the very first time this flow is collected after
    // updating to a build that has this fix, with no separate migration
    // step needed.
    val themeIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        val id = prefs[key] ?: AUTO_THEME_ID
        syncPrefs(context).edit().putString(SYNC_PREFS_KEY, id).apply()
        id
    }

    suspend fun setTheme(themeId: String) {
        context.dataStore.edit { it[key] = themeId }
        // Mirrored synchronously alongside the real DataStore write so the
        // sync cache never meaningfully lags the actual setting - the next
        // app launch/call after a theme change already has the right value
        // available without waiting on DataStore's first emission.
        syncPrefs(context).edit().putString(SYNC_PREFS_KEY, themeId).apply()
    }

    companion object {
        private fun syncPrefs(context: Context) =
            context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)

        /**
         * Synchronous, NEVER-BLOCKING best-effort read for window-background
         * painting only - see the class-level comment above. Reads whatever
         * is currently in the SharedPreferences mirror and returns
         * immediately; if the mirror hasn't been populated yet (a fresh
         * install, or this is genuinely the very first read anywhere in the
         * app since updating to a build with this cache), falls back to
         * AUTO_THEME_ID - the same default themeIdFlow itself actually uses
         * (see themeIdFlow's own `prefs[key] ?: AUTO_THEME_ID` above) -
         * rather than trying to read DataStore synchronously.
         *
         * THE FIX for "app defaults to a white/Ocean-blue first frame
         * instead of Slate on a fresh install": this previously fell back to
         * the literal string "ocean" here, with a doc comment (since
         * corrected) claiming that matched themeIdFlow's own default - it
         * didn't; themeIdFlow's real fallback was already AUTO_THEME_ID by
         * the time this comment was written, "ocean" had just never been
         * updated to match. MainActivity.onCreate always correctly resolves
         * this value through resolveThemeId() before painting, but
         * resolveThemeId("ocean", systemIsDark) just returns "ocean"
         * straight back unchanged - "ocean" isn't AUTO_THEME_ID, so the
         * auto -> light/dark resolution branch never ran, and a fresh
         * install's very first painted frame was hardcoded to Ocean's own
         * background regardless of the device's actual system theme, well
         * before ever reaching the real Slate/Dark-Mode default a moment
         * later. Falling back to AUTO_THEME_ID here instead means
         * resolveThemeId now actually runs its light-vs-dark branch on this
         * value too, exactly as it already does for every other caller.
         *
         * THE FIX for "app hangs/feels laggy sometimes on open": an earlier
         * version of this function fell back to a runBlocking +
         * withTimeout(150) synchronous DataStore read when the mirror was
         * empty, called directly from MainActivity.onCreate before
         * setContent. That blocking read ran on the main thread during the
         * app's very first frame and could take up to its own 150ms timeout
         * on a cold/uncached DataStore read - which is exactly what an
         * intermittent open-time hang would look like. There is no upside
         * worth that risk: this value only ever paints for the brief instant
         * before the very first Compose frame lands, and the correct theme
         * shows up regardless the moment that frame draws (from the real
         * themeIdFlow, collected reactively - see MainActivity/
         * InCallActivity's setContent). A wrong-but-instant placeholder for
         * one frame is a far smaller problem than a UI thread that can
         * freeze for real.
         */
        fun peekLastKnownThemeId(context: Context): String =
            syncPrefs(context).getString(SYNC_PREFS_KEY, null) ?: AUTO_THEME_ID

        @Deprecated(
            "Use peekLastKnownThemeId - this blocking variant caused intermittent open-time hangs (see peekLastKnownThemeId's doc comment) and should not be called from any Activity.onCreate.",
            ReplaceWith("peekLastKnownThemeId(context)")
        )
        fun lastKnownThemeIdSync(context: Context): String = peekLastKnownThemeId(context)
    }
}
