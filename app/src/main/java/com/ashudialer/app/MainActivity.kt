package com.ashudialer.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ashudialer.app.data.RecentCall
import com.ashudialer.app.telecom.DialerPermissions
import com.ashudialer.app.ui.components.DialerBottomNav
import com.ashudialer.app.ui.components.DialerTab
import com.ashudialer.app.ui.components.LockedNumberPinDialog
import com.ashudialer.app.ui.components.ThemePickerSheet
import com.ashudialer.app.ui.components.rememberButtonHaptic
import com.ashudialer.app.ui.screens.*
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.theme.AshuDialerTheme
import com.ashudialer.app.util.phoneNumbersMatch
import com.ashudialer.app.viewmodel.MainViewModel
import com.ashudialer.app.viewmodel.ViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class OverlayScreen { NONE, ACCOUNT, PRIVACY_POLICY, SETTINGS, BLOCKED_NUMBERS, HELP_FEEDBACK, NOTES, RECORDINGS, RECORDING_GUIDE, RECORDING_SETTINGS, RECORDING_ROOT_SETUP, RECORDING_ACCESSIBILITY_SETUP, RECORDING_ADB_SETUP, SIM_ROUTING, VIBRATION_PATTERNS, LOCAL_BACKUP, QUIET_HOURS, CALL_INSIGHTS, ABOUT, PRIVATE_SPACE, ADD_CONTACT, UPDATE_CHECK, CAPTIONS_TYPE_TO_TALK }

// THE FIX for the Modules/Root-setup screen "freezing" after tapping
// "Open Magisk" and coming back: overlay used to be plain `remember`,
// which only survives a configuration change (rotation) - it does NOT
// survive the app's process actually being killed while backgrounded,
// which is exactly what happens when Magisk (or any other external app)
// is opened from inside this app and the OS reclaims memory from this
// app's now-background process, common on MIUI in particular. When the
// person came back, Android restarted MainActivity fresh, `remember`
// had nothing to restore from, and overlay silently reset to NONE - at
// which point the existing "only redirect when overlay == NONE" guard
// (added for the same-process resume case) no longer helped, because
// overlay genuinely *was* NONE again. The person's Modules screen was
// gone with no back-stack to return to, indistinguishable from a freeze.
// rememberSaveable with an explicit Saver (storing just the enum's name
// as a String, since a plain enum isn't Parcelable/Serializable in a way
// Bundle can store directly) survives process death the same way
// rotation does, so overlay - and therefore which settings screen the
// person was on - comes back exactly as they left it.
private val OverlayScreenSaver = androidx.compose.runtime.saveable.Saver<OverlayScreen, String>(
    save = { it.name },
    restore = { name -> OverlayScreen.entries.find { it.name == name } ?: OverlayScreen.NONE }
)

// Private Space's own internal navigation, separate from OverlayScreen since
// these three steps (setup-if-first-time, unlock, the actual home screen)
// only ever make sense nested inside OverlayScreen.PRIVATE_SPACE - they're
// not independently reachable destinations the way ACCOUNT or RECORDINGS are.
private enum class PrivateSpaceStep { CHECKING, SETUP, UNLOCK, HOME, SETTINGS }

/**
 * Absorbs every pointer event that reaches this subtree without visually
 * changing anything, so descendants stay composed (preserving scroll
 * position, in-progress search text, etc.) but cannot receive taps.
 *
 * Used to fix a bug where, while an overlay screen (Settings, Account, ...)
 * was open, the tab screen underneath it (e.g. More) was still fully
 * composed and still clickable at the exact same screen coordinates. A tap
 * landing on a non-interactive gap of the visible overlay would fall
 * through and hit whatever row of the *hidden* screen happened to sit at
 * that same spot, silently triggering an action the person never saw or
 * intended to tap.
 */
private fun Modifier.blockInteractionWhen(blocked: Boolean): Modifier =
    if (!blocked) this else this.pointerInput(Unit) {
        awaitEachGesture {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            }
        }
    }

