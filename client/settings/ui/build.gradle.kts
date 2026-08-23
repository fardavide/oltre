import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The first preferences surface in the app, and a feature directory rather than a corner of
// `:client:player` — the gear lives on the strip, and what it opens is about the whole game.
//
// It draws two controls and decides nothing: which chip is lit, which switches are on and what the
// next alert will say all arrive as a `AlertSheetUiState` from `:client:settings:presentation`.
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
        namespace = "dev.fardavide.oltre.client.settings.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // **`api`, and the one place this module reaches `core`.** The three enums are the
            // vocabulary of every callback on the sheet — a tap says *this mode*, *this category*,
            // *this delivery* — so they are in the signature and travel with it. A `ui` model may
            // name a `core` type where the model genuinely is about one, which this is: a switch row
            // is a rendering of an `AlertCategory` and of nothing else.
            api(projects.core)
            // `api` like every other feature's `ui`: `AlertSheetUiState` carries `TextRes`, so a
            // consumer that builds one needs the type in its own compile classpath.
            api(projects.client.design.text)
            implementation(projects.client.design.component)
            implementation(projects.client.design.core)
            // The bell, which is the whole reason the panel needs no explaining: it is the same glyph
            // a facility row has carried since 0.15.4, one level up.
            implementation(projects.client.design.icon)

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
