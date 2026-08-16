import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// **What a world looks like.** One Canvas and the four traits it draws from, and nothing else.
//
// It lived in `:client:galaxy:ui` until 0.13, which was right while the galaxy was the only place a
// world had a face. The Fleets tab's worked list draws the same disc now — Claude Design's whole
// argument for it is that *"a face makes a row an object, and objects open"* — and a feature may not
// see another feature, so the portrait had to come out of Galaxy.
//
// **Not `:client:design:component`, and Davide's reason is the rule to keep**: *"Design system should
// not contain such full-ui components."* A cost chip or a section label is vocabulary; a procedural
// drawing of a planet from its temperature, gravity, pressure and hazards is a feature's worth of
// decisions, and putting it in the design system would have widened that module's one `core` edge —
// `ResourceKind`, argued for at length in its own build file — to four more types on the way past.
//
// So it is a shared surface beside `:client:dispatch`, and it passes that list's test: **nothing
// points out of it.** This module reaches `core` and the design tokens and no feature at all.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // The portrait's own baselines came with it. They photograph the disc alone at both sizes it is
    // drawn at, which is the one thing no screen-level frame can isolate.
    alias(libs.plugins.roborazzi)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "dev.fardavide.oltre.client.world.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // The four trait wrappers and nothing more. They keep their types rather than arriving
            // as `Int`s: this is the one place in the app that reads all three axes at once, and it
            // is the place a swapped pair would be hardest to see.
            implementation(projects.core)
            implementation(projects.client.design.core)

            // No `:client:design:component` and no `:icon`: a portrait is one Canvas, and it borrows
            // the palette rather than any widget.

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            // `kotlin-test` alone. `WorldPortraitTest` renders into an `ImageBitmap` and reads
            // pixels back, which needs no Compose test harness at all — and adding one here fails
            // outright, because `ui-test-junit4` publishes no Kotlin/Native variant and `commonTest`
            // is compiled for iOS too.
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
