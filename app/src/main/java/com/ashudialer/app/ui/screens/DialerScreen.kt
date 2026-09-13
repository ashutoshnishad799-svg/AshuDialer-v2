package com.ashudialer.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.Contact
import com.ashudialer.app.telecom.DtmfPlayer
import com.ashudialer.app.ui.components.Avatar
import com.ashudialer.app.ui.components.rememberButtonHaptic
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.util.normalizePhoneNumberForMatch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.components.glassCircle

private data class KeyDef(val digit: String, val letters: String)

private val keys = listOf(
    KeyDef("1", ""), KeyDef("2", "ABC"), KeyDef("3", "DEF"),
    KeyDef("4", "GHI"), KeyDef("5", "JKL"), KeyDef("6", "MNO"),
    KeyDef("7", "PQRS"), KeyDef("8", "TUV"), KeyDef("9", "WXYZ"),
    KeyDef("*", ""), KeyDef("0", "+"), KeyDef("#", "")
)

/**
 * Whether clipboard text looks enough like a real phone number to show the
 * "Paste" chip for it - previously this fired for *any* text containing
 * even a single digit ("Meeting at 5pm", "Room 204", any random paragraph
 * with a number in it), which meant the chip showed up almost constantly
 * and stopped meaning anything. Real phone numbers are, once formatting
 * characters (spaces, dashes, parens, a leading +) are stripped away,
 * mostly-to-entirely digits within a fairly narrow, well-known length
 * range - this checks for exactly that shape instead of "contains a digit
 * somewhere."
 */
private fun looksLikePhoneNumber(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > 25) return false

    val digitsOnly = trimmed.filter { it.isDigit() }
    // Real-world phone numbers run roughly 7 (short local numbers) to 15
    // (E.164's own maximum) digits.
    if (digitsOnly.length < 7 || digitsOnly.length > 15) return false

    // Every character besides the digits should be a formatting character a
    // real phone number would plausibly contain - not letters or punctuation
    // that would suggest this is prose with a number embedded in it.
    val allowedFormatting = setOf('+', '-', '(', ')', ' ', '.')
    val nonDigitsAreFormatting = trimmed.all { it.isDigit() || it in allowedFormatting }
    if (!nonDigitsAreFormatting) return false

    // Guards against something like "1.2.3.4.5.6.7" (7+ digits but almost
    // entirely separators) slipping through as "mostly digits" - a real
    // phone number's formatting characters are a small minority of its
    // length, not comparable to or exceeding the digit count.
    val formattingCharCount = trimmed.length - digitsOnly.length
    return formattingCharCount <= digitsOnly.length / 2
}

