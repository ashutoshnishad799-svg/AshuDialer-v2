package com.ashudialer.app.data

import com.ashudialer.app.data.db.BlockedNumberEntity
import com.ashudialer.app.data.db.CallDirection
import com.ashudialer.app.data.db.CallLogDao
import com.ashudialer.app.data.db.BlockedNumberDao
import com.ashudialer.app.data.db.CallLogEntity
import com.ashudialer.app.data.db.CallNoteDao
import com.ashudialer.app.data.db.CallNoteEntity
import com.ashudialer.app.data.db.SimRoutingDao
import com.ashudialer.app.data.db.SimRoutingEntity
import com.ashudialer.app.data.db.VibrationRuleDao
import com.ashudialer.app.data.db.VibrationRuleEntity
import com.ashudialer.app.data.db.ReportedSpamDao
import com.ashudialer.app.data.db.ReportedSpamEntity
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

sealed class LocalBackupExportResult {
    data class Success(val bytes: ByteArray) : LocalBackupExportResult()
    data class Failure(val reason: String) : LocalBackupExportResult()
}

sealed class LocalBackupImportResult {
    data class Success(
        val callLogCount: Int, val blockedCount: Int, val notesCount: Int,
        val simRulesCount: Int, val vibrationRulesCount: Int, val reportedSpamCount: Int = 0
    ) : LocalBackupImportResult()
    object WrongPin : LocalBackupImportResult()
    object NotAValidBackupFile : LocalBackupImportResult()
    data class Failure(val reason: String) : LocalBackupImportResult()
}


