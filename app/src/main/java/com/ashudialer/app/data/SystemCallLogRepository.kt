package com.ashudialer.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.ashudialer.app.data.db.CallDirection
import com.ashudialer.app.util.phoneNumbersMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SystemCallLogEntry(
    val phoneNumber: String,
    val displayName: String?,
    val direction: CallDirection,
    val timestampMillis: Long,
    val durationSeconds: Int,
    val photoUri: String?,


    val isCallable: Boolean = true
)


class SystemCallLogRepository(private val context: Context) {

    /**
     * The crash this guards against: every method below eventually calls
     * contentResolver.query/insert/delete on CallLog.Calls.CONTENT_URI,
     * which throws a SecurityException (not a caught Exception - it kills
     * the process) if READ_CALL_LOG/WRITE_CALL_LOG isn't granted yet.
     * hasCallLogPermission() lets each caller bail out to a safe empty/
     * false result instead of crashing, which matters because the UI can
     * legitimately call these functions (e.g. via LaunchedEffect keyed on
     * hasPermissions) in the brief window right around a permission grant,
     * before the granted state has propagated everywhere that reads it.
     */
    private fun hasCallLogPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

    suspend fun loadRecentHistory(limit: Int = 300): List<SystemCallLogEntry> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<SystemCallLogEntry>()
            if (!hasCallLogPermission()) return@withContext results

            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.CACHED_PHOTO_URI,
                CallLog.Calls.NUMBER_PRESENTATION
            )

            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durationIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val photoIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_PHOTO_URI)
                val presentationIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER_PRESENTATION)

                var loaded = 0
                while (loaded < limit && cursor.moveToNext()) {
                    val rawNumber = cursor.getString(numberIdx)
                    val presentation = if (presentationIdx >= 0) cursor.getInt(presentationIdx) else CallLog.Calls.PRESENTATION_ALLOWED
                    val resolved = resolveNumber(cursor.getString(numberIdx), presentation)

                    results.add(
                        SystemCallLogEntry(
                            phoneNumber = resolved.number,
                            displayName = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() } ?: resolved.fallbackLabel,
                            direction = mapCallType(cursor.getInt(typeIdx)),
                            timestampMillis = cursor.getLong(dateIdx),
                            durationSeconds = cursor.getInt(durationIdx),
                            photoUri = cursor.getString(photoIdx),
                            isCallable = resolved.isCallable
                        )
                    )
                    loaded++
                }
            }
            results
        }

    private data class ResolvedNumber(val number: String, val fallbackLabel: String?, val isCallable: Boolean)


    private fun resolveNumber(rawNumber: String?, presentation: Int): ResolvedNumber {
        val specialLabel = when (presentation) {
            CallLog.Calls.PRESENTATION_RESTRICTED -> "Private number"
            CallLog.Calls.PRESENTATION_UNKNOWN -> "Unknown number"
            CallLog.Calls.PRESENTATION_PAYPHONE -> "Payphone"
            else -> null
        }
        if (specialLabel != null) return ResolvedNumber(number = "", fallbackLabel = specialLabel, isCallable = false)
        if (rawNumber.isNullOrBlank()) return ResolvedNumber(number = "", fallbackLabel = "Unknown number", isCallable = false)
        return ResolvedNumber(number = rawNumber, fallbackLabel = null, isCallable = true)
    }


    suspend fun loadHistoryForNumber(phoneNumber: String, limit: Int = 200): List<SystemCallLogEntry> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<SystemCallLogEntry>()
            if (!hasCallLogPermission()) return@withContext results
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.CACHED_PHOTO_URI,
                CallLog.Calls.NUMBER_PRESENTATION
            )
            // CallLog stores numbers in whatever representation the modem/SIM
            // supplied. A contact may use +91xxxxxxxxxx while CallLog stores
            // 0xxxxxxxxxx (or formatting with spaces/dashes), so an exact
            // SQL equality misses real history. Scan the ordered call log and
            // compare with the same normalized matcher used by Contacts/Recents.
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durationIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val photoIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_PHOTO_URI)
                val presentationIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER_PRESENTATION)

                var loaded = 0
                while (loaded < limit && cursor.moveToNext()) {
                    val rawNumber = cursor.getString(numberIdx)
                    val presentation = if (presentationIdx >= 0) cursor.getInt(presentationIdx) else CallLog.Calls.PRESENTATION_ALLOWED
                    val resolved = resolveNumber(rawNumber, presentation)
                    if (resolved.isCallable && !phoneNumbersMatch(phoneNumber, resolved.number)) continue
                    if (!resolved.isCallable && phoneNumber.isNotBlank()) continue
                    results.add(
                        SystemCallLogEntry(
                            phoneNumber = resolved.number.ifEmpty { phoneNumber },
                            displayName = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() } ?: resolved.fallbackLabel,
                            direction = mapCallType(cursor.getInt(typeIdx)),
                            timestampMillis = cursor.getLong(dateIdx),
                            durationSeconds = cursor.getInt(durationIdx),
                            photoUri = cursor.getString(photoIdx)
                        )
                    )
                    loaded++
                }
            }
            results
        }

    private fun mapCallType(systemType: Int): CallDirection = when (systemType) {
        CallLog.Calls.INCOMING_TYPE -> CallDirection.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallDirection.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallDirection.REJECTED
        CallLog.Calls.BLOCKED_TYPE -> CallDirection.REJECTED
        else -> CallDirection.OUTGOING
    }


    suspend fun deleteHistoryForNumber(phoneNumber: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasCallLogPermission()) return@withContext false
        try {
            val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE)
            val matches = mutableListOf<Pair<String, Long>>()
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, projection, null, null, null
            )?.use { cursor ->
                val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                while (cursor.moveToNext()) {
                    val raw = cursor.getString(numberIdx) ?: continue
                    if (phoneNumbersMatch(phoneNumber, raw)) matches += raw to cursor.getLong(dateIdx)
                }
            }
            matches.forEach { (raw, date) ->
                context.contentResolver.delete(
                    CallLog.Calls.CONTENT_URI,
                    "${CallLog.Calls.NUMBER} = ? AND ${CallLog.Calls.DATE} = ?",
                    arrayOf(raw, date.toString())
                )
            }
            true
        } catch (e: Exception) {
            false
        }
    }


    suspend fun deleteEntryAt(phoneNumber: String, timestampMillis: Long): Boolean = withContext(Dispatchers.IO) {
        if (!hasCallLogPermission()) return@withContext false
        try {
            val matches = mutableListOf<Pair<String, Long>>()
            val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE)
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, projection,
                "${CallLog.Calls.DATE} = ?", arrayOf(timestampMillis.toString()), null
            )?.use { cursor ->
                val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                while (cursor.moveToNext()) {
                    val raw = cursor.getString(numberIdx) ?: continue
                    if (phoneNumbersMatch(phoneNumber, raw)) matches += raw to cursor.getLong(dateIdx)
                }
            }
            var deleted = 0
            matches.forEach { (raw, date) ->
                deleted += context.contentResolver.delete(
                    CallLog.Calls.CONTENT_URI,
                    "${CallLog.Calls.NUMBER} = ? AND ${CallLog.Calls.DATE} = ?",
                    arrayOf(raw, date.toString())
                )
            }
            deleted > 0
        } catch (e: Exception) {
            false
        }
    }


    suspend fun deleteAllHistory(): Boolean = withContext(Dispatchers.IO) {
        if (!hasCallLogPermission()) return@withContext false
        try {
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }


    suspend fun exportToCsv(): String = withContext(Dispatchers.IO) {
        val entries = loadRecentHistory(limit = Int.MAX_VALUE)
        val builder = StringBuilder()
        builder.append("number,name,direction,timestamp_millis,duration_seconds\n")
        for (entry in entries) {
            builder.append(csvField(entry.phoneNumber)).append(',')
            builder.append(csvField(entry.displayName ?: "")).append(',')
            builder.append(csvField(entry.direction.name)).append(',')
            builder.append(entry.timestampMillis).append(',')
            builder.append(entry.durationSeconds).append('\n')
        }
        builder.toString()
    }

    private fun csvField(value: String): String {
        val needsQuoting = value.contains(',') || value.contains('"') || value.contains('\n')
        return if (needsQuoting) "\"${value.replace("\"", "\"\"")}\"" else value
    }


    suspend fun importFromCsv(csv: String): Int = withContext(Dispatchers.IO) {
        var imported = 0
        if (!hasCallLogPermission()) return@withContext imported
        val lines = csv.split("\n").drop(1)
        for (line in lines) {
            if (line.isBlank()) continue
            try {
                val fields = parseCsvLine(line)
                if (fields.size < 5) continue
                val (number, name, directionName, timestampStr, durationStr) = fields
                val direction = try {
                    CallDirection.valueOf(directionName)
                } catch (e: IllegalArgumentException) {
                    CallDirection.OUTGOING
                }
                val timestampMillis = timestampStr.toLongOrNull() ?: continue
                val values = android.content.ContentValues().apply {
                    put(CallLog.Calls.NUMBER, number)
                    if (name.isNotBlank()) put(CallLog.Calls.CACHED_NAME, name)
                    put(CallLog.Calls.TYPE, direction.toSystemType())
                    put(CallLog.Calls.DATE, timestampMillis)
                    put(CallLog.Calls.DURATION, durationStr.toIntOrNull() ?: 0)
                }
                context.contentResolver.insert(CallLog.Calls.CONTENT_URI, values)
                imported++
            } catch (e: Exception) {

            }
        }
        imported
    }

    private fun CallDirection.toSystemType(): Int = when (this) {
        CallDirection.INCOMING -> CallLog.Calls.INCOMING_TYPE
        CallDirection.OUTGOING -> CallLog.Calls.OUTGOING_TYPE
        CallDirection.MISSED -> CallLog.Calls.MISSED_TYPE
        CallDirection.REJECTED -> CallLog.Calls.REJECTED_TYPE
    }


    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }
}
