import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// No Compose plugin and no Compose dependency, which is the reason this is a module rather than
// four constants at the foot of `DispatchSheet.kt` — the shape `:client:debug:domain` set and
// `:client:tilt:domain` repeated. **What a held stepper *does* is a cadence**: a rest, then a first
// repeat, then a ramp down to a floor. That is arithmetic, and arithmetic belongs where it can be
// tested as arithmetic rather than asserted in a comment nobody can run.
//
// It earns the module the same way tilt's did. Motion numbers a session invents have to be flagged
// as invented (`.claude/rules/session-roles.md`), and a claim like *"55 hulls down to 3 in about two
// seconds"* is either a test or a guess. Here it is a test.
//
// **`ui` depends on this, which rule 4 allows and rule 2 is the mirror of**: a ui module may take
// its own feature's domain, and a domain module knows nothing of a screen. Nothing points out of it
// at all — not even `core` — which keeps `:client:dispatch` honest as a `sharedSurfaces` entry.
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
        namespace = "dev.fardavide.oltre.client.dispatch.domain"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
