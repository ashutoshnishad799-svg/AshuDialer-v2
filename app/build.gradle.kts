plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}


tasks.register("checkGoogleServicesPackageName") {
    doLast {
        val jsonFile = file("google-services.json")
        val expectedPackage = "com.ashudialer.app"

        if (!jsonFile.exists()) {
            logger.error("")
            logger.error("========================================================================")
            logger.error("  ❌ app/google-services.json NOT FOUND")
            logger.error("  Firebase sign-in/cloud-backup will be silently disabled at runtime")
            logger.error("  (the app itself is coded to fail soft — it will NOT crash — but the")
            logger.error("  Account tab's sign-in button will show 'Sign-in isn't set up yet').")
            logger.error("========================================================================")
            logger.error("")
            return@doLast
        }

        val text = jsonFile.readText()

        val match = Regex("\"package_name\"\\s*:\\s*\"([^\"]+)\"").find(text)
        val foundPackages = Regex("\"package_name\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(text).map { it.groupValues[1] }.toSet()

        if (match == null) {
            logger.error("")
            logger.error("========================================================================")
            logger.error("  ❌ google-services.json found but no package_name field could be read")
            logger.error("  This usually means the file is corrupted, truncated, or not valid JSON.")
            logger.error("  Bytes: ${text.length}")
            logger.error("========================================================================")
            logger.error("")
            return@doLast
        }

        if (expectedPackage !in foundPackages) {
            logger.error("")
            logger.error("========================================================================")
            logger.error("  ❌ PACKAGE NAME MISMATCH — this is almost certainly your crash cause")
            logger.error("")
            logger.error("  app/build.gradle.kts applicationId : $expectedPackage")
            logger.error("  google-services.json package_name  : ${foundPackages.joinToString(", ")}")
            logger.error("")
            logger.error("  Fix: in Firebase Console → Project Settings → Your apps → Android app,")
            logger.error("  the Android package name MUST be exactly '$expectedPackage'.")
            logger.error("  If it isn't, add a NEW Android app in Firebase Console with that exact")
            logger.error("  package name, download ITS google-services.json, base64 it, and replace")
            logger.error("  the GOOGLE_SERVICES_JSON GitHub secret with the new value.")
            logger.error("========================================================================")
            logger.error("")
        } else {
            logger.lifecycle("✅ google-services.json package_name matches applicationId ($expectedPackage)")
        }
    }
}

tasks.matching { it.name == "processDebugGoogleServices" || it.name == "processReleaseGoogleServices" }
    .configureEach {
        dependsOn("checkGoogleServicesPackageName")
    }

android {
    namespace = "com.ashudialer.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ashudialer.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 8
        versionName = "1.5.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("normal") {
            dimension = "distribution"
            buildConfigField("boolean", "CALL_RECORDING_ENABLED", "false")
            // Live captions (speech-to-text of the other person's voice) on
            // a real carrier call need the same protected far-end audio
            // access as call recording - see CallRecorder's extensive
            // comments on setRecordSilenced. The Normal build already can't
            // reliably record that audio, so it can't reliably caption it
            // either; captions on this flavor are scoped to WebRTC data
            // calls only (see VideoCallActivity), where the remote
            // MediaStream's audio track is directly available to the app
            // with no carrier/OEM audio-policy restriction at all.
            buildConfigField("boolean", "CARRIER_CALL_CAPTIONS_ENABLED", "false")
        }
        create("root") {
            dimension = "distribution"
            buildConfigField("boolean", "CALL_RECORDING_ENABLED", "true")
            // Same protected-audio access this flavor already has for
            // recording (VOICE_CALL / RECORD_BACKGROUND_AUDIO or the
            // Magisk-privileged path) is what makes carrier-call captions
            // reliable here too, not a separate permission or capability.
            buildConfigField("boolean", "CARRIER_CALL_CAPTIONS_ENABLED", "true")
        }
    }

    signingConfigs {
        create("release") {
            // Values come from gradle.properties (local dev) or from
            // -P command-line args (CI, see build.yml). If any of these
            // four aren't set, this signingConfig is left incomplete and
            // Gradle falls back to debug-signing the release build instead
            // of crashing the build outright - see the check below.
            val ksFile = project.findProperty("ASHU_RELEASE_STORE_FILE") as String?
            val ksPass = project.findProperty("ASHU_RELEASE_STORE_PASSWORD") as String?
            val keyAliasProp = project.findProperty("ASHU_RELEASE_KEY_ALIAS") as String?
            val keyPass = project.findProperty("ASHU_RELEASE_KEY_PASSWORD") as String?

            if (ksFile != null && ksPass != null && keyAliasProp != null && keyPass != null) {
                storeFile = file(ksFile)
                storePassword = ksPass
                keyAlias = keyAliasProp
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (signingConfigs.getByName("release").storeFile != null) {
                signingConfigs.getByName("release")
            } else {
                // No release keystore props supplied - fall back to debug
                // signing so a local `./gradlew assembleRelease` doesn't
                // hard-fail with zero config. CI always supplies the four
                // ASHU_RELEASE_* properties (from GitHub Secrets), so
                // production builds never actually hit this branch.
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // ExperimentalFoundationApi covers combinedClickable (used for
        // long-press-to-select/copy interactions across several screens -
        // RecordingsScreen, ContactsScreen, RecentsScreen, ContactDetailScreen).
        // Opting in at the module level here means any future use of this or
        // other experimental Foundation APIs doesn't need its own per-file
        // @file:OptIn - those per-file annotations already added are still
        // valid (redundant but harmless) alongside this.
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")


    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")


    implementation("com.google.accompanist:accompanist-permissions:0.34.0")


    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")


    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")


    implementation("androidx.datastore:datastore-preferences:1.1.1")


    implementation("io.coil-kt:coil-compose:2.6.0")


    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.android.gms:play-services-auth:21.2.0")


    implementation("io.getstream:stream-webrtc-android:1.1.1")

    // Live call captions (speech-to-text of the other party's voice - see
    // CallCaptionEngine). android.speech.SpeechRecognizer was deliberately
    // NOT used here: it always listens to the live device microphone and
    // has no API to feed it a specific audio source instead, which rules
    // it out for both of this feature's real audio sources - the WebRTC
    // remote MediaStream's audio track (VideoCallActivity) and, on the
    // Root build only, the same protected VOICE_CALL-style source
    // CallRecorder already uses. Vosk's Recognizer.acceptWaveForm(ByteArray)
    // takes raw 16-bit PCM directly, independent of where that PCM came
    // from, which is exactly the audio-source-agnostic shape this feature
    // needs. Fully on-device/offline (no per-call cloud STT cost or a
    // third-party service seeing call audio) and Apache-2.0 licensed,
    // compatible with this project's own GPLv3 license.
    implementation("com.alphacephei:vosk-android:0.3.75")
    implementation("net.java.dev.jna:jna:5.13.0@aar")


    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
