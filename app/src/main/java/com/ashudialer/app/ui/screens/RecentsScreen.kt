@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.RecentCall
import com.ashudialer.app.data.Contact
import com.ashudialer.app.data.db.CallDirection
import com.ashudialer.app.ui.components.Avatar
import com.ashudialer.app.ui.components.BothWayIcon
import com.ashudialer.app.ui.components.DirectionIcon
import com.ashudialer.app.ui.components.ThemePickerButton
import com.ashudialer.app.ui.theme.LocalDialerPalette
import java.text.SimpleDateFormat
import java.util.*
import com.ashudialer.app.ui.components.glassCircle
import com.ashudialer.app.ui.components.liquidGlass

private val recentFilters = listOf("All", "Missed", "Incoming", "Outgoing", "Today", "Contacts", "Identified", "Spam")

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RecentsScreen(
    recents: List<RecentCall>,
    currentThemeId: String,
    onOpenThemePicker: () -> Unit,
    onCall: (RecentCall) -> Unit,
    onMessage: (RecentCall) -> Unit = {},
    onWhatsApp: (RecentCall) -> Unit = {},
    onViewOrAddContact: (RecentCall) -> Unit = {},
    searchContacts: List<Contact> = emptyList(),
    onOpenSearchContact: (Contact) -> Unit = {},
    onCallSearchContact: (Contact) -> Unit = {},
    onBlock: (RecentCall) -> Unit = {},
    onUnblock: (RecentCall) -> Unit = {},
    onDeleteHistoryFor: (RecentCall) -> Unit = {},
    onClearAllHistory: () -> Unit = {},
    onDeleteRecents: (List<RecentCall>) -> Unit = {},
    // Restores the "remind me to call this number back" action that used to
    // live in this same per-call action sheet before an earlier edit
    // accidentally deleted it (see the comment on SheetActionRow below).
    // triggerAtMillis is an absolute epoch time chosen from the in-sheet
    // time picker, not a duration - the underlying repository/scheduler
    // already expect an absolute RTC_WAKEUP time (see
    // CallbackReminderScheduler), so resolving "in 2 hours" vs "at 6pm"
    // into a concrete instant happens here, once, rather than being
    // recomputed differently at each call site.
    onSetCallbackReminder: (RecentCall, Long) -> Unit = { _, _ -> },
    isCallbackReminderSet: (String) -> Boolean = { false },
    isBlocked: (String) -> Boolean = { false },
    isSavedContact: (String) -> Boolean = { false },
    showContactThumbnails: Boolean = true,
    showPhoneNumbers: Boolean = false,
    useRelativeDate: Boolean = true,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    var filter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<RecentCall?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmDeleteTarget by remember { mutableStateOf<RecentCall?>(null) }
    var reminderPickerTarget by remember { mutableStateOf<RecentCall?>(null) }
    var selectedCallIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    val selectionMode = selectedCallIds.isNotEmpty()

    val filteredByTab = remember(recents, filter) {
        when (filter) {
            "Missed" -> recents.filter { it.direction == CallDirection.MISSED }
            "Incoming" -> recents.filter { it.direction == CallDirection.INCOMING }
            "Outgoing" -> recents.filter { it.direction == CallDirection.OUTGOING }
            "Today" -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                recents.filter { it.timestampMillis >= start }
            }
            "Spam" -> recents.filter { it.isSpam }

            "Contacts" -> recents.filter { isSavedContact(it.phoneNumber) }


            "Identified" -> recents.filter {
                !it.isSpam && !isSavedContact(it.phoneNumber) &&
                    it.phoneNumber.isNotBlank() && it.displayName != it.phoneNumber
            }
            else -> recents
        }
    }

    val query = searchQuery.trim()
    val filtered = remember(filteredByTab, query) {
        if (query.isEmpty()) filteredByTab
        else filteredByTab.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                it.phoneNumber.contains(query, ignoreCase = true)
        }
    }
    // Search is useful even when there is no matching recent call: surface a
    // saved contact directly so a person can still call/open the contact
    // instead of getting a dead-end "No matches" state.
    val contactMatches = remember(searchContacts, query) {
        if (query.length < 2) emptyList()
        else searchContacts.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                it.phoneNumber.contains(query, ignoreCase = true)
        }.take(5)
    }

    val grouped = remember(filtered) { filtered.groupBy { dateBucket(it.timestampMillis) } }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { selectedCallIds = emptySet() },
                        modifier = Modifier.size(28.dp).glassCircle(palette)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel selection", tint = palette.textPrimary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("${selectedCallIds.size} selected", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                }
                IconButton(
                    onClick = { confirmDeleteSelected = true },
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(palette.danger.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete selected", tint = palette.danger, modifier = Modifier.size(18.dp))
                }
            } else {
                Text(
                    text = "Recents",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textPrimary
                )
                ThemePickerButton(onClick = onOpenThemePicker, currentThemeId = currentThemeId)
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(palette.searchBackground)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (searchQuery.isEmpty()) {
                    // Matches the vertical padding on BasicTextField below so
                    // the placeholder and the real text field occupy exactly
                    // the same height and sit on the same baseline. Without
                    // this, the placeholder (a plain Text with no vertical
                    // padding of its own) was visibly shorter than the text
                    // field, and Box's default TopStart alignment pinned it
                    // to the top of that height difference - reading as the
                    // whole search bar being slightly off-center vertically
                    // whenever the field was empty (i.e. almost always).
                    Text(
                        "Search Recents",
                        color = palette.textSecondary,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = palette.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
            }
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                }
            }
            Box {
                IconButton(onClick = { showOverflowMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Clear call log") },
                        leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            confirmClearAll = true
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(recentFilters) { tab ->
                val isSelected = filter == tab
                Surface(
                    onClick = { filter = tab },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) palette.cardBackground else Color.Transparent,
                    contentColor = if (isSelected) palette.accent else palette.textPrimary
                ) {
                    Text(
                        text = tab,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (filtered.isEmpty()) {
            if (query.isNotEmpty() && contactMatches.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)
                ) {
                    item {
                        Text(
                            "Contacts",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                    items(contactMatches, key = { it.contactId }) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(palette.cardBackground.copy(alpha = 0.72f))
                                .clickable { onOpenSearchContact(contact) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(name = contact.displayName, photoUri = contact.photoUri, size = 42.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(contact.displayName, fontWeight = FontWeight.SemiBold, color = palette.textPrimary, maxLines = 1)
                                Text(contact.phoneNumber, fontSize = 12.sp, color = palette.textSecondary, maxLines = 1)
                                Text("No recent call found", fontSize = 10.sp, color = palette.textSecondary)
                            }
                            IconButton(onClick = { onCallSearchContact(contact) }) {
                                Icon(Icons.Filled.Phone, contentDescription = "Call ${contact.displayName}", tint = palette.callGreen)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (query.isNotEmpty()) "No recent call found for \"$query\"" else "No calls yet",
                        color = palette.textSecondary,
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {


                grouped.forEach { (bucket, callsInBucket) ->
                    item(key = "header_$bucket") {
                        Text(
                            text = bucket,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }

                    callsInBucket.forEachIndexed { index, call ->
                        val isFirst = index == 0
                        val isLast = index == callsInBucket.lastIndex
                        val corner = 20.dp
                        val shape = RoundedCornerShape(
                            topStart = if (isFirst) corner else 0.dp,
                            topEnd = if (isFirst) corner else 0.dp,
                            bottomStart = if (isLast) corner else 0.dp,
                            bottomEnd = if (isLast) corner else 0.dp
                        )

                        item(key = "call_${call.id}") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlass(palette, shape)
                            ) {
                                RecentRow(
                                    call = call,
                                    onCall = { onCall(call) },
                                    onOpenActions = { actionTarget = call },
                                    onAvatarClick = { onViewOrAddContact(call) },
                                    selectionMode = selectionMode,
                                    isSelected = call.id in selectedCallIds,
                                    onToggleSelect = {
                                        val groupIds = call.groupedIds.toSet()
                                        selectedCallIds = if (call.id in selectedCallIds) {
                                            selectedCallIds - groupIds
                                        } else {
                                            selectedCallIds + groupIds
                                        }
                                    },
                                    onLongPress = {
                                        selectedCallIds = selectedCallIds + call.groupedIds
                                    },
                                    showContactThumbnails = showContactThumbnails,
                                    showPhoneNumbers = showPhoneNumbers,
                                    useRelativeDate = useRelativeDate
                                )
                                if (!isLast) {
                                    HorizontalDivider(
                                        color = palette.cardBorder,
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(start = 70.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    val sheetTarget = actionTarget
    if (sheetTarget != null) {
        ModalBottomSheet(onDismissRequest = { actionTarget = null }) {
            RecentActionSheetContent(
                call = sheetTarget,
                isBlocked = isBlocked(sheetTarget.phoneNumber),
                isSavedContact = isSavedContact(sheetTarget.phoneNumber),
                isReminderSet = isCallbackReminderSet(sheetTarget.phoneNumber),
                onCall = { onCall(sheetTarget); actionTarget = null },
                onMessage = { onMessage(sheetTarget); actionTarget = null },
                onWhatsApp = { onWhatsApp(sheetTarget); actionTarget = null },
                onViewOrAddContact = { onViewOrAddContact(sheetTarget); actionTarget = null },
                onSetReminder = {
                    actionTarget = null
                    reminderPickerTarget = sheetTarget
                },
                onBlock = { onBlock(sheetTarget); actionTarget = null },
                onUnblock = { onUnblock(sheetTarget); actionTarget = null },
                onDelete = {
                    actionTarget = null
                    confirmDeleteTarget = sheetTarget
                }
            )
        }
    }

    val reminderTarget = reminderPickerTarget
    if (reminderTarget != null) {
        CallbackReminderPickerDialog(
            callerName = reminderTarget.displayName,
            onDismiss = { reminderPickerTarget = null },
            onConfirm = { triggerAtMillis ->
                onSetCallbackReminder(reminderTarget, triggerAtMillis)
                reminderPickerTarget = null
            }
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear call log?") },
            text = { Text("This removes every call from your history. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    onClearAllHistory()
                }) { Text("Clear", color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            title = { Text("Delete ${selectedCallIds.size} call${if (selectedCallIds.size > 1) "s" else ""}?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val selectedEntries = recents.filter { it.groupedIds.any { id -> id in selectedCallIds } }
                    onDeleteRecents(selectedEntries)
                    selectedCallIds = emptySet()
                    confirmDeleteSelected = false
                }) { Text("Delete", color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelected = false }) { Text("Cancel") }
            }
        )
    }

    val deleteTarget = confirmDeleteTarget
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteTarget = null },
            title = { Text("Delete these calls?") },
            text = { Text("This removes all calls with ${deleteTarget.displayName} from your history. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteTarget = null
                    onDeleteHistoryFor(deleteTarget)
                }) { Text("Delete", color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RecentActionSheetContent(
    call: RecentCall,
    isBlocked: Boolean,
    isSavedContact: Boolean,
    isReminderSet: Boolean,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onWhatsApp: () -> Unit,
    onViewOrAddContact: () -> Unit,
    onSetReminder: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalDialerPalette.current
    val clipboard = LocalClipboardManager.current
    val canReachNumber = call.phoneNumber.isNotBlank()

    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(name = call.displayName, photoUri = call.photoUri, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(call.displayName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                if (canReachNumber && call.phoneNumber != call.displayName) {
                    Text(call.phoneNumber, fontSize = 13.5.sp, color = palette.textSecondary)
                }
            }
        }
        HorizontalDivider(color = palette.cardBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

        if (!canReachNumber) {
            Text(
                text = "This call didn't come with a number, so there's nothing to call back, message, or block. You can still clear your whole call log from the ⋯ menu.",
                fontSize = 13.5.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
            return@Column
        }

        SheetActionRow(Icons.Filled.Call, "Call", palette) { onCall() }
        SheetActionRow(Icons.Filled.Message, "Message", palette) { onMessage() }
        // WhatsApp is only offered for numbers already saved as a contact - showing
        // it for any raw, unsaved recent number (e.g. an OTP/service number) implies
        // an integration we can't actually verify, since there's no reliable way to
        // check whether an arbitrary number has WhatsApp. Saved contacts remain the
        // person's own explicit signal that the number is someone worth messaging.
        if (isSavedContact) {
            SheetActionRow(Icons.Filled.Chat, "WhatsApp", palette) { onWhatsApp() }
        }
        SheetActionRow(
            icon = if (isSavedContact) Icons.Filled.Person else Icons.Filled.PersonAdd,
            label = if (isSavedContact) "View contact" else "Add to contacts",
            palette = palette
        ) { onViewOrAddContact() }
        SheetActionRow(Icons.Filled.ContentCopy, "Copy number", palette) {
            clipboard.setText(AnnotatedString(call.phoneNumber))
        }
        SheetActionRow(
            icon = Icons.Filled.Alarm,
            label = if (isReminderSet) "Change callback reminder" else "Remind me to call back",
            palette = palette
        ) { onSetReminder() }
        if (isBlocked) {
            SheetActionRow(Icons.Filled.Block, "Unblock this number", palette) { onUnblock() }
        } else {
            SheetActionRow(Icons.Filled.Block, "Block this number", palette, tint = palette.danger) { onBlock() }
        }
        SheetActionRow(Icons.Filled.Delete, "Delete from history", palette, tint = palette.danger) { onDelete() }
    }
}

/**
 * Recreated after an earlier edit accidentally deleted this definition
 * along with a nearby dead-code block it happened to sit next to - the
 * function itself was never meant to be removed, only the reminder/swipe
 * code around it. Signature and behavior match every call site above
 * exactly (icon + label + optional danger tint + trailing-lambda onClick).
 */
@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    tint: Color = palette.textPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 15.sp, color = tint)
    }
}


@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
private fun RecentRow(
    call: RecentCall,
    onCall: () -> Unit,
    onOpenActions: () -> Unit,
    onAvatarClick: () -> Unit,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongPress: () -> Unit = {},
    showContactThumbnails: Boolean = true,
    showPhoneNumbers: Boolean = false,
    useRelativeDate: Boolean = true
) {
    val palette = LocalDialerPalette.current
    val isMissed = call.direction == CallDirection.MISSED
    val canReachNumber = call.phoneNumber.isNotBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = if (selectionMode) onToggleSelect else onOpenActions,
                onLongClick = onLongPress
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            RadioButton(
                selected = isSelected,
                onClick = onToggleSelect,
                colors = RadioButtonDefaults.colors(selectedColor = palette.accent, unselectedColor = palette.textSecondary)
            )
            Spacer(Modifier.width(4.dp))
        }
        if (showContactThumbnails) {
            // The avatar has its own tap target, separate from the rest of
            // the row (which opens the actions bottom sheet - Call/Message/
            // Add to contacts/etc). This is deliberately not a nested
            // clickable on top of the row's own combinedClickable - Compose
            // resolves that correctly (the innermost clickable consumes the
            // tap first), but during selectionMode the avatar defers to
            // onToggleSelect instead of onAvatarClick, so tapping a photo
            // mid-bulk-select toggles that row's selection like everything
            // else in the row rather than unexpectedly opening a profile.
            Box(
                modifier = Modifier.clip(CircleShape).clickable(
                    onClick = if (selectionMode) onToggleSelect else onAvatarClick
                )
            ) {
                Avatar(name = call.displayName, photoUri = call.photoUri, size = 44.dp)
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (showPhoneNumbers && canReachNumber && call.phoneNumber != call.displayName)
                        "${call.displayName} · ${call.phoneNumber}" else call.displayName,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isMissed) palette.danger else palette.textPrimary,
                    maxLines = 1
                )
                if (call.callCount > 1) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "(${call.callCount})",
                        fontSize = 14.5.sp,
                        color = if (isMissed) palette.danger else palette.textSecondary
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (call.callCount > 1 && call.direction != CallDirection.MISSED) {
                    BothWayIcon()
                } else {
                    DirectionIcon(direction = call.direction)
                }
                Spacer(Modifier.width(5.dp))
                Text(
                    text = (if (canReachNumber) "Mobile • " else "") +
                        (if (useRelativeDate) formatRelativeShort(call.timestampMillis) else formatTime(call.timestampMillis)),
                    fontSize = 13.5.sp,
                    color = if (isMissed) palette.danger.copy(alpha = 0.85f) else palette.textSecondary
                )
            }
        }
        if (!selectionMode) {
            IconButton(
                onClick = onCall,
                enabled = canReachNumber,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (canReachNumber) palette.accentSoft else palette.accentSoft.copy(alpha = 0.4f))
            ) {
                Icon(
                    Icons.Filled.Phone,
                    contentDescription = "Call",
                    tint = if (canReachNumber) palette.accent else palette.textSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}


private fun dateBucket(millis: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
    val dayDiff = now.get(java.util.Calendar.DAY_OF_YEAR) - then.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Today"
        sameYear && dayDiff == 1 -> "Yesterday"
        sameYear && dayDiff in 2..6 -> "This week"
        else -> "Older"
    }
}


private fun formatRelativeShort(millis: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
    val dayDiff = now.get(java.util.Calendar.DAY_OF_YEAR) - then.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> formatTime(millis)
        sameYear && dayDiff == 1 -> "Yesterday"
        sameYear && dayDiff in 2..6 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(millis))
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))
    }
}

private fun formatTime(millis: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}