@Composable
fun DialerScreen(
    contacts: List<Contact>,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit = {},
    onAddContact: (String) -> Unit = {},
    onAvatarClick: (Contact) -> Unit = {},
    vibrateOnButtonPress: Boolean = true,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val context = LocalContext.current
    // TextFieldValue instead of a plain String so the number line can be a
    // real, editable, pasteable text field - not just a Text label. This
    // carries cursor/selection state, which is what lets a long-press bring
    // up the system paste menu and lets someone tap into the middle of a
    // number to fix a mistyped digit instead of only ever being able to
    // backspace from the end.
    var numberField by remember { mutableStateOf(TextFieldValue("")) }
    val number = numberField.text

    val dtmfPlayer = remember { DtmfPlayer() }
    DisposableEffect(Unit) {
        onDispose { dtmfPlayer.release() }
    }

    /**
     * Appends a dialpad digit at the current cursor position rather than
     * always at the end of the string - so typing after tapping into the
     * middle of a pasted or edited number inserts where the cursor actually
     * is, matching how every other text field on the device behaves.
     */
    fun insertAtCursor(text: String) {
        val start = numberField.selection.start.coerceIn(0, numberField.text.length)
        val end = numberField.selection.end.coerceIn(0, numberField.text.length)
        val newText = numberField.text.replaceRange(start, end, text)
        val newCursor = start + text.length
        numberField = TextFieldValue(newText, TextRange(newCursor))
    }

    // A visible one-tap Paste pill, separate from the system's own
    // long-press paste toolbar on the text field above. The long-press menu
    // still works (BasicTextField provides it for free), but it's discoverable
    // only by long-pressing, which several people won't think to try on a
    // field that looks like a plain number display. This pill surfaces the
    // same action explicitly whenever the dialpad is empty and the clipboard
    // actually holds something number-shaped, mirroring Google Dialer's own
    // "Paste" chip in that same empty-field state.
    var clipboardHasNumber by remember { mutableStateOf(false) }
    LaunchedEffect(number) {
        if (number.isEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipText = clipboard?.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                .orEmpty()
            clipboardHasNumber = looksLikePhoneNumber(clipText)
        } else {
            clipboardHasNumber = false
        }
    }
    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipText = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            .orEmpty()
        if (clipText.isNotEmpty()) {
            numberField = TextFieldValue(clipText, TextRange(clipText.length))
        }
    }


    // Every number that currently matches what's been typed so far, not just
    // the first one - a saved contact can have more than one number (Mobile,
    // Work, ...), and each of those is its own Contact row sharing the same
    // contactId (see ContactsRepository.loadAllContacts, which reads
    // Android's own Phone.CONTENT_URI - a row per number, not per person).
    // Previously only firstOrNull() was used, so a second matching number on
    // the same contact - or a second contact entirely - was silently hidden.
    val matchedContacts = remember(number, contacts) {
        if (number.length < 3) return@remember emptyList()
        val target = normalizePhoneNumberForMatch(number)
        contacts.filter { c ->
            val candidate = normalizePhoneNumberForMatch(c.phoneNumber)
            candidate.isNotEmpty() && (candidate == target || candidate.contains(target) || target.contains(candidate))
        }
    }
    // Grouped by person, preserving the order matches were found in, so one
    // contact with multiple matching numbers renders as a single card
    // listing each number, rather than one card per number.
    val matchedByContactId = remember(matchedContacts) {
        matchedContacts.groupBy { it.contactId }.toList()
    }


    val showAddContactHint = number.length >= 5 && matchedContacts.isEmpty()


    val vibrate = rememberButtonHaptic(vibrateOnButtonPress)

    /**
     * The number that should actually be dialed for the current input.
     * If what's typed matches exactly one saved number, dial that real,
     * complete saved number - not the raw partial digits still on screen.
     * Previously the Call button always dialed the literal typed digits
     * (`number`) even after a contact match was shown, so tapping Call
     * right after seeing a name appear tried to place a call to an
     * incomplete number and failed until the whole number was typed out.
     * With more than one match (ambiguous - could be different numbers for
     * the same person, or different people), the raw typed digits are used
     * so the person isn't sent to a possibly-wrong number; the match list
     * below is there precisely so they can tap the specific number they mean.
     */
    val numberToDial = matchedContacts.singleOrNull()?.phoneNumber ?: number


    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 28.dp).padding(top = 22.dp),
        // Keep the number display anchored at the top. The previous Bottom
        // arrangement let the presence/absence of contact-match content
        // change the whole block's vertical position, making the typed
        // number jump between the top and middle of the screen. The extra
        // top padding just nudges the whole anchored block down a bit -
        // there was unused space below the call button - and being a fixed
        // value (not weighted/dynamic) it can't reintroduce any of the
        // jumpiness the fixed-height zones below were built to prevent.
        verticalArrangement = Arrangement.Top
    ) {
        // Fixed-height zone for the typed number + match state. This used to
        // be a plain Column that grew taller as more contacts matched, which
        // pushed the dialpad further down each keystroke - with enough
        // matches the dialpad was shoved off-screen entirely (the
        // "dialpad gayab ho jaata hai" bug). Now the number line has its own
        // slot and the match list lives in a capped-height scrollable strip
        // right below it, so the dialpad's position never moves no matter
        // how many contacts match.
        //
        // THE FIX for "dialpad still moves up/down" (still happening after
        // the heightIn(min=...) attempt below): heightIn(min=...) only sets
        // a FLOOR, not a ceiling - it stops this zone from ever being
        // shorter than 104dp, but never stops it from growing TALLER, which
        // is exactly what happened whenever the "Add to Contacts" chip's own
        // AnimatedVisibility (below) played its enter animation - a chip
        // that slides/scales in adds real height to this Column while it's
        // animating in, on top of whatever height it settles at, and this
        // Column's parent is Arrangement.Top, so growing THIS block's height
        // pushes literally everything below it (match list, then dialpad,
        // then call button) down and back up again as the chip animates.
        // A fixed .height(...) (not heightIn) makes this zone a true
        // fixed-size window - nothing inside it, however it animates, can
        // ever change how much vertical space this Column occupies, so
        // nothing below it can ever be displaced by what happens to the
        // number field or the hint chip. 120dp (up from the old 104dp floor)
        // gives the chip enough headroom to fully play its slide/scale-in
        // animation without clipping.
        Column(
            modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 30.dp, bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // A real editable field, not a Text label - long-pressing now
            // brings up the system's own copy/paste/select-all toolbar the
            // same way it would in Messages or any other app, and tapping
            // anywhere in the number moves the cursor there instead of only
            // ever being able to edit from the end. cursorBrush uses the
            // theme's accent color so the blinking cursor matches whichever
            // palette is active rather than defaulting to black/white.
            //
            // readOnly = true is the key line here: it keeps cursor
            // placement, text selection, and the long-press copy/paste
            // toolbar fully working (those are just state changes this
            // composable still receives through onValueChange), but tells
            // Compose never to request the system soft keyboard for this
            // field. Without it, tapping the number line popped up Gboard's
            // (or whichever IME is installed) own numeric layout on top of
            // this screen's own dialpad - two number pads stacked on each
            // other, when only the app's own dialpad should ever be the
            // input source. Typing digits still works normally because
            // DialerKey's onPress calls insertAtCursor directly, which is a
            // regular state update, not something readOnly blocks.
            BasicTextField(
                value = numberField,
                onValueChange = { numberField = it },
                readOnly = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = if (number.length > 10) 28.sp else 34.sp,
                    fontWeight = FontWeight.Light,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center
                ),
                singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.accent),
                modifier = Modifier.fillMaxWidth()
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = showAddContactHint,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }) + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.accentSoft)
                        .clickable { onAddContact(number) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = palette.accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Add to Contacts",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.accent
                    )
                }
            }
        }

        // Capped-height strip: as more numbers match, this scrolls internally
        // instead of expanding and displacing the dialpad below it. As the
        // person keeps dialing, normalizeForMatch's contains() check only
        // gets stricter, so the match count naturally shrinks toward the one
        // real contact they meant - this container just makes sure that
        // narrowing is visible as a scroll-shortening list, not a layout jump.
        // Reserve a constant match-results slot. The old weighted AnimatedVisibility
        // changed the amount of occupied space as backspace removed the final match,
        // which made the dialpad subtly re-anchor. Keeping one fixed viewport prevents
        // that movement while still allowing the result list itself to animate.
        Box(Modifier.fillMaxWidth().height(140.dp)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = matchedByContactId.isNotEmpty(),
                enter = fadeIn() + scaleIn(initialScale = 0.97f),
                exit = fadeOut() + scaleOut(targetScale = 0.97f),
                modifier = Modifier.fillMaxSize()
            ) {
                MatchedContactsList(
                    matchedByContactId = matchedByContactId,
                    typedDigits = number,
                    palette = palette,
                    onCall = { num -> vibrate(); onCall(num) },
                    onMessage = { num -> vibrate(); onMessage(num) },
                    onAvatarClick = { contact -> vibrate(); onAvatarClick(contact) }
                )
            }
        }

        Spacer(Modifier.height(12.dp).fillMaxWidth())

        // THE FIX for "dialpad still moves up and down" (the biggest single
        // cause of it): these two hint chips (Add to Contacts / Paste) sit
        // directly in this Column's normal flow, immediately above the
        // dialpad grid. Whenever either one's AnimatedVisibility toggled -
        // e.g. typing past 5 digits shows "Add to Contacts", backspacing
        // down to an empty field can reveal "Paste" - it added or removed
        // real height right where it sits, and because everything below it
        // (the whole dialpad grid + call button) is laid out after it in
        // this same Arrangement.Top Column, that shifted the entire dialpad
        // up or down by exactly the chip's height on every single toggle -
        // which given how often typing crosses that 5-digit threshold or
        // clears the field, is easily the most frequently-triggered version
        // of this bug.
        //
        // Wrapping both in one fixed-height Box (rather than leaving them as
        // two independent AnimatedVisibility calls in the flow) reserves a
        // constant amount of space for "whichever hint chip is showing, if
        // any" - exactly the same reserve-space technique used for the
        // number field zone above, applied here for the same reason. The
        // two chips are mutually exclusive by construction (see the existing
        // comment below) and both align to the bottom of this fixed box, so
        // the chip appears to grow upward out of the space right above the
        // dialpad - matching the original slide-up entrance animation -
        // without the dialpad itself ever moving.
        Box(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Add-to-Contacts and Paste now render here, directly above the
            // dialpad grid, instead of under the number field near the top of
            // the screen. The two are mutually exclusive by construction -
            // showAddContactHint only turns on once 5+ digits are typed, and
            // clipboardHasNumber only turns on while the field is empty - so
            // exactly one of these (or neither) is ever visible at a time, no
            // extra coordination needed between them.
            androidx.compose.animation.AnimatedVisibility(
                visible = showAddContactHint,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }) + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.accentSoft)
                        .clickable { onAddContact(number) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = palette.accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Add to Contacts",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.accent
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = clipboardHasNumber,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }) + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, palette.cardBorder, RoundedCornerShape(16.dp))
                        .clickable { pasteFromClipboard() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Paste",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary
                    )
                }
            }
        }

        // THE FIX for "dialpad jumps up when I press backspace": keySize and
        // keyVerticalPadding used to animate the whole dialpad grid smaller
        // (78dp -> 62dp) whenever matchedByContactId was non-empty, and back
        // to full size the instant it emptied out (e.g. backspacing below 3
        // digits). Because the parent Column is Arrangement.Top, growing the
        // dialpad's own height while contactd matches were disappearing
        // above it made the whole dialpad+call-button block visibly shift
        // upward for the fraction of a second both animations were running.
        // The dialpad's key size is now always fixed - only the match list
        // above it grows/shrinks/scrolls (already capped via
        // weight(1f, fill=false) + AnimatedVisibility above), so backspacing
        // never resizes anything below the number field.
        val keySize = 72.dp
        val keyVerticalPadding = 0.dp

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            keys.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 7.dp, vertical = keyVerticalPadding),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        DialerKey(
                            key = key,
                            palette = palette,
                            size = keySize,
                            onPress = {
                                insertAtCursor(key.digit)
                                dtmfPlayer.play(key.digit.first())
                                vibrate()
                            }
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            val callInteractionSource = remember { MutableInteractionSource() }
            val isCallPressed by callInteractionSource.collectIsPressedAsState()
            val callScale by animateFloatSpring(if (isCallPressed) 0.9f else 1f)

            IconButton(
                onClick = { if (number.isNotEmpty()) onCall(numberToDial) },
                enabled = number.isNotEmpty(),
                interactionSource = callInteractionSource,
                modifier = Modifier
                    .size(62.dp)
                    .scale(callScale)
                    .clip(CircleShape)
                    .background(palette.callGreen)
            ) {
                Icon(
                    Icons.Filled.Phone,
                    contentDescription = "Call",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = number.isNotEmpty(),
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = fadeIn() + scaleIn(initialScale = 0.7f),
                exit = fadeOut() + scaleOut(targetScale = 0.7f)
            ) {
                var isBackspacePressed by remember { mutableStateOf(false) }
                val backspaceScale by animateFloatSpring(if (isBackspacePressed) 0.85f else 1f)

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .scale(backspaceScale)
                        .clip(CircleShape)
                        .repeatingClickable(
                            enabled = number.isNotEmpty(),
                            onPressChange = { isBackspacePressed = it },
                            onClick = {
                                val start = numberField.selection.start.coerceIn(0, numberField.text.length)
                                val end = numberField.selection.end.coerceIn(0, numberField.text.length)
                                numberField = if (start != end) {
                                    // A selection is active (e.g. after
                                    // pasting and selecting a run of digits)
                                    // - delete exactly that, same as any
                                    // text field's backspace-with-selection.
                                    TextFieldValue(
                                        numberField.text.removeRange(start, end),
                                        TextRange(start)
                                    )
                                } else if (start > 0) {
                                    TextFieldValue(
                                        numberField.text.removeRange(start - 1, start),
                                        TextRange(start - 1)
                                    )
                                } else {
                                    numberField
                                }
                                vibrate()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Backspace, contentDescription = "Backspace", tint = palette.textSecondary)
                }
            }
        }
    }
}

