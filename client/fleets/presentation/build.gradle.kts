import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Everything the Fleets tab decides: the fold from `GameState` and the event log into the models
// `:client:fleets:ui` draws, the unit tests that pin it, and the seam test that renders the two
// together.
//
// **No Compose plugin, and there is a seam test in here that renders a screen.** That is the point
// rather than an oversight: `FleetsFromStateBehaviourTest` maps a real colony and drives the real
// screen with the result — the only test in this feature that can catch a mapper and a fixture being
// wrong in agreement — and it composes nothing *itself*. The robot in `:client:fleets:ui-testing`
// does the composing, and hands the whole harness back as `api`. So this module gets the test
// without the compiler, and the build still fails the day somebody writes a composable here.
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
            implementation(projects.client.design.format)

            // A run counts down to a wall-clock instant and the ledger stamps what has landed, which
            // is the same pair of needs that put this on Colony, Research and Galaxy.
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        val desktopTest by getting {
            dependencies {
                // Brings the screen, the robot, the JUnit harness and the desktop Skiko binary
                // with it — see that module's build file.
                implementation(projects.client.fleets.uiTesting)
            }
        }
    }
}
