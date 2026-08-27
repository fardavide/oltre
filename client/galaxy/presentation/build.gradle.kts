import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Everything the Galaxy tab decides. Four mappers — the page, the ruler, the probe footer and the
// dispatch sheet — plus `GalaxyScreen`, which is the one composable in the repository that lives in
// a presentation module.
//
// **That is deliberate and it is what the layer is for.** Which system is on screen and which world
// has its sheet up are decisions, not drawings: `GalaxyScreen` holds them, re-derives the page from
// a `GameState` whenever one changes, and hands `GalaxyPage` a frame. Splitting it the other way —
// the state in `ui`, the mapping here — would put a `remember` in the leaf and leave this module
// unable to answer what the screen is currently showing.
//
// So the Compose plugins are here, and this is the only `presentation` module that has them. The
// test for whether a second one should is the same as it was: does the module *decide*, or does it
// draw a frame it was handed.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // **The screenshots moved here at 0.11.0**, and the reason is in `decisions.md`: the frames were
    // three thousand lines of stated fixtures in `:client:galaxy:ui-testing` because a ui module
    // cannot see a `GameState`, and that copy drifted from the mapper exactly as its own header
    // warned it would. A frame is `state.toGalaxyUiState(nav)` now — the same call the app makes —
    // so a mapper that re-words anything moves a baseline, which is what a baseline is for.
    alias(libs.plugins.roborazzi)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "dev.fardavide.oltre.client.galaxy.presentation"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
            // The sheet a world row raises, and the mapper that prices it. `implementation`
            // rather than `api`: `DispatchSelection` is this screen's own state and nothing
            // outside the module names it — the ui half arrives through `:client:galaxy:ui`.
            implementation(projects.client.dispatch.presentation)
            // `api`, so the composition root names this feature once — see `:client:colony:presentation`.
            api(projects.client.galaxy.ui)
            implementation(projects.client.design.component)
            implementation(projects.client.design.text)
            // `:client:design:format` was declined at 0.0.15 on the grounds that the one thing this
            // screen formats — a milli-unit as a decimal — had a single caller. It has two since
            // the adaptation branch put the same three axes on Research: a band there is read
            // against a reading here, so the two screens writing them differently would be the app
            // contradicting itself about a number the player is comparing.
            implementation(projects.client.design.format)
            // What the outbox means to the map card's bell and to a probe that cannot be held — see
            // the colony's presentation module, which takes it the same way.
            implementation(projects.client.net.domain)

            // Arrives with 0.2.0 and the probe: the system card's footer counts a flight down and
            // prints the wall-clock instant it lands at, which is the same pair of needs that put
            // this on Colony and Research.
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
                // The shared assertions; the harness that composes `GalaxyScreen` is next door in
                // `GalaxyScreenHarness`, because a ui-layer module may not depend on this one.
                implementation(projects.client.galaxy.uiTesting)
                implementation(projects.client.design.core)
                implementation(projects.client.design.screenshotTesting)

                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
                implementation(libs.compose.material3)
                implementation(libs.roborazzi.compose.desktop)
            }
        }
    }
}
