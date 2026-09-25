package com.ashudialer.app.data

data class Contact(
    val id: String,
    val contactId: String = id,
    val displayName: String,
    val phoneNumber: String,
    val numberLabel: String = "Mobile",
    val photoUri: String? = null,
    val isFavorite: Boolean = false
)

data class RecentCall(
    val id: Long,
    val displayName: String,
    val phoneNumber: String,
    val direction: com.ashudialer.app.data.db.CallDirection,
    val timestampMillis: Long,
    val durationSeconds: Int,
    val photoUri: String? = null,
    val callCount: Int = 1,
    val isSpam: Boolean = false,
    // Every underlying call_log row id folded into this grouped entry (see
    // CallLogRepository.groupConsecutive) - `id` alone only ever pointed at
    // the first row in the group, so deleting only by `id` left the other
    // rows of a "(3)"-style grouped entry behind in the database.
    val groupedIds: List<Long> = listOf(id)
)
