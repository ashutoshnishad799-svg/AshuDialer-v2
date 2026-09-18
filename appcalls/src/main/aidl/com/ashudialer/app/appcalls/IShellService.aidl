// Ported and trimmed from ShizuCallRecorder (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
// Dropped grantAppOpByPackage/grantRole from the upstream interface: those exist there to grant
// MANAGE_ONGOING_CALLS for an InCallService-based detection mode, which this module doesn't use
// (AshuDialer already detects WhatsApp/Telegram calls via NotificationListenerService instead -
// see AppCallNotificationListenerService.kt). Keeping the interface to only what's called avoids
// carrying an unused, permission-adjacent code path into this app.
package com.ashudialer.app.appcalls;

import android.os.ParcelFileDescriptor;
import com.ashudialer.app.appcalls.ILogCallback;

interface IShellService {
    ParcelFileDescriptor startRecording(
        String audioSource,
        String audioCodec,
        int audioBitRate,
        String serverPath,
        boolean isDebuggingModeEnabled,
        ILogCallback appLoggerCallback
    ) = 1;

    void stopRecording() = 2;

    boolean isRecording() = 3;

    // The special Shizuku transaction code for "destroy" process - MUST stay at this exact value;
    // Shizuku's UserService binder calls this specific transaction code when it wants a user
    // service torn down, regardless of what AIDL method number this file assigns elsewhere.
    void destroy() = 16777114;
}
