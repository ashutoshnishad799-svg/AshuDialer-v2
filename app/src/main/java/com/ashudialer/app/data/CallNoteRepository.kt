/*
 * Ashu Phone
 * Copyright (C) 2026 Ashutosh Nishad
 *
 * This file is part of Ashu Phone, licensed under the GNU General Public
 * License, version 3 or (at your option) any later version.
 * See the LICENSE and NOTICE files in the project root.
 * This program comes with ABSOLUTELY NO WARRANTY.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.ashudialer.app.data

import com.ashudialer.app.data.db.CallNoteDao
import com.ashudialer.app.data.db.CallNoteEntity
import kotlinx.coroutines.flow.Flow

class CallNoteRepository(private val dao: CallNoteDao) {

    fun observeAll(): Flow<List<CallNoteEntity>> = dao.observeAll()

    fun observeForNumber(phoneNumber: String): Flow<List<CallNoteEntity>> = dao.observeForNumber(phoneNumber)

    suspend fun addNote(phoneNumber: String, callerLabel: String, text: String) {
        if (text.isBlank()) return
        dao.insert(
            CallNoteEntity(
                phoneNumber = phoneNumber,
                callerLabel = callerLabel,
                text = text.trim()
            )
        )
    }

    suspend fun updateNote(note: CallNoteEntity, newText: String) {
        if (newText.isBlank()) return
        dao.update(note.copy(text = newText.trim()))
    }

    suspend fun deleteNote(note: CallNoteEntity) = dao.delete(note)

    suspend fun clearAll() = dao.clearAll()
}
