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
it to a release. This only runs in the official repository; forks and pull requests are built without any secrets
(compile and test only). The official workflow needs these repository secrets: `GOOGLE_SERVICES_JSON`,
`ASHU_RELEASE_KEYSTORE_BASE64`, `ASHU_RELEASE_STORE_PASSWORD`, `ASHU_RELEASE_KEY_ALIAS`,
`ASHU_RELEASE_KEY_PASSWORD`.

## Usage statistics

The app sends only anonymous, aggregate events through Firebase Analytics: app opened, and which
features were switched on. Never phone numbers, contacts, names or recordings. Active users and new
installs are visible in the Firebase console. Download counts are per release asset on the Releases
page.

## Security

Release builds are signed with the developer's key, and the app checks this itself:

- **Signature check.** A copy that was modified and re-signed with another key opens a "not the official app" screen
  instead of the app (calls keep working). The check runs at start-up and again every time the screen opens.
- **Verified updates.** Before an update is installed, the app checks that the downloaded file is a newer build of this
  same app signed with the same key. Anything else is deleted and refused.
- **Pinned recording engine.** The bundled `scrcpy-server` is verified against a fixed SHA-256 at build time and again
  before it runs.
- **Locked-down backend.** `firestore.rules` only lets an account read and write its own data, with size and shape checks.
- **Hardened build.** R8 shrinking and obfuscation are on, Auto Backup is off, Private Space blocks screenshots, and
  debug/verbose logging is stripped from release builds.

**Being honest about the limits:** this project is open source, so anyone can read the code and build their own
version. No check inside an app can stop a determined person from changing a copy on their own phone. What these
protections do is make sure a modified copy cannot pass as the official app and cannot be installed over it. Only install
from the [Releases](../../releases) page. Rooted phones, ADB and Shizuku are **not** blocked, because call recording needs them.

Found a vulnerability? Please read [SECURITY.md](SECURITY.md) and report it privately.

## License and credit

Ashu Phone is free software under the **GNU GPL v3 or later** (see [LICENSE](LICENSE) and [NOTICE](NOTICE)).
You may use, study, modify and share it, **but** if you distribute it (changed or not) you must:

1. keep all copyright notices and the names of the original authors,
2. publish your complete source code under the same GPL license, and
3. clearly mark what you changed.

A closed-source copy, or a copy with the credits removed, is a license violation and can be reported (for example with a
GitHub DMCA notice). The **name, icon and package name are not licensed for reuse**: if you fork, give your app its own
name, icon, package name, signing key and Firebase project. See [TRADEMARKS.md](TRADEMARKS.md).

Want to contribute? See [CONTRIBUTING.md](CONTRIBUTING.md).

## Building your own copy

1. Install JDK 17 and Android Studio.
2. Create **your own** Firebase project, add an Android app for your own package name, and place its
   `google-services.json` in `app/` (it is git-ignored, never commit it).
3. Deploy the rules: `firebase deploy --only firestore:rules`
4. `./gradlew assembleDebug`

Local and debug builds skip the official-signature check, so your build runs normally.

## Credits

Call-recording engine adapted from
[ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder) (GPL-3.0-or-later).
scrcpy-server by Genymobile (Apache-2.0); Shizuku API by RikkaApps (Apache-2.0). Full list in [NOTICE](NOTICE).

## Notification/UI polish

- Notifications now resolve the active app palette for light, dark, AMOLED and accent themes.
- Launcher Phone and Contacts aliases use the selected PNG icon assets.
