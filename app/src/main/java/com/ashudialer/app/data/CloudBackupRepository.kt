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
    val lastBackedUpAtMillis: Long
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

    /**
     * Every list is capped well under Firestore's 1 MiB single-document
     * limit (a single document, not subcollections, exactly mirrors how
     * this was already structured for callLog/blockedNumbers before -
     * kept as one document so restore is one read rather than several,
     * and because none of these lists individually get large enough on a
     * personal phone to need splitting). Contacts and call log are the
     * two realistically large lists on a real phone, so they get the
     * tightest caps; the rule tables (SIM routing, vibration, reported
     * spam) are things a person sets up a handful of at a time and are
     * capped generously rather than tightly.
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
        themeId: String
    ): BackupResult {
        val doc = userDoc(uid) ?: return BackupResult.Failure("Cloud backup isn't set up yet.")
        return try {
            val cappedCallLog = callLog.take(500)
            val cappedContacts = contacts.take(1000)
            val cappedNotes = callNotes.take(500)
            val payload = mapOf(
                "callLog" to cappedCallLog.map { it.toMap() },
                "blockedNumbers" to blockedNumbers.map { it.toMap() },
                "contacts" to cappedContacts.map { it.toMap() },
                "callNotes" to cappedNotes.map { it.toMap() },
                "simRoutingRules" to simRoutingRules.map { it.toMap() },
                "vibrationRules" to vibrationRules.map { it.toMap() },
                "reportedSpam" to reportedSpam.map { it.toMap() },
                "themeId" to themeId,
                "lastBackedUpAtMillis" to System.currentTimeMillis()
            )
            doc.set(payload, SetOptions.merge()).await()
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
        } catch (e: Exception) {
            BackupResult.Failure(e.message ?: "Backup failed.")
        }
    }

    suspend fun restore(uid: String): BackupSnapshot? {
        val doc = userDoc(uid) ?: return null
        val snapshot = doc.get().await()
        if (!snapshot.exists()) return null

        val callLogRaw = snapshot.get("callLog") as? List<*> ?: emptyList<Any>()
        val blockedRaw = snapshot.get("blockedNumbers") as? List<*> ?: emptyList<Any>()
        val contactsRaw = snapshot.get("contacts") as? List<*> ?: emptyList<Any>()
        val notesRaw = snapshot.get("callNotes") as? List<*> ?: emptyList<Any>()
        val simRaw = snapshot.get("simRoutingRules") as? List<*> ?: emptyList<Any>()
        val vibRaw = snapshot.get("vibrationRules") as? List<*> ?: emptyList<Any>()
        val spamRaw = snapshot.get("reportedSpam") as? List<*> ?: emptyList<Any>()

        val callLog = callLogRaw.mapNotNull { (it as? Map<*, *>)?.toCallLogEntity() }
        val blocked = blockedRaw.mapNotNull { (it as? Map<*, *>)?.toBlockedNumberEntity() }
        val contacts = contactsRaw.mapNotNull { (it as? Map<*, *>)?.toBackedUpContact() }
        val notes = notesRaw.mapNotNull { (it as? Map<*, *>)?.toCallNoteEntity() }
        val simRules = simRaw.mapNotNull { (it as? Map<*, *>)?.toSimRoutingEntity() }
        val vibRules = vibRaw.mapNotNull { (it as? Map<*, *>)?.toVibrationRuleEntity() }
        val spam = spamRaw.mapNotNull { (it as? Map<*, *>)?.toReportedSpamEntity() }
        val themeId = snapshot.getString("themeId") ?: com.ashudialer.app.ui.theme.AUTO_THEME_ID
        val lastBackedUp = snapshot.getLong("lastBackedUpAtMillis") ?: 0L

        return BackupSnapshot(callLog, blocked, contacts, notes, simRules, vibRules, spam, themeId, lastBackedUp)
    }

    suspend fun deleteUserData(uid: String) {
        userDoc(uid)?.delete()?.await()
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
