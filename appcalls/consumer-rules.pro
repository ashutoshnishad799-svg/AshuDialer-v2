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
