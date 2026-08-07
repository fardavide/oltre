// The sibling `:<module>:testing` shape from `.claude/docs/decisions.md`: KMP source sets cannot
// host Gradle test fixtures, so a helper that several modules' tests need lives in the *main*
// source set of a module of its own. That is precisely why `oltreRoborazziOptions` was copied three
// times before this existed.
//
// Desktop only. Roborazzi runs on the JVM against the desktop target, so there is nothing for an
// iOS or Android variant of this to hold.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                // `api`, because `RoborazziOptions` is the return type: a consumer that could not
                // see it could not name what this function gives back.
                api(libs.roborazzi.compose.desktop)
            }
        }
    }
}
