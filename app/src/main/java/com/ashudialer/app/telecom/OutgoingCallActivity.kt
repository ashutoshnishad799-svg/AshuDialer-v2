package com.ashudialer.app.telecom

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class OutgoingCallActivity : Activity() {

    companion object {
        private const val TAG = "OutgoingCallActivity"
        private const val CALL_PERMISSION_REQUEST_CODE = 101
    }

    // Held across the permission-request round trip: onCreate can't just
    // place the call inline after requestPermissions() because that call is
    // asynchronous - the grant/deny result only arrives later in
    // onRequestPermissionsResult(). This used to call finish() immediately
    // after firing the request, without ever waiting for that result, which
    // is exactly why a call could look like it "didn't really ring" and the
    // screen just closed: on a phone where Android had auto-reset the
    // CALL_PHONE permission (Android 12+ revokes unused permissions from
    // apps that haven't been opened in a while) or where it simply hadn't
    // been granted yet, the permission dialog would flash up and this
    // activity would already be gone before the person even tapped Allow -
    // the call was never actually placed on that attempt. Trying again right
    // after (once the permission was in fact granted from that same prompt)
    // is what made the second attempt work.
    private var pendingNumber: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val number = intent?.data?.schemeSpecificPart

        if (number.isNullOrBlank()) {
            Log.w(TAG, "No number in outgoing call intent, nothing to dial")
            finish()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingNumber = number
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), CALL_PERMISSION_REQUEST_CODE)
            // Deliberately NOT calling finish() here - the activity needs to
            // stay alive to receive onRequestPermissionsResult() below.
            return
        }

        placeCall(number)
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != CALL_PERMISSION_REQUEST_CODE) return

        val number = pendingNumber
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (granted && number != null) {
            placeCall(number)
        } else {
            Log.w(TAG, "CALL_PHONE permission denied - call not placed")
            Toast.makeText(
                this,
                "Call permission is needed to place calls. Please allow it and try again.",
                Toast.LENGTH_LONG
            ).show()
        }
        finish()
    }

    private fun placeCall(number: String) {
        try {
            val telecomManager = getSystemService(TELECOM_SERVICE) as? TelecomManager
            if (telecomManager == null) {
                Log.e(TAG, "TelecomManager unavailable, cannot place call")
                Toast.makeText(this, "Couldn't start the call. Please try again.", Toast.LENGTH_SHORT).show()
                return
            }
            val uri = Uri.fromParts("tel", number, null)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                telecomManager.placeCall(uri, Bundle())
            }
        } catch (e: SecurityException) {
            // Can happen if this app isn't (or is no longer) the default
            // dialer / phone account isn't enabled - fails closed with a
            // visible message instead of a silent no-op the person has no
            // way to diagnose.
            Log.e(TAG, "placeCall failed - not default dialer or missing permission?", e)
            Toast.makeText(this, "Couldn't place the call. Check that Ashu Dialer is set as your default dialer.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "placeCall failed unexpectedly", e)
            Toast.makeText(this, "Couldn't place the call. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }
}
