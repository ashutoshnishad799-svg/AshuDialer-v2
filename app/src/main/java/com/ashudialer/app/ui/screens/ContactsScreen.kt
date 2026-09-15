@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.ashudialer.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.Contact
import com.ashudialer.app.ui.components.Avatar
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlinx.coroutines.launch
import com.ashudialer.app.ui.components.glassCircle
import com.ashudialer.app.ui.components.liquidGlass


data class GroupedContact(
    val contactId: String,
    val displayName: String,
    val photoUri: String?,
    val isFavorite: Boolean,
    val numbers: List<Contact>
)


fun groupByPerson(contacts: List<Contact>): List<GroupedContact> =
    contacts
        .groupBy { it.contactId }
        .map { (contactId, entries) ->
            GroupedContact(
                contactId = contactId,
                displayName = entries.first().displayName,
                photoUri = entries.firstOrNull { it.photoUri != null }?.photoUri,
                isFavorite = entries.any { it.isFavorite },
                numbers = entries
            )
        }
        .sortedBy { it.displayName.lowercase() }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    contacts: List<Contact>,
    onCall: (Contact) -> Unit,
    onMessage: (Contact) -> Unit = {},
    onWhatsApp: (Contact) -> Unit = {},
    onShareNumber: (Contact) -> Unit = {},
    onOpenDetail: (Contact) -> Unit = {},
    onOpenAddContact: () -> Unit = {},
    onDeleteContacts: (Set<String>) -> Unit = {},
    onBlockNumber: (String) -> Unit = {},
    onUnblockNumber: (String) -> Unit = {},
    isBlocked: (String) -> Boolean = { false },


    precomputedGroupedContacts: List<GroupedContact>? = null,
    showSearchBar: Boolean = true,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    var query by remember { mutableStateOf("") }


    var selectedContactIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val selectionMode = selectedContactIds.isNotEmpty()

    // Same class of bug as Call Insights' day-detail screen: selectionMode
    // here is entirely local, derived state with nothing wiring it into
    // the system back button. Unlike Insights (an overlay, so there was
    // an existing top-level BackHandler to extend), this is a bottom-nav
    // tab with no back handling of its own at all, so a long-press
    // selection with no BackHandler fell straight through to Android's
    // default back behavior - which, with nothing else intercepting it
    // at this point in the screen stack, meant exiting the app entirely
    // instead of just clearing the selection the way the screen's own
    // "X" button (onClick at the top of the selection toolbar) already
    // does correctly.
    //
    // Ordered highest-priority-first: the delete confirmation dialog, if
    // open, should close on its own before back touches selection at all
    // - otherwise one back press would both dismiss the dialog AND drop
    // the selection in a single step, which isn't what a person tapping
    // back once would expect.
    BackHandler(enabled = showDeleteConfirm) { showDeleteConfirm = false }
    BackHandler(enabled = selectionMode && !showDeleteConfirm) { selectedContactIds = emptySet() }


    val filtered = remember(contacts, query) {
        if (query.isBlank()) contacts
        else {
            val digitsQuery = query.filter { it.isDigit() }
            contacts.filter { c ->
                c.displayName.contains(query, ignoreCase = true) ||
                    (digitsQuery.isNotEmpty() && c.phoneNumber.filter { it.isDigit() }.contains(digitsQuery))
            }
        }
    }

    val grouped = remember(filtered, precomputedGroupedContacts) {
        if (query.isBlank() && precomputedGroupedContacts != null) precomputedGroupedContacts
        else groupByPerson(filtered)
    }
    val favorites = remember(grouped) { grouped.filter { it.isFavorite } }
    val alphaGroups = remember(grouped) {
        grouped.groupBy { it.displayName.trim().firstOrNull()?.uppercaseChar() ?: '#' }.toSortedMap()
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()


    val letterToItemIndex = remember(alphaGroups, favorites) {
        val map = mutableMapOf<Char, Int>()
        var index = if (favorites.isNotEmpty()) 2 else 0
        alphaGroups.forEach { (letter, list) ->
            map[letter] = index
            index += 1 + list.size
        }
        map
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { selectedContactIds = emptySet() },
                        modifier = Modifier.size(32.dp).glassCircle(palette)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel selection", tint = palette.textPrimary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("${selectedContactIds.size} selected", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(palette.danger.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete selected", tint = palette.danger, modifier = Modifier.size(18.dp))
                }
            } else {
                Text("Contacts", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                IconButton(
                    onClick = onOpenAddContact,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add contact", tint = palette.accent, modifier = Modifier.size(22.dp))
                }
            }
        }

        if (showSearchBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.searchBackground)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("Search Contacts", color = palette.textSecondary, fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = palette.textPrimary, fontSize = 15.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.6f),
                    exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.6f)
                ) {
                    IconButton(onClick = { query = "" }, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        AnimatedVisibility(
            visible = grouped.isEmpty() && query.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("No contacts match \"$query\"", color = palette.textSecondary, fontSize = 14.sp)
            }
        }

        if (!(grouped.isEmpty() && query.isNotEmpty())) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                if (favorites.isNotEmpty()) {
                    item {
                        Text(
                            "Favourites", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = palette.textSecondary, modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            favorites.forEach { c ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(64.dp)
                                ) {
                                    Box(modifier = Modifier.clickable { c.numbers.firstOrNull()?.let(onCall) }) {
                                        Avatar(name = c.displayName, photoUri = c.photoUri, size = 56.dp)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = c.displayName.split(" ").first(),
                                        fontSize = 12.sp,
                                        color = palette.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                alphaGroups.forEach { (letter, list) ->
                    item(key = "header-$letter") {
                        Text(
                            text = letter.toString(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                    list.forEachIndexed { index, person ->
                        item(key = "person-${person.contactId}-$letter") {


                            val isFirst = index == 0
                            val isLast = index == list.lastIndex
                            val corner = 18.dp
                            val shape = RoundedCornerShape(
                                topStart = if (isFirst) corner else 0.dp,
                                topEnd = if (isFirst) corner else 0.dp,
                                bottomStart = if (isLast) corner else 0.dp,
                                bottomEnd = if (isLast) corner else 0.dp
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlass(palette, shape)
                            ) {
                                ScrollRevealItem(globalIndex = index) {
                                    PersonRow(
                                        person = person,
                                        isSelected = selectedContactIds.contains(person.contactId),
                                        selectionMode = selectionMode,
                                        onCall = onCall,
                                        onMessage = onMessage,
                                        onWhatsApp = onWhatsApp,
                                        onShareNumber = onShareNumber,
                                        onOpenDetail = onOpenDetail,
                                        onBlockNumber = onBlockNumber,
                                        onUnblockNumber = onUnblockNumber,
                                        isBlocked = isBlocked,
                                        onLongPress = {
                                            if (selectedContactIds.isEmpty()) {
                                                selectedContactIds = setOf(person.contactId)
                                            }
                                        },
                                        onToggleSelect = {
                                            selectedContactIds = if (selectedContactIds.contains(person.contactId)) {
                                                selectedContactIds - person.contactId
                                            } else {
                                                selectedContactIds + person.contactId
                                            }
                                        }
                                    )
                                }
                                if (!isLast) {
                                    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                                }
                            }
                            if (isLast) Spacer(Modifier.height(10.dp))
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (alphaGroups.size > 1) {
        AlphabetIndexStrip(
            letters = alphaGroups.keys.toList(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
            onLetterSelected = onLetter@{ letter ->
                val targetIndex = letterToItemIndex[letter] ?: return@onLetter
                scope.launch { listState.scrollToItem(targetIndex) }
            }
        )
    }
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${selectedContactIds.size} contact${if (selectedContactIds.size > 1) "s" else ""}?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onDeleteContacts(selectedContactIds)
                    selectedContactIds = emptySet()
                    showDeleteConfirm = false
                }) {
                    Text("Delete", color = palette.danger)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


@Composable
private fun AlphabetIndexStrip(
    letters: List<Char>,
    modifier: Modifier = Modifier,
    onLetterSelected: (Char) -> Unit
) {
    val palette = LocalDialerPalette.current
    var rowHeightPx by remember { mutableStateOf(1f) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }

    fun letterForOffsetY(y: Float): Char {
        val index = (y / rowHeightPx).toInt().coerceIn(0, letters.lastIndex)
        return letters[index]
    }

    Box(
        modifier = modifier
            .width(22.dp)
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val letter = letterForOffsetY(offset.y)
                        activeLetter = letter
                        onLetterSelected(letter)
                    },
                    onVerticalDrag = { change, _ ->
                        val letter = letterForOffsetY(change.position.y)
                        if (letter != activeLetter) {
                            activeLetter = letter
                            onLetterSelected(letter)
                        }
                    },
                    onDragEnd = { activeLetter = null },
                    onDragCancel = { activeLetter = null }
                )
            }
            .pointerInput(letters) {
                detectTapGestures { offset ->
                    val letter = letterForOffsetY(offset.y)
                    onLetterSelected(letter)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .onSizeChanged { size -> if (letters.isNotEmpty()) rowHeightPx = size.height / letters.size.toFloat() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {


            val letterFontSize = when {
                letters.size > 30 -> 7.sp
                letters.size > 22 -> 8.sp
                else -> 10.sp
            }
            letters.forEach { letter ->
                Text(
                    letter.toString(),
                    fontSize = letterFontSize,
                    fontWeight = FontWeight.Bold,
                    color = if (letter == activeLetter) palette.accent else palette.textSecondary.copy(alpha = 0.6f)
                )
            }
        }


        activeLetter?.let { letter ->
            Box(
                modifier = Modifier
                    .offset(x = (-52).dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(palette.swatchStart, palette.accent, palette.swatchEnd)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(letter.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}


@Composable
private fun ScrollRevealItem(globalIndex: Int, content: @Composable () -> Unit) {
    content()
}


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PersonRow(
    person: GroupedContact,
    isSelected: Boolean,
    selectionMode: Boolean,
    onCall: (Contact) -> Unit,
    onMessage: (Contact) -> Unit,
    onWhatsApp: (Contact) -> Unit,
    onShareNumber: (Contact) -> Unit,
    onOpenDetail: (Contact) -> Unit,
    onBlockNumber: (String) -> Unit,
    onUnblockNumber: (String) -> Unit,
    isBlocked: (String) -> Boolean,
    onLongPress: () -> Unit,
    onToggleSelect: () -> Unit
) {
    val palette = LocalDialerPalette.current
    var expanded by remember { mutableStateOf(false) }
    val primaryNumber = person.numbers.first()

    if (person.numbers.size == 1) {
        SingleNumberContactRow(
            displayName = person.displayName,
            photoUri = person.photoUri,
            isFavorite = person.isFavorite,
            isSelected = isSelected,
            selectionMode = selectionMode,
            onCall = { onCall(primaryNumber) },
            onMessage = { onMessage(primaryNumber) },
            onAvatarTap = { onOpenDetail(primaryNumber) },
            onLongPress = onLongPress,
            onToggleSelect = onToggleSelect
        )
    } else {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSelected) palette.accentSoft else Color.Transparent)
                    .combinedClickable(
                        onClick = { if (selectionMode) onToggleSelect() else expanded = !expanded },
                        onLongClick = onLongPress
                    )
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) palette.accent else palette.searchBackground)
                            .then(if (!isSelected) Modifier.border(1.5.dp, palette.textSecondary.copy(alpha = 0.4f), CircleShape) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                }
                Box(modifier = Modifier.clickable(onClick = if (selectionMode) onToggleSelect else { { onOpenDetail(primaryNumber) } })) {
                    Avatar(name = person.displayName, photoUri = person.photoUri, size = 40.dp)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = person.displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text("${person.numbers.size} numbers", fontSize = 12.sp, color = palette.textSecondary)
                if (person.isFavorite) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Star, contentDescription = "Favorite", tint = palette.accent, modifier = Modifier.size(14.dp))
                }


                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse numbers" else "Expand numbers",
                    tint = palette.textSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(start = 4.dp)
                        .rotate(if (expanded) 180f else 0f)
                )
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(modifier = Modifier.padding(start = 52.dp, bottom = 4.dp)) {
                    person.numbers.forEach { number ->
                        NumberRow(
                            number = number,
                            isBlocked = isBlocked(number.phoneNumber),
                            onCall = { onCall(number) },
                            onMessage = { onMessage(number) },
                            onWhatsApp = { onWhatsApp(number) },
                            onShareNumber = { onShareNumber(number) },
                            onBlock = { onBlockNumber(number.phoneNumber) },
                            onUnblock = { onUnblockNumber(number.phoneNumber) }
                        )
                    }
                }
            }
        }
    }
}


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NumberRow(
    number: Contact,
    isBlocked: Boolean,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onWhatsApp: () -> Unit,
    onShareNumber: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit
) {
    val palette = LocalDialerPalette.current
    val clipboard = LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onCall, onLongClick = { showMenu = true })
                .padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(number.phoneNumber, fontSize = 14.sp, color = palette.textPrimary)
                Text(
                    text = if (isBlocked) "${number.numberLabel} · Blocked" else number.numberLabel,
                    fontSize = 11.sp,
                    color = if (isBlocked) palette.danger else palette.textSecondary
                )
            }
            IconButton(onClick = onCall, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Phone, contentDescription = "Call ${number.numberLabel}", tint = palette.callGreen, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options for ${number.phoneNumber}", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Call") }, leadingIcon = { Icon(Icons.Filled.Phone, null) }, onClick = { showMenu = false; onCall() })
            DropdownMenuItem(text = { Text("Message") }, leadingIcon = { Icon(Icons.Filled.Message, null) }, onClick = { showMenu = false; onMessage() })
            DropdownMenuItem(text = { Text("WhatsApp") }, leadingIcon = { Icon(Icons.Filled.Chat, null) }, onClick = { showMenu = false; onWhatsApp() })
            DropdownMenuItem(
                text = { Text("Copy number") },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                onClick = { showMenu = false; clipboard.setText(AnnotatedString(number.phoneNumber)) }
            )
            DropdownMenuItem(
                text = { Text("Share number") },
                leadingIcon = { Icon(Icons.Filled.Share, null) },
                onClick = { showMenu = false; onShareNumber() }
            )
            if (isBlocked) {
                DropdownMenuItem(text = { Text("Unblock this number") }, leadingIcon = { Icon(Icons.Filled.Block, null) }, onClick = { showMenu = false; onUnblock() })
            } else {
                DropdownMenuItem(
                    text = { Text("Block this number", color = palette.danger) },
                    leadingIcon = { Icon(Icons.Filled.Block, null, tint = palette.danger) },
                    onClick = { showMenu = false; onBlock() }
                )
            }
        }
    }
}


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SingleNumberContactRow(
    displayName: String,
    photoUri: String?,
    isFavorite: Boolean,
    isSelected: Boolean,
    selectionMode: Boolean,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onAvatarTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelect: () -> Unit
) {
    val palette = LocalDialerPalette.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) palette.accentSoft else palette.cardBackground)
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onCall() },
                onLongClick = onLongPress
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) palette.accent else palette.searchBackground)
                    .then(if (!isSelected) Modifier.border(1.5.dp, palette.textSecondary.copy(alpha = 0.4f), CircleShape) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
        }
        Box(modifier = Modifier.clickable(onClick = if (selectionMode) onToggleSelect else onAvatarTap)) {
            Avatar(name = displayName, photoUri = photoUri, size = 40.dp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = displayName,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = palette.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (isFavorite) {
            Icon(
                Icons.Filled.Star, contentDescription = "Favorite",
                tint = palette.accent, modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        if (!selectionMode) {
            IconButton(onClick = onMessage, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Message, contentDescription = "Message",
                    tint = palette.textSecondary, modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