private fun formatClockShort(hour: Int, minute: Int): String {
    val period = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return if (minute == 0) "$displayHour $period" else "%d:%02d %s".format(displayHour, minute, period)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        ViewModelFactory(application as AshuDialerApp)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.ashudialer.app.data.AnalyticsTracker.logAppOpened(this)

        // Without this, the activity runs in Android's legacy (non
        // edge-to-edge) layout mode: the system draws the status bar and
        // navigation bar as opaque bars in whatever color the OS theme
        // defaults to (white in light system theme, black in dark), and
        // Compose content is inset to sit *below* them rather than drawing
        // underneath. That's why the bars looked like a fixed white/black
        // strip regardless of this app's own background - they were never
        // transparent in the first place, so there was nothing for the
        // app's gradient to show through. enableEdgeToEdge() makes both
        // bars transparent and lets content draw full-bleed behind them;
        // the actual icon color (light vs dark) is still controlled
        // separately below via WindowCompat's insets controller, same as
        // before - this only removes the opaque background that was
        // masking it.
        enableEdgeToEdge()
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        // THE FIX for "app hangs/feels laggy sometimes right on open": this
        // used to call ThemePreference.lastKnownThemeIdSync(this) here in
        // onCreate, before setContent - and that function falls back to a
        // runBlocking+withTimeout(150) synchronous DataStore read whenever
        // its SharedPreferences mirror is empty (a fresh install, or the
        // mirror not yet populated for any reason). That blocking read was
        // running directly on MainActivity's main thread during onCreate,
        // which could genuinely freeze the very first frame of the app for
        // up to 150ms on a cold, uncached DataStore read - exactly what
        // reads as "hangs sometimes on open". That blocking fallback exists
        // for InCallActivity, which has no reactive alternative available
        // this early (a call's UI needs a window background painted before
        // any Compose state can be collected). MainActivity has no such
        // constraint: viewModel.themeId below is a real StateFlow collected
        // inside setContent a few lines down, so the same-frame runtime
        // window-background paint isn't worth a blocking main-thread read
        // here. Using the plain non-blocking sync-only read instead - it
        // simply falls back to "ocean" if the cache isn't populated yet,
        // same as before this feature existed at all, and the very next
        // recomposition (once viewModel.themeId emits) repaints with the
        // correct theme's actual background regardless.
        val syncedThemeId = com.ashudialer.app.data.ThemePreference.peekLastKnownThemeId(this)
        val placeholderColor = com.ashudialer.app.ui.theme.paletteById(syncedThemeId).solidBackground
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(placeholderColor.toArgb())
        )

        setContent {
            val context = LocalContext.current
            val themeId by viewModel.themeId.collectAsState()
            val recents by viewModel.recents.collectAsState()
            val contacts by viewModel.contacts.collectAsState()
            // The main Contacts screen groups from THIS, not `contacts`
            // directly - visibleContacts already excludes any locked
            // contact when Private Space's "hide from Contacts" toggle is
            // on (see MainViewModel.visibleContacts), while `contacts`
            // itself stays unfiltered for internal name-matching (Recents
            // rows, dialer autocomplete, etc. still need to resolve a
            // locked number's saved name even when its card is hidden).
            val visibleContacts by viewModel.visibleContacts.collectAsState()
            val hideLockedCallHistoryFromRecents by viewModel.hideLockedCallHistoryFromRecents.collectAsState()
            val hideLockedContactsFromContactsList by viewModel.hideLockedContactsFromContactsList.collectAsState()


            val groupedContacts = remember(visibleContacts) { com.ashudialer.app.ui.screens.groupByPerson(visibleContacts) }
            val currentUser by viewModel.currentUser.collectAsState()
            val settings by viewModel.settings.collectAsState()
            val backupState by viewModel.backupState.collectAsState()
            val lastBackedUpAt by viewModel.lastBackedUpAtMillis.collectAsState()
            val blockedNumbers by viewModel.blockedNumbers.collectAsState()
            val quietHoursSchedule by viewModel.quietHoursSchedule.collectAsState()
            val callInsights by viewModel.callInsights.collectAsState()
            var insightsPeriod by remember { mutableStateOf(com.ashudialer.app.data.InsightsPeriod.WEEK) }


            var activeCall by remember { mutableStateOf<android.telecom.Call?>(null) }
            DisposableEffect(Unit) {
                val listener = { activeCall = com.ashudialer.app.telecom.PixelInCallService.currentCall }
                com.ashudialer.app.telecom.PixelInCallService.addCallListener(listener)
                onDispose { com.ashudialer.app.telecom.PixelInCallService.removeCallListener(listener) }
            }
            val callNotes by viewModel.callNotes.collectAsState()
            val simRoutingRules by viewModel.simRoutingRules.collectAsState()
            val vibrationRules by viewModel.vibrationRules.collectAsState()
            val localBackupStatusFlow by viewModel.localBackupStatus.collectAsState()
            val localBackupBusyFlow by viewModel.localBackupBusy.collectAsState()
            val availableSims = remember { DialerPermissions.availableSims(context) }


            var recordings by remember { mutableStateOf(emptyList<java.io.File>()) }

            var hasPermissions by remember { mutableStateOf(DialerPermissions.hasAll(context)) }
            var isDefaultDialer by remember { mutableStateOf(DialerPermissions.isDefaultDialer(context)) }
            // THE FIX for onboarding/setup appearing stuck on "Grant
            // permissions" even after actually granting them: isDefaultDialer
            // and hasPermissions used to only ever refresh when a launcher
            // *this app itself* started (defaultDialerLauncher/
            // permissionLauncher below) returned a result. That covers
            // tapping the in-app "Grant permissions" button, but not the
            // very next thing this screen tells the person to do if that
            // button's system dialog doesn't stick - going to Settings ->
            // Apps -> Default apps -> Phone app and picking Ashu Dialer
            // manually. Coming back from Settings that way resumes this
            // Activity without ever going through either launcher, so the
            // stale `false` from this screen's first composition was never
            // replaced - the person had already fixed it, but the screen
            // kept insisting they hadn't. Re-checking both flags on every
            // resume (not just launcher results) means this screen always
            // reflects whatever is actually true right now, regardless of
            // which path the person used to grant it.
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        hasPermissions = DialerPermissions.hasAll(context)
                        isDefaultDialer = DialerPermissions.isDefaultDialer(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            val app = context.applicationContext as AshuDialerApp
            val onboardingComplete by app.onboardingPreference.isCompleteFlow.collectAsState(initial = null)
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var selectedTab by remember { mutableStateOf(DialerTab.RECENT) }
            var recordingGuideOpenedFromSettings by remember { mutableStateOf(false) }
            // The initial tab is RECENT, so clear any missed-call badge/
            // notification count right away too (covers the case where the
            // person opens the app itself, not just when they tap the tab).
            LaunchedEffect(Unit) {
                com.ashudialer.app.telecom.CallNotificationHelper.clearMissedCallCount(context)
            }
            LaunchedEffect(selectedTab) {
                if (selectedTab == DialerTab.RECENT) {
                    com.ashudialer.app.telecom.CallNotificationHelper.clearMissedCallCount(context)
                }
            }
            var showThemePicker by remember { mutableStateOf(false) }
            var updateCheck by remember { mutableStateOf<com.ashudialer.app.data.UpdateCheckResult?>(null) }
            var updateCheckBusy by remember { mutableStateOf(false) }
            var updateInstallBusy by remember { mutableStateOf(false) }
            val updateChecker = remember { com.ashudialer.app.data.UpdateChecker(context) }
            var overlay by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = OverlayScreenSaver) { mutableStateOf(OverlayScreen.NONE) }
            var privateSpaceStep by remember { mutableStateOf(PrivateSpaceStep.CHECKING) }
            var privateSpaceCallHistory by remember { mutableStateOf<List<com.ashudialer.app.data.RecentCall>>(emptyList()) }
            // When placeCall() finds the target number is in the locked-numbers
            // list, the actual call is held here rather than placed immediately -
            // PendingLockedCall.verify unlocks it. Null means no call is
            // currently gated behind a PIN prompt.
            var pendingLockedCall by remember { mutableStateOf<String?>(null) }
            // Bumped whenever a recording is moved into/out of Private
            // Space's private storage, or deleted from within it - since
            // filesystem writes don't push change notifications the way a
            // Flow-backed DB table does, incrementing this token is what
            // triggers the LaunchedEffect below to re-read the folder.
            var privateSpaceRecordingsRefreshToken by remember { mutableStateOf(0) }
            var privateSpaceRecordings by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
            var dialerAddContactNumber by remember { mutableStateOf<String?>(null) }
            var selectedContactForDetail by remember { mutableStateOf<com.ashudialer.app.data.Contact?>(null) }
            var callHistoryPageContact by remember { mutableStateOf<com.ashudialer.app.data.Contact?>(null) }
            var callHistoryPageEntries by remember { mutableStateOf<List<com.ashudialer.app.data.SystemCallLogEntry>>(emptyList()) }
            // Tapping an unsaved number's avatar used to jump straight into
            // AddContactScreen's form - this holds the number instead, so a
            // lightweight "About" style page (UnknownNumberDetailScreen) can
            // show first, matching what happens for a saved contact's
            // avatar, with "Add to contacts" as one action on that screen
            // rather than the only thing that can happen.
            var unknownNumberForDetail by remember { mutableStateOf<String?>(null) }


            var contactCallHistory by remember { mutableStateOf<List<com.ashudialer.app.data.SystemCallLogEntry>>(emptyList()) }
            var contactEmail by remember { mutableStateOf<String?>(null) }
            var contactRingtoneUri by remember { mutableStateOf<String?>(null) }
            val contactAccounts = remember { viewModel.listContactAccounts() }
            var pendingImportBytes by remember { mutableStateOf<ByteArray?>(null) }
            var pendingImportFileName by remember { mutableStateOf<String?>(null) }
            var unknownNumberCallHistory by remember { mutableStateOf<List<com.ashudialer.app.data.SystemCallLogEntry>>(emptyList()) }

            LaunchedEffect(callHistoryPageContact?.phoneNumber) {
                val number = callHistoryPageContact?.phoneNumber
                callHistoryPageEntries = if (number != null) viewModel.loadCallHistoryForNumber(number) else emptyList()
            }

            LaunchedEffect(unknownNumberForDetail) {
                val number = unknownNumberForDetail
                unknownNumberCallHistory = if (number != null) viewModel.loadCallHistoryForNumber(number) else emptyList()
            }

            LaunchedEffect(selectedContactForDetail?.phoneNumber) {
                val number = selectedContactForDetail?.phoneNumber
                contactCallHistory = if (number != null) viewModel.loadCallHistoryForNumber(number) else emptyList()
            }

            LaunchedEffect(selectedContactForDetail?.contactId) {
                val contactId = selectedContactForDetail?.contactId
                contactEmail = if (contactId != null) viewModel.loadEmailForContact(contactId) else null
                contactRingtoneUri = if (contactId != null) viewModel.loadRingtoneForContact(contactId) else null
            }

            val ringtonePickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val contact = selectedContactForDetail
                if (contact != null) {
                    val chosenUri = result.data?.getParcelableExtra<android.net.Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                    // A null result here is what the picker returns for "Silent"
                    // or if the person chose the device default explicitly - in
                    // either case this correctly clears any custom ringtone
                    // back to null, rather than leaving a stale value.
                    contactRingtoneUri = chosenUri?.toString()
                    viewModel.setContactRingtone(contact.contactId, chosenUri?.toString())
                }
            }

            var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
            // True when the in-flight crop (pendingCropUri) was launched
            // from AddContactScreen's avatar tap rather than an existing
            // contact's "change photo" - determines whether the cropped
            // result goes to updateContactPhoto (existing contact) or gets
            // handed back into AddContactScreen as croppedPhotoBytes to
            // save with the new contact. Reset whenever a new pick starts.
            var cropIsForNewContact by remember { mutableStateOf(false) }
            var newContactPhotoBytes by remember { mutableStateOf<ByteArray?>(null) }
            val photoPickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                // Hand off to the crop dialog instead of saving the raw picked
                // image directly - the crop step (drag to pan, pinch to zoom,
                // fixed circular frame) runs afterward and is what actually
                // calls updateContactPhoto (or, for a not-yet-created contact,
                // stores the bytes to save along with the rest of the form)
                // once the person confirms.
                if (uri != null) {
                    pendingCropUri = uri
                }
            }
            // Separate launcher for AddContactScreen's avatar tap so its
            // result routes to newContactPhotoBytes instead of an existing
            // contact - kept distinct from photoPickerLauncher above rather
            // than threading an extra "which flow" flag through one shared
            // launcher's callback.
            val addContactPhotoPickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    cropIsForNewContact = true
                    pendingCropUri = uri
                }
            }


            var pendingCallHistoryCsvBytes by remember { mutableStateOf<ByteArray?>(null) }
            val exportCallHistoryLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/csv")
            ) { uri ->
                val bytes = pendingCallHistoryCsvBytes
                pendingCallHistoryCsvBytes = null
                if (uri != null && bytes != null) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        Toast.makeText(context, "Call history exported", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Couldn't save the file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            val importCallHistoryLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    scope.launch {
                        try {
                            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                            if (text != null) {
                                val count = viewModel.importCallHistoryCsv(text)
                                Toast.makeText(context, "Imported $count calls", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Couldn't read that file: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                // Only DialerPermissions.core decides hasPermissions - not
                // the full `results` map, which also contains the optional
                // batch (Camera, Bluetooth). Denying an optional permission
                // used to flip hasPermissions to false even though every
                // core permission (including READ_CALL_LOG) was granted -
                // that stale `false` could then race with code elsewhere
                // that assumes granted-core implies safe-to-query, which is
                // exactly the gap that let SystemCallLogRepository crash
                // with a SecurityException before its own guard was added.
                // Re-checking via hasAll(context) rather than trusting
                // `results` directly also covers a permission that was
                // already granted before this launch (and so never appears
                // as a key in `results` at all).
                hasPermissions = DialerPermissions.hasAll(context)
                if (hasPermissions) viewModel.loadContacts()
                // THE FIX for "Grant permissions doesn't lead anywhere until
                // I go set default dialer from Settings myself": this
                // callback used to only refresh hasPermissions, leaving
                // isDefaultDialer exactly as stale as it was when this
                // Composable first ran. PermissionsScreen's own button
                // branches strictly on both flags (see its `when` block),
                // so even though granting permissions here correctly made
                // hasPermissions true, a stale isDefaultDialer=false from
                // before the grant should have been enough to show the
                // "Set as default dialer" button next - and normally is,
                // through simple Compose recomposition. On some builds
                // (module/priv-app installs in particular) the underlying
                // TelecomManager.defaultDialerPackage / RoleManager query
                // can return a momentarily cached answer right after a
                // permission dialog dismisses, without the ON_RESUME
                // lifecycle event that would otherwise re-check it (the
                // system permission dialog doesn't always produce a full
                // pause/resume of this Activity the way leaving to Settings
                // does). Explicitly re-checking here, immediately after the
                // permission result lands - not waiting on ON_RESUME at all -
                // closes that gap: PermissionsScreen re-renders with
                // whatever is actually true right now on every path, not
                // just the ON_RESUME one.
                isDefaultDialer = DialerPermissions.isDefaultDialer(context)

                // Belt-and-suspenders for the same staleness explained
                // above: on the builds where it shows up at all, it's
                // specifically a brief system-side lag (the OS hasn't
                // finished settling defaultDialerPackage/RoleManager state
                // yet), not a permanently wrong answer - so one more
                // re-check a moment later catches it without ever leaving
                // the screen stuck showing "Set as default dialer" as the
                // next step when the person just waits a second.
                scope.launch {
                    kotlinx.coroutines.delay(300)
                    isDefaultDialer = DialerPermissions.isDefaultDialer(context)
                }            }

            val defaultDialerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                isDefaultDialer = DialerPermissions.isDefaultDialer(context)
                if (!isDefaultDialer) {
                    // Some OEM role pickers return without changing the role.
                    // Send the person straight to Android's Default apps page
                    // instead of leaving the setup flow stranded.
                    scope.launch {
                        kotlinx.coroutines.delay(700)
                        if (!DialerPermissions.isDefaultDialer(context)) {
                            com.ashudialer.app.telecom.OemPermissionHelper.openDefaultAppsSettings(context)
                        }
                    }
                }
                // THE FIX for "Set as default dialer doesn't work until I go
                // do it manually from Settings myself": the exact same
                // staleness explained in permissionLauncher's callback below
                // applies here too, if anything more so - RoleManager's
                // request-role picker can return its ActivityResult (RESULT_
                // OK) before the OS has actually finished committing the new
                // default-dialer role assignment, so TelecomManager.
                // defaultDialerPackage read immediately on this callback can
                // still report the OLD default for a brief moment on some
                // builds. Without a re-check, isDefaultDialer could get set
                // to false here (the stale read) and then never update again
                // - nothing else in this screen re-checks it after this
                // point except ON_RESUME, and the picker dismissing doesn't
                // reliably trigger a full pause/resume of this Activity the
                // way actually leaving to Settings does - which is exactly
                // "stuck until I open Settings and do it myself" (that
                // manual path works because opening Settings *does* trigger
                // a real ON_RESUME when returning).
                //
                // Polls a few times with increasing delay rather than one
                // fixed delay: the OS-side settle time isn't consistent
                // across devices, so this keeps re-checking - and stops the
                // moment it actually reads true - instead of gambling on one
                // specific delay being long enough. Each iteration is cheap
                // (a single Binder call), and the loop exits immediately
                // once it succeeds, so this costs nothing extra on a device
                // where the very first re-check would already have worked.
                scope.launch {
                    for (delayMs in longArrayOf(200, 300, 500, 1000)) {
                        if (isDefaultDialer) break
                        kotlinx.coroutines.delay(delayMs)
                        isDefaultDialer = DialerPermissions.isDefaultDialer(context)
                    }
                }
            }

            val signInLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                viewModel.handleSignInResult(result.data) { success, errorMessage ->
                    if (!success) {
                        Toast.makeText(context, errorMessage ?: "Sign-in failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }


            val exportBackupLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/octet-stream")
            ) { uri ->
                val bytes = viewModel.takePendingExportBytes()
                if (uri != null && bytes != null) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        viewModel.setLocalBackupStatus("Backup saved. Keep the PIN somewhere safe — it can't be recovered.")
                    } catch (e: Exception) {
                        viewModel.setLocalBackupStatus("Couldn't save the file: ${e.message}")
                    }
                }
            }


            val importPickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    try {
                        pendingImportBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        pendingImportFileName = queryDisplayName(context, uri) ?: "Backup file"
                        viewModel.clearLocalBackupStatus()
                    } catch (e: Exception) {
                        viewModel.setLocalBackupStatus("Couldn't read that file: ${e.message}")
                    }
                }
            }

            LaunchedEffect(hasPermissions) {
                if (hasPermissions) {
                    viewModel.loadContacts()
                    viewModel.syncCallHistory()
                }
            }

            fun placeCallDirect(number: String) {
                // applicationScope, not lifecycleScope: this is a local fun re-created
                // every recomposition, so a lifecycleScope.launch started from it could
                // get interrupted by a recomposition happening right after the tap
                // (very plausible - tapping Call often triggers a visible UI change).
                // On dual-SIM devices the suspend DB lookup below could be cut off
                // before it resolves, silently dropping the whole call attempt - the
                // person would tap Call, nothing would happen, and a second tap
                // (single-SIM code path, no DB hop needed) would then work.
                app.applicationScope.launch {
                    val sims = DialerPermissions.availableSims(context)
                    val handle = if (sims.size > 1) {
                        val preferredId = try {
                            app.database.simRoutingDao().getPreferredSimId(number)
                        } catch (_: Exception) {
                            null // fall through with no preferred SIM rather than lose the call entirely
                        }
                        preferredId?.let { id -> sims.firstOrNull { it.handle.id == id }?.handle }
                    } else null
                    DialerPermissions.placeCall(context, number, handle)
                }
            }

            // The single choke point every onCall in this file routes through
            // (Recents, Contacts, the dialer, a contact's detail screen, ...).
            // Gating the PIN check here - rather than in each individual
            // onCall callback - means locking a number protects it everywhere
            // a call to it could be started from, with no risk of a new call
            // site being added later and forgetting the check.
            fun placeCall(number: String) {
                app.applicationScope.launch {
                    val locked = try {
                        viewModel.isNumberLocked(number)
                    } catch (_: Exception) {
                        false // never block a call outright just because the lock check itself failed
                    }
                    if (locked) {
                        pendingLockedCall = number
                    } else {
                        placeCallDirect(number)
                    }
                }
            }

            fun sendMessage(number: String) {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }

            fun openWhatsApp(number: String) {
                try {


                    val cleaned = number.filter { it.isDigit() || it == '+' }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleaned")).apply {
                        setPackage("com.whatsapp")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {


                    try {
                        val cleaned = number.filter { it.isDigit() || it == '+' }
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleaned"))
                                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        )
                    } catch (e2: Exception) {
                        Toast.makeText(context, "Couldn't open WhatsApp", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            fun recordingUri(file: java.io.File): Uri =
                androidx.core.content.FileProvider.getUriForFile(context, "com.ashudialer.app.fileprovider", file)

            fun playRecording(file: java.io.File) {
                if (!com.ashudialer.app.BuildConfig.CALL_RECORDING_ENABLED) return
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(recordingUri(file), "audio/mp4")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(intent)
            }

            fun shareRecording(file: java.io.File) {
                if (!com.ashudialer.app.BuildConfig.CALL_RECORDING_ENABLED) return
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/mp4"
                    putExtra(Intent.EXTRA_STREAM, recordingUri(file))
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(Intent.createChooser(intent, "Share recording").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }

            fun deleteRecording(file: java.io.File) {
                if (!com.ashudialer.app.BuildConfig.CALL_RECORDING_ENABLED) return
                val deleted = com.ashudialer.app.telecom.CallRecorder.deleteRecording(context, file)
                recordings = com.ashudialer.app.telecom.CallRecorder.listRecordings(context)
                if (!deleted) {
                    Toast.makeText(context, "Couldn't delete that recording", Toast.LENGTH_SHORT).show()
                }
            }

            fun moveRecordingsToPrivateSpace(files: List<java.io.File>) {
                if (!com.ashudialer.app.BuildConfig.CALL_RECORDING_ENABLED) return
                viewModel.moveRecordingsToPrivateSpace(context, files) {
                    // Both lists refresh: the moved files vanish from the
                    // public list (the point of the move) and reappear in
                    // Private Space's own recordings list the next time that
                    // screen is visited (privateSpaceRecordingsRefreshToken
                    // below covers that side).
                    recordings = com.ashudialer.app.telecom.CallRecorder.listRecordings(context)
                    privateSpaceRecordingsRefreshToken += 1
                }
            }

            fun moveRecordingOutOfPrivateSpace(file: java.io.File) {
                if (!com.ashudialer.app.BuildConfig.CALL_RECORDING_ENABLED) return
                viewModel.moveRecordingOutOfPrivateSpace(context, file) {
                    recordings = com.ashudialer.app.telecom.CallRecorder.listRecordings(context)
                    privateSpaceRecordingsRefreshToken += 1
                }
            }

            fun deletePrivateSpaceRecording(file: java.io.File) {
                if (!com.ashudialer.app.BuildConfig.CALL_RECORDING_ENABLED) return
                val deleted = com.ashudialer.app.telecom.CallRecorder.deleteRecording(context, file)
                privateSpaceRecordingsRefreshToken += 1
                if (!deleted) {
                    Toast.makeText(context, "Couldn't delete that recording", Toast.LENGTH_SHORT).show()
                }
            }

            fun testVibrationPattern(pattern: com.ashudialer.app.telecom.VibrationPattern) {
                try {
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    }
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern.timings, -1))
                } catch (_: Exception) {
                }
            }

            AshuDialerTheme(themeId = themeId, fontSizeIndex = settings.fontSizeIndex) {
                val palette = LocalDialerPalette.current
                val buttonHaptic = rememberButtonHaptic(settings.vibrateOnButtonPress)

                // Status bar icon color follows the active theme's palette, not a
                // fixed value. Previously windowLightStatusBar was hardcoded true
                // in themes.xml, forcing dark status bar icons regardless of theme -
                // fine on the light palettes, but on the dark ones (Midnight,
                // Violet, Dark Mode, System-in-dark-mode) it made the clock/battery/
                // signal icons render dark-on-dark and effectively disappear. This
                // keeps icon color in sync with whichever theme is active, updating
                // immediately on every theme change.
                val view = LocalView.current
                LaunchedEffect(palette.isDark) {
                    val window = (view.context as? ComponentActivity)?.window ?: return@LaunchedEffect
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = !palette.isDark
                    // Same fix as the status bar, applied to the nav bar /
                    // gesture area - it was left at its own default (which,
                    // combined with enableEdgeToEdge() above making it
                    // transparent, would otherwise mean dark icons drawn
                    // straight onto whatever dark part of the app's gradient
                    // sits behind them, or vice versa on the light themes).
                    insetsController.isAppearanceLightNavigationBars = !palette.isDark
                }

                BackHandler(enabled = showThemePicker) { showThemePicker = false }
                BackHandler(enabled = callHistoryPageContact != null) { callHistoryPageContact = null }
                BackHandler(enabled = selectedContactForDetail != null) { selectedContactForDetail = null }
                BackHandler(enabled = unknownNumberForDetail != null) { unknownNumberForDetail = null }
                // Previously this one handler fired for every overlay,
                // Private Space included, and always closed the overlay
                // completely regardless of which of its own internal steps
                // (SETTINGS, HOME, UNLOCK, etc.) was showing - so pressing
                // back from Private Space's Settings screen jumped straight
                // out of Private Space entirely instead of returning to its
                // Home screen the way tapping the screen's own back arrow
                // already correctly did. Private Space needs its own
                // handler that steps back through privateSpaceStep first,
                // only falling through to closing the overlay once already
                // at its top level (HOME) - the general handler below is
                // disabled while Private Space is open so the two can't
                // both fire for the same back press.
                BackHandler(enabled = overlay == OverlayScreen.PRIVATE_SPACE) {
                    when (privateSpaceStep) {
                        PrivateSpaceStep.SETTINGS -> privateSpaceStep = PrivateSpaceStep.HOME
                        PrivateSpaceStep.HOME, PrivateSpaceStep.UNLOCK, PrivateSpaceStep.SETUP, PrivateSpaceStep.CHECKING ->
                            overlay = OverlayScreen.NONE
                    }
                }
                BackHandler(enabled = overlay != OverlayScreen.NONE && overlay != OverlayScreen.PRIVATE_SPACE) {
                    when (overlay) {
                        OverlayScreen.ADD_CONTACT -> {
                            dialerAddContactNumber = null
                            overlay = OverlayScreen.NONE
                        }
                        OverlayScreen.RECORDING_GUIDE -> overlay = if (recordingGuideOpenedFromSettings) OverlayScreen.SETTINGS else OverlayScreen.RECORDINGS
                        OverlayScreen.RECORDING_SETTINGS -> overlay = OverlayScreen.RECORDINGS
                        OverlayScreen.RECORDING_ROOT_SETUP, OverlayScreen.RECORDING_ACCESSIBILITY_SETUP, OverlayScreen.RECORDING_ADB_SETUP ->
                            overlay = OverlayScreen.RECORDING_SETTINGS
                        else -> overlay = OverlayScreen.NONE
                    }
                }

                if (onboardingComplete == null) {
                    // THE FIX for the ~0.6s flash of the "Set up Ashu Dialer"
                    // checklist screen right at app launch, before Onboarding
                    // ever appears: onboardingComplete starts as null (see
                    // collectAsState(initial = null) above) until the
                    // DataStore read actually completes, which is async and
                    // takes a moment even though it's usually fast. The
                    // check below this one, `onboardingComplete == false`,
                    // is false while the value is still null (null != false)
                    // - so without this branch, code fell straight through
                    // to `else if (!hasPermissions || !isDefaultDialer)`,
                    // which is true for literally every first-time install
                    // (no permissions granted yet) and rendered the Setup
                    // checklist for that brief loading window, before the
                    // real onboardingComplete=false value arrived a moment
                    // later and switched to Onboarding - a visible flash of
                    // the wrong screen. Rendering nothing while still
                    // loading closes that gap entirely: whichever screen is
                    // actually correct (Onboarding for a new install, Setup
                    // or the main app for a returning one) is now the very
                    // first thing ever shown, with no flash of the other one
                    // first. This state is only ever visible for the single
                    // frame or two the DataStore read genuinely takes, so a
                    // blank background reads as instantaneous, not as a
                    // loading screen someone would consciously notice.
                    Box(modifier = Modifier.fillMaxSize().background(palette.background))
                } else if (onboardingComplete == false) {
                    OnboardingScreen(
                        onFinished = { scope.launch { app.onboardingPreference.markComplete() } },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if ((!hasPermissions || !isDefaultDialer) && overlay == OverlayScreen.NONE) {
                    // THE FIX for the Modules/Root-setup screen "freezing":
                    // this branch used to fire on every single resume the
                    // instant hasPermissions/isDefaultDialer read false,
                    // with no regard for what the person was actually
                    // looking at - including while they were sitting on
                    // the Modules (RootRecordingSetupScreen) overlay,
                    // which lives inside the `else` branch below and only
                    // exists there. On MIUI in particular,
                    // isDefaultDialer's underlying RoleManager/
                    // TelecomManager query can read stale/false for a
                    // moment right after returning from another app (e.g.
                    // tapping "Open Magisk" from Modules and coming back),
                    // which used to yank the entire overlay Compose
                    // subtree out from under the person and replace it
                    // with PermissionsScreen - the Modules screen just
                    // vanished with no back-stack to return to, which is
                    // exactly what read as a freeze even though nothing
                    // had actually hung. Gating this redirect on
                    // `overlay == OverlayScreen.NONE` means a real missing
                    // permission is still caught (and still redirects)
                    // the moment the person is back on the main app with
                    // no overlay open, but never interrupts whatever
                    // settings screen they're already mid-task on.
                    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
                        PermissionsScreen(
                            isDefaultDialer = isDefaultDialer,
                            hasPermissions = hasPermissions,
                            onRequestPermissions = {
                                permissionLauncher.launch(DialerPermissions.required)
                            },
                            onSetDefaultDialer = {
                                // Wrapped in try/catch: on some MIUI builds,
                                // RoleManager.createRequestRoleIntent or the
                                // launch itself can throw (rather than just
                                // returning RESULT_CANCELED) if the OEM's
                                // own role-picker component is missing or
                                // misbehaving - previously nothing caught
                                // this, so the tap silently did nothing and
                                // the screen looked frozen with no
                                // indication of what went wrong or what to
                                // do next. Falling back to Android's own
                                // Default apps settings screen means the
                                // person still has a path forward instead
                                // of being stuck on this screen.
                                try {
                                    defaultDialerLauncher.launch(
                                        DialerPermissions.requestDefaultDialerIntent(context)
                                    )
                                } catch (_: Exception) {
                                    com.ashudialer.app.telecom.OemPermissionHelper.openDefaultAppsSettings(context)
                                }
                            },
                            onOpenDefaultAppsSettings = {
                                com.ashudialer.app.telecom.OemPermissionHelper.openDefaultAppsSettings(context)
                            },
                            onOpenMiuiAutostartSettings = {
                                com.ashudialer.app.telecom.OemPermissionHelper.openMiuiAutostartSettings(context)
                            }
                        )
                    }
                } else {
                    Scaffold(
                        containerColor = Color.Transparent,
                        bottomBar = {
                            if (overlay == OverlayScreen.NONE && callHistoryPageContact == null && selectedContactForDetail == null && unknownNumberForDetail == null) {
                                DialerBottomNav(selected = selectedTab, onSelect = { selectedTab = it })
                            }
                        }
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(palette.background)
                                .padding(padding)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                val ongoingCall = activeCall
                                if (ongoingCall != null && overlay == OverlayScreen.NONE && callHistoryPageContact == null && selectedContactForDetail == null && unknownNumberForDetail == null) {
                                    ReturnToCallBanner(
                                        call = ongoingCall,
                                        palette = palette,
                                        onClick = {
                                            val intent = Intent(context, com.ashudialer.app.telecom.InCallActivity::class.java).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                            }
                                            context.startActivity(intent)
                                        }
                                    )
                                }
                            AnimatedContent(
                                targetState = selectedTab,
                                modifier = Modifier
                                    .weight(1f)
                                    .blockInteractionWhen(overlay != OverlayScreen.NONE || callHistoryPageContact != null || selectedContactForDetail != null || unknownNumberForDetail != null),
                                transitionSpec = {
                                    val forward = targetState.ordinal > initialState.ordinal


                                    val slideDistance = { fullWidth: Int -> fullWidth / 10 }
                                    (fadeIn(tween(160)) + slideInHorizontally(
                                        animationSpec = tween(160),
                                        initialOffsetX = { w -> if (forward) slideDistance(w) else -slideDistance(w) }
                                    )) togetherWith
                                        (fadeOut(tween(110)) + slideOutHorizontally(
                                            animationSpec = tween(110),
                                            targetOffsetX = { w -> if (forward) -slideDistance(w) else slideDistance(w) }
                                        ))
                                },
                                label = "tab-content"
                            ) { tab ->
                                when (tab) {
                                    DialerTab.RECENT -> RecentsScreen(
                                        recents = recents,
                                        currentThemeId = themeId,
                                        onOpenThemePicker = { showThemePicker = true },
                                        onCall = { call: RecentCall -> buttonHaptic(); placeCall(call.phoneNumber) },
                                        onMessage = { call -> buttonHaptic(); sendMessage(call.phoneNumber) },
                                        onWhatsApp = { call -> buttonHaptic(); openWhatsApp(call.phoneNumber) },
                                        onViewOrAddContact = { call ->
                                            buttonHaptic()
                                            val match = contacts.firstOrNull { phoneNumbersMatch(it.phoneNumber, call.phoneNumber) }
                                            if (match != null) selectedContactForDetail = match
                                            else unknownNumberForDetail = call.phoneNumber
                                        },
                                        searchContacts = contacts,
                                        onOpenSearchContact = { contact ->
                                            buttonHaptic()
                                            selectedContactForDetail = contact
                                        },
                                        onCallSearchContact = { contact ->
                                            buttonHaptic()
                                            placeCall(contact.phoneNumber)
                                        },
                                        onBlock = { call -> viewModel.blockNumber(call.phoneNumber) },
                                        onUnblock = { call ->
                                            blockedNumbers.firstOrNull { phoneNumbersMatch(it.phoneNumber, call.phoneNumber) }
                                                ?.let { viewModel.unblockNumber(it) }
                                        },
                                        onDeleteHistoryFor = { call -> viewModel.deleteCallHistoryForNumber(call.phoneNumber) },
                                        onClearAllHistory = { viewModel.clearCallHistory() },
                                        onDeleteRecents = { entries -> viewModel.deleteRecentEntries(entries) },
                                        isBlocked = { number -> blockedNumbers.any { phoneNumbersMatch(it.phoneNumber, number) } },
                                        isSavedContact = { number -> contacts.any { c -> phoneNumbersMatch(c.phoneNumber, number) } },
                                        showContactThumbnails = settings.showContactThumbnails,
                                        showPhoneNumbers = settings.showPhoneNumbers,
                                        useRelativeDate = settings.useRelativeDate,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    DialerTab.CONTACTS -> ContactsScreen(
                                        contacts = visibleContacts,
                                        onCall = { contact -> buttonHaptic(); placeCall(contact.phoneNumber) },
                                        onMessage = { contact -> buttonHaptic(); sendMessage(contact.phoneNumber) },
                                        onWhatsApp = { contact -> buttonHaptic(); openWhatsApp(contact.phoneNumber) },
                                        onShareNumber = { contact ->
                                            buttonHaptic()
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, "${contact.displayName}: ${contact.phoneNumber}")
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share number"))
                                        },
                                        onOpenDetail = { contact -> buttonHaptic(); selectedContactForDetail = contact },
                                        onOpenAddContact = {
                                            buttonHaptic()
                                            dialerAddContactNumber = ""
                                            overlay = OverlayScreen.ADD_CONTACT
                                        },
                                        onDeleteContacts = { ids -> viewModel.deleteContacts(ids) },
                                        onBlockNumber = { number -> viewModel.blockNumber(number) },
                                        onUnblockNumber = { number ->
                                            blockedNumbers.firstOrNull { phoneNumbersMatch(it.phoneNumber, number) }?.let { viewModel.unblockNumber(it) }
                                        },
                                        isBlocked = { number -> blockedNumbers.any { phoneNumbersMatch(it.phoneNumber, number) } },
                                        precomputedGroupedContacts = groupedContacts,
                                        showSearchBar = settings.showSearchBar,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    DialerTab.DIALER -> DialerScreen(
                                        contacts = contacts,
                                        onCall = { number -> buttonHaptic(); placeCall(number) },
                                        onMessage = { number -> buttonHaptic(); sendMessage(number) },
                                        onAddContact = { number ->
                                            buttonHaptic()
                                            dialerAddContactNumber = number
                                            overlay = OverlayScreen.ADD_CONTACT
                                        },
                                        onAvatarClick = { contact -> selectedContactForDetail = contact },
                                        vibrateOnButtonPress = settings.vibrateOnButtonPress,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    DialerTab.PROTECT -> ProtectScreen(
                                        blockedNumbers = blockedNumbers,
                                        onOpenBlockedNumbers = { buttonHaptic(); overlay = OverlayScreen.BLOCKED_NUMBERS },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    DialerTab.MORE -> MoreScreen(
                                        showSimRouting = availableSims.size > 1,
                                        vibrateOnButtonPress = settings.vibrateOnButtonPress,
                                        onItemClick = { item ->
                                            when (item) {
                                                // THE FIX for "Set as default" from More/Settings
                                                // not going anywhere: unlike the PermissionsScreen
                                                // version of this same action (see onSetDefaultDialer
                                                // above), this call site had no try/catch and no
                                                // fallback at all. On MIUI builds where
                                                // RoleManager.createRequestRoleIntent/launch() throws
                                                // or the OEM role-picker just never resolves, the tap
                                                // did nothing with zero feedback - the person's only
                                                // way forward was leaving the app and finding
                                                // Settings -> Apps -> Default apps themselves. Now
                                                // mirrors the same catch + direct-to-Settings fallback,
                                                // and isDefaultDialer already gets re-checked on every
                                                // ON_RESUME (see the DisposableEffect above) so the
                                                // More screen picks up the change the moment the
                                                // person returns, from either path.
                                                "Set as default dialer" -> {
                                                    try {
                                                        defaultDialerLauncher.launch(
                                                            DialerPermissions.requestDefaultDialerIntent(context)
                                                        )
                                                    } catch (_: Exception) {
                                                        com.ashudialer.app.telecom.OemPermissionHelper.openDefaultAppsSettings(context)
                                                    }
                                                }
                                                "Appearance" -> showThemePicker = true
                                                "Account" -> overlay = OverlayScreen.ACCOUNT
                                                "Backup" -> overlay = OverlayScreen.ACCOUNT
                                                "Local Backup" -> overlay = OverlayScreen.LOCAL_BACKUP
                                                "Notes" -> overlay = OverlayScreen.NOTES
                                                "SIM Routing" -> overlay = OverlayScreen.SIM_ROUTING
                                                "Vibration Patterns" -> overlay = OverlayScreen.VIBRATION_PATTERNS
                                                "Recordings" -> if (com.ashudialer.app.BuildConfig.CALL_RECORDING_ENABLED) {
                                                    recordings = com.ashudialer.app.telecom.CallRecorder.listRecordings(context)
                                                    overlay = OverlayScreen.RECORDINGS
                                                }
                                                "Call Insights" -> overlay = OverlayScreen.CALL_INSIGHTS
                                                "Captions & Type-to-talk" -> overlay = OverlayScreen.CAPTIONS_TYPE_TO_TALK
                                                "Check for updates" -> {
                                                    updateCheck = null
                                                    updateCheckBusy = true
                                                    overlay = OverlayScreen.UPDATE_CHECK
                                                }
                                                "Privacy Policy" -> overlay = OverlayScreen.PRIVACY_POLICY
                                                "Quiet Hours" -> overlay = OverlayScreen.QUIET_HOURS
                                                "Settings" -> overlay = OverlayScreen.SETTINGS
                                                "Blocked numbers" -> overlay = OverlayScreen.BLOCKED_NUMBERS
                                                "Private Space" -> {
                                                    privateSpaceStep = PrivateSpaceStep.CHECKING
                                                    overlay = OverlayScreen.PRIVATE_SPACE
                                                }
                                                "Help & feedback" -> overlay = OverlayScreen.HELP_FEEDBACK
                                                "Voicemail" -> {
                                                    val started = DialerPermissions.callVoicemail(context)
                                                    if (!started) {
                                                        Toast.makeText(context, "Couldn't reach voicemail on this device", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                "About" -> overlay = OverlayScreen.ABOUT
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            }


                            var renderedOverlay by remember { mutableStateOf(overlay) }
                            if (overlay != OverlayScreen.NONE) renderedOverlay = overlay

                            AnimatedVisibility(
                                visible = overlay != OverlayScreen.NONE,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut()
                            ) {
                                Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
                                    // AnimatedVisibility above only fires when overlay
                                    // transitions to/from NONE - navigating from one
                                    // overlay screen straight to another (e.g. Settings
                                    // -> Recording guide) never toggles that visible
                                    // flag, so it played no transition at all for that
                                    // case. This inner AnimatedContent, keyed on
                                    // renderedOverlay itself, fires on every overlay
                                    // identity change, including overlay-to-overlay,
                                    // without touching any of the individual screens.
                                    androidx.compose.animation.AnimatedContent(
                                        targetState = renderedOverlay,
                                        transitionSpec = {
                                            (slideInHorizontally { width -> width / 3 } + fadeIn()) togetherWith
                                                (slideOutHorizontally { width -> -width / 3 } + fadeOut())
                                        },
                                        label = "overlayScreenTransition"
                                    ) { targetOverlay ->
                                    when (targetOverlay) {
                                        OverlayScreen.ACCOUNT -> AccountScreen(
                                            user = currentUser,
                                            cloudBackupEnabled = settings.cloudBackupEnabled,
                                            lastBackedUpAtMillis = lastBackedUpAt,
                                            backupState = backupState,
                                            myPhoneNumber = settings.myPhoneNumber,
                                            onBack = { overlay = OverlayScreen.NONE },
                                            onSignIn = {
                                                val intent = viewModel.signInIntent()
                                                if (intent != null) {
                                                    signInLauncher.launch(intent)
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Sign-in isn't set up yet",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            },
                                            onRegisterLocal = { email, password, name -> viewModel.registerLocalAccount(email, password, name) },
                                            onSignInLocal = { email, password -> viewModel.signInLocalAccount(email, password) },
                                            onSignOut = { viewModel.signOut() },
                                            onToggleCloudBackup = { enabled -> viewModel.setCloudBackupEnabled(enabled) },
                                            onBackupNow = { viewModel.backupNow() },
                                            onSaveMyPhoneNumber = { number -> viewModel.setMyPhoneNumber(number) },
                                            onDeleteAccount = {
                                                viewModel.deleteAccount { success ->
                                                    val msg = if (success) "Account deleted" else "Couldn't delete account"
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    if (success) overlay = OverlayScreen.NONE
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.PRIVACY_POLICY -> PrivacyPolicyScreen(
                                            onBack = { overlay = OverlayScreen.NONE },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.ADD_CONTACT -> AddContactScreen(
                                            prefillNumber = dialerAddContactNumber.orEmpty(),
                                            accounts = contactAccounts,
                                            onBack = {
                                                dialerAddContactNumber = null
                                                newContactPhotoBytes = null
                                                overlay = OverlayScreen.NONE
                                            },
                                            onSave = { input ->
                                                viewModel.saveNewContact(input)
                                                dialerAddContactNumber = null
                                                newContactPhotoBytes = null
                                                overlay = OverlayScreen.NONE
                                            },
                                            onPickPhoto = { addContactPhotoPickerLauncher.launch("image/*") },
                                            croppedPhotoBytes = newContactPhotoBytes,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.ABOUT -> AboutScreen(
                                            onBack = { overlay = OverlayScreen.NONE },
                                            versionName = com.ashudialer.app.BuildConfig.VERSION_NAME,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.PRIVATE_SPACE -> {
                                            val isSetUp by viewModel.privateSpaceIsSetUp.collectAsState()
                                            // On first entering PRIVATE_SPACE (privateSpaceStep is
                                            // still CHECKING), route to SETUP or UNLOCK based on
                                            // whether a password already exists. isSetUp starts at
                                            // its stateIn default (false) for a brief moment even
                                            // when a password *does* exist, since the DB read hasn't
                                            // resolved yet - keying this off privateSpaceStep instead
                                            // of re-running every time isSetUp changes means that
                                            // brief false doesn't cause a flash of the setup screen
                                            // for someone who already has a password.
                                            LaunchedEffect(overlay) {
                                                if (privateSpaceStep == PrivateSpaceStep.CHECKING) {
                                                    val setUp = viewModel.privateSpaceIsSetUp.first()
                                                    privateSpaceStep = if (setUp) PrivateSpaceStep.UNLOCK else PrivateSpaceStep.SETUP
                                                }
                                            }

                                            when (privateSpaceStep) {
                                                PrivateSpaceStep.CHECKING -> {
                                                    // Deliberately blank rather than a spinner - this
                                                    // resolves in well under a frame in practice (a
                                                    // single local DB read), so a loading indicator
                                                    // would just flash.
                                                }
                                                PrivateSpaceStep.SETUP -> PrivateSpaceSetupScreen(
                                                    onBack = { overlay = OverlayScreen.NONE },
                                                    onSetup = { password -> viewModel.setupPrivateSpace(password) },
                                                    onComplete = { privateSpaceStep = PrivateSpaceStep.HOME },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                PrivateSpaceStep.UNLOCK -> PrivateSpaceUnlockScreen(
                                                    onBack = { overlay = OverlayScreen.NONE },
                                                    onVerifyPassword = { attempt -> viewModel.verifyPrivateSpacePassword(attempt) },
                                                    onResetWithBackupCode = { code, newPassword -> viewModel.resetPrivateSpaceWithBackupCode(code, newPassword) },
                                                    onUnlocked = { privateSpaceStep = PrivateSpaceStep.HOME },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                PrivateSpaceStep.HOME -> {
                                                    val lockedNumbers by viewModel.lockedNumbers.collectAsState()
                                                    LaunchedEffect(lockedNumbers) {
                                                        privateSpaceCallHistory = viewModel.privateSpaceCallHistory()
                                                    }
                                                    LaunchedEffect(privateSpaceRecordingsRefreshToken, com.ashudialer.app.BuildConfig.CALL_RECORDING_ENABLED) {
                                                        privateSpaceRecordings = if (com.ashudialer.app.BuildConfig.CALL_RECORDING_ENABLED) {
                                                            viewModel.privateSpaceRecordings(context)
                                                        } else {
                                                            emptyList()
                                                        }
                                                    }
                                                    PrivateSpaceHomeScreen(
                                                        lockedNumbers = lockedNumbers,
                                                        callHistory = privateSpaceCallHistory,
                                                        recordings = privateSpaceRecordings,
                                                        onBack = {
                                                            // Leaving Private Space re-locks it - the
                                                            // next visit starts over at UNLOCK, not
                                                            // HOME, so simply backgrounding the app or
                                                            // navigating away doesn't leave it sitting
                                                            // unlocked.
                                                            privateSpaceStep = PrivateSpaceStep.CHECKING
                                                            overlay = OverlayScreen.NONE
                                                        },
                                                        onCall = { number ->
                                                            // placeCallDirect, not placeCall - a number
                                                            // reached from inside Private Space's own
                                                            // Numbers tab is already past the password
                                                            // gate for this session, so re-prompting the
                                                            // same PIN a second time here would be pure
                                                            // friction with no added protection.
                                                            placeCallDirect(number)
                                                        },
                                                        onAddNumber = { number, label -> viewModel.lockNumber(number, label) },
                                                        onRemoveNumber = { number -> viewModel.unlockNumber(number) },
                                                        onPlayRecording = { file -> playRecording(file) },
                                                        onShareRecording = { file -> shareRecording(file) },
                                                        onDeleteRecording = { file -> deletePrivateSpaceRecording(file) },
                                                        onMoveRecordingOut = { file -> moveRecordingOutOfPrivateSpace(file) },
                                                        showRecordings = com.ashudialer.app.BuildConfig.CALL_RECORDING_ENABLED,
                                                        onOpenSettings = { privateSpaceStep = PrivateSpaceStep.SETTINGS },
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                PrivateSpaceStep.SETTINGS -> PrivateSpaceSettingsScreen(
                                                    onBack = { privateSpaceStep = PrivateSpaceStep.HOME },
                                                    onChangePassword = { current, new -> viewModel.changePrivateSpacePassword(current, new) },
                                                    onWipeEverything = {
                                                        viewModel.wipePrivateSpace(context)
                                                        // A wipe resets isSetUp to false, so the next
                                                        // time Private Space is opened it should show
                                                        // first-time SETUP again rather than trying to
                                                        // UNLOCK a password that no longer exists.
                                                        // Leaving PRIVATE_SPACE entirely (rather than
                                                        // routing back to HOME/UNLOCK) reflects that the
                                                        // whole space was just torn down.
                                                        privateSpaceStep = PrivateSpaceStep.CHECKING
                                                        overlay = OverlayScreen.NONE
                                                    },
                                                    hideLockedCallHistoryFromRecents = hideLockedCallHistoryFromRecents,
                                                    onSetHideLockedCallHistoryFromRecents = { viewModel.setHideLockedCallHistoryFromRecents(it) },
                                                    hideLockedContactsFromContactsList = hideLockedContactsFromContactsList,
                                                    onSetHideLockedContactsFromContactsList = { viewModel.setHideLockedContactsFromContactsList(it) },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                        OverlayScreen.SETTINGS -> SettingsScreen(
                                            settings = settings,
                                            onBack = { overlay = OverlayScreen.NONE },
                                            onOpenAppearance = { showThemePicker = true },
                                            onToggleCallRecording = { enabled ->
                                                if (enabled) {
                                                    // Route through the guide first instead of
                                                    // flipping the setting immediately - the person
                                                    // needs to see, before recording is even
                                                    // possible, that two-way capture isn't
                                                    // guaranteed on their device and what the real
                                                    // options are. Turning off never needs this.
                                                    recordingGuideOpenedFromSettings = true
                                                    overlay = OverlayScreen.RECORDING_GUIDE
                                                } else {
                                                    viewModel.setCallRecordingEnabled(false)
                                                }
                                            },
                                            onToggleAutoRecordAll = { enabled -> viewModel.setAutoRecordAll(enabled) },
                                            onToggleLedFlash = { enabled -> viewModel.setLedFlashForAlerts(enabled) },
                                            onToggleVibrateOnButton = { enabled -> viewModel.setVibrateOnButtonPress(enabled) },
                                            onToggleKeepCallsInNotifications = { enabled -> viewModel.setKeepCallsInNotifications(enabled) },
                                            onToggleBackEndsCall = { enabled -> viewModel.setBackEndsCall(enabled) },
                                            onToggleDisableProximity = { enabled -> viewModel.setDisableProximitySensor(enabled) },
                                            onToggleShowThumbnails = { enabled -> viewModel.setShowContactThumbnails(enabled) },
                                            onToggleShowPhoneNumbers = { enabled -> viewModel.setShowPhoneNumbers(enabled) },
                                            onToggleRelativeDate = { enabled -> viewModel.setUseRelativeDate(enabled) },
                                            onToggleShowSearchBar = { enabled -> viewModel.setShowSearchBar(enabled) },
                                            onSelectFontSize = { index -> viewModel.setFontSizeIndex(index) },
                                            onExportCallHistory = {
                                                scope.launch {
                                                    val csv = viewModel.exportCallHistoryCsv()
                                                    pendingCallHistoryCsvBytes = csv.toByteArray(Charsets.UTF_8)
                                                    exportCallHistoryLauncher.launch("call_history.csv")
                                                }
                                            },
                                            onImportCallHistory = {
                                                importCallHistoryLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                                            },
                                            onCheckForUpdates = {
                                                updateCheck = null
                                                updateCheckBusy = true
                                                overlay = OverlayScreen.UPDATE_CHECK
                                            },
                                            onOpenTelegramChannel = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/ashuapps_07x")).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                try {
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Couldn't open Telegram", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            quietHoursSubtitle = if (quietHoursSchedule.enabled) {
                                                "%s – %s".format(
                                                    formatClockShort(quietHoursSchedule.startHour, quietHoursSchedule.startMinute),
                                                    formatClockShort(quietHoursSchedule.endHour, quietHoursSchedule.endMinute)
                                                )
                                            } else "Off",
                                            onOpenQuietHours = { overlay = OverlayScreen.QUIET_HOURS },
                                            onOpenMiuiAutostartSettings = if (com.ashudialer.app.telecom.OemPermissionHelper.isLikelyMiui()) {
                                                { com.ashudialer.app.telecom.OemPermissionHelper.openMiuiAutostartSettings(context) }
                                            } else null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.QUIET_HOURS -> QuietHoursScreen(
                                            schedule = quietHoursSchedule,
                                            onBack = { overlay = OverlayScreen.NONE },
                                            onSave = { updated -> viewModel.saveQuietHoursSchedule(updated) },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.CALL_INSIGHTS -> {
                                            LaunchedEffect(insightsPeriod) {
                                                viewModel.loadCallInsights(insightsPeriod)
                                            }
                                            CallInsightsScreen(
                                                insights = callInsights,
                                                period = insightsPeriod,
                                                onPeriodChange = { insightsPeriod = it },
                                                onBack = { overlay = OverlayScreen.NONE },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        OverlayScreen.CAPTIONS_TYPE_TO_TALK -> com.ashudialer.app.ui.screens.CaptionSettingsScreen(
                                            liveCaptionsEnabled = settings.liveCaptionsEnabled,
                                            typeToTalkEnabled = settings.typeToTalkEnabled,
                                            captionLanguageName = settings.captionLanguage,
                                            captionModelManager = app.captionModelManager,
                                            onBack = { overlay = OverlayScreen.NONE },
                                            onSetLiveCaptionsEnabled = { viewModel.setLiveCaptionsEnabled(it) },
                                            onSetTypeToTalkEnabled = { viewModel.setTypeToTalkEnabled(it) },
                                            onSetCaptionLanguage = { viewModel.setCaptionLanguage(it.name) },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.BLOCKED_NUMBERS -> BlockedNumbersScreen(
                                            blockedNumbers = blockedNumbers,
                                            onBack = { overlay = OverlayScreen.NONE },
                                            onBlock = { number -> viewModel.blockNumber(number) },
                                            onUnblock = { entry -> viewModel.unblockNumber(entry) },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.HELP_FEEDBACK -> HelpFeedbackScreen(
                                            onBack = { overlay = OverlayScreen.NONE },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.NOTES -> NotesScreen(
                                            notes = callNotes,
                                            onBack = { overlay = OverlayScreen.NONE },
                                            onDelete = { note -> viewModel.deleteNote(note) },
                                            onCallNumber = { number ->
                                                overlay = OverlayScreen.NONE
                                                placeCall(number)
                                            },
                                            onSaveNoteText = { note, newText ->
                                                viewModel.saveContactNote(
                                                    phoneNumber = note.phoneNumber,
                                                    callerLabel = note.callerLabel,
                                                    text = newText,
                                                    existingNotes = callNotes
                                                )
                                            },
                                            onShareNote = { note ->
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, "${note.callerLabel.ifBlank { note.phoneNumber }}: ${note.text}")
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share note").apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                })
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.RECORDINGS -> RecordingsScreen(
                                            recordings = recordings,
                                            onBack = { overlay = OverlayScreen.NONE },
                                            onPlay = { file -> playRecording(file) },
                                            onShare = { file -> shareRecording(file) },
                                            onDelete = { file -> deleteRecording(file) },
                                            onMoveToPrivateSpace = { files -> moveRecordingsToPrivateSpace(files) },
                                            onOpenRecordingGuide = {
                                                recordingGuideOpenedFromSettings = false
                                                overlay = OverlayScreen.RECORDING_GUIDE
                                            },
                                            onOpenRecordingSettings = { overlay = OverlayScreen.RECORDING_SETTINGS },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.RECORDING_SETTINGS -> com.ashudialer.app.ui.screens.RecordingSettingsScreen(
                                            onBack = { overlay = OverlayScreen.RECORDINGS },
                                            onOpenGuide = {
                                                recordingGuideOpenedFromSettings = false
                                                overlay = OverlayScreen.RECORDING_GUIDE
                                            },
                                            onOpenRootSetup = { overlay = OverlayScreen.RECORDING_ROOT_SETUP },
                                            onOpenAccessibilitySetup = { overlay = OverlayScreen.RECORDING_ACCESSIBILITY_SETUP },
                                            onOpenAdbSetup = { overlay = OverlayScreen.RECORDING_ADB_SETUP },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.RECORDING_ROOT_SETUP -> com.ashudialer.app.ui.screens.RootRecordingSetupScreen(
                                            onBack = { overlay = OverlayScreen.RECORDING_SETTINGS },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.RECORDING_ACCESSIBILITY_SETUP -> com.ashudialer.app.ui.screens.AccessibilityRecordingSetupScreen(
                                            onBack = { overlay = OverlayScreen.RECORDING_SETTINGS },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.RECORDING_ADB_SETUP -> com.ashudialer.app.ui.screens.AdbRecordingSetupScreen(
                                            onBack = { overlay = OverlayScreen.RECORDING_SETTINGS },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.UPDATE_CHECK -> {
                                            LaunchedEffect(Unit) {
                                                if (updateCheck == null) {
                                                    updateCheckBusy = true
                                                    updateCheck = updateChecker.check()
                                                    updateCheckBusy = false
                                                }
                                            }
                                            com.ashudialer.app.ui.screens.UpdateScreen(
                                                result = updateCheck,
                                                busy = updateCheckBusy,
                                                installing = updateInstallBusy,
                                                onBack = { overlay = OverlayScreen.NONE },
                                                onCheck = {
                                                    updateCheckBusy = true
                                                    scope.launch {
                                                        updateCheck = updateChecker.check()
                                                        updateCheckBusy = false
                                                    }
                                                },
                                                onInstallUpdate = {
                                                    val url = updateCheck?.apkDownloadUrl
                                                    if (url != null) {
                                                        updateInstallBusy = true
                                                    scope.launch {
                                                        val destination = java.io.File(context.cacheDir, "updates/AshuPhone-update.apk")
                                                        val ok = updateChecker.downloadApk(url, destination)
                                                        updateInstallBusy = false
                                                        if (ok) {
                                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                                                !context.packageManager.canRequestPackageInstalls()) {
                                                                try {
                                                                    val settingsIntent = Intent(
                                                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                                                        Uri.parse("package:${context.packageName}")
                                                                    )
                                                                    context.startActivity(settingsIntent)
                                                                    Toast.makeText(context, "Allow Ashu Dialer to install updates, then tap Download & install again.", Toast.LENGTH_LONG).show()
                                                                } catch (_: Exception) {
                                                                    Toast.makeText(context, "Please allow installs from this app in Android settings.", Toast.LENGTH_LONG).show()
                                                                }
                                                            } else {
                                                                try {
                                                                    val uri = FileProvider.getUriForFile(context, "com.ashudialer.app.fileprovider", destination)
                                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                                        setDataAndType(uri, "application/vnd.android.package-archive")
                                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                    }
                                                                    context.startActivity(intent)
                                                                } catch (_: Exception) {
                                                                    Toast.makeText(context, "Couldn't open the installer", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        } else {
                                                            android.widget.Toast.makeText(context, "Update download failed", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        OverlayScreen.RECORDING_GUIDE -> com.ashudialer.app.ui.screens.RecordingGuideScreen(
                                            onBack = {
                                                overlay = if (recordingGuideOpenedFromSettings) OverlayScreen.SETTINGS else OverlayScreen.RECORDINGS
                                            },
                                            onConfirmEnable = if (recordingGuideOpenedFromSettings) {
                                                {
                                                    viewModel.setCallRecordingEnabled(true)
                                                    overlay = OverlayScreen.SETTINGS
                                                }
                                            } else null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.SIM_ROUTING -> SimRoutingScreen(
                                            availableSims = availableSims,
                                            rules = simRoutingRules,
                                            onBack = { overlay = OverlayScreen.NONE },
                                            onAddRule = { number, simId -> viewModel.setSimRoutingRule(number, simId) },
                                            onRemoveRule = { number -> viewModel.removeSimRoutingRule(number) },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.VIBRATION_PATTERNS -> VibrationPatternsScreen(
                                            rules = vibrationRules,
                                            onBack = { overlay = OverlayScreen.NONE },
                                            onAddRule = { number, patternId -> viewModel.setVibrationRule(number, patternId) },
                                            onRemoveRule = { number -> viewModel.removeVibrationRule(number) },
                                            onTestPattern = { pattern -> testVibrationPattern(pattern) },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.LOCAL_BACKUP -> LocalBackupScreen(
                                            onBack = {
                                                overlay = OverlayScreen.NONE
                                                pendingImportBytes = null
                                                pendingImportFileName = null
                                                viewModel.clearLocalBackupStatus()
                                            },
                                            onExport = { pin ->
                                                viewModel.exportLocalBackup(pin) {
                                                    exportBackupLauncher.launch("ashudialer_backup.adlb")
                                                }
                                            },
                                            onPickImportFile = { importPickerLauncher.launch(arrayOf("*/*")) },
                                            pendingImportFileName = pendingImportFileName,
                                            onConfirmImport = { pin ->
                                                val bytes = pendingImportBytes
                                                if (bytes != null) viewModel.importLocalBackup(bytes, pin)
                                            },
                                            statusMessage = localBackupStatusFlow,
                                            isBusy = localBackupBusyFlow,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        OverlayScreen.NONE -> {}
                                    }
                                    }
                                }
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = selectedContactForDetail != null,
                                enter = androidx.compose.animation.slideInVertically(
                                    animationSpec = androidx.compose.animation.core.tween(280)
                                ) { fullHeight -> fullHeight / 4 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(280)),
                                exit = androidx.compose.animation.slideOutVertically(
                                    animationSpec = androidx.compose.animation.core.tween(220)
                                ) { fullHeight -> fullHeight / 4 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(220))
                            ) {
                                // Re-derive from the live `contacts` list by id rather than
                                // using the frozen snapshot directly. selectedContactForDetail
                                // is only set once, at the moment the person taps into this
                                // screen; without this, toggling favorite (or any other field
                                // change - photo, name) here updated the database and the live
                                // list correctly, but this screen kept showing the stale value
                                // from that original tap until the person backed out and back in.
                                val contact = selectedContactForDetail?.let { snapshot ->
                                    contacts.find { it.contactId == snapshot.contactId } ?: snapshot
                                }
                                if (contact != null) {
                                val isBlocked = blockedNumbers.any { phoneNumbersMatch(it.phoneNumber, contact.phoneNumber) }


                                val contactNote = callNotes
                                    .filter { phoneNumbersMatch(it.phoneNumber, contact.phoneNumber) }
                                    .maxByOrNull { it.createdAtMillis }
                                var noteDraft by remember(contact.phoneNumber) {
                                    mutableStateOf(contactNote?.text ?: "")
                                }
                                ContactDetailScreen(
                                    contact = contact,
                                    isBlocked = isBlocked,
                                    isFavorite = contact.isFavorite,
                                    videoCallAvailable = currentUser != null,
                                    hasEmail = contactEmail != null,
                                    callHistory = contactCallHistory,
                                    noteText = noteDraft,
                                    onBack = { selectedContactForDetail = null },
                                    onCall = { placeCall(contact.phoneNumber) },
                                    onMessage = { sendMessage(contact.phoneNumber) },
                                    onWhatsApp = { openWhatsApp(contact.phoneNumber) },
                                    onVideoCall = {
                                        val intent = com.ashudialer.app.telecom.VideoCallActivity.callerIntent(
                                            context, contact.phoneNumber, contact.displayName
                                        )
                                        context.startActivity(intent)
                                    },
                                    onEmail = {
                                        val email = contactEmail
                                        if (email != null) {
                                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onToggleFavorite = {
                                        viewModel.toggleContactFavorite(contact.contactId, contact.isFavorite)
                                    },
                                    onNoteChange = { newText ->
                                        noteDraft = newText
                                        viewModel.saveContactNote(
                                            phoneNumber = contact.phoneNumber,
                                            callerLabel = contact.displayName,
                                            text = newText,
                                            existingNotes = callNotes
                                        )
                                    },
                                    onChangePhoto = { photoPickerLauncher.launch("image/*") },
                                    onShareContact = {
                                        com.ashudialer.app.data.ContactShareHelper.share(context, contact)
                                    },
                                    onAddToHomeScreen = {
                                        if (com.ashudialer.app.data.ContactShortcutHelper.isSupported(context)) {
                                            com.ashudialer.app.data.ContactShortcutHelper.pin(context, contact)
                                        } else {
                                            Toast.makeText(context, "Your launcher doesn't support home screen shortcuts", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onShareNumber = { number ->
                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, number)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share number"))
                                    },
                                    onOpenCallHistory = {
                                        callHistoryPageContact = contact
                                        selectedContactForDetail = null
                                    },
                                    onDeleteCallHistory = {
                                        viewModel.deleteCallHistoryForNumber(contact.phoneNumber)
                                        contactCallHistory = emptyList()
                                    },
                                    onDeleteSingleCall = { entry ->
                                        viewModel.deleteSingleCallHistoryEntry(entry)
                                        contactCallHistory = contactCallHistory.filterNot {
                                            it.timestampMillis == entry.timestampMillis && it.phoneNumber == entry.phoneNumber
                                        }
                                    },
                                    onToggleBlock = {
                                        if (isBlocked) {
                                            val entry = blockedNumbers.firstOrNull { phoneNumbersMatch(it.phoneNumber, contact.phoneNumber) }
                                            if (entry != null) viewModel.unblockNumber(entry)
                                        } else {
                                            viewModel.blockNumber(contact.phoneNumber)
                                        }
                                    },
                                    onOpenVibrationPattern = {
                                        selectedContactForDetail = null
                                        overlay = OverlayScreen.VIBRATION_PATTERNS
                                    },
                                    onOpenSimRouting = {
                                        selectedContactForDetail = null
                                        overlay = OverlayScreen.SIM_ROUTING
                                    },
                                    currentRingtoneLabel = remember(contactRingtoneUri) {
                                        val uriString = contactRingtoneUri
                                        if (uriString == null) {
                                            "Default"
                                        } else {
                                            try {
                                                android.media.RingtoneManager.getRingtone(context, Uri.parse(uriString))
                                                    ?.getTitle(context) ?: "Default"
                                            } catch (e: Exception) {
                                                "Default"
                                            }
                                        }
                                    },
                                    onOpenRingtonePicker = {
                                        val pickerIntent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_RINGTONE)
                                            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                            val existing = contactRingtoneUri
                                            putExtra(
                                                android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                                if (existing != null) Uri.parse(existing) else null
                                            )
                                        }
                                        ringtonePickerLauncher.launch(pickerIntent)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                }
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = callHistoryPageContact != null,
                                enter = androidx.compose.animation.slideInHorizontally(
                                    animationSpec = androidx.compose.animation.core.tween(260)
                                ) { fullWidth -> fullWidth / 3 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)),
                                exit = androidx.compose.animation.slideOutHorizontally(
                                    animationSpec = androidx.compose.animation.core.tween(220)
                                ) { fullWidth -> fullWidth / 3 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180))
                            ) {
                                val contact = callHistoryPageContact
                                if (contact != null) {
                                    com.ashudialer.app.ui.screens.CallHistoryScreen(
                                        contact = contact,
                                        history = callHistoryPageEntries,
                                        onBack = { callHistoryPageContact = null },
                                        onCall = { placeCall(contact.phoneNumber) },
                                        onDeleteEntry = { entry ->
                                            viewModel.deleteSingleCallHistoryEntry(entry)
                                            callHistoryPageEntries = callHistoryPageEntries.filterNot { it.timestampMillis == entry.timestampMillis && it.phoneNumber == entry.phoneNumber }
                                        },
                                        onDeleteAll = {
                                            viewModel.deleteCallHistoryForNumber(contact.phoneNumber)
                                            callHistoryPageEntries = emptyList()
                                        }
                                    )
                                }
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = unknownNumberForDetail != null,
                                enter = androidx.compose.animation.slideInVertically(
                                    animationSpec = androidx.compose.animation.core.tween(280)
                                ) { fullHeight -> fullHeight / 4 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(280)),
                                exit = androidx.compose.animation.slideOutVertically(
                                    animationSpec = androidx.compose.animation.core.tween(220)
                                ) { fullHeight -> fullHeight / 4 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(220))
                            ) {
                                val number = unknownNumberForDetail
                                if (number != null) {
                                    val isBlockedNumber = blockedNumbers.any { phoneNumbersMatch(it.phoneNumber, number) }
                                    val unknownNote = callNotes
                                        .filter { phoneNumbersMatch(it.phoneNumber, number) }
                                        .maxByOrNull { it.createdAtMillis }
                                    var unknownNoteDraft by remember(number) {
                                        mutableStateOf(unknownNote?.text ?: "")
                                    }
                                    UnknownNumberDetailScreen(
                                        phoneNumber = number,
                                        displayLabel = number,
                                        isBlocked = isBlockedNumber,
                                        callHistory = unknownNumberCallHistory,
                                        noteText = unknownNoteDraft,
                                        onNoteChange = { text ->
                                            unknownNoteDraft = text
                                            viewModel.saveContactNote(
                                                phoneNumber = number,
                                                callerLabel = number,
                                                text = text,
                                                existingNotes = callNotes
                                            )
                                        },
                                        onBack = { unknownNumberForDetail = null },
                                        onCall = { placeCall(number) },
                                        onMessage = { sendMessage(number) },
                                        onAddToContacts = {
                                            dialerAddContactNumber = number
                                            unknownNumberForDetail = null
                                            overlay = OverlayScreen.ADD_CONTACT
                                        },
                                        onToggleBlock = {
                                            if (isBlockedNumber) {
                                                blockedNumbers.firstOrNull { phoneNumbersMatch(it.phoneNumber, number) }
                                                    ?.let { viewModel.unblockNumber(it) }
                                            } else {
                                                viewModel.blockNumber(number)
                                            }
                                        },
                                        onReportSpam = {
                                            viewModel.blockNumber(number)
                                            unknownNumberForDetail = null
                                        },
                                        onDeleteSingleCall = { entry ->
                                            viewModel.deleteSingleCallHistoryEntry(entry)
                                            unknownNumberCallHistory = unknownNumberCallHistory.filterNot {
                                                it.timestampMillis == entry.timestampMillis && it.phoneNumber == entry.phoneNumber
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }

                    if (showThemePicker) {
                        ThemePickerSheet(
                            currentThemeId = themeId,
                            onSelect = { id -> viewModel.setTheme(id) },
                            onDismiss = { showThemePicker = false }
                        )
                    }

                    pendingLockedCall?.let { lockedNumber ->
                        LockedNumberPinDialog(
                            onDismiss = { pendingLockedCall = null },
                            onVerify = { attempt -> viewModel.verifyPrivateSpacePassword(attempt) },
                            onVerified = {
                                pendingLockedCall = null
                                placeCallDirect(lockedNumber)
                            }
                        )
                    }

                    pendingCropUri?.let { uri ->
                        com.ashudialer.app.ui.components.PhotoCropDialog(
                            imageUri = uri,
                            onCancel = {
                                pendingCropUri = null
                                cropIsForNewContact = false
                            },
                            onCropped = { croppedBytes ->
                                val forNewContact = cropIsForNewContact
                                pendingCropUri = null
                                cropIsForNewContact = false
                                if (forNewContact) {
                                    // AddContactScreen's contact doesn't exist yet, so
                                    // there's no contactId to call updateContactPhoto
                                    // with - the bytes are held here and passed back
                                    // into AddContactScreen as croppedPhotoBytes, which
                                    // it carries into NewContactInput on Save (see
                                    // ContactsRepository.insertContact's photoJpegBytes
                                    // param - saved atomically with the rest of the
                                    // new contact, not a separate follow-up write).
                                    newContactPhotoBytes = croppedBytes
                                } else {
                                    val contact = selectedContactForDetail
                                    if (contact != null) {
                                        // updateContactPhoto() already refreshes the live
                                        // contacts list, and the contact-detail screen
                                        // re-derives its displayed contact from that same
                                        // live list (see the reactive `contact` lookup
                                        // below), so the new photo appears immediately
                                        // without needing to leave and re-enter the screen.
                                        viewModel.updateContactPhoto(contact.contactId, croppedBytes)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}


private fun queryDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    } catch (_: Exception) {
        null
    }
}


@Composable
private fun ReturnToCallBanner(
    call: android.telecom.Call,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val number = call.details?.handle?.schemeSpecificPart ?: "Unknown"
    var resolvedName by remember(number) { mutableStateOf<String?>(null) }
    LaunchedEffect(number) {
        val app = context.applicationContext as? com.ashudialer.app.AshuDialerApp
        resolvedName = if (app != null && number.isNotBlank() && number != "Unknown") {
            app.contactsRepository.lookupNameForNumber(number)?.displayName
        } else {
            null
        }
    }
    val callerLabel = call.details?.callerDisplayName?.takeIf { it.isNotBlank() } ?: resolvedName ?: number
    val statusLabel = when (call.state) {
        android.telecom.Call.STATE_RINGING -> "Incoming call"
        android.telecom.Call.STATE_HOLDING -> "On hold"
        android.telecom.Call.STATE_DIALING, android.telecom.Call.STATE_CONNECTING -> "Calling…"
        else -> "Ongoing call"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.callGreen)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(statusLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(callerLabel, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text("Tap to return", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
