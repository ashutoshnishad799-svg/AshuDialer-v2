# AshuDialer 1.5.4 — call recording repair

## Fixed
- Replaced the old unreliable `MediaRecorder.VOICE_CALL`-first design with an elevated scrcpy audio pipeline.
- Normal APK: Shizuku shell backend, with first-run permission flow continuing after the permission dialog is accepted.
- Root/Magisk/KernelSU APK: direct `su` backend; it does not depend on Shizuku.
- Both paths use the same AAC/M4A packet pipeline and bundled verified scrcpy-server.
- Kept a microphone fallback for normal installs when no elevated backend is available; it is explicitly reported as microphone mode and is not presented as guaranteed two-way call capture.
- Fixed the CI compile blocker in `PixelInCallService.kt` by using `VideoProfile.SESSION_MODIFY_REQUEST_SUCCESS` instead of the nonexistent `VideoProfile.SessionModificationState.SUCCESS`.
- Restored the Gradle wrapper files so the project is directly buildable from CI/Termux.
- Fixed recording filenames to match AshuDialer’s existing recordings parser (`caller_yyyyMMdd_HHmmss.m4a`).
- Added recorder status UI to the recordings screen and updated setup screens for the real backend state.
- Root backend now launches `/system/bin/app_process` explicitly and has a bounded `su` availability check.
- Root APK is still packaged into the Magisk module using the exact same release APK bytes produced by the workflow.

## Verification status
Static project/file checks were completed locally. A full Gradle compile was not possible in this container because the Gradle distribution could not be downloaded due the environment's DNS/network restriction. The GitHub Actions workflow provisions Gradle 8.7 and remains the intended full build/compile verification path.

## Physical device verification still required
Call-audio capture is OEM/ROM/Android-version dependent. Before release, test at least one outgoing and one incoming cellular call on the exact rooted device/ROM, and test the normal APK with a running Shizuku server and granted Shizuku permission.
