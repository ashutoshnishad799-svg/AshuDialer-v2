package com.ashudialer.app.telecom

import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log

// Merge this logic into your existing PixelInCallService.kt
abstract class PixelInCallServiceFixes : InCallService() {

    fun isVideoCallSupported(call: Call): Boolean {
        val capabilities = call.details.callCapabilities
        val supportsLocal = (capabilities and Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL) != 0
        val supportsRemote = (capabilities and Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL) != 0
        return supportsLocal && supportsRemote
    }

    fun upgradeToVideoCall(call: Call) {
        if (isVideoCallSupported(call)) {
            val videoCall = call.videoCall
            if (videoCall != null) {
                Log.d("AshuDialer", "Requesting ViLTE Upgrade")
                // Request Bi-directional (Camera + Display) video call
                val videoProfile = VideoProfile(VideoProfile.STATE_BIDIRECTIONAL)
                videoCall.sendSessionModifyRequest(videoProfile)
            } else {
                Log.e("AshuDialer", "VideoCall object is null. Framework blocked it.")
            }
        } else {
            Log.e("AshuDialer", "Carrier does not support Video for this call.")
        }
    }

    // Don't forget to register this callback when a call is added!
    val callCallback = object : Call.Callback() {
        override fun onDetailsChanged(call: Call, details: Call.Details) {
            super.onDetailsChanged(call, details)
            
            // Check if call state actually changed to Video
            if (VideoProfile.isVideo(details.videoState)) {
                Log.d("AshuDialer", "Call successfully upgraded to Video!")
                // TODO: Launch VideoCallActivity.kt or update UI surface here
            }
        }
    }
}
