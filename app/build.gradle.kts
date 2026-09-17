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
        versionCode = 9
        versionName = "1.5.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // SIZE FIX (without removing any feature): this app is distributed
        // as a plain .apk built via `assembleRelease` (see
        // .github/workflows/build.yml - NOT an .aab/App Bundle, which
        // would otherwise let Google Play auto-generate one slim APK per
        // device automatically), so without this, a single APK ships
        // native .so libraries for every ABI Android supports - most
        // significantly stream-webrtc-android's native WebRTC binary,
        // which is large per architecture, plus Firebase's smaller native
        // pieces on top. abiFilters keeps arm64-v8a (every 64-bit device -
        // required by Google Play policy since Aug 2019, and what modern
        // devices including this app's own primary test targets, Redmi 12
        // 5G / POCO M6 Pro 5G, actually run) and armeabi-v7a (32-bit ARM -
        // still genuinely in use on older/budget Indian devices, so kept
        // rather than dropped) and drops x86/x86_64, which exist purely
        // for Intel-based emulators and essentially never appear on a
        // real phone this app's users would install it on. This changes
        // nothing about what the app does or which features it has - it
        // only stops bundling native code compiled for CPU architectures
        // none of this app's actual users run.
        // SIZE FIX (without removing any feature): this app has no
        // values-*/ locale folders of its own (confirmed - every string
        // shown anywhere in this app's UI is a plain Kotlin string
        // literal in the source, not a string resource pulled from
        // res/values-<locale>/), so it never actually reads a localized
        // resource string from Firebase/Play Services/AndroidX at
        // runtime regardless of the device's language. Those libraries
        // each ship their own translated strings for dozens of
        // languages, all bundled into every APK by default whether or
        // not the app itself ever displays any of them. resConfigs keeps
        // only the locale qualifiers actually meaningful here (the
        // default/English resources, which is what would render
        // regardless) and drops every other language's copy of those
        // libraries' resource strings - this doesn't remove or change
        // any UI text this app itself shows, since none of it was ever
        // sourced from those dropped resources to begin with.
        resourceConfigurations += listOf("en")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("normal") {
            dimension = "distribution"
            buildConfigField("boolean", "CALL_RECORDING_ENABLED", "false")
        }
        create("root") {
            dimension = "distribution"
            buildConfigField("boolean", "CALL_RECORDING_ENABLED", "true")
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
            // SIZE FIX (without removing any feature): isMinifyEnabled
            // above already shrinks/obfuscates CODE via R8, but that's a
            // separate pass from resources - without this, every drawable,
            // layout, and string in the compiled resource table ships in
            // the APK whether or not any code path actually references it
            // anymore (common after months of UI iteration - old drawables
            // from a since-replaced icon, string resources for a removed
            // string, etc.). isShrinkResources runs R8's resource shrinker,
            // which traces actual reachability from code exactly like the
            // code shrinker does, and only removes resources it can prove
            // are unreachable - it does not touch or guess about anything
            // a real code path can still reach, so this cannot silently
            // drop something in use the way manually deleting drawables by
            // hand could. Requires isMinifyEnabled = true (already set
            // above) since it reads R8's own reachability analysis.
            isShrinkResources = true
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
            // SIZE FIX (without removing any feature): every one of these
            // is build/legal metadata that several of this app's
            // dependencies (Firebase, Play Services, Kotlin coroutines,
            // AndroidX) each bundle their own duplicate copy of inside
            // META-INF/ - none of them are read at runtime by anything,
            // they're purely packaging artifacts left over from how those
            // libraries are published to Maven. Excluding them is
            // equivalent to the AL2.0/LGPL2.1 exclusion already above,
            // just covering the handful of other well-known duplicate
            // patterns that show up once a project has this many
            // dependencies - none of these have ever been license files
            // this app is obligated to ship (the actual required
            // attributions live in the app's own about/licenses screen,
            // not in these per-library build metadata files).
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/LICENSE-notice.md"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "/META-INF/versions/9/previous-compilation-data.bin"
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
    implementation("io.getstream:stream-webrtc-android:1.3.10")


    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
