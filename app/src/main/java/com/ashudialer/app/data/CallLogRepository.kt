package com.ashudialer.app.data

import com.ashudialer.app.data.db.CallDirection
import com.ashudialer.app.data.db.CallLogDao
import com.ashudialer.app.data.db.CallLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CallLogRepository(private val dao: CallLogDao) {


    fun observeGroupedRecents(): Flow<List<RecentCall>> =
        dao.observeAll().map { entries -> groupConsecutive(entries) }

    fun observeMissed(): Flow<List<RecentCall>> =
        dao.observeMissed().map { entries -> groupConsecutive(entries) }


    suspend fun syncFromSystem(systemRepo: SystemCallLogRepository) {
        val systemEntries = systemRepo.loadRecentHistory()
        if (systemEntries.isEmpty()) return

        val asEntities = systemEntries.map { entry ->
            CallLogEntity(
                phoneNumber = entry.phoneNumber,
                displayName = entry.displayName,
                direction = entry.direction,
                timestampMillis = entry.timestampMillis,
                durationSeconds = entry.durationSeconds,
                photoUri = entry.photoUri
            )
        }
        dao.insertAll(asEntities)
    }

    private fun groupConsecutive(entries: List<CallLogEntity>): List<RecentCall> {
        if (entries.isEmpty()) return emptyList()
        val result = mutableListOf<RecentCall>()
        var i = 0
        while (i < entries.size) {
            val current = entries[i]
            var count = 1
            var j = i + 1
            while (j < entries.size && entries[j].phoneNumber == current.phoneNumber) {
                count++
                j++
            }
            val idsInGroup = (i until j).map { entries[it].id }
            result.add(
                RecentCall(
                    id = current.id,
                    displayName = current.displayName?.takeIf { it.isNotBlank() }
                        ?: current.phoneNumber.ifBlank { "Unknown number" },
                    phoneNumber = current.phoneNumber,
                    direction = current.direction,
                    timestampMillis = current.timestampMillis,
                    durationSeconds = current.durationSeconds,
                    photoUri = current.photoUri,
                    callCount = count,
                    isSpam = current.isSpam,
                    groupedIds = idsInGroup
                )
            )
            i = j
        }
        return result
    }

    suspend fun logCall(
        number: String,
        name: String?,
        direction: CallDirection,
        durationSeconds: Int = 0,
        photoUri: String? = null,
        isSpam: Boolean = false
    ) {
        dao.insert(
            CallLogEntity(
                phoneNumber = number,
                displayName = name,
                direction = direction,
                timestampMillis = System.currentTimeMillis(),
                durationSeconds = durationSeconds,
                photoUri = photoUri,
                isSpam = isSpam
            )
        )
    }

    suspend fun clearHistory() = dao.clearAll()


    suspend fun deleteLocalHistoryForNumber(phoneNumber: String) = dao.deleteByNumber(phoneNumber)

    suspend fun deleteEntriesByIds(ids: Set<Long>) = dao.deleteByIds(ids)


    suspend fun rawEntriesForBackup(limit: Int = 500): List<CallLogEntity> =
        dao.getRecentSnapshot(limit)


    suspend fun replaceAllFromBackup(entries: List<CallLogEntity>) {
        if (entries.isEmpty()) return


        val withoutIds = entries.map { it.copy(id = 0) }
        dao.insertAll(withoutIds)
    }
}