class LocalBackupRepository(
    private val callLogDao: CallLogDao,
    private val blockedNumberDao: BlockedNumberDao,
    private val callNoteDao: CallNoteDao,
    private val simRoutingDao: SimRoutingDao,
    private val vibrationRuleDao: VibrationRuleDao,
    private val reportedSpamDao: ReportedSpamDao
) {
    private val magic = byteArrayOf('P'.code.toByte(), 'D'.code.toByte(), 'L'.code.toByte(), 'B'.code.toByte())
    private val pbkdf2Iterations = 210_000
    private val keyLengthBits = 256

    suspend fun export(pin: String): LocalBackupExportResult {
        if (pin.length < 4) return LocalBackupExportResult.Failure("PIN must be at least 4 characters")

        return try {
            val payload = buildPayloadJson().toString().toByteArray(Charsets.UTF_8)

            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(pin, salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val ciphertext = cipher.doFinal(payload)

            LocalBackupExportResult.Success(magic + salt + iv + ciphertext)
        } catch (e: Exception) {
            LocalBackupExportResult.Failure(e.message ?: "Encryption failed")
        }
    }

    suspend fun import(fileBytes: ByteArray, pin: String): LocalBackupImportResult {
        if (fileBytes.size < magic.size + 16 + 12 + 16) {
            return LocalBackupImportResult.NotAValidBackupFile
        }
        val fileMagic = fileBytes.copyOfRange(0, 4)
        if (!fileMagic.contentEquals(magic)) {
            return LocalBackupImportResult.NotAValidBackupFile
        }

        return try {
            val salt = fileBytes.copyOfRange(4, 20)
            val iv = fileBytes.copyOfRange(20, 32)
            val ciphertext = fileBytes.copyOfRange(32, fileBytes.size)
            val key = deriveKey(pin, salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plaintext = try {
                cipher.doFinal(ciphertext)
            } catch (e: AEADBadTagException) {


                return LocalBackupImportResult.WrongPin
            }

            val json = JSONObject(String(plaintext, Charsets.UTF_8))
            applyPayloadJson(json)
        } catch (e: Exception) {
            LocalBackupImportResult.Failure(e.message ?: "Import failed")
        }
    }

    private fun deriveKey(pin: String, salt: ByteArray): SecretKeySpec {
        val spec: KeySpec = PBEKeySpec(pin.toCharArray(), salt, pbkdf2Iterations, keyLengthBits)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private suspend fun buildPayloadJson(): JSONObject {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAtMillis", System.currentTimeMillis())

        val callLog = JSONArray()
        callLogDao.getRecentSnapshot(Int.MAX_VALUE).forEach { entry ->
            callLog.put(JSONObject().apply {
                put("phoneNumber", entry.phoneNumber)
                put("displayName", entry.displayName ?: JSONObject.NULL)
                put("direction", entry.direction.name)
                put("timestampMillis", entry.timestampMillis)
                put("durationSeconds", entry.durationSeconds)
                put("isSpam", entry.isSpam)
            })
        }
        root.put("callLog", callLog)

        val blocked = JSONArray()
        blockedNumberDao.observeAll().first().forEach { entry ->
            blocked.put(JSONObject().apply {
                put("phoneNumber", entry.phoneNumber)
                put("reason", entry.reason)
                put("addedAtMillis", entry.addedAtMillis)
            })
        }
        root.put("blockedNumbers", blocked)

        val notes = JSONArray()
        callNoteDao.observeAll().first().forEach { entry ->
            notes.put(JSONObject().apply {
                put("phoneNumber", entry.phoneNumber)
                put("callerLabel", entry.callerLabel)
                put("text", entry.text)
                put("createdAtMillis", entry.createdAtMillis)
            })
        }
        root.put("notes", notes)

        val simRules = JSONArray()
        simRoutingDao.observeAll().first().forEach { entry ->
            simRules.put(JSONObject().apply {
                put("phoneNumber", entry.phoneNumber)
                put("preferredSimAccountId", entry.preferredSimAccountId)
            })
        }
        root.put("simRules", simRules)

        val vibRules = JSONArray()
        vibrationRuleDao.observeAll().first().forEach { entry ->
            vibRules.put(JSONObject().apply {
                put("phoneNumber", entry.phoneNumber)
                put("patternId", entry.patternId)
            })
        }
        root.put("vibrationRules", vibRules)

        val reportedSpam = JSONArray()
        reportedSpamDao.observeAll().first().forEach { entry ->
            reportedSpam.put(JSONObject().apply {
                put("phoneNumber", entry.phoneNumber)
                put("reportedAtMillis", entry.reportedAtMillis)
                put("reason", entry.reason)
            })
        }
        root.put("reportedSpam", reportedSpam)

        return root
    }

    private suspend fun applyPayloadJson(json: JSONObject): LocalBackupImportResult.Success {
        var callLogCount = 0
        json.optJSONArray("callLog")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                callLogDao.insert(
                    CallLogEntity(
                        phoneNumber = o.getString("phoneNumber"),
                        displayName = if (o.isNull("displayName")) null else o.getString("displayName"),
                        direction = CallDirection.valueOf(o.getString("direction")),
                        timestampMillis = o.getLong("timestampMillis"),
                        durationSeconds = o.optInt("durationSeconds", 0),
                        isSpam = o.optBoolean("isSpam", false)
                    )
                )
                callLogCount++
            }
        }

        var blockedCount = 0
        json.optJSONArray("blockedNumbers")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                blockedNumberDao.block(
                    BlockedNumberEntity(
                        phoneNumber = o.getString("phoneNumber"),
                        reason = o.optString("reason", "Restored from backup"),
                        addedAtMillis = o.optLong("addedAtMillis", System.currentTimeMillis())
                    )
                )
                blockedCount++
            }
        }

        var notesCount = 0
        json.optJSONArray("notes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                callNoteDao.insert(
                    CallNoteEntity(
                        phoneNumber = o.getString("phoneNumber"),
                        callerLabel = o.optString("callerLabel", ""),
                        text = o.getString("text"),
                        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis())
                    )
                )
                notesCount++
            }
        }

        var simRulesCount = 0
        json.optJSONArray("simRules")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                simRoutingDao.setRule(SimRoutingEntity(o.getString("phoneNumber"), o.getString("preferredSimAccountId")))
                simRulesCount++
            }
        }

        var vibrationRulesCount = 0
        json.optJSONArray("vibrationRules")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                vibrationRuleDao.setRule(VibrationRuleEntity(o.getString("phoneNumber"), o.getString("patternId")))
                vibrationRulesCount++
            }
        }

        var reportedSpamCount = 0
        json.optJSONArray("reportedSpam")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                reportedSpamDao.report(
                    ReportedSpamEntity(
                        phoneNumber = o.getString("phoneNumber"),
                        reportedAtMillis = o.optLong("reportedAtMillis", System.currentTimeMillis()),
                        reason = o.optString("reason", "")
                    )
                )
                reportedSpamCount++
            }
        }

        return LocalBackupImportResult.Success(callLogCount, blockedCount, notesCount, simRulesCount, vibrationRulesCount, reportedSpamCount)
    }
}
