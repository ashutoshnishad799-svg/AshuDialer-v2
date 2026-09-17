package com.ashudialer.app.telecom

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat


object CarrierDetector {


    fun currentCarrierId(context: Context): String? {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
            val name = tm.simOperatorName?.takeIf { it.isNotBlank() }
                ?: tm.networkOperatorName?.takeIf { it.isNotBlank() }
                ?: return null
            normalize(name)
        } catch (_: Exception) {
            null
        }
    }


    fun currentCarrierDisplayName(context: Context): String? {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
            tm.simOperatorName?.takeIf { it.isNotBlank() } ?: tm.networkOperatorName?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reads this device's own SIM-assigned phone number, when the carrier
     * exposes it and READ_PHONE_NUMBERS is granted - used by
     * AshuDialerApp to auto-fill AppSettings.myPhoneNumber the first time
     * the app runs, rather than making video calling depend on the person
     * finding More → Account and typing their own number in manually
     * before the feature does anything at all.
     *
     * Returns null (never throws) whenever the number genuinely isn't
     * available - the permission wasn't granted, the SIM/carrier doesn't
     * populate line1Number (common on some prepaid/MVNO SIMs and is a
     * long-standing platform limitation, not a bug in this app), or
     * there's no SIM at all. AccountScreen's existing manual-entry field
     * remains exactly as it was as the fallback for all of those cases -
     * this only removes the *need* to use it on devices where the number
     * is already knowable, it doesn't remove the option itself.
     */
    fun readSimPhoneNumberOrNull(context: Context): String? {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_NUMBERS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
            @Suppress("MissingPermission")
            tm.line1Number?.trim()?.takeIf { it.isNotBlank() && it.any(Char::isDigit) }
        } catch (_: Exception) {
            null
        }
    }


    private fun normalize(rawName: String): String {
        val lower = rawName.lowercase().trim()
        return when {
            lower.contains("jio") -> "jio"
            lower.contains("airtel") -> "airtel"
            lower.contains("vi") || lower.contains("vodafone") || lower.contains("idea") -> "vi"
            lower.contains("bsnl") -> "bsnl"
            else -> lower
        }
    }
}
