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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(name = "pixel_dialer_onboarding")

class OnboardingPreference(private val context: Context) {
    private val key = booleanPreferencesKey("onboarding_complete")

    val isCompleteFlow: Flow<Boolean> = context.onboardingDataStore.data.map { it[key] ?: false }

    suspend fun markComplete() {
        context.onboardingDataStore.edit { it[key] = true }
    }
}