/**
 * Every matched number as one compact row - avatar, name, and number sharing
 * a single line the way a T9 predictive dialer does, so many matches read as
 * a short scrollable list rather than a stack of tall multi-line cards. The
 * list itself scrolls in a height-capped LazyColumn (see DialerScreen's
 * `.weight(1f, fill = false)` + this composable's own heightIn cap) instead
 * of growing without bound, which is what used to push the dialpad down and
 * off-screen as more contacts matched.
 */
@Composable
private fun MatchedContactsList(
    matchedByContactId: List<Pair<String, List<Contact>>>,
    typedDigits: String,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onAvatarClick: (Contact) -> Unit = {}
) {
    // Flatten to one row per number (not per person), preserving which
    // group a row belongs to only for the divider - a contact with two
    // matching numbers (Mobile + Work) still shows as two independently
    // tappable rows, same as the earlier grouped-card version.
    val rows = remember(matchedByContactId) {
        matchedByContactId.flatMap { (_, numbers) -> numbers }
    }

    // Width now fills the available space (the parent Column already caps
    // total width via its own 28dp horizontal padding) instead of a fixed
    // 340dp - on wider phones the list previously sat narrower than the
    // dialpad above it, looking cramped rather than "open." Height cap
    // raised from 216dp to 340dp so noticeably more rows are visible before
    // the list needs to scroll, matching the more spacious feel of the
    // reference screenshot rather than a small tucked-away card.
    LazyColumn(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(max = 340.dp)
            .glassCard(palette, 20.dp),
    ) {
        items(rows, key = { it.contactId + "|" + it.phoneNumber }) { contact ->
            MatchedContactRow(
                contact = contact,
                typedDigits = typedDigits,
                palette = palette,
                onCall = { onCall(contact.phoneNumber) },
                onMessage = { onMessage(contact.phoneNumber) },
                onAvatarClick = { onAvatarClick(contact) }
            )
            if (contact != rows.last()) {
                HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
            }
        }
    }
}

