# Ashu Dialer — Shizuku recording fix

## What was fixed

- The setup screen no longer treats `CAPTURE_AUDIO_OUTPUT` as if it were the same thing as Shizuku app authorization.
- After Shizuku is running and **Ashu Dialer** is authorized, the setup/recording settings status turns ready instead of staying red only because `checkRemotePermission(CAPTURE_AUDIO_OUTPUT)` is false.
- The actual scrcpy audio source is still tested when recording starts; this avoids claiming that Android/OEM audio capture is guaranteed on every device.
- Removed the duplicate `VOICE_COMMUNICATION` status branch in the in-call UI.
- Updated the recording guide so it matches the actual Shizuku flow.

## Important

`CAPTURE_AUDIO_OUTPUT` is a privileged Android permission. Shizuku authorization does not automatically grant that permission. Android/OEM policy can still reject or silence a particular call-audio source. Therefore this change fixes the incorrect setup-state gate; it does **not** honestly promise universal two-way call capture on every phone.

The APK remains Shizuku-only: no Magisk module, root recording service, or accessibility recording path is added by this fix.
