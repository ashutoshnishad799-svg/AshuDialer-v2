# Ashu Dialer 1.5.1

- Fixed the Add Call Compose `KeyboardOptions` import that was breaking the Kotlin build.
- Added Firebase Analytics for aggregate app-usage measurement.
- Analytics does not intentionally send phone numbers, contacts, call contents, or other personally identifying dialer data.
- Version bumped to 1.5.1 (versionCode 7), so an installed 1.5 build can update through the existing updater.
