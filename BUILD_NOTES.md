# Build / validation notes

This revision is prepared for the GitHub Actions workflow in `.github/workflows/build.yml`.

## CI outputs

- `normalRelease` → regular Ashu Phone APK with recording UI/runtime permissions disabled.
- `rootRelease` → privileged/root-oriented APK with recording enabled.
- `AshuPhone-Magisk-RootDialer.zip` → Magisk/KernelSU module containing the Root APK and privilege permissions XML.

CI passes `-PASHU_UPDATE_REPO="$GITHUB_REPOSITORY"`, so **Settings → Check for updates** points at the repository that actually built the APK.

## Local validation performed

- All Android XML files parse successfully.
- GitHub Actions YAML parses successfully.
- Modified Kotlin files were checked for balanced braces/parentheses.
- Normal/root manifest split was inspected for recording permission separation.

A full Android Gradle build was **not run in this container** because the uploaded project does not include `gradlew`/`gradle-wrapper.jar` and no Gradle executable is installed here. The GitHub workflow generates its wrapper with Gradle 8.7 before building.

## CI output-path fix

The release variants are no longer assumed to live in `app/build/outputs/apk/normalRelease` or `rootRelease`. GitHub Actions now discovers each flavor's actual APK recursively under `app/build/outputs/apk/**`, stages them under `ci-artifacts/`, and packages the Magisk module from the staged Root APK. This avoids AGP flavor-directory layout differences causing a post-build CI failure.
