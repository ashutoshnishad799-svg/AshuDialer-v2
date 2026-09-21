package com.ashudialer.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    // Accepted shapes only (per product decision - the paste chip should be
    // rare and meaningful, not fire on any 7-digit string):
    //   * 3-digit service codes (100, 112, 198 ...)
    //   * 4-6 digit short codes / premium-rate numbers (e.g. 5xxxx SMS codes)
    //     ONLY when written with a leading "*" or "#" (USSD) or a leading
    //     "1"/"5"/"9" short-code style - kept conservative
    //   * exactly 10 digits (Indian mobile / national format)
    //   * exactly 12 digits (91 + 10-digit mobile), with or without "+"
    //   * 11 digits starting with 0 (trunk-prefixed national) or 13 with "+91"-like
    // Anything else (a 7-digit id, a 9-digit order number, a 16-digit card)
    // no longer triggers the chip.
    val len = digitsOnly.length
    val startsWithUssd = trimmed.startsWith("*") || trimmed.startsWith("#")
    val accepted = when {
        len == 3 -> true
        len in 4..6 -> startsWithUssd || trimmed.startsWith("1") || trimmed.startsWith("5") || trimmed.startsWith("9")
        len == 10 -> true
        len == 11 -> digitsOnly.startsWith("0")
        len == 12 -> true
        len == 13 -> trimmed.startsWith("+") || digitsOnly.startsWith("091")
        else -> false
    }
    if (!accepted) return false

    // Every character besides the digits should be a formatting character a
    // real phone number would plausibly contain - not letters or punctuation
    // that would suggest this is prose with a number embedded in it.
    val allowedFormatting = setOf('+', '-', '(', ')', ' ', '.', '*', '#')
    val nonDigitsAreFormatting = trimmed.all { it.isDigit() || it in allowedFormatting }
    if (!nonDigitsAreFormatting) return false

    // Guards against something like "1.2.3.4.5.6.7" (7+ digits but almost
    // entirely separators) slipping through as "mostly digits" - a real
    // phone number's formatting characters are a small minority of its
    // length, not comparable to or exceeding the digit count.
    val formattingCharCount = trimmed.length - digitsOnly.length
    return formattingCharCount <= digitsOnly.length / 2
}

