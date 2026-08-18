import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The dispatch sheet as it is drawn, and the models it renders. **The one surface in the app that
// belongs to no tab**: a run is raised from a world row on Galaxy and, since #62, from a landing on
// Fleets — so the sheet has to belong to neither or one feature ends up owning the other's verb.
//
// Davide settled where it lives on 2026-08-13: *"We absolutely do not put code in shell! I'd suggest
// `client/dispatch/ui` with its UI state."* Two homes were refused with it — `:client:shell`, which
// is the composition root and stays chrome, and `:client:design:component`, whose single `core` edge
// is `ResourceKind` and which has no business reading a `GameState`.
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
        namespace = "dev.fardavide.oltre.client.dispatch.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Three types and no more: `ResourceKind` for the gathering chips, `GalaxyCoordinate`
            // because the offer carries the whole address it would send a run to, and `Duration` for
            // the window ladder. Everything that reads a *world* — `worldAt`, `FleetBalance`,
            // `DepositBalance` — is `:client:dispatch:presentation`'s, which is where reaching for
            // one belongs.
            implementation(projects.core)
            // The one thing on this sheet that is a *rule* rather than a drawing: the cadence a held
            // stepper repeats at. Rule 4 allows `ui -> domain` and the `module-rules` skill names
            // this exact shape — a ui module may take its own feature's domain. What it buys is that
            // four invented motion numbers are tested arithmetic rather than a comment.
            implementation(projects.client.dispatch.domain)
            // `OltreBottomSheet`, which is the chrome every sheet in the app has swiped away with
            // since 0.7.1.
            implementation(projects.client.design.component)
            // Every string the sheet carries is a `TextRes`, and its models name the type.
            api(projects.client.design.text)
            implementation(projects.client.design.core)

            // No `:client:design:format`: by the time a countdown, a richness or a haul reaches this
            // module it is a `String`, which is exactly the property that makes a frame a frame.

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// **No `roborazzi` block and no test source set, deliberately.** The sheet's nine baselines and its
// behaviour tests stay in `:client:galaxy:presentation` — see that module's `DispatchFrames`. They
// photograph the sheet *over a real page*, scrim and dimmed list included, which is what
// `decisions.md` argues a sheet baseline is for; rendering it here on a bare `Surface` would record
// a screen no device produces. The day Fleets raises the same sheet, its own frames join Galaxy's
// rather than replacing them.
