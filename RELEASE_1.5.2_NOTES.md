# Ashu Dialer 1.5.2

- Incoming-call UI now launches the real `InCallActivity` directly from `InCallService`.
- `showWhenLocked` + `turnScreenOn` remain enabled so the call UI can appear over the lock screen and wake the display.
- High-priority incoming-call notification with full-screen intent remains as an independent fallback.
- Removed the unavailable `bringToForeground()` call that caused the CI Kotlin build failure.
- Update downloads can fall back from Firebase Hosting to the latest public GitHub Release APK.
- APK downloads use a temporary `.part` file and replace the destination only after a successful download.
- Version 1.5.2 / versionCode 8.

Note: Android/OEM policy can still restrict full-screen presentation if the user has disabled notifications or full-screen call access. The app does not bypass a secure PIN/pattern/password.
