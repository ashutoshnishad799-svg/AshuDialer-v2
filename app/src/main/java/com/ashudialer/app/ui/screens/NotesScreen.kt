@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ashudialer.app.data.db.CallNoteEntity
import com.ashudialer.app.ui.theme.LocalDialerPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ashudialer.app.ui.components.glassCard

/**
 * Note: tapping a row (avatar included) used to fire onCall directly - the
 * avatar's own clickable placed a call rather than opening any kind of
 * detail view, which meant there was no way to see a note full-size, edit
 * its text, or share it without leaving this screen. Now every tap (avatar
 * or row) opens NoteDetailSheet, which is where editing, sharing, calling,
 * and deleting all actually live - calling is still one tap away from
 * there, just not the *only* thing a tap could do.
 */
@Composable
fun NotesScreen(
    notes: List<CallNoteEntity>,
    onBack: () -> Unit,
    onDelete: (CallNoteEntity) -> Unit,
    onCallNumber: (String) -> Unit,
    onSaveNoteText: (CallNoteEntity, String) -> Unit,
    onShareNote: (CallNoteEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    var selectedNote by remember { mutableStateOf<CallNoteEntity?>(null) }

    // Multi-select state, same pattern as RecordingsScreen: an empty set
    // means "not in selection mode"; long-pressing a row seeds it with that
    // one note, and clearing back to empty exits selection mode. Nothing
    // else needs a separate "am I in selection mode" boolean.
    var selectedNotes by remember { mutableStateOf(setOf<CallNoteEntity>()) }
    val inSelectionMode = selectedNotes.isNotEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        if (inSelectionMode) {
            NotesSelectionAppBar(
                selectedCount = selectedNotes.size,
                onClose = { selectedNotes = emptySet() },
                onDeleteSelected = {
                    if (selectedNote in selectedNotes) selectedNote = null
                    selectedNotes.forEach { onDelete(it) }
                    selectedNotes = emptySet()
                },
                palette = palette
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
                }
                Spacer(Modifier.width(4.dp))
                Text("Notes", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            }
        }

        if (notes.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.NoteAlt, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text("No notes yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap \"Note\" from the \"More\" menu during a call to jot something down.",
                    fontSize = 13.sp, color = palette.textSecondary
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 12.dp)) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().glassCard(palette, 18.dp)
                    ) {
                        notes.forEachIndexed { index, note ->
                            NoteRow(
                                note = note,
                                inSelectionMode = inSelectionMode,
                                selected = note in selectedNotes,
                                onOpen = {
                                    if (inSelectionMode) {
                                        selectedNotes = if (note in selectedNotes) selectedNotes - note else selectedNotes + note
                                    } else {
                                        selectedNote = note
                                    }
                                },
                                onLongPress = {
                                    if (!inSelectionMode) selectedNotes = setOf(note)
                                }
                            )
                            if (index != notes.lastIndex) {
                                HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    val note = selectedNote
    if (note != null) {
        NoteDetailSheet(
            note = note,
            onDismiss = { selectedNote = null },
            onSave = { newText ->
                onSaveNoteText(note, newText)
                selectedNote = null
            },
            onCall = { onCallNumber(note.phoneNumber) },
            onShare = { onShareNote(note) },
            onDelete = {
                onDelete(note)
                selectedNote = null
            }
        )
    }
}

/**
 * Same "contextual action bar" pattern as RecordingsScreen's
 * SelectionAppBar - close button, count, and the batch action (delete).
 */
@Composable
private fun NotesSelectionAppBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onDeleteSelected: () -> Unit,
    palette: com.ashudialer.app.ui.theme.DialerPalette
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel selection", tint = palette.textPrimary)
        }
        Spacer(Modifier.width(4.dp))
        Text("$selectedCount selected", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, modifier = Modifier.weight(1f))
        IconButton(onClick = { showDeleteConfirm = true }) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete selected", tint = palette.danger)
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (selectedCount == 1) "Delete note?" else "Delete $selectedCount notes?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteSelected()
                }) {
                    Text("Delete", color = palette.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun NoteRow(
    note: CallNoteEntity,
    inSelectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit
) {
    val palette = LocalDialerPalette.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            if (inSelectionMode) {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (selected) "Selected" else "Not selected",
                    tint = if (selected) palette.accent else palette.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    note.callerLabel.take(1).uppercase().ifBlank { "?" },
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.accent
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    note.callerLabel.ifBlank { note.phoneNumber },
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    formatNoteTime(note.createdAtMillis),
                    fontSize = 11.5.sp, color = palette.textSecondary
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(note.text, fontSize = 13.5.sp, color = palette.textSecondary, lineHeight = 18.sp, maxLines = 3)
        }
    }
}

/**
 * Full-screen-ish modal for one note: shows the complete text (the row
 * above truncates to 3 lines), lets it be edited in place, and surfaces
 * Call/Share/Delete together - previously none of these existed anywhere
 * for a note except delete (as a small icon on the row) and call (as the
 * row's entire tap target, which is what this replaces).
 */
@Composable
private fun NoteDetailSheet(
    note: CallNoteEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onCall: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalDialerPalette.current
    var isEditing by remember(note.id) { mutableStateOf(false) }
    var draftText by remember(note.id) { mutableStateOf(note.text) }
    var confirmingDelete by remember(note.id) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = palette.cardBackground) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(palette.accentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            note.callerLabel.take(1).uppercase().ifBlank { "?" },
                            fontSize = 17.sp, fontWeight = FontWeight.Bold, color = palette.accent
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            note.callerLabel.ifBlank { note.phoneNumber },
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary, maxLines = 1
                        )
                        Text(formatNoteTime(note.createdAtMillis), fontSize = 12.sp, color = palette.textSecondary)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (isEditing) {
                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { draftText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        placeholder = { Text("Note text") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.cardBorder,
                            cursorColor = palette.accent
                        )
                    )
                } else {
                    Text(
                        note.text.ifBlank { "(empty note)" },
                        fontSize = 14.5.sp,
                        color = palette.textPrimary,
                        lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(18.dp))

                if (isEditing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { isEditing = false; draftText = note.text },
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancel") }
                        Button(
                            onClick = { onSave(draftText) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                        ) { Text("Save") }
                    }
                } else if (confirmingDelete) {
                    Text(
                        "Delete this note? This can't be undone.",
                        fontSize = 13.sp, color = palette.danger, modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { confirmingDelete = false }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.danger)
                        ) { Text("Delete") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        NoteActionButton(icon = Icons.Filled.Call, label = "Call", palette = palette, onClick = onCall, modifier = Modifier.weight(1f))
                        NoteActionButton(icon = Icons.Filled.Edit, label = "Edit", palette = palette, onClick = { isEditing = true }, modifier = Modifier.weight(1f))
                        NoteActionButton(icon = Icons.Filled.Share, label = "Share", palette = palette, onClick = onShare, modifier = Modifier.weight(1f))
                        NoteActionButton(icon = Icons.Filled.Delete, label = "Delete", palette = palette, destructive = true, onClick = { confirmingDelete = true }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    destructive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (destructive) palette.danger else palette.accent
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(if (destructive) palette.danger.copy(alpha = 0.12f) else palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.5.sp, color = tint)
    }
}

private fun formatNoteTime(millis: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(millis))
