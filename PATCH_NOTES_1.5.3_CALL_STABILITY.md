# AshuDialer call-stability patch

Applied in this patch:

- Hardened outgoing Telecom call placement, including explicit selection of a single capable SIM and retry without a stale SIM handle.
- Kept the in-call Activity alive longer while Telecom finishes creating the Call object, replacing the old blank/instant-close window with a themed connecting screen.
- Cleared stale Call objects/audio state when InCallService is destroyed so a Telecom service restart cannot reuse an old disconnected call.
- Added defensive error handling around answer/reject/hangup requests.
- Refreshed the incoming-call UI into a cleaner glass/aurora design that follows the active Ashu Dialer theme and keeps answer/decline actions prominent.
- Disabled Type-to-Talk in the call UI for now. Android's public TTS/audio APIs do not guarantee synthesized audio is injected into the remote cellular/WebRTC uplink; leaving the existing implementation enabled could play the synthesis locally. This patch intentionally skips that feature rather than pretending it is fixed.

Build note: this environment does not contain the project's Gradle wrapper/distribution, so a full Android APK build could not be run here. The source patch was checked for obvious structural/syntax issues and packaged as a replacement project ZIP.
