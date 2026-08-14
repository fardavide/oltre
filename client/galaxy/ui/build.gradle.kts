import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Galaxy tab as it is drawn: the page, the header, the ruler, the map, the world list, the probe
// footer, the dispatch sheet, and the models all of them render. Nothing here reads a seed or a
// `GameState` — `:client:galaxy:presentation` does, and depends on this module rather than the
// reverse.
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
        namespace = "dev.fardavide.oltre.client.galaxy.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Four enums and no more: `ResourceKind` for the gathering chips, `StarClass` for a lens
            // dot, `AdaptationTechnology` because a blocked row's tap target is the ladder itself
            // rather than the word it prints, and `ShipType` nowhere at all. Everything that reads
            // the *galaxy* — `worldAt`, `starClassAt`, `verdictFor`, the balances — went up to
            // `presentation`, which is where reaching for one belongs.
            implementation(projects.core)
            // No `:client:design:icon` — the galaxy draws its own map, and a star and fifteen
            // orbit dots are this screen's geometry rather than a glyph any other screen wants.
            implementation(projects.client.design.component)
            implementation(projects.client.design.core)

            // No `:client:design:format` and no `kotlinx-datetime`. Both went up with the mappers:
            // by the time a countdown, a milli-unit or a landing clock reaches this module it is a
            // `String`, which is exactly the property that makes a frame a frame.

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
                // The robot, shared with `:client:galaxy:presentation`'s stateful-screen tests — see
                // that module's build file for why it is a module.
                implementation(projects.client.galaxy.uiTesting)
                implementation(projects.client.design.screenshotTesting)

                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
                implementation(libs.roborazzi.compose.desktop)
            }
        }
    }
}
