import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Everything the dispatch sheet decides: what a world would give a fleet, what it refuses and why.
// The shapes it fills in are `:client:dispatch:ui`'s, which knows nothing about a `GameState`.
//
// **No Compose plugin, and none is wanted.** The sheet's own state — which world is open, and what
// has been chosen inside it — belongs to the screen that raised it, so there is no composable here
// and the build fails the day somebody writes one. `GalaxyScreen` holds that state today; the Fleets
// tab will hold its own.
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
        namespace = "dev.fardavide.oltre.client.dispatch.presentation"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
            // `api`, so a feature that raises this sheet names one module rather than two — the same
            // rule `:client:colony:presentation` follows for its own ui layer.
            api(projects.client.dispatch.ui)
            // The bell's own model. `implementation` rather than `api` for the reason
            // `:client:shipyard:presentation` uses it: what this module hands back is a
            // `DispatchUiState`, and the square inside it is `:client:dispatch:ui`'s to expose.
            implementation(projects.client.design.component)
            implementation(projects.client.design.format)
            // What the outbox means to the sheet's one holdable control — see the colony's
            // presentation module, which takes it the same way and for the same reason.
            implementation(projects.client.net.domain)
            implementation(projects.client.design.text)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// **The mapper's tests are in `:client:galaxy:presentation`, and that is not an omission.**
// `DispatchUiStateTest`'s own subject is the pairing this file's `probe` parameter exists for: the
// unsurveyed refusal offers a flight *only when the map card above it would honour one*, and the
// thing that decides that is `toProbeActionUiState`, which is Galaxy's. A copy of it here would be a
// second copy of exactly the decision the pairing keeps single. Kover aggregates by class rather
// than by module, so this module's coverage is measured wherever its tests happen to live.
