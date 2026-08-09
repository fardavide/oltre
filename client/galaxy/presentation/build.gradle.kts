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
            // No `:client:design:icon` — the galaxy draws its own map, and a star and fifteen
            // orbit dots are this screen's geometry rather than a glyph any other screen wants.
            implementation(projects.client.design.component)
            implementation(projects.client.design.core)
            // `:client:design:format` was declined at 0.0.15 on the grounds that the one thing this
            // screen formats — a milli-unit as a decimal — had a single caller. It has two since
            // the adaptation branch put the same three axes on Research: a band there is read
            // against a reading here, so the two screens writing them differently would be the app
            // contradicting itself about a number the player is comparing.
            implementation(projects.client.design.format)

            // Arrives with 0.2.0 and the probe: the system card's footer counts a flight down and
            // prints the wall-clock instant it lands at, which is the same pair of needs that put
            // this on Colony and Research.
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
