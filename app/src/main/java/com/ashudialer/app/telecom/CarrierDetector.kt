package com.ashudialer.app.telecom

import android.content.Context
import android.telephony.TelephonyManager


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
