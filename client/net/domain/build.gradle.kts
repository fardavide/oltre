import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// **What is outstanding, read as a set of controls rather than as a list of verbs.** The outbox is a
// queue of `VerbEnvelope`s and every screen in the app has the same question about it — *is the thing
// I am about to draw one of them?* — so the answer is derived once, here, and never re-derived by a
// mapper.
//
// A `domain` module for the reason `:client:dispatch:domain` and `:client:tilt:domain` are ones: this
// is arithmetic over data, it decides something a screen must not, and it is testable without Compose
// and without a socket. It is also the layer both sides can legally reach — `data` may depend on
// `domain` (rule 2's mirror) and so may `presentation` (rule 3 forbids only `presentation -> data`),
// which is exactly what a fact that the outbox produces and eight screens consume needs.
//
// **Nothing points out of it**, which is the property `sharedSurfaces` asks for and the reason `net`
// could join that list: this depends on `:protocol` and on nothing else, `:client:net:data` depends on
// this and on `:protocol`, and neither reaches a feature. So `net` cannot become the back door one tab
// reaches another through — see the root build script.
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
        namespace = "dev.fardavide.oltre.client.net.domain"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`: every question here is asked with a `core` subject — a `BuildingType`, a
            // `WatchTarget`, an `AlertCategory` — and answered with an `IdempotencyKey`, which is
            // `:protocol`'s. A consumer that could not see both could call nothing. `:protocol`
            // exposes `:core` the same way, so a `BuildingType` travels with it.
            api(projects.protocol)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
