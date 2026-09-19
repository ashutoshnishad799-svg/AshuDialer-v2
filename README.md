# Ashu Dialer

A modern Android phone app with built-in **call recording** (phone calls + WhatsApp + Telegram).

## Install

Download `AshuPhone-Release-Signed.apk` from the [Releases](../../releases) page and install it.
There is only one APK. No root, no Magisk module.

## Call recording setup (one time, ~3 minutes)

Recording works through [Shizuku](https://github.com/RikkaApps/Shizuku), a free helper app.

1. Install **Shizuku**.
2. Open Ashu Dialer > **Recordings** > **Recording settings** > **Open setup checklist**.
3. Follow the checklist. Each step turns green when done. New to Shizuku? Tap the **?** button for a
   step-by-step guide in Hindi and English.
4. Switch **Record calls** on.

Rooted phone: start Shizuku with root, allow Ashu Dialer, and turn on "Start on boot" in Shizuku.

## Build

Pushing to `main` runs the GitHub Actions workflow, which builds and signs the APK and attaches it
to a release. It needs these repository secrets: `GOOGLE_SERVICES_JSON`, `ASHU_RELEASE_KEYSTORE_BASE64`,
`ASHU_RELEASE_STORE_PASSWORD`, `ASHU_RELEASE_KEY_ALIAS`, `ASHU_RELEASE_KEY_PASSWORD`.

## Credits

Call-recording engine adapted from [ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder)
(GPLv3+) and the Ever Dialer recorder module. scrcpy-server by Genymobile (Apache-2.0).
