import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Everything the Fleets tab decides: the fold from `GameState` and the event log into the models
// `:client:fleets:ui` draws, the unit tests that pin it, and the seam test that renders the two
// together.
//
// **The Compose plugin arrived at 0.13, and it is `:client:galaxy:presentation`'s argument rather
// than a new one.** This module had none until then, deliberately — the note it replaces said "the
// build still fails the day somebody writes a composable here" — and what changed is that the tab
// gained something to decide. Which world has its sheet up is a decision, not a drawing: the
// stateful `FleetsScreen` holds it, re-derives the page from a `GameState` whenever one changes, and
// hands `FleetsPage` a frame. Splitting it the other way would put a `remember` in the leaf.
//
// The test for a third one is unchanged: does the module *decide*, or does it draw a frame it was
// handed. `:client:colony:presentation` and `:client:research:presentation` still draw.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "dev.fardavide.oltre.client.fleets.presentation"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
            // `api`, so the composition root names this feature once — see `:client:colony:presentation`.
            api(projects.client.fleets.ui)
            // The sheet a worked row raises, and the mapper that prices it. `implementation`:
            // `DispatchSelection` is this feature's own state and nothing outside names it —
            // the ui half arrives through `:client:fleets:ui`.
            implementation(projects.client.dispatch.presentation)
            implementation(projects.client.design.format)

            // A run counts down to a wall-clock instant and the ledger stamps what has landed, which
            // is the same pair of needs that put this on Colony, Research and Galaxy.
            implementation(libs.kotlinx.datetime)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        val desktopTest by getting {
            dependencies {
                // Brings the page, the robot, the JUnit harness and the desktop Skiko binary with
                // it — see that module's build file.
                implementation(projects.client.fleets.uiTesting)
                // The theme and the `Surface` the *stateful* screen is composed in. `FleetsScreen`
                // lives in this module, so its harness has to as well — a ui-layer module may not
                // depend on a presentation one.
                implementation(projects.client.design.core)
                implementation(libs.compose.material3)
            }
        }
    }
}