/** Tallest the contact-match list area ever gets (about 2.5 rows at ~64dp each). */
private const val MAX_MATCH_ZONE_DP = 160f

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
            // Android 12+ shows its own "<app> pasted from your clipboard"
            // toast every time an app reads the clip *contents* (primaryClip).
            // That toast is drawn by the system and cannot be turned off, so
            // the only fix is to read the contents as rarely as possible:
            //   1. primaryClipDescription is metadata only - reading it never
            //      shows the toast. Bail out unless the clip is plain text.
            //   2. Only read the contents when the clip is NEW since the last
            //      time we looked (its timestamp changed). Opening the dialer
            //      ten times with the same old clip now reads it at most once
            //      instead of ten times.
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val description = clipboard?.primaryClipDescription
            val isText = description?.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN) == true ||
                description?.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_HTML) == true
            if (!isText) {
                clipboardHasNumber = false
            } else {
                val stamp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    description?.timestamp ?: 0L
                } else 0L
                val seenPrefs = context.getSharedPreferences("ashu_clipboard_seen", Context.MODE_PRIVATE)
                val lastStamp = seenPrefs.getLong("last_stamp", -1L)
                val lastWasNumber = seenPrefs.getBoolean("last_was_number", false)
                if (stamp != 0L && stamp == lastStamp) {
                    // Same clip as last time: reuse the previous verdict, no read, no toast.
                    clipboardHasNumber = lastWasNumber
                } else {
                    val clipText = clipboard?.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                        .orEmpty()
                    val isNumber = looksLikePhoneNumber(clipText)
                    clipboardHasNumber = isNumber
                    seenPrefs.edit().putLong("last_stamp", stamp).putBoolean("last_was_number", isNumber).apply()
                }
            }
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


    // ------------------------------------------------------------------------------
    // RESPONSIVE LAYOUT. The dialer used to be one Column of fixed-dp blocks
    // (72dp keys, 120dp number zone, 140dp match zone ...) stacked from the top.
    // That total (~740dp) fit a small/old phone snugly but left a large empty
    // gap on a tall one, and it could not shrink on a short screen or when the
    // system font/display size was raised - which is why the same app looked
    // different (and worse) from phone to phone and ROM to ROM.
    //
    // Now everything that used a fixed dp is derived from the space this screen
    // is actually given (BoxWithConstraints already excludes the bottom nav and
    // system bars, because the Scaffold hands this composable only what is left):
    //   * dialer keys are sized from BOTH the available width and height, so they
    //     never overflow sideways on a narrow phone nor get cramped on a short one;
    //   * the number zone, match-results zone, hint row and vertical gaps are a
    //     fraction of the available height;
    //   * the whole block is centred in whatever is left over instead of being
    //     glued to the top, so a tall screen gets even breathing room.
    // The zones are still FIXED for a given screen size (they only change when the
    // screen itself changes), so the "dialpad jumps while typing" fixes explained
    // below still hold exactly as before.
    // ------------------------------------------------------------------------------
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // LAYOUT, top to bottom (everything is anchored to the BOTTOM, next to the thumb, like a stock dialer):
        //
        //   [ free space ]  [ contact suggestions ]  [ typed number ]  [ Add to Contacts / Paste ]  [ keys ]  [ call ]
        //
        // Every zone has a FIXED height that never depends on what has been typed, so nothing shifts while
        // typing. Only the keys are sized from the screen: they are 70dp (the size this dialer has always
        // had) and only SHRINK on a short phone or a large system font. Row spacing is the key plus a 10dp
        // cell margin, with no extra gap - that is what made the keypad look "spread out" when a solver
        // stretched it to fill the height.
        val availW = maxWidth.value
        val availH = maxHeight.value

        val sidePadF = (availW * 0.07f).coerceIn(16f, 32f)
        val keyFromWidth = (availW - sidePadF * 2f - 28f) / 3f

        val topPadF = 8f
        val numberZoneF = 64f          // the typed number
        val hintZoneF = 48f            // "Add to Contacts" / "Paste" chips (a chip is 36dp, so it can never be clipped)
        val minMatchF = 56f            // the suggestion list always has room for at least one row
        val haloF = 10f                // every key sits in a (key + 10dp) cell
        val callTopGapF = 14f
        val callBottomGapF = 26f

        // Height left for 4 key rows + the call button once the fixed zones are taken out; the call button is
        // about 0.86 of a key, so the whole stack is (4 + 0.86) keys plus the margins.
        val keyByHeight = (availH - topPadF - numberZoneF - hintZoneF - minMatchF - callTopGapF - callBottomGapF - 4f * haloF) / 4.86f
        val keySizeF = minOf(keyByHeight, keyFromWidth, 70f).coerceAtLeast(44f)
        val callSizeF = (keySizeF * 0.86f).coerceIn(46f, 60f)

        val bottomBlockF = 4f * (keySizeF + haloF) + callTopGapF + callSizeF + callBottomGapF
        val leftoverF = availH - topPadF - numberZoneF - hintZoneF - bottomBlockF
        val matchZoneF = leftoverF.coerceAtLeast(minMatchF).coerceAtMost(MAX_MATCH_ZONE_DP)
        val topSpacerF = (leftoverF - matchZoneF).coerceAtLeast(0f)
        // Only a very short screen (or landscape) cannot fit everything: then the column scrolls.
        val needsScroll = topPadF + numberZoneF + hintZoneF + matchZoneF + bottomBlockF > availH + 1f

        val sidePad = sidePadF.dp
        val keySize = keySizeF.dp
        val numberZoneH = numberZoneF.dp
        val hintZoneH = hintZoneF.dp
        val matchZoneH = matchZoneF.dp
        val topPad = topPadF.dp
        val callSize = callSizeF.dp
        val callTopGap = callTopGapF.dp
        val callBottomGap = callBottomGapF.dp
        val numberFont = when {
            number.length <= 11 -> 36f
            number.length <= 15 -> 30f
            else -> 24f
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (needsScroll) Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()) else Modifier)
            .padding(horizontal = sidePad)
            .padding(top = topPad),
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
        // Leftover height on tall phones: empty space ABOVE the number area, so everything below it
        // (number, matches, keypad, call button) sits at the bottom within thumb reach. It is 0 on
        // phones where the screen is fully used, and constant for a given screen (it never changes
        // while typing), so the "dialpad jumps" fixes are unaffected.
        if (topSpacerF > 0f) Spacer(Modifier.height(topSpacerF.dp))

        // Contact suggestions. Bottom-aligned, so the list sits right on top of the number and grows upward as
        // more contacts match; the zone itself is a fixed height, so nothing below it ever moves.
        Box(Modifier.fillMaxWidth().height(matchZoneH), contentAlignment = Alignment.BottomCenter) {
            androidx.compose.animation.AnimatedVisibility(
                visible = matchedByContactId.isNotEmpty(),
                enter = fadeIn(animationSpec = tween(150)) + scaleIn(animationSpec = tween(170), initialScale = 0.97f),
                exit = fadeOut(animationSpec = tween(100)) + scaleOut(animationSpec = tween(100), targetScale = 0.97f),
                modifier = Modifier.fillMaxWidth().heightIn(max = matchZoneH)
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

        // The typed number: fixed height, centred, directly above the chips row and the keys.
        Box(
            modifier = Modifier.fillMaxWidth().height(numberZoneH),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = numberField,
                onValueChange = { numberField = it },
                readOnly = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = numberFont.sp,
                    fontWeight = FontWeight.Light,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center
                ),
                singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // "Add to Contacts" / "Paste". ONE row, ONE copy of each chip. There used to be a second "Add to Contacts"
        // chip inside the number zone as well; both sat in boxes shorter than the chip, so each was cut off (the
        // hidden "Add to Contacts" and the empty pill under the number). The row is a fixed 48dp tall and the chips
        // are 36dp, so they always fit; if both apply they sit side by side instead of on top of each other.
        Box(
            modifier = Modifier.fillMaxWidth().height(hintZoneH),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showAddContactHint,
                    enter = fadeIn(animationSpec = tween(140)) + scaleIn(animationSpec = tween(160), initialScale = 0.9f),
                    exit = fadeOut(animationSpec = tween(100)) + scaleOut(animationSpec = tween(100), targetScale = 0.9f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(palette.accentSoft)
                            .clickable { onAddContact(number) }
                            .padding(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(text = "Add to Contacts", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.accent, maxLines = 1)
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = clipboardHasNumber,
                    enter = fadeIn(animationSpec = tween(140)) + scaleIn(animationSpec = tween(160), initialScale = 0.9f),
                    exit = fadeOut(animationSpec = tween(100)) + scaleOut(animationSpec = tween(100), targetScale = 0.9f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.dp, palette.cardBorder, RoundedCornerShape(18.dp))
                            .clickable { pasteFromClipboard() }
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(text = "Paste", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary, maxLines = 1)
                    }
                }
            }
        }

        val keyVerticalPadding = 0.dp

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            keys.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp, vertical = keyVerticalPadding),
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
            modifier = Modifier.fillMaxWidth().padding(top = callTopGap, bottom = callBottomGap),
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
                    .size(callSize)
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
                // Fade only (no scale-in): a key that is still growing when the first digit is typed ignores taps
                // for the first moments, which felt like the backspace "not working".
                enter = fadeIn(animationSpec = tween(120)),
                exit = fadeOut(animationSpec = tween(120))
            ) {
                var isBackspacePressed by remember { mutableStateOf(false) }
                val backspaceGlow by animateFloatAsState(
                    targetValue = if (isBackspacePressed) 1f else 0f,
                    animationSpec = tween(if (isBackspacePressed) 45 else 220),
                    label = "backspace-glow"
                )
                val backspaceScale by animateFloatAsState(
                    targetValue = if (isBackspacePressed) 0.88f else 1f,
                    animationSpec = if (isBackspacePressed) tween(50) else spring(dampingRatio = 0.7f, stiffness = 520f),
                    label = "backspace-scale"
                )

                Box(
                    modifier = Modifier
                        // 56dp: the old 44dp target was smaller than a fingertip.
                        .size(56.dp)
                        .graphicsLayer { scaleX = backspaceScale; scaleY = backspaceScale }
                        .clip(CircleShape)
                        .background(palette.textSecondary.copy(alpha = 0.16f * backspaceGlow))
                        .repeatingClickable(
                            enabled = number.isNotEmpty(),
                            onPressChange = { isBackspacePressed = it },
                            onClick = {
                                val start = numberField.selection.start.coerceIn(0, numberField.text.length)
                                val end = numberField.selection.end.coerceIn(0, numberField.text.length)
                                numberField = if (start != end) {
                                    TextFieldValue(numberField.text.removeRange(start, end), TextRange(start))
                                } else if (start > 0) {
                                    TextFieldValue(numberField.text.removeRange(start - 1, start), TextRange(start - 1))
                                } else {
                                    numberField
                                }
                                vibrate()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = palette.textPrimary.copy(alpha = 0.72f),
                        modifier = Modifier.size(26.dp)
                    )
                }
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


    animationSpec = spring(dampingRatio = 0.72f, stiffness = 560f),
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
    // The digit is entered the moment the finger goes DOWN (and the tone and haptic start with it), exactly like a
    // real keypad. It used to wait for the finger to LIFT (clickable), so every key felt a beat late, and it ran three
    // bouncy springs at once (scale + two halos) that made the keys wobble after each tap.
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = if (pressed) tween(50, easing = FastOutSlowInEasing) else spring(dampingRatio = 0.68f, stiffness = 520f),
        label = "dial-key-scale"
    )
    val glow by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) 45 else 240),
        label = "dial-key-glow"
    )
    val fire = onPress

    Box(Modifier.size(size + 10.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                // State is read inside these lambdas, so a press redraws the key instead of recomposing the screen.
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .glassCircle(palette)
                .drawWithContent {
                    // Soft accent glow that lights up on touch-down and fades out on release.
                    drawCircle(color = palette.accent.copy(alpha = 0.20f * glow))
                    drawContent()
                }
                .pointerInput(key) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            fire()
                            tryAwaitRelease()
                            pressed = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
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
