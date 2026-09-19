package com.ashudialer.app.telecom

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import android.Manifest
import android.os.Build

data class SimAccount(
    val handle: PhoneAccountHandle,
    val label: String
)

object DialerPermissions {

    /**
     * Permissions the app cannot function as a dialer without. Missing any of
     * these keeps the person on the permissions screen.
     */
    val core: Array<String> = buildList {
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.WRITE_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.WRITE_CONTACTS)
        add(Manifest.permission.ANSWER_PHONE_CALLS)
        // Call recording is an optional feature. Its Shizuku setup belongs in
        // the dedicated Call recording setup screen and must never block a
        // normal user from finishing the basic dialer setup. In particular,
        // RECORD_AUDIO / media-audio access are not dialer prerequisites.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    /**
     * Permissions that unlock specific extra features (video calling, Bluetooth
     * audio routing) but aren't needed for basic calling. These are requested
     * in the same system dialog batch as [core] for a single smooth setup flow,
     * but denying one of these must never be treated as "setup incomplete" —
     * previously a single decline here (e.g. Camera, if the person doesn't want
     * video calling) silently blocked `hasAll()` from ever becoming true, which
     * could leave even POST_NOTIFICATIONS looking "ungranted" from the app's
     * point of view with no way for the person to tell why notifications
     * weren't showing.
     */
    val optional: Array<String> = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    /** Everything requested together in one system permission batch. */
    val required: Array<String> = core + optional

    fun hasAll(context: Context): Boolean =
        core.all {
            ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun isDefaultDialer(context: Context): Boolean {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return telecomManager.defaultDialerPackage == context.packageName
    }


    fun requestDefaultDialerIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        } else {
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
        }
    }


    fun placeCall(context: Context, number: String) {
        require(number.isNotBlank()) { "number must not be blank" }
        if (isDefaultDialer(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val accounts = try { telecomManager.callCapablePhoneAccounts } catch (_: SecurityException) { emptyList() }
            val extras = Bundle().apply {
                // A single capable SIM should be selected explicitly. This avoids
                // an OEM Telecom stack briefly creating and then tearing down an
                // outgoing Call because it had no resolved phone account yet.
                if (accounts.size == 1) {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accounts.first())
                }
            }
            telecomManager.placeCall(Uri.fromParts("tel", number.trim(), null), extras)
        } else {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number.trim())}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }


    fun placeCall(context: Context, number: String, simHandle: PhoneAccountHandle?) {
        if (simHandle == null) {
            placeCall(context, number)
            return
        }
        if (isDefaultDialer(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val extras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, simHandle)
            }
            try {
                telecomManager.placeCall(Uri.fromParts("tel", number.trim(), null), extras)
            } catch (e: RuntimeException) {
                // If an OEM rejects a stale/disabled SIM handle, retry through
                // Telecom without forcing that account instead of making the
                // tap look like an instant failed call.
                android.util.Log.w("DialerPermissions", "SIM-specific placeCall failed; retrying without account", e)
                placeCall(context, number)
            }
        } else {
            placeCall(context, number)
        }
    }


    fun availableSims(context: Context): List<SimAccount> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyList()
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.callCapablePhoneAccounts.map { handle ->
                val account = telecomManager.getPhoneAccount(handle)
                SimAccount(handle = handle, label = account?.label?.toString() ?: "SIM")
            }
        } catch (_: SecurityException) {
            emptyList()
        }
    }


    /**
     * Standard voicemail access numbers for carriers where the SIM doesn't
     * reliably report one via TelephonyManager. These are each carrier's
     * well-known, publicly documented access number, used only as a last
     * resort before falling back to the generic voicemail: intent.
     */
    private val carrierVoicemailNumbers = mapOf(
        "jio" to "199",
        "airtel" to "543",
        "vi" to "199",
        "bsnl" to "6222"
    )

    /**
     * Dials voicemail, trying progressively less specific options so a
     * tap on "Voicemail" reliably does something on as many devices/
     * carriers as possible, and reports back if nothing worked instead of
     * failing silently.
     *
     * Previously this only tried the generic voicemail: URI, which many
     * carriers (notably several Indian carriers) don't register a handler
     * for; a failure there was swallowed with no feedback, so tapping
     * Voicemail did nothing with no way to tell why.
     *
     * @return true if a call attempt was launched, false if every option
     * was exhausted (caller should show the person a message).
     */
    fun callVoicemail(context: Context): Boolean {
        // 1) The number actually stored for this SIM - the correct, most
        // reliable source when the carrier/OS has it configured.
        val storedNumber = try {
            if (isGranted(context, Manifest.permission.READ_PHONE_STATE)) {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                tm?.voiceMailNumber?.takeIf { it.isNotBlank() }
            } else null
        } catch (_: SecurityException) {
            null
        }
        if (storedNumber != null) {
            placeCall(context, storedNumber)
            return true
        }

        // 2) A known access number for the detected carrier.
        val carrierNumber = CarrierDetector.currentCarrierId(context)?.let { carrierVoicemailNumbers[it] }
        if (carrierNumber != null) {
            placeCall(context, carrierNumber)
            return true
        }

        // 3) Generic voicemail: URI - works on stock/Google-services devices
        // even when the above don't apply.
        return try {
            context.startActivity(
                Intent(Intent.ACTION_CALL, Uri.parse("voicemail:")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
