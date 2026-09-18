# AshuDialer Ever 4.0 recording port

This build ports the supplied Ever-Call-Recorder 4.0 Shizuku/scrcpy implementation into AshuDialer:
- Shizuku user-service binding lifecycle
- scrcpy 4.0 server argument construction
- VOICE_CALL / UPLINK / DOWNLINK / VOICE_COMMUNICATION / OUTPUT / PLAYBACK source keys
- socket-to-pipe relay architecture
- scrcpy stream header + frame parsing
- MediaMuxer packet handling and timestamp normalization
- bundled server SHA-256 verification

AshuDialer-specific storage, call UI, call detection and APK-only architecture are retained. Root/Magisk/accessibility recording paths remain removed.

The setup screen no longer treats CAPTURE_AUDIO_OUTPUT as a Shizuku-grantable permission. The real source capability is determined by the Android/OEM audio stack when recording starts.

Build validation: source/static validation was performed here, but a physical Android call on the user's device cannot be simulated in this environment. Therefore no honest claim of universal 100% recording success is made.
