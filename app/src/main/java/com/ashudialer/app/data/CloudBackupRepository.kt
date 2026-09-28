package com.ashudialer.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ashudialer.app.data.db.BlockedNumberEntity
import com.ashudialer.app.data.db.CallDirection
import com.ashudialer.app.data.db.CallLogEntity
import com.ashudialer.app.data.db.CallNoteEntity
import com.ashudialer.app.data.db.ReportedSpamEntity
import com.ashudialer.app.data.db.SimRoutingEntity
import com.ashudialer.app.data.db.VibrationRuleEntity
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

/**
 * One saved contact, in the shape needed to both display it in the
 * account screen ("X contacts backed up") and hand it straight to
 * ContactsRepository.insertContact() on restore. Contacts live in the
 * OS's own Contacts provider, not this app's Room database (unlike every
 * other type here) - "backing up contacts" for this app specifically
 * means capturing enough of each one (name, number, email, address,
 * company, note) to recreate it as a real device contact afterwards, not
 * syncing this app's own local table.
 */
data class BackedUpContact(
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val phoneLabel: String,
    val email: String,
    val homeAddress: String,
    val company: String,
    val notes: String
)

data class BackupSnapshot(
    val callLog: List<CallLogEntity>,
    val blockedNumbers: List<BlockedNumberEntity>,
    val contacts: List<BackedUpContact>,
    val callNotes: List<CallNoteEntity>,
    val simRoutingRules: List<SimRoutingEntity>,
    val vibrationRules: List<VibrationRuleEntity>,
    val reportedSpam: List<ReportedSpamEntity>,
    val themeId: String,
    val lastBackedUpAtMillis: Long,
    /** The person's own mobile number (Settings > My number), so a restore onto a new phone brings it back. */
    val myPhoneNumber: String = ""
)

/** Per-category counts for what a backup actually contains - shown on the Account screen so "Back up now" isn't a black box. */
data class BackupCounts(
    val callLogCount: Int,
    val blockedCount: Int,
    val contactsCount: Int,
    val notesCount: Int,
    val simRulesCount: Int,
    val vibrationRulesCount: Int,
    val reportedSpamCount: Int
)

sealed class BackupResult {
    data class Success(val counts: BackupCounts) : BackupResult()
    data class Failure(val message: String) : BackupResult()
}


class CloudBackupRepository {


