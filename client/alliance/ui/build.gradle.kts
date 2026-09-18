import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// **The alliance's screens and the models they render, and nothing that decides.** Four faces —
// searching, waiting, enlisted, held — chosen by account state rather than by a toggle
// (`alliance-sheet.md` §7). `:client:alliance:presentation` is what chooses; this draws what it is
// handed.
//
// It held the whole feature while the feature was one sentence, on the footing `module-rules` states
// for `:client:debug:ui`. That stopped being true the moment there was a roster to order, a pool to
// price and a role to check, so the `presentation` this file's first version said it did not need
// now exists beside it.
//
// **`:protocol` and not `core`.** The ids on these models — an alliance, a seat, a petition, a
// project — are the server's surrogate keys, which is what a tap has to hand back; nothing here
// reads a `GameState`.
//
// Roborazzi, as every player-facing module is.
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
        namespace = "dev.fardavide.oltre.client.alliance.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`, because the ids on `AllianceUiState` are `:protocol`'s and a caller handing
            // back what it tapped names them.
            api(projects.protocol)
            implementation(projects.client.design.component)
            implementation(projects.client.design.core)
            api(projects.client.design.text)

            // **The one cross-feature edge in this build, and the build warns about it.** The member
            // card draws the commander's own mark at 44dp, and `IdentityMark` is where a `PlayerMark`
            // becomes a picture — so the alternative was a second drawing of the same forty marks,
            // which would be drift the first time one of them was retouched.
            //
            // It is not in `sharedSurfaces`, deliberately. That list is for modules nothing points out
            // of, and `:client:player:ui` is a feature with its own faces; silencing the warning would
            // hand every future edge into this feature the same pass. Davide's call, 2026-09-18, taken
            // on this edge alone — the warning is the instrument the rule chose for exactly that.
            implementation(projects.client.player.ui)

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
                implementation(projects.client.design.screenshotTesting)

                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
                implementation(libs.roborazzi.compose.desktop)
            }
        }
    }
}
