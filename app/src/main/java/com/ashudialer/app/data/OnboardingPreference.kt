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
