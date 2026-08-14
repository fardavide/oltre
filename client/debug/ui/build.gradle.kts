import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// **The worked example of "a presentation module is optional".** Every other feature in the build
// is a `ui` module plus a `presentation` one; the debug menu is a `ui` module and nothing else,
// because the half a presentation layer would hold is already `:client:debug:domain` — what a skip
// decides, what the report says, what a shake means. A `presentation` here would forward its
// arguments and no more, which is the placeholder layer the rule exists to prevent.
//
// No Roborazzi, and it is a deliberate omission rather than an oversight — the only module with a
// screen in it that has none. A baseline is the assertion that a *design* still renders the way it
// was drawn, and this sheet has no design behind it: Davide's call, 2026-08-09, was that the debug
// UI does not go through Claude Design. Pinning pixels nobody chose would make every future tweak
// to a developer tool a baseline to re-record. What it has instead is behaviour tests, which assert
// the thing that actually matters here — that the buttons do what they say.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "dev.fardavide.oltre.client.debug.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `FutureEvent`, to say what the next thing to happen is.
            implementation(projects.core)
            // What every row on this sheet is a rendering of. A `ui` module reaching its own
            // feature's `domain` is the judgement the rule allows rather than a violation — the
            // model this sheet draws *is* `DebugReport`, and a `presentation` interposed to hand it
            // over unchanged would be the placeholder layer. It is `data` a ui module may not see,
            // and this module sees none.
            //
            // `api` rather than `implementation`, for the reason `:client:save:data` exposes
            // `:core`: `DebugReport` is in `DebugSheet`'s own signature, so it is this module's
            // vocabulary and travels with it. As `implementation` the shell would compile only
            // because it happens to declare the same dependency itself.
            api(projects.client.debug.domain)
            implementation(projects.client.design.component)
            implementation(projects.client.design.core)
            implementation(projects.client.design.format)

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
                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}
