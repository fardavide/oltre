import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Everything the Shipyard tab decides: the mapping from `GameState` into the models
// `:client:shipyard:ui` draws, the unit tests that pin every price and every clause of the pool
// line, and the seam test that renders the two together.
//
// **No Compose plugin, and there is a seam test in here that renders a screen.** That is the point
// rather than an oversight — see `:client:fleets:presentation`, which is the same shape for the same
// reason: the robot in `:client:shipyard:ui-testing` does the composing and hands the whole harness
// back as `api`, so this module gets the test without the compiler.
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
        namespace = "dev.fardavide.oltre.client.shipyard.presentation"
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
            api(projects.client.shipyard.ui)
            implementation(projects.client.design.component)
            implementation(projects.client.design.format)

            // The ghost on an unaffordable card carries a wait, and the wait is a duration written
            // the way every other wait in the app is written.
            //
            // **The date library arrived at 0.9.0 and the comment that used to be here said why it
            // could not be needed** — *"the Shipyard names no instant, because a hull has no
            // completion to name"*. It has one now: the yard has a clock, so a card on the slipway
            // says when it is done in wall-clock time, exactly as a facility row does.
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        val desktopTest by getting {
            dependencies {
                // Brings the screen, the robot, the JUnit harness and the desktop Skiko binary
                // with it — see that module's build file.
                implementation(projects.client.shipyard.uiTesting)
            }
        }
    }
}
