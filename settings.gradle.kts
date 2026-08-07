rootProject.name = "oltre"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":core")
include(":sim")
include(":client:colony:presentation")
// `:client:design` is a directory of layer modules, not a module — the same shape every feature
// directory has. Compose's own split is the model: tokens, components and icons are separate
// artifacts because they have different dependencies and different rates of change.
include(":client:design:component")
include(":client:design:core")
include(":client:design:format")
include(":client:design:icon")
include(":client:design:testing")
include(":client:notifications:data")
include(":client:research:presentation")
include(":client:save:data")
include(":client:shell")
include(":server")
