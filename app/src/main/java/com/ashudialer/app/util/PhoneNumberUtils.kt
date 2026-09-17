package com.ashudialer.app.util

/**
 * Strips a phone number down to just its digits (keeping a leading "+" if
 * present) so two numbers can be compared for a real match regardless of
 * formatting differences - spaces, dashes, parentheses, or a missing/extra
 * country code prefix. Two numbers are "the same" for matching purposes if
 * their normalized forms are equal, or if one contains the other (covers a
 * saved contact stored as "9129990819" matching an incoming/typed number of
 * "+919129990819", or vice versa).
 *
 * This used to live as a private function inside DialerScreen.kt, used only
 * for its own predictive-match list. It's pulled out here because the exact
 * same "is this the same number" question comes up anywhere contacts are
 * matched against a raw phone number string - e.g. RecentsScreen deciding
 * whether to offer "View contact" or "Add to contacts" for a call log entry
 * - and having two separate implementations of the same comparison risks
 * them silently drifting apart (one normalizing formatting differences, the
 * other still doing brittle exact-string equality).
 */
fun normalizePhoneNumberForMatch(raw: String): String {
    val hasPlus = raw.trimStart().startsWith("+")
    val digitsOnly = raw.filter { it.isDigit() }
    return if (hasPlus) "+$digitsOnly" else digitsOnly
}

/**
 * True if [a] and [b] refer to the same underlying phone number once
 * formatting differences are normalized away - exact match, or one being a
 * suffix of the other (handles a stored number missing a country code that
 * the other has, e.g. saved "9129990819" matching typed "+919129990819").
 *
 * Deliberately NOT a plain substring check (`contains` anywhere in the
 * string) - that was the original implementation, and it was a real bug:
 * short numbers like service/short codes ("198", "112", "100") would match
 * *any* saved contact whose number happened to contain that 3-digit run
 * anywhere in it, not just at a natural country-code boundary. E.g. a
 * contact saved as "+91 98198 xxxxx" contains the digit run "198" right in
 * the middle, nowhere near where a country code would sit - dialing 198
 * would incorrectly resolve to that contact's detail page instead of
 * showing 198 as an unknown/service number. Matching only on a shared
 * suffix, and only once both numbers are long enough to be an actual phone
 * number (not a 2-4 digit short/service code), avoids that entirely: real
 * "same number, different formatting" cases always agree on their last several
 * digits, so requiring the shorter one to be at least [MIN_MATCH_LENGTH]
 * digits *and* an actual suffix of the other keeps that case working while
 * ruling out coincidental digit runs elsewhere in a longer number.
 */
private const val MIN_MATCH_LENGTH = 7

fun phoneNumbersMatch(a: String, b: String): Boolean {
    val normA = normalizePhoneNumberForMatch(a).removePrefix("+")
    val normB = normalizePhoneNumberForMatch(b).removePrefix("+")
    if (normA.isEmpty() || normB.isEmpty()) return false
    if (normA == normB) return true

    // Short codes (service numbers, USSD-style codes, etc.) should only
    // ever match themselves exactly, never as a suffix/substring of a
    // longer real phone number - a 3-digit code being "contained" in an
    // 10+ digit contact number is always coincidence, not the same number.
    if (normA.length < MIN_MATCH_LENGTH || normB.length < MIN_MATCH_LENGTH) return false

    val (shorter, longer) = if (normA.length <= normB.length) normA to normB else normB to normA
    if (longer.endsWith(shorter)) return true

    // Common mobile-number representation differences: in India a local
    // number may be stored as 0XXXXXXXXXX while another provider/contact
    // stores +91XXXXXXXXXX. Once the trunk prefix/country code is stripped,
    // the subscriber number is the same. Prefer the last 10 digits for normal
    // full-length mobile numbers; short/service codes were already rejected.
    if (normA.length >= 10 && normB.length >= 10) {
        if (normA.takeLast(10) == normB.takeLast(10)) return true
    }

    val trunkA = normA.removePrefix("0")
    val trunkB = normB.removePrefix("0")
    if (trunkA.length >= MIN_MATCH_LENGTH && trunkB.length >= MIN_MATCH_LENGTH) {
        if (trunkA == trunkB || trunkA.endsWith(trunkB) || trunkB.endsWith(trunkA)) return true
    }
    return false
}

/**
 * Formats [raw] as the digits-only, country-code-prefixed string WhatsApp's
 * click-to-chat deep link (api.whatsapp.com/send?phone=...) requires - per
 * WhatsApp's own documentation this must be the full international number
 * with no "+", no spaces/dashes, and no leading trunk "0". Passing anything
 * else (a bare local number, a "+", a leading 0) silently fails to resolve
 * to a chat rather than erroring, so getting this formatting right here -
 * once - matters more than it would for a normal display string.
 *
 * A bare 10-digit number, once any leading trunk "0" is stripped, is
 * assumed to be an Indian mobile subscriber number missing its country
 * code and gets "91" prepended - the same assumption phoneNumbersMatch
 * above already makes (see its takeLast(10) comparison) for exactly the
 * same class of number. Anything already longer than 10 digits is assumed
 * to already carry its own country code and is left alone.
 */
fun formatForWhatsAppDeepLink(raw: String): String {
    val digitsOnly = raw.filter { it.isDigit() }.removePrefix("0")
    return if (digitsOnly.length == 10) "91$digitsOnly" else digitsOnly
}
