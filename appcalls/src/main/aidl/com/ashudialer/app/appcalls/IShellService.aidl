// Ported from ShizuCallRecorder (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
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

    /**
     * Runs `appops set --user <id> <package> <op> allow` with the shell identity. Kept so the
     * app can grant itself AppOps that a normal app cannot (e.g. MANAGE_ONGOING_CALLS).
     */
    boolean grantAppOpByPackage(String packageName, String opName, int userProfileId) = 4;

    // The special Shizuku transaction code for "destroy" process - MUST stay at this exact value;
    // Shizuku's UserService binder calls this specific transaction code when it wants a user
    // service torn down, regardless of what AIDL method number this file assigns elsewhere.
    void destroy() = 16777114;
}
