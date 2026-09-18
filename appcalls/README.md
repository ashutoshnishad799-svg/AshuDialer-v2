# :appcalls

Records WhatsApp and Telegram voice/video calls. This is a separate capability from
`CallRecorder.kt` in the `:app` module (native phone-call recording): a VoIP call placed inside
a third-party app has no modem-level `VOICE_CALL` audio tap to read from at all, so this module
uses a different, independently-verified technique.

## How it works

1. `AppCallNotificationListenerService` detects an in-progress WhatsApp/Telegram call via its
   persistent ongoing-call notification (`Notification.CATEGORY_CALL` + `FLAG_ONGOING_EVENT`) -
   there is no broadcast for "a VoIP call is happening in app X", so this is the one reliable
   signal every well-behaved call app exposes.
2. `ShizukuConnectionManager` binds to `ShellService`, which runs inside the privileged shell
   process (UID 2000, or 0 on root) that Shizuku grants access to.
3. `ShellService` launches `scrcpy-server` via `app_process`, configured as `audio_source=output`
   (`REMOTE_SUBMIX` - captures the final mixed audio going to the speaker), streams the result
   back over a Unix socket + kernel pipe.
4. `ScrcpyClient` parses scrcpy's binary audio-packet stream; `ScrcpyAudioMuxer` writes it into a
   standard `.m4a` (AAC/MPEG-4) file - the same format and `RecordingResult`/`RecordingMode`
   shape `CallRecorder.kt` already produces, so both engines' output lands in one Recordings
   list, played by one player.

`OUTPUT`/`REMOTE_SUBMIX` is the one audio source that actually works here: `PLAYBACK`
(`AudioPlaybackCaptureConfiguration`) hard-excludes audio tagged `USAGE_VOICE_COMMUNICATION` -
exactly how WhatsApp/Telegram tag call audio - and any `MIC`-class source competes with the
calling app's own concurrent microphone session and gets silenced by Android's privacy
protections. `OUTPUT` is a privileged, `CAPTURE_AUDIO_OUTPUT`-gated system mix tap (same
permission class as `VOICE_CALL`), predating that newer exclusion.

## Provenance

Adapted from [ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder) (GPLv3+),
specifically the version vendored into this project's other uploaded source (`Ever-Dialer`).
Every file here was read against that original in full before porting, then re-verified
independently rather than trusted on sight:

- The bundled `scrcpy-server-v4.0` jar's SHA-256 was computed directly from its bytes
  (`84924bd564a1eb6089c872c7521f968058977f91f5ff02514a8c74aff3210f3a`), not copied from the
  upstream build script - and only used after it matched.
- `dev.rikka.shizuku:api:13.1.5` / `dev.rikka.shizuku:provider:13.1.5` were confirmed as real,
  published Maven Central artifacts before being added as dependencies.
- `NotificationListenerService`'s exact manifest permission/intent-filter-action requirement, and
  `NotificationManager.isNotificationListenerAccessGranted`, were confirmed against the official
  Android reference docs, not assumed from memory.

Trimmed from upstream, deliberately, to keep this module scoped to the one gap it fills:

- Only `AAC`/`.m4a` output (upstream also supports Opus/OGG) - matches what `CallRecorder.kt`
  already produces natively.
- Only the `OUTPUT` audio source (upstream exposes a much larger debug/picker matrix of sources
  meant for its own general-purpose recorder) - `CallRecorder.kt` already owns the equivalent
  native-telephony sources for real phone calls.
- `IShellService`'s AIDL interface dropped `grantAppOpByPackage`/`grantRole` - those exist
  upstream to grant `MANAGE_ONGOING_CALLS` for an `InCallService`-based detection mode this
  module doesn't use (detection here is via `NotificationListenerService` instead).

## Wiring into `:app`

`:app` already depends on `:appcalls` (for this feature). To avoid the reverse dependency that
would create - `:appcalls` needing to read `:app`'s `AshuDialerApp`/`AppSettingsRepository`
directly - `AppCallsHost` is a small interface `:appcalls` defines and `AshuDialerApp` implements,
registered once in `onCreate()`. See that interface's doc comment.

Settings: `AppSettings.recordWhatsAppCallsEnabled` / `recordTelegramCallsEnabled`, both gated
behind `callRecordingEnabled` overall - configured from `AppCallRecordingSetupScreen` in `:app`.

## What's NOT yet done here

- **Not build-verified.** This was written and hand-traced (every cross-module/cross-file
  reference checked against the real declaring file) in an environment without a Gradle wrapper
  for this project, so nothing here has actually compiled yet. Push and check CI.
- **Not wired into Private Space.** `CallRecorder.moveToPrivateSpace()`/`moveOutOfPrivateSpace()`
  in `:app` don't yet know about recordings this module produces.
- **Not wired into the Magisk module side** of the build.
