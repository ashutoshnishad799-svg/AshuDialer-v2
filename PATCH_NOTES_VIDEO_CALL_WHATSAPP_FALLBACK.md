# Video call: WhatsApp fallback when the other person isn't on AshuDialer

## What changed

Previously, if you video-called a number that wasn't registered on
AshuDialer's own signaling directory (the other person doesn't have the
app, or hasn't opened it since installing), the call just failed with
"[Name] hasn't set up video calling yet." and the only recovery offered
was "Switch to voice call."

That screen now also offers **"Video call via WhatsApp"**, shown only
when all three of these are true:
- the failure is specifically "couldn't find them on our own directory"
  (not e.g. a camera-permission or sign-in failure - a WhatsApp button on
  those screens would make no sense)
- WhatsApp is actually installed on this device
- there's an actual number to use (same rule "Switch to voice call"
  already followed - never show a button that can't do anything)

Tapping it opens a WhatsApp chat with that number via WhatsApp's own
click-to-chat deep link. It does **not** start the WhatsApp call directly -
WhatsApp does not expose any public way for a third-party app to do that
(only Google itself, via a direct partnership for Google Messages, has
that). From the opened chat, you tap WhatsApp's own video-call button
yourself - the same as any OEM dialer's "call via WhatsApp" shortcut
already works under the hood.

## Bug fixed along the way

`MainActivity`'s existing WhatsApp quick-action (Recents/Contacts
long-press menu, contact detail) had its own, separate, subtly broken
number formatting: it kept a leading `+` (WhatsApp's docs explicitly say
not to include one) and never added a country code to a bare local
number - the exact shape most numbers in this app's own call log/contacts
are stored in. Both call sites now go through one shared, correctly
formatted implementation (`util/WhatsAppLauncher.kt` +
`PhoneNumberUtils.formatForWhatsAppDeepLink`) instead of two copies
drifting apart.

## What this does *not* do, and why

The other half of the original ask - a same-carrier video call connecting
with no internet and no recharge, the way carrier VoLTE/ViLTE calling
does on a stock dialer - is **not implemented, and can't be** by any app
distributed like this one. That calling path requires the app itself to
be registered with the carrier's IMS/ViLTE network as the device's system
dialer, a privilege (`BIND_IMS_SERVICE` / carrier IMS registration) the
Android platform only grants to a dialer the phone's manufacturer and
carrier have jointly certified and signed into the system partition - not
to any app installed the normal way, this one included, regardless of how
it's built or what permissions it requests. This is the same category of
hard platform limit this project already ran into and documented for
Live Captions/Type-to-Talk (see `FEATURE_REMOVAL_NOTES_1.5.3.md`): the
right move is saying so plainly rather than shipping something that looks
like it works and doesn't.

## Build note

This environment has no network access and does not contain the
project's Gradle wrapper/distribution, so a full Android build could not
be run here (same limitation noted in
`PATCH_NOTES_1.5.3_CALL_STABILITY.md`). The change was checked by hand -
every new/changed symbol traced to its one definition and every call
site, brace/paren balance verified file-by-file, and the Android 11+
package-visibility `<queries>` entry for `com.whatsapp` confirmed already
present in the manifest - but not compiled. Please build and test the
actual failure screen (call a number that isn't on AshuDialer) before
relying on it.
