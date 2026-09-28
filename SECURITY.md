# Security policy

## Reporting a vulnerability

**Please do not open a public issue for a security problem.**

Use GitHub's private reporting instead: open the **Security** tab of this
repository and choose **Report a vulnerability**. Include what you found, how to
reproduce it, and which version you tested.

You will get an answer as soon as the developer can look at it. Please give a
reasonable time to fix the problem before you publish details.

## Supported versions

Only the latest release on the Releases page receives fixes. The in-app updater
(Settings > About > Check for updates) installs it.

## What the app does to protect you

- **Signature check.** The official release is signed with the developer's key.
  A copy modified and re-signed with another key opens a "not the official app"
  screen instead of the app. Calls keep working.
- **Verified updates.** A downloaded update is checked before it is installed: it
  must be for this same app, must be a newer version, and must be signed with the
  same key as the version already installed. Anything else is deleted and refused.
- **Pinned recording engine.** The bundled `scrcpy-server` is checked against a
  fixed SHA-256 when the app is built and again before it is run.
- **No backups, no cloud copy of your calls by default.** Android Auto Backup is
  off. Private Space content is blocked from screenshots.
- **Salted PBKDF2 (120,000 iterations)** protects the Private Space password/PIN
  and its recovery code. They are never stored in plain text.
- **Locked-down backend.** The Firestore rules only let an account touch its own
  data, and every write is size- and shape-checked. See `firestore.rules`.
- **Hardened release build.** Code shrinking and obfuscation (R8) are on, and
  debug/verbose logging is removed from release builds.

## What no app can promise

Because this project is open source, anyone can read the code and build their own
version. **No check inside an app can stop a determined person from modifying a
copy they have on their own phone.** The protections above are designed so that
a modified copy cannot pass as the official one and cannot be installed over it,
not so that modification is impossible. Only ever install the app from the
official Releases page.

## For people who run their own copy

Do not reuse this project's Firebase project, signing key or update server. See
`TRADEMARKS.md`. Never commit `google-services.json`, a keystore (`*.jks`,
`*.keystore`) or `local.properties`.
