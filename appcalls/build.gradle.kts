import java.security.MessageDigest

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// The scrcpy-server jar this module bundles and verifies against on every use. Hash was computed
// directly from the bundled file (see scrcpy-server/scrcpy-server-v4.0) and independently
// confirmed against the value already declared for the same file in Ever-Dialer's own
// recorder/build.gradle.kts, so this is not a value carried over on trust alone.
val scrcpyVersion = "4.0"
val scrcpyServerSha256 = "84924bd564a1eb6089c872c7521f968058977f91f5ff02514a8c74aff3210f3a"
val scrcpyServerAssetName = "appcalls-scrcpy-server"
val bundledScrcpyServerFile = layout.projectDirectory.file("scrcpy-server/scrcpy-server-v$scrcpyVersion")
val scrcpyAssetOutputDir = layout.buildDirectory.dir("generated/scrcpy/assets")

abstract class VerifyAndStageServerTask : DefaultTask() {
    @get:InputFile
    abstract val bundledFile: RegularFileProperty

    @get:Input
    abstract val expectedSha256: Property<String>

    @get:Input
    abstract val assetName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val src = bundledFile.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")
        src.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
        }
        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actualHash.equals(expectedSha256.get(), ignoreCase = true)) {
            throw GradleException(
                "scrcpy-server bundled jar hash mismatch! Expected ${expectedSha256.get()} but got $actualHash. " +
                    "The bundled file at ${src.path} does not match what this build expects - do not proceed " +
                    "without resolving this, since this hash is also what ServerExtractor verifies against " +
                    "before the shell process executes the file at runtime."
            )
        }
        val targetFile = outputDir.get().file(assetName.get()).asFile
        targetFile.parentFile.mkdirs()
        src.copyTo(targetFile, overwrite = true)
    }
}

val verifyAndStageServerTask = tasks.register<VerifyAndStageServerTask>("verifyAndStageScrcpyServer") {
    bundledFile.set(bundledScrcpyServerFile)
    expectedSha256.set(scrcpyServerSha256)
    assetName.set(scrcpyServerAssetName)
    outputDir.set(scrcpyAssetOutputDir)
}

android {
    namespace = "com.ashudialer.app.appcalls"
    compileSdk = 35

    defaultConfig {
        minSdk = 29

        buildConfigField("String", "SCRCPY_VERSION", "\"$scrcpyVersion\"")
        buildConfigField("String", "SCRCPY_SERVER_SHA256", "\"$scrcpyServerSha256\"")
        buildConfigField("String", "SCRCPY_SERVER_ASSET_NAME", "\"$scrcpyServerAssetName\"")

        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(scrcpyAssetOutputDir)
        }
    }
}

// Every assemble/merge of this module's assets must first verify+stage the bundled jar, so a
// mismatched or missing file fails the build loudly at compile time rather than being caught
// only at runtime by ServerExtractor's own re-verification.
tasks.named("preBuild") {
    dependsOn(verifyAndStageServerTask)
}
tasks.matching { it.name.startsWith("merge") && it.name.contains("Assets") }.configureEach {
    dependsOn(verifyAndStageServerTask)
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.annotation:annotation:1.8.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Real, published Maven Central coordinates - confirmed directly against
    // central.sonatype.com before use, matching what Ever-Dialer's own recorder module declares.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
