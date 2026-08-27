import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// **How a player proves who they are to Apple or to Google**, and the only module in the app whose
// job is finished before the game starts. What it produces is an ID token and the nonce that binds
// it; what happens to that token is `:client:net:data`'s, and what the player *sees* while it happens
// is `:client:auth:presentation`'s.
//
// **Three platforms and one shape.** The half of this that decides anything — minting a nonce,
// hashing it, building an authorize URL, reading a redirect back, refusing one whose `state` does not
// match — is in `commonMain` and is covered by plain `…Test`s, because none of it needs a browser.
// What is left per platform is the ceremony of *showing* a sheet, which is a few dozen lines each and
// is the part no machine in this repository can run. That split is deliberate and it is what
// `.claude/rules/session-roles.md` asks for; it is also the only way an integration nobody here can
// execute is reviewable rather than merely trusted.
//
// A `data` module, so rules 2–4 forbid it from seeing a `presentation` or a `ui`. That is the point:
// this module cannot build a word the player reads, so a refusal leaves here as a type.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "dev.fardavide.oltre.client.auth.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`: a sign-in hands back an `IdToken` and a `SignInNonce`, and names an
            // `AuthProvider` to say which button was pressed. All three are `:protocol`'s, which is
            // the only honest home for them — see `Auth.kt` there.
            api(projects.protocol)

            implementation(libs.kotlinx.coroutines.core)
            // The token endpoint answers JSON and the only question asked of it is whether there is an
            // `id_token` in it. `:core` already carries this codec for the save format, so it is a
            // dependency the app has rather than one this module adds.
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services)
            implementation(libs.google.id)
        }
    }
}
