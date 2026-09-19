# ShellService is instantiated by Shizuku via reflection (see its own doc comment: it requires
# an exact no-arg constructor AND a single-Context constructor to be preserved). R8 renaming or
# stripping either constructor breaks Shizuku's binding at runtime with no build-time error, so
# this class - and the AIDL-generated Stub/Proxy classes it and its callers depend on - must be
# kept wholesale, matching how app/proguard-rules.pro already keeps com.ashudialer.app.telecom.**
# for the same reflection-based-binding reason (telecom framework binding, here Shizuku binding).
-keep class com.ashudialer.app.appcalls.ShellService {
    <init>();
    <init>(android.content.Context);
    *;
}
-keep class com.ashudialer.app.appcalls.IShellService { *; }
-keep class com.ashudialer.app.appcalls.IShellService$* { *; }
-keep class com.ashudialer.app.appcalls.ILogCallback { *; }
-keep class com.ashudialer.app.appcalls.ILogCallback$* { *; }

# AppCallNotificationListenerService is bound by the system NotificationListenerService
# framework via its manifest declaration, the same reflection-like binding class as InCallService.
-keep class com.ashudialer.app.appcalls.AppCallNotificationListenerService { *; }

# ---- Call recording (Shizuku) ---------------------------------------------------------------
# RecordingSession is @Parcelize and travels inside an Intent to RecordingForegroundService.
# R8 must not rename or strip its generated CREATOR field or the service would read a null
# session and silently never record. This only breaks in the minified RELEASE build, never in
# debug, which is exactly why it is kept explicitly.
-keep class com.ashudialer.app.appcalls.recording.RecordingSession { *; }
-keep class com.ashudialer.app.appcalls.recording.RecordingSession$Creator { *; }
-keep class com.ashudialer.app.appcalls.recording.CallDirection { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Started/triggered by the system through manifest entries (PHONE_STATE broadcast, foreground
# service intents, notification action buttons).
-keep class com.ashudialer.app.appcalls.recording.PhoneStateReceiver { *; }
-keep class com.ashudialer.app.appcalls.recording.PostRecordingActionReceiver { *; }
-keep class com.ashudialer.app.appcalls.recording.RecordingForegroundService { *; }

# Settings enums are persisted by their key string, but keep names stable for logs and mapping.
-keep enum com.ashudialer.app.appcalls.scrcpy.ScrcpyAudioSource { *; }
-keep enum com.ashudialer.app.appcalls.scrcpy.ScrcpyAudioCodec { *; }

# Shizuku's own classes use reflection internally.
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
