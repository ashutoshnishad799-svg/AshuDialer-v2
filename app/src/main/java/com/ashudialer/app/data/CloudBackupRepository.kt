package com.ashudialer.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ashudialer.app.data.db.BlockedNumberEntity
import com.ashudialer.app.data.db.CallDirection
import com.ashudialer.app.data.db.CallLogEntity
import kotlinx.coroutines.tasks.await

data class BackupSnapshot(
    val callLog: List<CallLogEntity>,
    val blockedNumbers: List<BlockedNumberEntity>,
    val themeId: String,
    val lastBackedUpAtMillis: Long
)

sealed class BackupResult {
    object Success : BackupResult()
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

    suspend fun backup(
        uid: String,
        callLog: List<CallLogEntity>,
        blockedNumbers: List<BlockedNumberEntity>,
        themeId: String
    ): BackupResult {
        val doc = userDoc(uid) ?: return BackupResult.Failure("Cloud backup isn't set up yet.")
        return try {
            val payload = mapOf(
                "callLog" to callLog.take(500).map { it.toMap() },
                "blockedNumbers" to blockedNumbers.map { it.toMap() },
                "themeId" to themeId,
                "lastBackedUpAtMillis" to System.currentTimeMillis()
            )
            doc.set(payload, SetOptions.merge()).await()
            BackupResult.Success
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

        val callLog = callLogRaw.mapNotNull { (it as? Map<*, *>)?.toCallLogEntity() }
        val blocked = blockedRaw.mapNotNull { (it as? Map<*, *>)?.toBlockedNumberEntity() }
        val themeId = snapshot.getString("themeId") ?: "ocean"
        val lastBackedUp = snapshot.getLong("lastBackedUpAtMillis") ?: 0L

        return BackupSnapshot(callLog, blocked, themeId, lastBackedUp)
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
}