/**
 * A single matched row: avatar, name + number on one line, message/call
 * actions trailing - mirrors the reference Google Dialer row layout instead
 * of the earlier two-line name-above-number card. The digits the person has
 * actually typed so far are bolded and tinted with the accent color inside
 * the number, the same "highlight what you typed" cue Google Dialer uses,
 * so it's visually obvious *why* each row matched.
 */
@Composable
private fun MatchedContactRow(
    contact: Contact,
    typedDigits: String,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onAvatarClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCall)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar gets its own tap target (opens the contact/number detail
        // page) instead of inheriting the row's onCall - previously the
        // whole row, avatar included, shared one clickable, so tapping the
        // avatar specifically (the natural "show me who this is" gesture,
        // and the same gesture that opens a detail page from Recents)
        // instead silently placed a call. clip + clickable scoped to just
        // the Avatar's own Box keeps that a distinct, smaller tap target
        // than the rest of the row.
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onAvatarClick)
        ) {
            Avatar(name = contact.displayName, photoUri = contact.photoUri, size = 38.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = highlightMatchedDigits(contact.phoneNumber, typedDigits, palette.accent, palette.textSecondary),
                fontSize = 13.sp,
                maxLines = 1
            )
        }
        IconButton(
            onClick = onMessage,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(Icons.Filled.Message, contentDescription = "Message", tint = palette.textSecondary, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = onCall,
            modifier = Modifier.size(30.dp).clip(CircleShape).background(palette.accentSoft)
        ) {
            Icon(Icons.Filled.Phone, contentDescription = "Call", tint = palette.accent, modifier = Modifier.size(13.dp))
        }
    }
}

