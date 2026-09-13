# Google Sign-In not working — where to actually look

Your `build.yml` is correct: it already decodes the `GOOGLE_SERVICES_JSON`
secret into `app/google-services.json` before the build step, and
`app/build.gradle.kts` already has a `checkGoogleServicesPackageName` task
that validates that file against the app's package name
(`com.ashudialer.app`). Nothing needs to change in either file.

Since the pipeline itself is right, the problem is one of:

## 1. Check the `checkGoogleServicesPackageName` task's own output first

This is the fastest way to know which of the two things below is actually
wrong. In the GitHub Actions log for your last build:

- Expand the step that runs the Gradle build (`Build Signed Release APK`).
- Search for either:
  - `✅ google-services.json package_name matches applicationId` → the file
    and package name are fine; the problem is elsewhere (see #3).
  - `❌ app/google-services.json NOT FOUND` → the `GOOGLE_SERVICES_JSON`
    secret is empty, or the earlier "Create google-services.json from
    secret" step failed silently.
  - `❌ google-services.json found but no package_name field could be read`
    → the secret's *content* isn't valid JSON (see #2).
  - A message listing `google-services.json package_name: ...` next to
    your `applicationId` → the file is valid, but was downloaded from the
    wrong Firebase Android app (see #2).

## 2. Most likely cause: the secret's content, or which Firebase app it's from

- **Re-download** `google-services.json` from Firebase Console → your
  project → Project Settings → Your apps → the Android app entry.
- **Confirm the Android app in Firebase is registered under exactly
  `com.ashudialer.app`.** If Firebase still only has an app registered
  under an old package name (e.g. `com.pixeldialer.app`, from before the
  rename), you need to add a **new** Android app in Firebase Console with
  package name `com.ashudialer.app` and download *that* app's
  `google-services.json` - the old one will never pass the check no matter
  how many times you re-upload it.
- **Update the GitHub secret** with the freshly downloaded file's raw
  contents (this repo's workflow does `printf '%s' > app/google-services.json`
  directly, no base64 step - so the secret's value should be the literal
  JSON file contents, not a base64 encoding of it).

## 3. If the package-name check passes but sign-in still fails at runtime

That means the file itself is fine, so the issue is Firebase project
configuration rather than anything in this repo:

- **SHA-1/SHA-256 fingerprint**: Google Sign-In additionally checks the
  signing certificate that built the APK against fingerprints registered in
  Firebase Console (Project Settings → Your apps → the Android app → "Add
  fingerprint"). Since this workflow signs release builds with the
  persistent keystore restored from `ASHU_RELEASE_KEYSTORE_BASE64`, that
  keystore's SHA-1 needs to be registered in Firebase - a correct
  `google-services.json` with no matching fingerprint still fails
  sign-in with an opaque error on-device. Get the fingerprint with:
  ```
  keytool -list -v -keystore app/ashuphone-release.keystore -alias <your alias>
  ```
  (run locally against a copy of the same keystore, or add a temporary CI
  step) and add the SHA-1 (and ideally SHA-256) shown to Firebase Console.
- **OAuth client / Web client ID**: confirm Firebase Authentication has the
  Google sign-in provider enabled (Authentication → Sign-in method →
  Google), not just the Android app registered.
