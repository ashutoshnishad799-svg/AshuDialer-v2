# Third-party notices

## Ever-Dialer / ShizuCallRecorder recorder pipeline
This AshuDialer recording implementation adapts the ShizuCallRecorder approach from the provided Ever-Dialer 12.0.0 source, including its Shizuku shell bridge, scrcpy audio transport, packet parsing, and AAC muxing concepts. The relevant upstream project is GPLv3-or-later with additional Section 7 terms. The copied/adapted recorder source remains subject to those terms; keep the upstream license and attribution when distributing derivative recorder code.

## scrcpy server
The bundled `app/src/main/assets/scrcpy-server` is the scrcpy v4.0 server binary supplied by the provided Ever-Dialer source. SHA-256:
`84924bd564a1eb6089c872c7521f968058977f91f5ff02514a8c74aff3210f3a`

This file is used only as the privileged audio capture transport; AshuDialer does not embed a separate full desktop/video scrcpy UI.

## Shizuku
The normal APK uses Shizuku API/provider 13.1.5 for the elevated shell service used by the recording pipeline.
