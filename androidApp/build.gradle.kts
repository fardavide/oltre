// The Android application — a manifest, the launcher icons and this file. There is deliberately
// no Kotlin here: every entry point in the project lives in `:client:shell` beside the others
// (`Main.kt` for desktop, `MainViewController.kt` for iOS, `MainActivity.kt` for Android), and
// the manifest names the Activity across the module boundary.
//
// It exists at all because AGP 9 stopped the Kotlin Multiplatform plugin working alongside
// `com.android.application`, so the shell — which already *is* the desktop application, via the
// `compose.desktop.application` block in its own build file — cannot also be the Android one.
// That is a packaging accident rather than an architectural difference: `iosApp/` is the same
// wrapper around the same composition root, and escapes rule 7 only by being an Xcode project
// rather than a Gradle module. See `.claude/docs/decisions.md` for the carve-out argument.
plugins {
    alias(libs.plugins.androidApplication)
}

val oltreVersion = libs.versions.oltre.get()

// `major * 10_000 + minor * 100 + patch`, so 0.2.0 is 200 and every bump the versioning
// convention allows moves it upward. The package manager refuses an update whose code did not
// increase, and a hand-maintained integer beside a hand-maintained version string is a second
// thing to forget — this one is read from the catalogue that already holds the version.
val oltreVersionCode = oltreVersion.split(".")
    .map(String::toInt)
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

// Signing reaches CI as secrets and never as a file in the repository. Absent — a local build,
// or a pull request from a fork — the release variant is simply unsigned, so `./gradlew
// assemble` still works everywhere; only the release workflow needs the real key, and it
// checks for it before it builds anything.
//
// Read through `providers` rather than `System.getenv`, so the configuration cache treats the
// values as inputs instead of baking the first build's answer into every later one.
val keystoreFile = providers.environmentVariable("OLTRE_KEYSTORE_FILE")
val keystorePassword = providers.environmentVariable("OLTRE_KEYSTORE_PASSWORD")
val keystoreAlias = providers.environmentVariable("OLTRE_KEY_ALIAS")
val keystoreKeyPassword = providers.environmentVariable("OLTRE_KEY_PASSWORD")
val signingIsConfigured = listOf(keystoreFile, keystorePassword, keystoreAlias, keystoreKeyPassword)
    .all { it.isPresent }

android {
    namespace = "dev.fardavide.oltre.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // The iOS bundle identifier, so one name follows the game across both stores.
        applicationId = "dev.fardavide.oltre"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = oltreVersionCode
        versionName = oltreVersion
    }

    signingConfigs {
        if (signingIsConfigured) {
            create("release") {
                storeFile = file(keystoreFile.get())
                storePassword = keystorePassword.get()
                keyAlias = keystoreAlias.get()
                keyPassword = keystoreKeyPassword.get()
            }
        }
    }

    buildTypes {
        getByName("release") {
            // Off, and not by oversight: nothing has audited keep rules for Compose, kotlinx
            // -serialization (which carries the save format) or the reflection Compose's
            // resource loader does. A preview channel that shrinks wrong ships a build that
            // crashes where the debug build does not, and the APK is ~20 MB either way.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // The one dependency, and the only edge in the build that reaches the composition root.
    implementation(projects.client.shell)
}
