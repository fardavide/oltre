// The Galaxy robot, and it is a module rather than a file for the reason
// `:client:design:screenshot-testing` is one: KMP source sets cannot host Gradle test fixtures, so a
// helper that **two** modules' tests need has to live in the main source set of a module of its own.
//
// The two are `:client:galaxy:ui`, whose behaviour and screenshot tests hand `GalaxyPage` a frame,
// and `:client:galaxy:presentation`, whose tests drive the stateful `GalaxyScreen` from a real
// `GameState`. The second is where a tap that changes system or raises a sheet can be asserted at
// all, because that state does not exist in the leaf — so the robot cannot belong to either alone.
//
// **What is here is the frame-driven half of the robot only.** The overload that composes
// `GalaxyScreen` lives with that screen, because a ui-layer module may not depend on a presentation
// one and this module carries a ui module's restrictions: rule 5 strips the `-testing` suffix, so
// `ui-testing` *is* `ui`. The assertions are shared; the two ways of putting a screen on the glass
// are not.
//
// Desktop only. The Compose test harness runs on the JVM against the desktop target.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                // `api` throughout, so a consumer needs no Compose plugin of its own: the robot
                // names a `GalaxyUiState`, hands back an object built on a `ComposeUiTest`, and
                // running one needs the desktop Skiko binary.
                api(projects.client.galaxy.ui)
                api(compose.desktop.uiTestJUnit4)
                api(compose.desktop.currentOs)

                implementation(projects.core)
                // `CostChipUiState`, which the probe offer in every frame carries.
                implementation(projects.client.design.component)
                implementation(projects.client.design.core)
                implementation(libs.compose.material3)
            }
        }
    }
}