/**
 * Bolds and accent-tints whichever digits of `phoneNumber` are the ones the
 * person actually typed, so a matched row visually explains itself the way
 * Google Dialer's predictive list does - e.g. typing "198" against
 * "+91 94151 19835" highlights the embedded "198" run rather than leaving
 * the whole number a flat, unexplained color.
 */
private fun highlightMatchedDigits(
    phoneNumber: String,
    typedDigits: String,
    highlightColor: Color,
    baseColor: Color
): AnnotatedString {
    val target = normalizePhoneNumberForMatch(typedDigits)
    if (target.isEmpty()) {
        return buildAnnotatedString { withStyle(SpanStyle(color = baseColor)) { append(phoneNumber) } }
    }
    // Match against digits-only so formatting characters (spaces, +, -) in
    // the displayed string don't break the digit run we're trying to
    // highlight; matchStartDigitIndex/matchLen are then indices into the
    // digit-only stream, and digitsSeen re-walks phoneNumber to translate
    // those back into positions in the original formatted string.
    val digitsOnlyTarget = target.removePrefix("+")
    val digitsOnlyPhone = phoneNumber.filter { it.isDigit() }
    val matchStartDigitIndex = digitsOnlyPhone.indexOf(digitsOnlyTarget)

    return buildAnnotatedString {
        if (matchStartDigitIndex < 0 || digitsOnlyTarget.isEmpty()) {
            withStyle(SpanStyle(color = baseColor)) { append(phoneNumber) }
            return@buildAnnotatedString
        }
        var digitsSeen = 0
        for (ch in phoneNumber) {
            val isDigit = ch.isDigit()
            val inMatchRange = isDigit &&
                digitsSeen >= matchStartDigitIndex &&
                digitsSeen < matchStartDigitIndex + digitsOnlyTarget.length
            withStyle(
                SpanStyle(
                    color = if (inMatchRange) highlightColor else baseColor,
                    fontWeight = if (inMatchRange) FontWeight.Bold else FontWeight.Normal
                )
            ) { append(ch) }
            if (isDigit) digitsSeen++
        }
    }
}

