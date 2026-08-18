import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Colony tab as it is drawn: five composables, the models they render, and the fixtures and
// baselines that pin them. Nothing here reads a `GameState` — `:client:colony:presentation` does
// that, and depends on this module rather than the other way round.
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
        namespace = "dev.fardavide.oltre.client.colony.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `BuildingType` and `BuildingLevel`, and nothing else out of `core`. A ui module
            // reaching `core` is a judgement rather than a violation, and this is the shape it is
            // allowed in: the row is *keyed* by a facility, so the enum is the model's own
            // vocabulary. Every string on the row arrives already written — reaching for a balance
            // constant or a rule here would be the signal that a mapping belongs one layer up.
            implementation(projects.core)
            implementation(projects.client.design.component)
            api(projects.client.design.text)
            implementation(projects.client.design.core)
            implementation(projects.client.design.icon)

            // No `:client:design:format` and no `kotlinx-datetime`: a duration is a `String` by the
            // time it reaches this module. Both moved up to `presentation` with the mapper that
            // formats them.

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
