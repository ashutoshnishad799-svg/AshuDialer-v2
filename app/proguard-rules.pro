# Keep Room entities
-keep class com.ashudialer.app.data.** { *; }

# Keep InCallService / CallScreeningService (telecom framework binds these by reflection-like mechanisms)
-keep class com.ashudialer.app.telecom.** { *; }

# Firebase / Firestore model serialization relies on reflection over field names
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**


# Release builds drop verbose / debug / info logging (warnings and errors stay). Some of these messages name callers
# or notification titles, which should not sit in logcat on someone's phone.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
