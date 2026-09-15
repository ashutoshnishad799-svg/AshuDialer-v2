# AshuDialer call-stability patch

Applied in this patch:

- Hardened outgoing Telecom call placement and kept permission/Telecom handoff failures visible instead of silently closing.
- Added a strict Telecom handoff ceiling so the in-call Activity cannot remain on a permanent connecting/loading screen.
- Cleared stale Call objects/audio state when InCallService is destroyed so a Telecom service restart cannot reuse an old disconnected call.
- Added defensive error handling around answer/reject/hangup requests.
- Refreshed the incoming-call UI into a cleaner glass/aurora design that follows the active Ashu Dialer theme and keeps answer/decline actions prominent.
- Disabled Type-to-Talk in the call UI for now. Android's public TTS/audio APIs do not guarantee synthesized audio is injected into the remote cellular/WebRTC uplink; leaving the existing implementation enabled could play the synthesis locally. This patch intentionally skips that feature rather than pretending it is fixed.

Build note: this environment does not contain the project's Gradle wrapper/distribution, so a full Android APK build could not be run here. The source patch was checked for obvious structural/syntax issues and packaged as a replacement project ZIP.


## Deep stability follow-up

- In-call Activity now closes immediately after a local end/reject action; Telecom finishes disconnecting independently.
- Added a short Telecom Call-object handoff ceiling so the UI cannot remain on a permanent “Connecting call…” screen.
- Added a dialing/connecting timeout that requests a real Telecom disconnect when a call never progresses.
- Current calls exclude disconnected Call objects, preventing stale-call UI after teardown.
- Speaker/Bluetooth/mute notification state now follows the same Telecom-confirmed `CallAudioState` used by the in-call screen.
- Audio-state changes refresh the ongoing notification so rapid route changes do not leave stale controls.
- Onboarding theme selection is now a polished two-column card grid using the existing built-in themes only; no extra image assets are added.
- Release version bumped to 1.5.3 / versionCode 9 for public distribution.
