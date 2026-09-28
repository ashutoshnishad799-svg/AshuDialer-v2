# Ashu Phone - R8 / ProGuard rules for release builds.
# Copyright (C) 2026 Ashutosh Nishad. GPL-3.0-or-later.
#
# Goal: shrink + obfuscate as hard as possible WITHOUT breaking what the framework
# or reflection needs. Keep rules are kept as narrow as they can be, because every
# broad "-keep class ... { *; }" leaves that whole package readable in the APK.

# ---------------------------------------------------------------------------
# Obfuscation strength
# ---------------------------------------------------------------------------
# R8 already renames classes, methods and fields in release builds. These flags
# make it a little more aggressive without changing where kept classes live.
# (-repackageclasses is deliberately NOT used: this app has classes that Android,
# Shizuku and Parcelable read by their fully-qualified name, and moving packages
# gains little over renaming while adding a release-only crash risk that cannot
# be caught in a debug build.)
-allowaccessmodification
-overloadaggressively
-optimizationpasses 5

# R8 (AGP default) already runs in full mode: it renames everything that is not
# kept above to short names, merges classes and removes unused code. The narrow
# keep rules in this file are what let it do that to almost the whole app.

# Strip source file names and line numbers from stack traces in release builds so
# they do not leak original file names. (Upload the mapping.txt if you need to
# de-obfuscate your own crash reports.)
-renamesourcefileattribute ''
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# ---------------------------------------------------------------------------
# Room: entities / DAOs are accessed by generated code
# ---------------------------------------------------------------------------
# Only the Room-annotated classes and their members need to survive, not the
# entire data package (which also holds repositories and settings code).
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * { @androidx.room.* <fields>; }
-keep class com.ashudialer.app.data.db.Converters { *; }

# ---------------------------------------------------------------------------
# Android components the system instantiates by name from the manifest.
# (AGP already keeps Activity/Service/Receiver subclasses declared in the
# manifest; these are listed for the telecom services explicitly.)
# ---------------------------------------------------------------------------
-keep class com.ashudialer.app.telecom.PixelInCallService { *; }
-keep class com.ashudialer.app.telecom.PixelCallScreeningService { *; }

# ---------------------------------------------------------------------------
# AIDL / Shizuku user-service: bound across a process boundary by class name.
# ---------------------------------------------------------------------------
-keep class com.ashudialer.app.appcalls.IShellService { *; }
-keep class com.ashudialer.app.appcalls.IShellService$* { *; }
-keep class com.ashudialer.app.appcalls.ILogCallback { *; }
-keep class com.ashudialer.app.appcalls.ILogCallback$* { *; }
-keep class com.ashudialer.app.appcalls.ShellService { *; }

# ---------------------------------------------------------------------------
# Firebase / Firestore
# ---------------------------------------------------------------------------
# Firestore maps documents to objects by field name. Only classes actually used
# as Firestore models need their names kept; the SDK ships its own consumer
# rules for itself, so the old blanket keep on com.google.firebase.** is dropped.
-dontwarn com.google.firebase.**

# WebRTC native library calls back into Java by name.
-keep class org.webrtc.** { *; }
-keep class io.getstream.webrtc.** { *; }
-dontwarn org.webrtc.**

# ---------------------------------------------------------------------------
# Logging: release builds drop verbose / debug / info logging. Warnings and
# errors stay (they are needed for real bug reports).
# ---------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Kotlin intrinsics null-check messages embed parameter names for every public
# function. Removing them costs nothing at runtime and removes a lot of readable
# metadata from the compiled app.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(java.lang.Object);
    public static void checkNotNull(java.lang.Object, java.lang.String);
    public static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    public static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    public static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    public static void checkNotNullParameter(java.lang.Object, java.lang.String);
}
