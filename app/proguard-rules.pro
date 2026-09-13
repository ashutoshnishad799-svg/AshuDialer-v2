# Keep Room entities
-keep class com.ashudialer.app.data.** { *; }

# Keep InCallService / CallScreeningService (telecom framework binds these by reflection-like mechanisms)
-keep class com.ashudialer.app.telecom.** { *; }

# Firebase / Firestore model serialization relies on reflection over field names
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

