import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Research tab as it is drawn: the screen, its two lists, the models they render, and the
// fixtures and baselines that pin them. Nothing here reads a `GameState` —
// `:client:research:presentation` does that, and depends on this module rather than the reverse.
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
        namespace = "dev.fardavide.oltre.client.research.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `Technology`, `AdaptationTechnology` and `TechLevel`, and nothing else out of `core`:
            // a row is keyed by the technology it offers, so the enums are the model's own
            // vocabulary. Every string a row prints arrives already written.
            implementation(projects.core)
            // No `:client:design:icon` — research draws no glyph. The energy bolt belongs to the
            // two screens that report power, and this is not one of them.
            implementation(projects.client.design.component)
            api(projects.client.design.text)
            implementation(projects.client.design.core)

            // No `:client:design:format` here and no `kotlinx-datetime`: by the time a duration
            // reaches this module it is a `String`. Both went up to `presentation` with the mapper
            // that writes them. The *fixtures* below still format, which is why the test source set
            // keeps `format` and this one does not.

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
                // The screenshot fixtures state their own figures and assemble them with the design
                // system's formatters — the same ones the mapper uses — so a frame's numbers are
                // written the way the app writes them without this module seeing the mapper. See the
                // note over `outputVerdict` in `TestResearchUiState`.
                implementation(projects.client.design.format)
                implementation(projects.client.design.screenshotTesting)

                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
                implementation(libs.roborazzi.compose.desktop)
            }
        }
    }
}
