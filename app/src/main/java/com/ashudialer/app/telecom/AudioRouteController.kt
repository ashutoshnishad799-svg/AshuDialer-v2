package com.ashudialer.app.telecom

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

enum class AudioRoute { EARPIECE, SPEAKER, BLUETOOTH, WIRED_HEADSET }


class AudioRouteController(private val context: Context) {

    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager


    fun availableRoutes(): List<AudioRoute> {
        val routes = mutableListOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER)
        try {
            if (isWiredHeadsetConnected()) routes.add(AudioRoute.WIRED_HEADSET)
        } catch (_: Exception) { }
        try {
            if (isBluetoothAudioConnected()) routes.add(AudioRoute.BLUETOOTH)
        } catch (_: Exception) { }
        return routes
    }

    fun currentRoute(): AudioRoute {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                when (audioManager.communicationDevice?.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> return AudioRoute.SPEAKER
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> return AudioRoute.BLUETOOTH
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> return AudioRoute.WIRED_HEADSET
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> return AudioRoute.EARPIECE
                    else -> {}
                }
            } catch (_: Exception) {
                // Some OEM AudioManager implementations can throw more than
                // SecurityException while Telecom is still establishing the
                // call. Never let an audio-route probe crash InCallActivity.
            }
        }
        return try {
            when {
                isBluetoothScoOn() -> AudioRoute.BLUETOOTH
                audioManager.isSpeakerphoneOn -> AudioRoute.SPEAKER
                isWiredHeadsetConnected() && !audioManager.isSpeakerphoneOn -> AudioRoute.WIRED_HEADSET
                else -> AudioRoute.EARPIECE
            }
        } catch (_: Exception) {
            AudioRoute.EARPIECE
        }
    }

    fun selectRoute(route: AudioRoute) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                selectRouteModern(route)
            } else {
                selectRouteLegacy(route)
            }
        } catch (_: Exception) {
            // Route switching is best-effort; a temporary Telecom/OEM audio
            // state must never take down the call UI.
        }
    }


    private fun selectRouteModern(route: AudioRoute) {
        val targetType = when (route) {
            AudioRoute.SPEAKER -> AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            AudioRoute.EARPIECE -> AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            AudioRoute.WIRED_HEADSET -> AudioDeviceInfo.TYPE_WIRED_HEADSET
            AudioRoute.BLUETOOTH -> AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        val device = audioManager.availableCommunicationDevices.firstOrNull { it.type == targetType }
        if (device != null) {
            audioManager.setCommunicationDevice(device)
        } else {


            selectRouteLegacy(route)
        }
    }

    private fun selectRouteLegacy(route: AudioRoute) {
        when (route) {
            AudioRoute.SPEAKER -> {
                stopBluetoothScoIfNeeded()
                audioManager.isSpeakerphoneOn = true
            }
            AudioRoute.EARPIECE -> {
                stopBluetoothScoIfNeeded()
                audioManager.isSpeakerphoneOn = false
            }
            AudioRoute.WIRED_HEADSET -> {
                stopBluetoothScoIfNeeded()
                audioManager.isSpeakerphoneOn = false
            }
            AudioRoute.BLUETOOTH -> {
                audioManager.isSpeakerphoneOn = false
                startBluetoothSco()
            }
        }
    }

    private fun isWiredHeadsetConnected(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            return devices.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
        }
        @Suppress("DEPRECATION")
        return audioManager.isWiredHeadsetOn
    }

    private fun isBluetoothAudioConnected(): Boolean {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
            if (!adapter.isEnabled) return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                devices.any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                }
            } else {
                @Suppress("DEPRECATION")
                adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
            }
        } catch (e: SecurityException) {

            false
        }
    }

    private fun isBluetoothScoOn(): Boolean = audioManager.isBluetoothScoOn

    private fun startBluetoothSco() {
        try {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        } catch (e: Exception) {


        }
    }

    private fun stopBluetoothScoIfNeeded() {
        try {
            if (audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
        } catch (e: Exception) {
        }
    }
}
