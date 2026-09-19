package com.ashudialer.app.data

import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


data class ContactAccount(val name: String, val type: String?, val isDevice: Boolean)


class ContactsRepository(private val context: Context) {


    suspend fun loadAllContacts(): List<Contact> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Contact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
            val labelIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)
            val photoThumbIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
            val photoFullIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            val starredIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.STARRED)


            val seenPairs = HashSet<String>()
            while (cursor.moveToNext()) {
                val contactId = cursor.getString(idIdx) ?: continue
                val number = cursor.getString(numberIdx)?.trim() ?: continue
                if (number.isEmpty()) continue

                val pairKey = "$contactId|${number.filter { it.isDigit() || it == '+' }}"
                if (!seenPairs.add(pairKey)) continue

                val typeLabel = phoneTypeLabel(cursor.getInt(typeIdx), cursor.getString(labelIdx))

                results.add(
                    Contact(
                        id = "$contactId:$number",
                        contactId = contactId,
                        displayName = cursor.getString(nameIdx) ?: "Unknown",
                        phoneNumber = number,
                        numberLabel = typeLabel,


                        photoUri = cursor.getString(photoThumbIdx) ?: cursor.getString(photoFullIdx),
                        isFavorite = cursor.getInt(starredIdx) == 1
                    )
                )
            }
        }
        results
    }

    suspend fun lookupNameForNumber(number: String): Contact? = withContext(Dispatchers.IO) {
        try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            val projection = arrayOf(
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_URI
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val contactId = cursor.getString(0) ?: ""
                    return@withContext Contact(
                        id = "$contactId:$number",
                        contactId = contactId,
                        displayName = cursor.getString(1) ?: number,
                        phoneNumber = number,
                        photoUri = cursor.getString(2)
                    )
                }
            }
            null
        } catch (e: Exception) {
            // This runs unconditionally the instant a call screen appears
            // (InCallActivity, PixelInCallService's notification naming,
            // call screening). If READ_CONTACTS is ever missing (e.g.
            // Android auto-revoked an unused permission) this must fall
            // back to "no local match" rather than crash the whole call UI.
            null
        }
    }


    suspend fun loadEmailForContact(contactId: String): String? = withContext(Dispatchers.IO) {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS)
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            projection,
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return@withContext cursor.getString(0)?.takeIf { it.isNotBlank() }
            }
        }
        null
    }

    private fun phoneTypeLabel(type: Int, customLabel: String?): String = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Main"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "Work Fax"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "Home Fax"
        ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> "Pager"
        ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "Other"
        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> customLabel?.takeIf { it.isNotBlank() } ?: "Other"
        else -> "Mobile"
    }

    private fun labelToPhoneType(label: String): Int = when (label) {
        "Mobile" -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
        "Home" -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
        "Work" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
        else -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
    }


    fun listContactAccounts(): List<ContactAccount> {
        val accounts = mutableListOf<ContactAccount>()
        try {
            val accountManager = android.accounts.AccountManager.get(context)
            val seen = HashSet<String>()
            accountManager.accounts.forEach { account ->
                val name = account.name?.trim().orEmpty()
                val type = account.type?.trim().orEmpty()
                if (name.isNotEmpty() && type.isNotEmpty()) {
                    val key = "$name|$type"
                    if (seen.add(key)) {
                        accounts.add(ContactAccount(name = name, type = type, isDevice = false))
                    }
                }
            }
        } catch (_: Exception) {
            // Keep the local/device option even when account visibility is restricted.
        }
        accounts.add(ContactAccount(name = "Device only", type = null, isDevice = true))
        return accounts.distinctBy { "${it.name}|${it.type}|${it.isDevice}" }
    }

    suspend fun insertContact(
        firstName: String,
        lastName: String,
        phoneNumber: String,
        phoneLabel: String,
        email: String,
        homeAddress: String = "",
        company: String = "",
        notes: String = "",
        account: ContactAccount? = null,
        // Cropped JPEG bytes from AddContactScreen's avatar picker, applied
        // as part of the same ContentProviderOperation batch as the rest of
        // the new contact's fields - one atomic insert rather than an
        // insert-then-separate-updateContactPhoto-call, which would need a
        // second round trip to look up the just-created contact's ID first.
        photoJpegBytes: ByteArray? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val ops = ArrayList<android.content.ContentProviderOperation>()

            ops.add(
                android.content.ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, account?.type)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, if (account?.isDevice == true) null else account?.name)
                    .build()
            )

            ops.add(
                android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName.ifBlank { null })
                    .build()
            )

            ops.add(
                android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, labelToPhoneType(phoneLabel))
                    .build()
            )

            if (email.isNotBlank()) {
                ops.add(
                    android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                        .build()
                )
            }

            if (homeAddress.isNotBlank()) {
                ops.add(
                    android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, homeAddress)
                        .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME)
                        .build()
                )
            }

            if (company.isNotBlank()) {
                ops.add(
                    android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, company)
                        .build()
                )
            }

            if (notes.isNotBlank()) {
                ops.add(
                    android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Note.NOTE, notes)
                        .build()
                )
            }

            if (photoJpegBytes != null) {
                ops.add(
                    android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoJpegBytes)
                        .build()
                )
            }

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            false
        }
    }


    suspend fun deleteContacts(contactIds: Set<String>): Boolean = withContext(Dispatchers.IO) {
        if (contactIds.isEmpty()) return@withContext true
        try {
            val ops = ArrayList<android.content.ContentProviderOperation>()
            for (contactId in contactIds) {
                ops.add(
                    android.content.ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                        .withSelection(
                            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                            arrayOf(contactId)
                        )
                        .build()
                )
            }
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            false
        }
    }


    suspend fun updateContactPhoto(contactId: String, jpegBytes: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val existingDataId = context.contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(ContactsContract.Data._ID),
                    "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(contactId, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE),
                    null
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

                val values = android.content.ContentValues().apply {
                    put(ContactsContract.CommonDataKinds.Photo.PHOTO, jpegBytes)
                }

                if (existingDataId != null) {
                    context.contentResolver.update(
                        ContactsContract.Data.CONTENT_URI,
                        values,
                        "${ContactsContract.Data._ID} = ?",
                        arrayOf(existingDataId.toString())
                    )
                } else {
                    val rawContactId = context.contentResolver.query(
                        ContactsContract.RawContacts.CONTENT_URI,
                        arrayOf(ContactsContract.RawContacts._ID),
                        "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                        arrayOf(contactId),
                        null
                    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
                        ?: return@withContext false

                    values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    values.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                    context.contentResolver.insert(ContactsContract.Data.CONTENT_URI, values)
                }
                true
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Writes the chosen ringtone URI (or null to clear it back to default)
     * to Contacts.CUSTOM_RINGTONE. This is the standard Android field for a
     * per-contact ringtone - the system's own Telecom/ringing machinery
     * automatically picks it up and plays it for calls from this contact,
     * so nothing else in this app needs to manage ringtone audio playback
     * itself during an incoming call.
     */
    suspend fun updateContactRingtone(contactId: String, ringtoneUri: String?): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val values = android.content.ContentValues().apply {
                    put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtoneUri)
                }
                val rows = context.contentResolver.update(
                    ContactsContract.Contacts.CONTENT_URI,
                    values,
                    "${ContactsContract.Contacts._ID} = ?",
                    arrayOf(contactId)
                )
                rows > 0
            } catch (e: Exception) {
                false
            }
        }

    /**
     * The currently set custom ringtone for a contact, or null if it's using
     * the device default.
     */
    suspend fun getContactRingtone(contactId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts.CUSTOM_RINGTONE),
                    "${ContactsContract.Contacts._ID} = ?",
                    arrayOf(contactId),
                    null
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            } catch (e: Exception) {
                null
            }
        }


    suspend fun setContactFavorite(contactId: String, favorite: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val contactIdLong = contactId.toLongOrNull() ?: return@withContext false
                val uri = android.content.ContentUris.withAppendedId(
                    ContactsContract.Contacts.CONTENT_URI,
                    contactIdLong
                )
                val values = android.content.ContentValues().apply {
                    put(ContactsContract.Contacts.STARRED, if (favorite) 1 else 0)
                }
                context.contentResolver.update(uri, values, null, null) > 0
            } catch (e: Exception) {
                false
            }
        }
}
