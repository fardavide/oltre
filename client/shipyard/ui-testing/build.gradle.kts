// The Shipyard robot, and it is a module rather than a file for the reason
// `:client:design:screenshot-testing` is one: KMP source sets cannot host Gradle test fixtures, so a
// helper that **two** modules' tests need has to live in the main source set of a module of its own.
//
// The two are `:client:shipyard:ui`, whose behaviour and screenshot tests drive the screen from
// hand-written frames, and `:client:shipyard:presentation`, whose `ShipyardFromStateBehaviourTest`
// drives the same screen through the real mapper. That second one is the seam test — the only kind
// that can catch a mapper and a fixture being wrong in agreement — and it cannot be written without
// both halves, so the robot cannot belong to either of them alone.
//
// `ui-testing`, not `testing`: rule 5 matches on the `-testing` suffix and strips it to find the
// layer, so this module is `ui` and carries a ui module's restrictions. Reachable only from a test
// source set, which is what stops a robot ever shipping.
//
// Desktop only. The Compose test harness runs on the JVM against the desktop target, so there is
// nothing for an iOS or Android variant of this to hold.
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
                // `api` throughout, and it is what lets a consumer of this robot apply no Compose
                // plugin at all: `shipyard(uiState) { … }` names a `FleetsUiState` and hands back a
                // robot built on a `ComposeUiTest`, and running one needs the desktop Skiko binary.
                // A test module that gets all three from here composes nothing itself, so it needs
                // no Compose compiler — see `:client:shipyard:presentation`, which has none.
                api(projects.client.shipyard.ui)
                // `ShipType`: the robot's methods take the hull they act on, because a tag keyed by
                // the enum is what stops an assertion silently retargeting when a name changes.
                api(projects.core)
                api(compose.desktop.uiTestJUnit4)
                api(compose.desktop.currentOs)

                implementation(projects.client.design.core)
                implementation(libs.compose.material3)
            }
        }
    }
}