@Composable
private fun animateFloatSpring(target: Float) = androidx.compose.animation.core.animateFloatAsState(
    targetValue = target,


    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
    label = "press-scale"
)


private fun Modifier.repeatingClickable(
    enabled: Boolean = true,
    initialDelayMillis: Long = 400L,
    minDelayMillis: Long = 45L,
    delayDecayFactor: Float = 0.82f,
    onPressChange: (Boolean) -> Unit = {},
    onClick: () -> Unit
): Modifier = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentEnabled by rememberUpdatedState(enabled)

    this.pointerInput(Unit) {
        coroutineScope {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                if (!currentEnabled) return@awaitEachGesture

                onPressChange(true)
                currentOnClick()

                var waitMillis = initialDelayMillis


                while (withTimeoutOrNull(waitMillis) { waitForUpOrCancellation(); false } ?: true) {
                    currentOnClick()
                    waitMillis = (waitMillis * delayDecayFactor).toLong().coerceAtLeast(minDelayMillis)
                }
                onPressChange(false)
            }
        }
    }
}

@Composable
private fun DialerKey(
    key: KeyDef,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    size: androidx.compose.ui.unit.Dp = 78.dp,
    onPress: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatSpring(if (isPressed) 0.90f else 1f)
    val haloScale by animateFloatSpring(if (isPressed) 1.18f else 0.94f)
    val haloAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.72f else 0f,
        animationSpec = tween(if (isPressed) 90 else 170),
        label = "dial-halo-alpha"
    )
    val innerAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.42f else 0f,
        animationSpec = tween(if (isPressed) 70 else 150),
        label = "dial-inner-alpha"
    )

    Box(Modifier.size(size + 10.dp), contentAlignment = Alignment.Center) {
        // Pressed-key glass morphism: two soft concentric glass rings expand
        // around the key so the touch feels like it is pushing through a
        // translucent surface, rather than only shrinking the button.
        Box(
            Modifier
                .size(size)
                .scale(haloScale)
                .alpha(haloAlpha)
                .border(1.2.dp, palette.accent.copy(alpha = 0.55f), CircleShape)
        )
        Box(
            Modifier
                .size(size * 0.91f)
                .scale(if (isPressed) 1.05f else 0.92f)
                .alpha(innerAlpha)
                .clip(CircleShape)
                .background(palette.accentSoft.copy(alpha = 0.70f))
        )
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .glassCircle(palette)
                .clickable(
                    indication = null,
                    interactionSource = interactionSource,
                    onClick = onPress
                ),
            contentAlignment = Alignment.Center
        ) {
        // Digit/letter sizes scale proportionally with the key's own size
        // (both driven off the same 78dp full-size baseline), so shrinking
        // the whole dialpad while matches are showing keeps everything in
        // the same visual proportion instead of the glyphs staying full-size
        // inside a smaller circle.
        val sizeFraction = size / 78.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(key.digit, fontSize = (30 * sizeFraction).sp, fontWeight = FontWeight.Normal, color = palette.textPrimary)
            if (key.letters.isNotEmpty()) {
                Text(
                    key.letters, fontSize = (9 * sizeFraction).sp, fontWeight = FontWeight.Bold,
                    color = palette.textSecondary, letterSpacing = 1.2.sp
                )
            }
        }
        }
    }
}
