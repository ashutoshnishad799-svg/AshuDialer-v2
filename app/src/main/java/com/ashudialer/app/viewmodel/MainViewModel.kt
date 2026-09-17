package com.ashudialer.app.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ashudialer.app.data.AppSettingsRepository
import com.ashudialer.app.data.AuthRepository
import com.ashudialer.app.data.BackupResult
import com.ashudialer.app.data.CallLogRepository
import com.ashudialer.app.data.CallNoteRepository
import com.ashudialer.app.data.CloudBackupRepository
import com.ashudialer.app.data.Contact
import com.ashudialer.app.data.ContactsRepository
import com.ashudialer.app.util.phoneNumbersMatch
import com.ashudialer.app.data.RecentCall
import com.ashudialer.app.data.SignInResult
import com.ashudialer.app.data.SignedInUser
import com.ashudialer.app.data.SystemCallLogEntry
import com.ashudialer.app.data.SystemCallLogRepository
import com.ashudialer.app.data.ThemePreference
import com.ashudialer.app.data.db.BlockedNumberEntity
import com.ashudialer.app.data.db.CallLogEntity
import com.ashudialer.app.data.db.CallNoteEntity
import com.ashudialer.app.ui.screens.BackupState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val callLogRepository: CallLogRepository,
    private val systemCallLogRepository: SystemCallLogRepository,
    private val contactsRepository: ContactsRepository,
    private val themePreference: ThemePreference,
    private val appSettingsRepository: AppSettingsRepository,
    private val authRepository: AuthRepository,
    private val cloudBackupRepository: CloudBackupRepository,
    private val blockedNumberDao: com.ashudialer.app.data.db.BlockedNumberDao,
    private val callNoteRepository: CallNoteRepository,
    private val simRoutingDao: com.ashudialer.app.data.db.SimRoutingDao,
    private val vibrationRuleDao: com.ashudialer.app.data.db.VibrationRuleDao,
    private val localBackupRepository: com.ashudialer.app.data.LocalBackupRepository,
    private val videoCallSignalingRepository: com.ashudialer.app.data.VideoCallSignalingRepository,
    private val quietHoursRepository: com.ashudialer.app.data.QuietHoursRepository,
    private val callInsightsRepository: com.ashudialer.app.data.CallInsightsRepository,
    private val privateSpaceRepository: com.ashudialer.app.data.PrivateSpaceRepository,
    private val localAuthRepository: com.ashudialer.app.data.LocalAuthRepository,
    private val callbackReminderRepository: com.ashudialer.app.data.CallbackReminderRepository
) : ViewModel() {
    val themeId: StateFlow<String> = themePreference.themeIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "gradient")

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    // A saved contact's name always wins over whatever CallLog.Calls
    // .CACHED_NAME the system stored for a call - CACHED_NAME reflects
    // whatever Caller-Name-Presentation (CNAP) info the carrier/SIM sent
    // at call time, and Android caches that on the call log row itself.
    // Without this, Recents could show a completely different name than
    // Contacts for the exact same number - the carrier's registered name
    // for the SIM, not the name actually saved in this app - since nothing
    // upstream (SystemCallLogRepository, CallLogRepository) ever cross-
    // referenced the saved contacts list before now. This combine() is
    // applied at read time rather than by rewriting stored CallLogEntity
    // rows, so it stays correct automatically as contacts are added, 
    // renamed, or removed, with no re-sync needed.
    private fun withSavedContactNames(calls: List<RecentCall>, contactList: List<Contact>): List<RecentCall> {
        if (contactList.isEmpty()) return calls
        return calls.map { call ->
            val match = contactList.firstOrNull { phoneNumbersMatch(it.phoneNumber, call.phoneNumber) }
            if (match != null && match.displayName.isNotBlank() && match.displayName != call.displayName) {
                call.copy(displayName = match.displayName)
            } else {
                call
            }
        }
    }

    val recents: StateFlow<List<RecentCall>> = combine(
        callLogRepository.observeGroupedRecents(),
        contacts,
        privateSpaceRepository.lockedNumbers,
        privateSpaceRepository.hideLockedCallHistoryFromRecents
    ) { calls, contactList, locked, hideLocked ->
        val named = withSavedContactNames(calls, contactList)
        if (!hideLocked || locked.isEmpty()) {
            named
        } else {
            // Private Space's own call history (privateSpaceCallHistory()
            // below) reads from the same underlying call log independently
            // of this filter, so a locked number's calls are never lost -
            // they just stop appearing in the *main* Recents list once this
            // toggle is on, exactly like a locked contact disappearing from
            // the main Contacts list below.
            named.filterNot { recent -> locked.any { com.ashudialer.app.util.phoneNumbersMatch(it.phoneNumber, recent.phoneNumber) } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val missedOnly: StateFlow<List<RecentCall>> = combine(
        callLogRepository.observeMissed(),
        contacts,
        privateSpaceRepository.lockedNumbers,
        privateSpaceRepository.hideLockedCallHistoryFromRecents
    ) { calls, contactList, locked, hideLocked ->
        val named = withSavedContactNames(calls, contactList)
        if (!hideLocked || locked.isEmpty()) {
            named
        } else {
            named.filterNot { recent -> locked.any { com.ashudialer.app.util.phoneNumbersMatch(it.phoneNumber, recent.phoneNumber) } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    // Combines both sign-in sources into the single user AccountScreen
    // renders. Google sign-in is preferred when both somehow exist (it's
    // the fuller-featured path - cloud backup depends on it) but in
    // practice only one will ever be active at a time, since the person
    // picks one path from SignedOutContent's two buttons.
    val currentUser: StateFlow<SignedInUser?> = kotlinx.coroutines.flow.combine(
        authRepository.currentUser,
        localAuthRepository.currentUser
    ) { googleUser, localUser -> googleUser ?: localUser }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun registerLocalAccount(email: String, password: String, displayName: String): com.ashudialer.app.data.LocalAuthResult =
        localAuthRepository.register(email, password, displayName)

    suspend fun signInLocalAccount(email: String, password: String): com.ashudialer.app.data.LocalAuthResult =
        localAuthRepository.signIn(email, password)

    suspend fun isLocalAccountRegistered(): Boolean = localAuthRepository.isRegistered()

    val settings = appSettingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.ashudialer.app.data.AppSettings())

    private val _backupState = MutableStateFlow(BackupState.IDLE)
    val backupState: StateFlow<BackupState> = _backupState

    private val _lastBackedUpAtMillis = MutableStateFlow(0L)
    val lastBackedUpAtMillis: StateFlow<Long> = _lastBackedUpAtMillis

    val blockedNumbers: StateFlow<List<BlockedNumberEntity>> = blockedNumberDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callNotes: StateFlow<List<CallNoteEntity>> = callNoteRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingCallbackReminders: StateFlow<List<com.ashudialer.app.data.db.CallbackReminderEntity>> =
        callbackReminderRepository.observePending()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun isCallbackReminderSet(phoneNumber: String): Boolean =
        callbackReminderRepository.isReminderSet(phoneNumber)

    fun setCallbackReminder(phoneNumber: String, displayName: String, triggerAtMillis: Long) {
        viewModelScope.launch { callbackReminderRepository.setReminder(phoneNumber, displayName, triggerAtMillis) }
    }

    fun cancelCallbackReminder(reminder: com.ashudialer.app.data.db.CallbackReminderEntity) {
        viewModelScope.launch { callbackReminderRepository.cancelReminder(reminder) }
    }

    val quietHoursSchedule: StateFlow<com.ashudialer.app.data.db.QuietHoursEntity> = quietHoursRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.ashudialer.app.data.db.QuietHoursEntity())

    fun saveQuietHoursSchedule(schedule: com.ashudialer.app.data.db.QuietHoursEntity) {
        viewModelScope.launch { quietHoursRepository.save(schedule) }
    }

    val privateSpaceIsSetUp: StateFlow<Boolean> = privateSpaceRepository.isSetUp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lockedNumbers: StateFlow<List<com.ashudialer.app.data.db.LockedNumberEntity>> = privateSpaceRepository.lockedNumbers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Both default false (see PrivateSpaceRepository's comment) so
    // isolation is opt-in from inside Private Space's own settings, never
    // silently on.
    val hideLockedCallHistoryFromRecents: StateFlow<Boolean> = privateSpaceRepository.hideLockedCallHistoryFromRecents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hideLockedContactsFromContactsList: StateFlow<Boolean> = privateSpaceRepository.hideLockedContactsFromContactsList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // The main Contacts screen should read from THIS, not `contacts`
    // directly. `contacts` itself (the raw _contacts StateFlow) stays
    // unfiltered on purpose: `recents`/`missedOnly` above still need it to
    // resolve a saved contact's name onto a call row (withSavedContactNames)
    // even for a locked number whose *contact card* is hidden but whose
    // *calls* aren't (the two toggles are independent) - filtering the
    // shared `contacts` list itself would have silently broken that
    // name-matching for anyone with only hideLockedContactsFromContactsList
    // turned on.
    val visibleContacts: StateFlow<List<Contact>> = combine(
        contacts,
        privateSpaceRepository.lockedNumbers,
        privateSpaceRepository.hideLockedContactsFromContactsList
    ) { contactList, locked, hideLocked ->
        if (!hideLocked || locked.isEmpty()) {
            contactList
        } else {
            // Private Space's own contact list (privateSpaceContacts()
            // below) reads the same underlying contactsRepository
            // independently, so a locked contact is never lost - it just
            // stops appearing in the *main* Contacts list once this toggle
            // is on.
            contactList.filterNot { c -> locked.any { com.ashudialer.app.util.phoneNumbersMatch(it.phoneNumber, c.phoneNumber) } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Private Space's own contacts view - the locked-contact counterpart to
     * privateSpaceCallHistory() below. A suspend snapshot rather than a Flow
     * since, like that function, it's read on entering Private Space's
     * screens rather than needing live push updates.
     */
    suspend fun privateSpaceContacts(): List<Contact> {
        val locked = privateSpaceRepository.getLockedNumbersSnapshot().map { it.phoneNumber }
        if (locked.isEmpty()) return emptyList()
        val allContacts = contacts.value.ifEmpty { contactsRepository.loadAllContacts() }
        return allContacts.filter { c -> locked.any { com.ashudialer.app.util.phoneNumbersMatch(it, c.phoneNumber) } }
    }

    fun setHideLockedCallHistoryFromRecents(enabled: Boolean) {
        privateSpaceRepository.setHideLockedCallHistoryFromRecents(enabled)
    }

    fun setHideLockedContactsFromContactsList(enabled: Boolean) {
        privateSpaceRepository.setHideLockedContactsFromContactsList(enabled)
    }

    suspend fun setupPrivateSpace(password: String): com.ashudialer.app.data.PrivateSpaceSetupResult =
        privateSpaceRepository.setup(password)

    suspend fun verifyPrivateSpacePassword(attempt: String): Boolean =
        privateSpaceRepository.verifyPassword(attempt)

    suspend fun resetPrivateSpaceWithBackupCode(backupCode: String, newPassword: String): com.ashudialer.app.data.PrivateSpaceResetResult =
        privateSpaceRepository.resetWithBackupCode(backupCode, newPassword)

    fun wipePrivateSpace(context: android.content.Context) {
        viewModelScope.launch {
            privateSpaceRepository.resetEverything()
            // Recordings live in the filesystem, not the DB tables
            // resetEverything() clears - without this they'd be orphaned
            // files nobody could ever list or delete again, since the UI
            // path to them (Private Space's Recordings tab) only exists
            // once isSetUp is true again.
            com.ashudialer.app.telecom.CallRecorder.wipeAllPrivateSpaceRecordings(context)
        }
    }

    suspend fun changePrivateSpacePassword(currentPassword: String, newPassword: String): com.ashudialer.app.data.PrivateSpaceResetResult =
        privateSpaceRepository.changePassword(currentPassword, newPassword)

    fun lockNumber(phoneNumber: String, label: String = "") {
        viewModelScope.launch { privateSpaceRepository.lockNumber(phoneNumber, label) }
    }

    fun unlockNumber(phoneNumber: String) {
        viewModelScope.launch { privateSpaceRepository.unlockNumber(phoneNumber) }
    }

    suspend fun isNumberLocked(phoneNumber: String): Boolean =
        privateSpaceRepository.isNumberLocked(phoneNumber)

    /**
     * Snapshot of every locked-number's recent calls, pulled from the same
     * call log Recents already reads (callLogRepository) and filtered down
     * to just the protected numbers - Private Space's "call history" is a
     * view over the real call log, not a separate duplicated record of
     * calls.
     */
    suspend fun privateSpaceCallHistory(): List<RecentCall> {
        val locked = privateSpaceRepository.getLockedNumbersSnapshot().map { it.phoneNumber }
        if (locked.isEmpty()) return emptyList()
        val allRecents = recents.value.ifEmpty { callLogRepository.observeGroupedRecents().first() }
        return allRecents.filter { recent ->
            locked.any { com.ashudialer.app.util.phoneNumbersMatch(it, recent.phoneNumber) }
        }
    }

    /**
     * Private Space's own recordings list - files that have been moved out
     * of public storage into the app-private folder (see
     * CallRecorder.moveToPrivateSpace). Kept as its own suspend read rather
     * than a Flow since, like the public recordings list in AshuDialerApp's
     * caller, nothing pushes change notifications for filesystem writes -
     * the caller re-queries after a move/delete completes.
     */
    fun privateSpaceRecordings(context: android.content.Context): List<java.io.File> =
        com.ashudialer.app.telecom.CallRecorder.listPrivateSpaceRecordings(context)

    fun moveRecordingsToPrivateSpace(context: android.content.Context, files: List<java.io.File>, onDone: () -> Unit) {
        viewModelScope.launch {
            files.forEach { file ->
                com.ashudialer.app.telecom.CallRecorder.moveToPrivateSpace(context, file)
            }
            onDone()
        }
    }

    fun moveRecordingOutOfPrivateSpace(context: android.content.Context, file: java.io.File, onDone: () -> Unit) {
        viewModelScope.launch {
            com.ashudialer.app.telecom.CallRecorder.moveOutOfPrivateSpace(context, file)
            onDone()
        }
    }

    private val _callInsights = MutableStateFlow<com.ashudialer.app.data.CallInsights?>(null)
    val callInsights: StateFlow<com.ashudialer.app.data.CallInsights?> = _callInsights

    fun loadCallInsights(period: com.ashudialer.app.data.InsightsPeriod) {
        viewModelScope.launch {
            _callInsights.value = null // show loading state while recomputing for the new period
            _callInsights.value = callInsightsRepository.computeInsights(period)
        }
    }

    fun deleteNote(note: CallNoteEntity) {
        viewModelScope.launch { callNoteRepository.deleteNote(note) }
    }


    fun notesForNumber(phoneNumber: String) = callNoteRepository.observeForNumber(phoneNumber)


    fun saveContactNote(phoneNumber: String, callerLabel: String, text: String, existingNotes: List<CallNoteEntity>) {
        viewModelScope.launch {
            val latest = existingNotes.filter { it.phoneNumber == phoneNumber }.maxByOrNull { it.createdAtMillis }
            if (latest != null) {
                callNoteRepository.updateNote(latest, text)
            } else if (text.isNotBlank()) {
                callNoteRepository.addNote(phoneNumber, callerLabel, text)
            }
        }
    }


    suspend fun loadCallHistoryForNumber(phoneNumber: String) = systemCallLogRepository.loadHistoryForNumber(phoneNumber)


    suspend fun loadEmailForContact(contactId: String) = contactsRepository.loadEmailForContact(contactId)


    fun deleteCallHistoryForNumber(phoneNumber: String) {
        viewModelScope.launch {
            val success = systemCallLogRepository.deleteHistoryForNumber(phoneNumber)
            callLogRepository.deleteLocalHistoryForNumber(phoneNumber)
            if (success) {
                callLogRepository.syncFromSystem(systemCallLogRepository)
            }
        }
    }

    /**
     * Deletes a specific multi-selected set of Recents entries by their exact
     * underlying row ids. Deliberately local-only (Room), not also reaching
     * into the system call log the way deleteCallHistoryForNumber does above:
     * that existing action deletes *all* history for one number by design,
     * but a multi-select here can be a partial pick within a number's calls,
     * and system deletion is only available per-number - doing that here too
     * risks silently deleting more of the person's system call history than
     * they actually selected.
     */
    fun deleteRecentEntries(entries: List<com.ashudialer.app.data.RecentCall>) {
        viewModelScope.launch {
            val allIds = entries.flatMap { it.groupedIds }.toSet()
            callLogRepository.deleteEntriesByIds(allIds)
        }
    }


    fun deleteSingleCallHistoryEntry(entry: SystemCallLogEntry) {
        viewModelScope.launch {
            systemCallLogRepository.deleteEntryAt(entry.phoneNumber, entry.timestampMillis)


            callLogRepository.deleteLocalHistoryForNumber(entry.phoneNumber)
            callLogRepository.syncFromSystem(systemCallLogRepository)
        }
    }


    fun toggleContactFavorite(contactId: String, currentlyFavorite: Boolean) {
        viewModelScope.launch {
            val success = contactsRepository.setContactFavorite(contactId, favorite = !currentlyFavorite)
            if (success) {
                loadContacts()
            }
        }
    }


    fun updateContactPhoto(contactId: String, jpegBytes: ByteArray) {
        viewModelScope.launch {
            contactsRepository.updateContactPhoto(contactId, jpegBytes)
            loadContacts()
        }
    }

    suspend fun loadRingtoneForContact(contactId: String) = contactsRepository.getContactRingtone(contactId)

    fun setContactRingtone(contactId: String, ringtoneUri: String?, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            contactsRepository.updateContactRingtone(contactId, ringtoneUri)
            onDone()
        }
    }

    val simRoutingRules: StateFlow<List<com.ashudialer.app.data.db.SimRoutingEntity>> = simRoutingDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSimRoutingRule(phoneNumber: String, simAccountId: String) {
        viewModelScope.launch {
            simRoutingDao.setRule(com.ashudialer.app.data.db.SimRoutingEntity(phoneNumber, simAccountId))
        }
    }

    fun removeSimRoutingRule(phoneNumber: String) {
        viewModelScope.launch { simRoutingDao.clearRule(phoneNumber) }
    }

    val vibrationRules: StateFlow<List<com.ashudialer.app.data.db.VibrationRuleEntity>> = vibrationRuleDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setVibrationRule(phoneNumber: String, patternId: String) {
        viewModelScope.launch {
            vibrationRuleDao.setRule(com.ashudialer.app.data.db.VibrationRuleEntity(phoneNumber, patternId))
        }
    }

    fun removeVibrationRule(phoneNumber: String) {
        viewModelScope.launch { vibrationRuleDao.clearRule(phoneNumber) }
    }


    private val _localBackupBusy = MutableStateFlow(false)
    val localBackupBusy: StateFlow<Boolean> = _localBackupBusy

    private val _localBackupStatus = MutableStateFlow<String?>(null)
    val localBackupStatus: StateFlow<String?> = _localBackupStatus


    private var pendingExportBytes: ByteArray? = null
    fun takePendingExportBytes(): ByteArray? = pendingExportBytes.also { pendingExportBytes = null }

    fun exportLocalBackup(pin: String, onReadyToSave: () -> Unit) {
        viewModelScope.launch {
            _localBackupBusy.value = true
            when (val result = localBackupRepository.export(pin)) {
                is com.ashudialer.app.data.LocalBackupExportResult.Success -> {
                    pendingExportBytes = result.bytes
                    _localBackupBusy.value = false
                    onReadyToSave()
                }
                is com.ashudialer.app.data.LocalBackupExportResult.Failure -> {
                    _localBackupBusy.value = false
                    _localBackupStatus.value = "Export failed: ${result.reason}"
                }
            }
        }
    }

    fun importLocalBackup(fileBytes: ByteArray, pin: String) {
        viewModelScope.launch {
            _localBackupBusy.value = true
            _localBackupStatus.value = null
            val result = localBackupRepository.import(fileBytes, pin)
            _localBackupBusy.value = false
            _localBackupStatus.value = when (result) {
                is com.ashudialer.app.data.LocalBackupImportResult.Success ->
                    "Restored ${result.callLogCount} calls, ${result.notesCount} notes, ${result.blockedCount} blocked numbers, " +
                        "${result.simRulesCount} SIM rules, and ${result.vibrationRulesCount} vibration patterns."
                com.ashudialer.app.data.LocalBackupImportResult.WrongPin -> "That PIN doesn't match this backup file."
                com.ashudialer.app.data.LocalBackupImportResult.NotAValidBackupFile -> "That doesn't look like an AshuPhone backup file."
                is com.ashudialer.app.data.LocalBackupImportResult.Failure -> "Restore failed: ${result.reason}"
            }
            if (result is com.ashudialer.app.data.LocalBackupImportResult.Success) {
                loadContacts()
                syncCallHistory()
            }
        }
    }

    fun clearLocalBackupStatus() {
        _localBackupStatus.value = null
    }


    fun setLocalBackupStatus(message: String) {
        _localBackupStatus.value = message
    }

    fun blockNumber(number: String) {
        if (number.isBlank()) return
        viewModelScope.launch { blockedNumberDao.block(BlockedNumberEntity(phoneNumber = number)) }
    }

    fun unblockNumber(entry: BlockedNumberEntity) {
        viewModelScope.launch { blockedNumberDao.unblock(entry) }
    }

    fun setTheme(id: String) {
        viewModelScope.launch {
            themePreference.setTheme(id)
        }
    }

    fun loadContacts() {
        viewModelScope.launch {
            _contacts.value = contactsRepository.loadAllContacts()
        }
    }


    fun saveNewContact(input: com.ashudialer.app.ui.screens.NewContactInput, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = contactsRepository.insertContact(
                firstName = input.firstName,
                lastName = input.lastName,
                phoneNumber = input.phoneNumber,
                phoneLabel = input.phoneLabel,
                email = input.email,
                homeAddress = input.homeAddress,
                company = input.company,
                notes = input.notes,
                account = input.account,
                photoJpegBytes = input.photoJpegBytes
            )
            if (success) loadContacts()
            onDone(success)
        }
    }


    fun listContactAccounts() = contactsRepository.listContactAccounts()


    fun deleteContacts(contactIds: Set<String>) {
        viewModelScope.launch {
            val success = contactsRepository.deleteContacts(contactIds)
            if (success) loadContacts()
        }
    }


    fun syncCallHistory() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                callLogRepository.syncFromSystem(systemCallLogRepository)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun clearCallHistory() {
        viewModelScope.launch {
            callLogRepository.clearHistory()


            systemCallLogRepository.deleteAllHistory()
        }
    }

    fun setCallRecordingEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setCallRecordingEnabled(enabled) }
    }

    fun setAutoRecordAll(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setAutoRecordAll(enabled) }
    }

    fun setAnnounceRecording(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setAnnounceRecording(enabled) }
    }

    fun setLedFlashForAlerts(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setLedFlashForAlerts(enabled) }
    }

    fun setVibrateOnButtonPress(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setVibrateOnButtonPress(enabled) }
    }

    fun setAlwaysFullScreenIncoming(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setAlwaysFullScreenIncoming(enabled) }
    }

    fun setKeepCallsInNotifications(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setKeepCallsInNotifications(enabled) }
    }

    fun setBackEndsCall(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setBackEndsCall(enabled) }
    }

    fun setDisableProximitySensor(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setDisableProximitySensor(enabled) }
    }

    fun setShowContactThumbnails(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setShowContactThumbnails(enabled) }
    }

    fun setShowPhoneNumbers(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setShowPhoneNumbers(enabled) }
    }

    fun setUseRelativeDate(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setUseRelativeDate(enabled) }
    }

    fun setShowSearchBar(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setShowSearchBar(enabled) }
    }

    fun setFontSizeIndex(index: Int) {
        viewModelScope.launch { appSettingsRepository.setFontSizeIndex(index) }
    }

    fun setIncomingCallStyle(style: String) {
        viewModelScope.launch { appSettingsRepository.setIncomingCallStyle(style) }
    }


    suspend fun exportCallHistoryCsv(): String = systemCallLogRepository.exportToCsv()


    suspend fun importCallHistoryCsv(csv: String): Int {
        val count = systemCallLogRepository.importFromCsv(csv)
        if (count > 0) {
            callLogRepository.syncFromSystem(systemCallLogRepository)
        }
        return count
    }


    fun signInIntent(): Intent? = authRepository.signInIntent()
    fun signInDiagnosisMessage(): String? = authRepository.diagnosisMessage()

    fun handleSignInResult(data: Intent?, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val result = authRepository.handleSignInResult(data)) {
                is SignInResult.Success -> {


                    val savedNumber = appSettingsRepository.settingsFlow.first().myPhoneNumber
                    if (savedNumber.isNotBlank()) {
                        videoCallSignalingRepository.publishPhoneDirectoryEntry(result.user.uid, savedNumber)
                    }
                    onDone(true, null)
                }
                is SignInResult.Failure -> onDone(false, result.message)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            // Whichever path is actually signed in gets signed out - a
            // local-account person tapping "Sign out" should not silently
            // no-op just because this code defaulted to the Google path.
            if (authRepository.isSignedIn()) {
                authRepository.signOut()
            } else {
                localAuthRepository.signOut()
            }
            appSettingsRepository.setCloudBackupEnabled(false)
        }
    }

    fun deleteAccount(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (authRepository.isSignedIn()) {
                val uid = currentUser.value?.uid
                if (uid != null) {
                    cloudBackupRepository.deleteUserData(uid)
                }
                val result = authRepository.deleteAccount()
                appSettingsRepository.setCloudBackupEnabled(false)
                onDone(result.isSuccess)
            } else {
                localAuthRepository.deleteAccount()
                appSettingsRepository.setCloudBackupEnabled(false)
                onDone(true)
            }
        }
    }


    fun setCloudBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setCloudBackupEnabled(enabled)
            if (enabled) backupNow()
        }
    }

    fun setMyPhoneNumber(number: String) {
        viewModelScope.launch {
            appSettingsRepository.setMyPhoneNumber(number)


            val uid = authRepository.currentUserUidOrNull() ?: return@launch
            if (number.isNotBlank()) {
                videoCallSignalingRepository.publishPhoneDirectoryEntry(uid, number)
            }
        }
    }

    fun backupNow() {
        val uid = currentUser.value?.uid ?: return
        viewModelScope.launch {
            _backupState.value = BackupState.IN_PROGRESS
            val callLog: List<CallLogEntity> = callLogRepository.rawEntriesForBackup()
            val blocked: List<BlockedNumberEntity> = blockedNumbers.value
            val result = cloudBackupRepository.backup(uid, callLog, blocked, themeId.value)
            when (result) {
                is BackupResult.Success -> {
                    _backupState.value = BackupState.SUCCESS
                    _lastBackedUpAtMillis.value = System.currentTimeMillis()
                }
                is BackupResult.Failure -> _backupState.value = BackupState.FAILED
            }
        }
    }

    fun restoreFromCloud(onDone: (Boolean) -> Unit) {
        val uid = currentUser.value?.uid ?: run { onDone(false); return }
        viewModelScope.launch {
            val snapshot = cloudBackupRepository.restore(uid)
            if (snapshot != null) {
                callLogRepository.replaceAllFromBackup(snapshot.callLog)
                themePreference.setTheme(snapshot.themeId)
                _lastBackedUpAtMillis.value = snapshot.lastBackedUpAtMillis
                onDone(true)
            } else {
                onDone(false)
            }
        }
    }
}
