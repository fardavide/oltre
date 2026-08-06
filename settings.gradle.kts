rootProject.name = "oltre"

// No TYPESAFE_PROJECT_ACCESSORS. It is incompatible with this repo's module layout: the
// accessor is built from a project's *name*, so `:client:save:data` and
// `:client:notifications:data` both generate `…data` and one silently wins. That is not
// hypothetical — it landed at 0.0.9 and cost a CI cycle. See `.claude/docs/decisions.md`.

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
include(":client:design")
include(":client:notifications:data")
include(":client:save:data")
include(":client:shell")
include(":server")
