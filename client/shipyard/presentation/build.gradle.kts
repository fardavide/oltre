import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
            // No `:client:design:icon` — a price list draws no glyph. The hull is named rather than
            // pictured, which is also what keeps a second hull from needing a second drawing.
            implementation(projects.client.design.component)
            implementation(projects.client.design.core)
            implementation(projects.client.design.format)

            // The ghost on an unaffordable card carries a wait, and the wait is a duration written
            // the way every other wait in the app is written.
            //
            // **The date library arrived at 0.9.0 and the comment that used to be here said why it
            // could not be needed** — *"the Shipyard names no instant, because a hull has no
            // completion to name"*. It has one now: the yard has a clock, so a card on the slipway
            // says when it is done in wall-clock time, exactly as a facility row does.
            implementation(libs.kotlinx.datetime)

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
