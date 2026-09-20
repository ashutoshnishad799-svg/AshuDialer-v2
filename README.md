# Ashu Dialer

A modern Android phone app with built-in **call recording** for phone calls, WhatsApp, Telegram,
Instagram and Snapchat.

## Install

Download `AshuPhone-<version>.apk` from the [Releases](../../releases) page and install it.
There is only one APK. No root, no Magisk module. The app checks for updates itself
(Settings > About > Check for updates) and installs them from the same Releases page.

## Call recording setup (one time, about 3 minutes)

Recording works through [Shizuku](https://github.com/RikkaApps/Shizuku), a free helper app.

1. Install **Shizuku**.
2. Open Ashu Dialer > **Settings** > **Call recording** and switch **Enable call recording** on.
3. Follow the checklist. Each step turns green when done. New to Shizuku? Tap the **?** button for a
   step-by-step guide in Hindi and English.
4. Optional: switch **Auto-record all calls** on. It is off by default.

Rooted phone: start Shizuku with root, allow Ashu Dialer, and turn on "Start on boot" in Shizuku.

Recordings are saved in `Music/Ashu Dialer/`, in one folder per source: `Phone`, `WhatsApp`,
`Telegram`, `Instagram`, `Snapchat`.

If a recording turns out to contain only silence, the app tries the next audio source by itself
(for app calls) and otherwise tells you, so you do not find an empty file after the call.

## Build

Pushing to `main` or `master` (or running the workflow by hand) builds and signs the APK and attaches
it to a release. It needs these repository secrets: `GOOGLE_SERVICES_JSON`,
`ASHU_RELEASE_KEYSTORE_BASE64`, `ASHU_RELEASE_STORE_PASSWORD`, `ASHU_RELEASE_KEY_ALIAS`,
`ASHU_RELEASE_KEY_PASSWORD`.

## Usage statistics

The app sends only anonymous, aggregate events through Firebase Analytics: app opened, and which
features were switched on. Never phone numbers, contacts, names or recordings. Active users and new
installs are visible in the Firebase console. Download counts are per release asset on the Releases
page.

## Credits

Call-recording engine adapted from [ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder)
(GPLv3+) and the Ever Dialer recorder module. scrcpy-server by Genymobile (Apache-2.0).
