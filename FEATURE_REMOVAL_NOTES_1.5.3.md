# Captions / Type-to-Talk Removal

The experimental Live Captions and Type-to-Talk features have been completely removed from the project because reliable far-end cellular audio capture / uplink TTS injection cannot be guaranteed through the supported Android call-audio APIs.

Removed:
- Live Captions UI and settings
- Type-to-Talk UI and TTS call injection
- Caption model management and Vosk/JNA dependencies
- WebRTC caption audio source
- Root carrier caption audio source
- Caption/Type-to-Talk settings stored in DataStore
- Caption/Type-to-Talk ViewModel wiring
- Captions & Type-to-talk entry from More

The normal recording TTS announcement (`RecordingAnnouncement`) remains intact; it is unrelated to Type-to-Talk.

Existing DataStore values for the removed keys are simply ignored. No destructive migration is required.
