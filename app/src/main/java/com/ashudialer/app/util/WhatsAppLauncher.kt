package com.ashudialer.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

/**
 * Single shared place for "open WhatsApp for this phone number" - used by
 * both MainActivity's existing quick-action buttons (Recents/Contacts
 * long-press menu, contact detail) and VideoCallActivity's fallback when a
 * video call can't reach the other person through this app's own
 * signaling directory. Pulled out into its own file, rather than kept as
 * two separate implementations, for the same reason
 * PhoneNumberUtils.normalizePhoneNumberForMatch already documents for
 * itself: two copies of the same operation risk silently drifting apart -
 * which had, in fact, already happened once. MainActivity's original
 * inline version formatted numbers as `.filter { it.isDigit() || it ==
 * '+' }`, which kept a leading "+" (WhatsApp's own docs explicitly say not
 * to include one) and never added a country code to a bare local number -
 * exactly the shape most numbers in this app's own call log/contacts are
 * stored in (see PhoneNumberUtils' own India-focused last-10-digits
 * comparisons elsewhere). That combination meant the existing WhatsApp
 * quick action was already silently unreliable for a large share of
 * numbers before this file existed; formatForWhatsAppDeepLink below is the
 * fix for both call sites at once.
 */

/**
 * True if the WhatsApp package is actually installed on this device -
 * lets a caller decide whether to show a WhatsApp action at all, rather
 * than only discovering it doesn't work once tapped.
 */
fun isWhatsAppInstalled(context: Context): Boolean = try {
    context.packageManager.getPackageInfo("com.whatsapp", 0)
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

/**
 * Opens a WhatsApp chat with [phoneNumber] via WhatsApp's own click-to-chat
 * deep link. Tries setPackage("com.whatsapp") first so this goes straight
 * to the app rather than ever showing a chooser; if that specific resolve
 * fails (most commonly: WhatsApp was uninstalled in the gap between an
 * isWhatsAppInstalled check and this actually being tapped), falls back to
 * the same URL without setPackage, which still gives the person a working
 * result - the system resolves it to whichever app can open it, WhatsApp
 * included - rather than doing nothing. Only shows the "Couldn't open
 * WhatsApp" Toast if both attempts genuinely fail.
 *
 * This can only ever open the chat screen, never start a call directly -
 * WhatsApp does not expose any public, third-party-usable deep link that
 * starts an outgoing voice or video call to a number the way this
 * function opens a chat. (Even Google's own Messages app needed a direct
 * integration partnership with WhatsApp to wire a "WhatsApp Video Call"
 * button straight through - nothing a regular installed app can call into
 * itself.) From here, the person still taps WhatsApp's own call button
 * themselves - the same as tapping a "call via WhatsApp" shortcut on any
 * OEM dialer that offers one already works under the hood.
 */
fun openWhatsAppChat(context: Context, phoneNumber: String) {
    val formatted = formatForWhatsAppDeepLink(phoneNumber)
    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$formatted")
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (e2: ActivityNotFoundException) {
            Toast.makeText(context, "Couldn't open WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }
}
