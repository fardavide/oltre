import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Shipyard tab as it is drawn: the screen, the two hull lists, the models they render, and the
// frames and baselines that pin them. Nothing here prices a hull or reads the slipway —
// `:client:shipyard:presentation` does that, and depends on this module rather than the reverse.
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
        namespace = "dev.fardavide.oltre.client.shipyard.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `ShipType` alone: a card is keyed by the hull it sells, so the enum is the model's own
            // vocabulary and is what `onBuild` hands back. Everything that *prices* one went up to
            // `presentation`.
            implementation(projects.core)
            // No `:client:design:icon` — a price list draws no glyph. The hull is named rather than
            // pictured, which is also what keeps a second hull from needing a second drawing.
            implementation(projects.client.design.component)
            implementation(projects.client.design.core)
            // `api`, because every string this module's models carry is a `TextRes` — a card's name,
            // its pool line, its ghost — and `:client:shipyard:presentation` next door builds them.
            api(projects.client.design.text)

            // No `:client:design:format` and no `kotlinx-datetime`: the ghost's wait and the
            // slipway's wall clock are `String`s by the time a card is handed one.

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
                // The robot, shared with `:client:shipyard:presentation`'s seam test — see that
                // module's build file for why it is a module.
                implementation(projects.client.shipyard.uiTesting)
                implementation(projects.client.design.screenshotTesting)

                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
                implementation(libs.roborazzi.compose.desktop)
            }
        }
    }
}
