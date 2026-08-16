import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Fleets tab as it is drawn: the screen, the run card, the models they render, and the frames
// and baselines that pin them. Nothing here reads a `GameState` or the event log —
// `:client:fleets:presentation` does that, and depends on this module rather than the reverse.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.roborazzi)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "dev.fardavide.oltre.client.fleets.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `ResourceKind` alone, to tint a landing by what came back. The rest of `core` went up
            // to `presentation` with the fold that reads it.
            implementation(projects.core)
            // `api`, because `FleetsUiState` names both: the sheet a row raises, and the face
            // that makes the row a door.
            api(projects.client.dispatch.ui)
            api(projects.client.world.ui)
            // No `:client:design:icon` — a run card draws no glyph. The three-phase bar is this
            // feature's own geometry rather than a mark any other screen wants.
            implementation(projects.client.design.component)
            implementation(projects.client.design.core)

            // No `:client:design:format` and no `kotlinx-datetime`: a countdown is a `String` by the
            // time it reaches a card.

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        val desktopTest by getting {
            dependencies {
                // The robot, shared with `:client:fleets:presentation`'s seam test — see that
                // module's build file for why it is a module.
                implementation(projects.client.fleets.uiTesting)
                implementation(projects.client.design.screenshotTesting)

                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
                implementation(libs.roborazzi.compose.desktop)
            }
        }
    }
}
