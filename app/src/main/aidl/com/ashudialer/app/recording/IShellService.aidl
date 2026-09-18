package com.ashudialer.app.recording;
import android.os.ParcelFileDescriptor;
import com.ashudialer.app.recording.ILogCallback;
interface IShellService {
    ParcelFileDescriptor startRecording(String audioSource, String audioCodec, int audioBitRate, String serverPath, boolean isDebuggingModeEnabled, ILogCallback appLoggerCallback) = 1;
    void stopRecording() = 2;
    boolean isRecording() = 3;
    boolean grantAppOpByPackage(String packageName, String opName, int userProfileId) = 4;
    boolean grantRole(String packageName, String roleName, int userProfileId) = 5;
    void destroy() = 16777114;
}