    private val db: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: IllegalStateException) {
        android.util.Log.w("CloudBackupRepository", "Firebase not configured — cloud backup disabled.", e)
        null
    }

    private fun userDoc(uid: String) = db?.collection("users")?.document(uid)

    private companion object {
        /** Longest any single backup/restore/delete is allowed to run before reporting a failure instead of hanging. */
        const val NETWORK_TIMEOUT_MS = 30_000L
        /** Rows per Firestore document; keeps each document far below the 1 MiB limit. */
        const val CHUNK_SIZE = 200
        const val MAX_CALL_LOG = 500
        const val MAX_CONTACTS = 1000
        const val MAX_NOTES = 500
    }

    /**
     * THE FIX for "Cloud Backup stuck on 'Backing up...' forever" and for
     * large backups being rejected.
     *
     * Two problems lived here:
     *  1. doc.set(...).await() had no timeout. Firestore queues a write for
     *     offline persistence, so when the backend is unreachable (no data,
     *     Firestore not enabled, or security rules denying the write) the
     *     Task simply never completes - await() suspended forever, nothing
     *     was ever thrown, and the Account screen sat on "Backing up..." with
     *     no way out. Every network call below now runs under
     *     [NETWORK_TIMEOUT_MS] and reports a clear failure instead.
     *  2. Everything was written into ONE document, but Firestore caps a
     *     document at 1 MiB. 500 call-log rows plus 1000 contacts (each with
     *     several fields) can pass that on a real phone, and the write is then
     *     rejected. The data is now split: the small "meta" document lives at
     *     users/{uid}, and each category is stored in its own document(s)
     *     under users/{uid}/backup, with call log and contacts chunked into
     *     [CHUNK_SIZE]-row pieces so no single document can approach the limit.
     */
    suspend fun backup(
        uid: String,
        callLog: List<CallLogEntity>,
        blockedNumbers: List<BlockedNumberEntity>,
        contacts: List<BackedUpContact>,
        callNotes: List<CallNoteEntity>,
        simRoutingRules: List<SimRoutingEntity>,
        vibrationRules: List<VibrationRuleEntity>,
        reportedSpam: List<ReportedSpamEntity>,
        themeId: String,
        myPhoneNumber: String = ""
    ): BackupResult {
        val doc = userDoc(uid) ?: return BackupResult.Failure("Cloud backup isn't set up yet.")
        return try {
            val cappedCallLog = callLog.take(MAX_CALL_LOG)
            val cappedContacts = contacts.take(MAX_CONTACTS)
            val cappedNotes = callNotes.take(MAX_NOTES)

            val parts = HashMap<String, Map<String, Any?>>()
            cappedCallLog.map { it.toMap() }.chunked(CHUNK_SIZE).forEachIndexed { i, chunk ->
                parts["callLog_$i"] = mapOf("items" to chunk)
            }
            cappedContacts.map { it.toMap() }.chunked(CHUNK_SIZE).forEachIndexed { i, chunk ->
                parts["contacts_$i"] = mapOf("items" to chunk)
            }
            parts["blockedNumbers"] = mapOf("items" to blockedNumbers.map { it.toMap() })
            parts["callNotes"] = mapOf("items" to cappedNotes.map { it.toMap() })
            parts["simRoutingRules"] = mapOf("items" to simRoutingRules.map { it.toMap() })
            parts["vibrationRules"] = mapOf("items" to vibrationRules.map { it.toMap() })
            parts["reportedSpam"] = mapOf("items" to reportedSpam.map { it.toMap() })

            val callLogParts = (cappedCallLog.size + CHUNK_SIZE - 1) / CHUNK_SIZE
            val contactParts = (cappedContacts.size + CHUNK_SIZE - 1) / CHUNK_SIZE

            withTimeout(NETWORK_TIMEOUT_MS) {
                val partsCollection = doc.collection("backup")
                // Clear stale chunks first so a smaller backup than last time
                // doesn't leave old rows behind to be restored later.
                val existing = partsCollection.get().await()
                existing.documents.forEach { it.reference.delete().await() }
                parts.forEach { (name, data) -> partsCollection.document(name).set(data).await() }
                doc.set(
                    mapOf(
                        "themeId" to themeId,
                        "myPhoneNumber" to myPhoneNumber,
                        "lastBackedUpAtMillis" to System.currentTimeMillis(),
                        "callLogParts" to callLogParts,
                        "contactParts" to contactParts,
                        "format" to 2
                    ),
                    SetOptions.merge()
                ).await()
            }
            BackupResult.Success(
                BackupCounts(
                    callLogCount = cappedCallLog.size,
                    blockedCount = blockedNumbers.size,
                    contactsCount = cappedContacts.size,
                    notesCount = cappedNotes.size,
                    simRulesCount = simRoutingRules.size,
                    vibrationRulesCount = vibrationRules.size,
                    reportedSpamCount = reportedSpam.size
                )
            )
        } catch (e: TimeoutCancellationException) {
            BackupResult.Failure(
                "Timed out. Check your internet, and that Firestore is created and its rules allow signed-in users."
            )
        } catch (e: Exception) {
            BackupResult.Failure(e.message ?: "Backup failed.")
        }
    }

    suspend fun restore(uid: String): BackupSnapshot? {
        val doc = userDoc(uid) ?: return null
        val snapshot = withTimeout(NETWORK_TIMEOUT_MS) { doc.get().await() }
        if (!snapshot.exists()) return null

        // "format" 2 = chunked layout written by backup() above. Anything else
        // is the old single-document layout, still read below so backups made
        // by earlier versions of the app keep restoring.
        val isChunked = (snapshot.getLong("format") ?: 1L) >= 2L

        // Old (format 1) backups kept every list inline on this one document.
        fun legacyList(field: String): List<*> =
            if (isChunked) emptyList<Any>() else snapshot.get(field) as? List<*> ?: emptyList<Any>()

        var callLogRaw: List<*> = legacyList("callLog")
        var contactsRaw: List<*> = legacyList("contacts")
        var blockedRaw: List<*> = legacyList("blockedNumbers")
        var notesRaw: List<*> = legacyList("callNotes")
        var simRaw: List<*> = legacyList("simRoutingRules")
        var vibRaw: List<*> = legacyList("vibrationRules")
        var spamRaw: List<*> = legacyList("reportedSpam")

        if (isChunked) {
            val partsSnapshot = withTimeout(NETWORK_TIMEOUT_MS) { doc.collection("backup").get().await() }
            val byName = partsSnapshot.documents.associateBy { it.id }
            fun single(name: String): List<*> = byName[name]?.get("items") as? List<*> ?: emptyList<Any>()
            fun chunked(prefix: String, count: Int): List<Any?> =
                (0 until count).flatMap { i -> byName["${prefix}_$i"]?.get("items") as? List<*> ?: emptyList<Any>() }
            callLogRaw = chunked("callLog", (snapshot.getLong("callLogParts") ?: 0L).toInt())
            contactsRaw = chunked("contacts", (snapshot.getLong("contactParts") ?: 0L).toInt())
            blockedRaw = single("blockedNumbers")
            notesRaw = single("callNotes")
            simRaw = single("simRoutingRules")
            vibRaw = single("vibrationRules")
            spamRaw = single("reportedSpam")
        }

        val callLog = callLogRaw.mapNotNull { (it as? Map<*, *>)?.toCallLogEntity() }
        val blocked = blockedRaw.mapNotNull { (it as? Map<*, *>)?.toBlockedNumberEntity() }
        val contacts = contactsRaw.mapNotNull { (it as? Map<*, *>)?.toBackedUpContact() }
        val notes = notesRaw.mapNotNull { (it as? Map<*, *>)?.toCallNoteEntity() }
        val simRules = simRaw.mapNotNull { (it as? Map<*, *>)?.toSimRoutingEntity() }
        val vibRules = vibRaw.mapNotNull { (it as? Map<*, *>)?.toVibrationRuleEntity() }
        val spam = spamRaw.mapNotNull { (it as? Map<*, *>)?.toReportedSpamEntity() }
        val themeId = snapshot.getString("themeId") ?: com.ashudialer.app.ui.theme.AUTO_THEME_ID
        val lastBackedUp = snapshot.getLong("lastBackedUpAtMillis") ?: 0L
        val myPhoneNumber = snapshot.getString("myPhoneNumber") ?: ""

        return BackupSnapshot(callLog, blocked, contacts, notes, simRules, vibRules, spam, themeId, lastBackedUp, myPhoneNumber)
    }

    suspend fun deleteUserData(uid: String) {
        val doc = userDoc(uid) ?: return
        withTimeout(NETWORK_TIMEOUT_MS) {
            // Firestore does not delete a document's subcollections when the
            // document itself is deleted, so remove the chunk documents first.
            doc.collection("backup").get().await().documents.forEach { it.reference.delete().await() }
            doc.delete().await()
        }
    }

    private fun CallLogEntity.toMap() = mapOf(
        "phoneNumber" to phoneNumber,
        "displayName" to displayName,
        "direction" to direction.name,
        "timestampMillis" to timestampMillis,
        "durationSeconds" to durationSeconds,
        "isSpam" to isSpam,
        "photoUri" to photoUri
    )

    private fun BlockedNumberEntity.toMap() = mapOf(
        "phoneNumber" to phoneNumber,
        "reason" to reason,
        "addedAtMillis" to addedAtMillis
    )

    private fun BackedUpContact.toMap() = mapOf(
        "firstName" to firstName,
        "lastName" to lastName,
        "phoneNumber" to phoneNumber,
        "phoneLabel" to phoneLabel,
        "email" to email,
        "homeAddress" to homeAddress,
        "company" to company,
        "notes" to notes
    )

    private fun CallNoteEntity.toMap() = mapOf(
        "phoneNumber" to phoneNumber,
        "callerLabel" to callerLabel,
        "text" to text,
        "createdAtMillis" to createdAtMillis
    )

    private fun SimRoutingEntity.toMap() = mapOf(
        "phoneNumber" to phoneNumber,
        "preferredSimAccountId" to preferredSimAccountId
    )

    private fun VibrationRuleEntity.toMap() = mapOf(
        "phoneNumber" to phoneNumber,
        "patternId" to patternId
    )

    private fun ReportedSpamEntity.toMap() = mapOf(
        "phoneNumber" to phoneNumber,
        "reportedAtMillis" to reportedAtMillis,
        "reason" to reason
    )

    private fun Map<*, *>.toCallLogEntity(): CallLogEntity? {
        val number = this["phoneNumber"] as? String ?: return null
        val directionName = this["direction"] as? String ?: return null
        val direction = try {
            CallDirection.valueOf(directionName)
        } catch (e: IllegalArgumentException) {
            return null
        }
        return CallLogEntity(
            phoneNumber = number,
            displayName = this["displayName"] as? String,
            direction = direction,
            timestampMillis = (this["timestampMillis"] as? Number)?.toLong() ?: return null,
            durationSeconds = (this["durationSeconds"] as? Number)?.toInt() ?: 0,
            isSpam = this["isSpam"] as? Boolean ?: false,
            photoUri = this["photoUri"] as? String
        )
    }

    private fun Map<*, *>.toBlockedNumberEntity(): BlockedNumberEntity? {
        val number = this["phoneNumber"] as? String ?: return null
        return BlockedNumberEntity(
            phoneNumber = number,
            reason = this["reason"] as? String ?: "Blocked by user",
            addedAtMillis = (this["addedAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    private fun Map<*, *>.toBackedUpContact(): BackedUpContact? {
        val phoneNumber = this["phoneNumber"] as? String ?: return null
        if (phoneNumber.isBlank()) return null
        return BackedUpContact(
            firstName = this["firstName"] as? String ?: "",
            lastName = this["lastName"] as? String ?: "",
            phoneNumber = phoneNumber,
            phoneLabel = this["phoneLabel"] as? String ?: "Mobile",
            email = this["email"] as? String ?: "",
            homeAddress = this["homeAddress"] as? String ?: "",
            company = this["company"] as? String ?: "",
            notes = this["notes"] as? String ?: ""
        )
    }

    private fun Map<*, *>.toCallNoteEntity(): CallNoteEntity? {
        val phoneNumber = this["phoneNumber"] as? String ?: return null
        val text = this["text"] as? String ?: return null
        return CallNoteEntity(
            phoneNumber = phoneNumber,
            callerLabel = this["callerLabel"] as? String ?: phoneNumber,
            text = text,
            createdAtMillis = (this["createdAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    private fun Map<*, *>.toSimRoutingEntity(): SimRoutingEntity? {
        val phoneNumber = this["phoneNumber"] as? String ?: return null
        val simId = this["preferredSimAccountId"] as? String ?: return null
        return SimRoutingEntity(phoneNumber = phoneNumber, preferredSimAccountId = simId)
    }

    private fun Map<*, *>.toVibrationRuleEntity(): VibrationRuleEntity? {
        val phoneNumber = this["phoneNumber"] as? String ?: return null
        val patternId = this["patternId"] as? String ?: return null
        return VibrationRuleEntity(phoneNumber = phoneNumber, patternId = patternId)
    }

    private fun Map<*, *>.toReportedSpamEntity(): ReportedSpamEntity? {
        val phoneNumber = this["phoneNumber"] as? String ?: return null
        return ReportedSpamEntity(
            phoneNumber = phoneNumber,
            reportedAtMillis = (this["reportedAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            reason = this["reason"] as? String ?: ""
        )
    }
}
