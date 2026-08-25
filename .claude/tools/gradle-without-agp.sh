#!/usr/bin/env bash
#
# Run Gradle against the modules that do not need AGP, from a session that cannot resolve it.
#
# WHY THIS EXISTS
#
# The remote environment's egress policy answers 403 to `dl.google.com`, and every route to the
# Android Gradle Plugin ends there — `maven.google.com` redirects to it, the Gradle Plugin Portal
# redirects to Maven Central, and Google does not publish AGP to Maven Central. So the root
# `plugins {}` block cannot resolve and *every* Gradle invocation fails during configuration,
# including ones that touch no Android code at all.
#
# AGP is in these modules only to publish an Android target. Drop that target and the same
# `commonMain` sources compile against the JVM one, from Maven Central alone. That is what this
# script does: it swaps in a minimal overlay for the build files below, runs Gradle, and always
# puts the real ones back.
#
# WHAT IT COVERS, AND THE LINE IT CANNOT CROSS
#
# Everything without Compose. Measured, not assumed (2026-08-09): `:client:save:data` compiles and
# its tests run green through this script. **Compose itself is a hard stop**, and the reason is not
# AGP at all — `org.jetbrains.compose.ui:ui` depends transitively on `androidx.compose.runtime:
# runtime-saveable`, `androidx.lifecycle:lifecycle-runtime` and `androidx.savedstate:savedstate`,
# which are published to Google's Maven and nowhere else. So a *desktop-only* Compose module fails
# to resolve exactly like an Android one does, and no overlay can fix it.
#
# The practical split for a cloud session, then:
#
#   buildable here      :core, :protocol, :sim, :server, :client:net:data,
#                       :client:net:data-testing, :client:save:data, :client:notifications:data,
#                       :client:design:text, :client:design:format, :client:debug:domain,
#                       :client:debug:data, :client:tilt:domain, :client:tilt:data
#   not buildable here  every Compose module — :client:shell, :client:*:presentation,
#                       :client:design:{core,icon,component}
#
# A cloud session doing domain work should therefore run the tests rather than reason about them.
# UI it writes is still verified by CI's Build job and by the manual Record screenshots workflow,
# never here. See `.claude/rules/session-roles.md`.
#
# The overlays are a hand-maintained mirror of the real build files. They carry only what the JVM
# target needs, so they drift harmlessly when an Android-only or iOS-only line changes — and they
# must be updated when a *dependency* does. Nothing here is ever committed into the actual build.
#
# **The drift is silent and it costs a session, so it is worth saying what it looks like.** Twice
# before `#112` an overlay went stale in a way no test could catch, because the failure is a
# *compile* error in a module the overlay claims to cover: `:client:save:data` gained
# `alias(libs.plugins.kotlinxSerialization)` for `PreferencesStore` and the overlay did not, so
# `serializer()` stopped resolving; `:client:notifications:data` gained
# `api(projects.client.design.text)` for `TextRes` and the overlay had neither that line nor the
# module. A session that hits one of those cannot tell it from its own breakage, which is the whole
# cost. **Adding a dependency to a covered module means adding it here in the same commit** — and
# if the dependency is a module this file does not yet include, it needs an `include(…)`, a
# `kover(…)` and an overlay build file of its own.
#
# USAGE
#
#   .claude/tools/gradle-without-agp.sh :sim:run
#   .claude/tools/gradle-without-agp.sh :core:jvmTest :sim:test
#   .claude/tools/gradle-without-agp.sh :server:test
#   .claude/tools/gradle-without-agp.sh :client:debug:domain:desktopTest
#   .claude/tools/gradle-without-agp.sh :core:jvmTest --tests '*BalanceCurveTest*'
#
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO"

FILES=(
  settings.gradle.kts
  build.gradle.kts
  core/build.gradle.kts
  protocol/build.gradle.kts
  client/design/text/build.gradle.kts
  client/design/format/build.gradle.kts
  client/net/data/build.gradle.kts
  client/net/data-testing/build.gradle.kts
  client/save/data/build.gradle.kts
  client/notifications/data/build.gradle.kts
  client/debug/domain/build.gradle.kts
  client/debug/data/build.gradle.kts
  client/tilt/domain/build.gradle.kts
  client/tilt/data/build.gradle.kts
)

BACKUP="$REPO/build/without-agp-backup"

if [ $# -eq 0 ]; then
  echo "usage: $0 <gradle task> [more tasks/flags]" >&2
  echo "example: $0 :sim:run" >&2
  exit 2
fi

# Restore by copy rather than by `git checkout --`, which is what this used to do. Two reasons, and
# the second is what forced the change: a `git checkout` restore cannot put back a build file that
# is not committed yet, so the script could not be used while *building* a new module — which is
# exactly when its tests are most worth running. Copying also means the script no longer has to
# refuse to start against edited build files, since it never discards them.
restore() {
  for file in "${FILES[@]}"; do
    if [ -f "$BACKUP/$file" ]; then
      mkdir -p "$(dirname "$file")"
      cp "$BACKUP/$file" "$file"
    fi
  done
  rm -rf "$BACKUP"
}

# A run killed outright (SIGKILL, a dead container) leaves the tree swapped and the backup behind.
# Restoring it here rather than refusing to start means the next run repairs the tree instead of
# handing a human a puzzle — the backup is by definition the real file, so this cannot lose work.
if [ -d "$BACKUP" ]; then
  echo "note: a previous run left overlays in place; restoring the real build files first." >&2
  restore
fi

mkdir -p "$BACKUP"
for file in "${FILES[@]}"; do
  if [ -f "$file" ]; then
    mkdir -p "$BACKUP/$(dirname "$file")"
    cp "$file" "$BACKUP/$file"
  fi
done

trap restore EXIT INT TERM

cat > settings.gradle.kts <<'OVERLAY'
// OVERLAY — generated by .claude/tools/gradle-without-agp.sh, never committed.
//
// Rule 1's layout check is deliberately absent: it walks the whole tree and would fail on nothing
// here, but it is the real settings file's job and this one exists only to narrow the build.
rootProject.name = "oltre"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":core")
include(":protocol")
include(":sim")
// `:server` needs no build-file overlay of its own — it applies `kotlinJvm` and nothing else, so it
// is in `:sim`'s position: AGP was never in it, and everything it asks for is on Maven Central.
include(":server")
// Two design modules that apply AGP and hold no Compose — the catalogue of what the game says, and
// the arithmetic that decides which numbers it shows. They are here because
// `:client:notifications:data` depends on the first and cannot compile without it.
include(":client:design:text")
include(":client:design:format")
include(":client:net:data")
include(":client:net:data-testing")
include(":client:save:data")
include(":client:notifications:data")
include(":client:debug:domain")
include(":client:debug:data")
include(":client:tilt:domain")
include(":client:tilt:data")
OVERLAY

cat > build.gradle.kts <<'OVERLAY'
// OVERLAY — generated by .claude/tools/gradle-without-agp.sh, never committed.
// No module-rule checks: the real root build file owns those, and CI runs it.
//
// Kover *is* here, and it is worth the lines. CI's Coverage job is the only thing that can report
// the whole project, but a session that can only read the number after a six-minute round trip ends
// up guessing at which lines are uncovered and writing tests on spec. `koverXmlReport` over the
// modules this overlay can build answers that locally, in seconds, for the non-Compose half.
plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.kover)
}

subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
}

dependencies {
    kover(projects.core)
    kover(projects.protocol)
    kover(projects.server)
    kover(projects.client.design.text)
    kover(projects.client.design.format)
    // `:client:net:data-testing` is deliberately absent, matching the real root build file: a
    // `-testing` module's main source set is test scaffolding, and counting it measures the tests
    // testing themselves.
    kover(projects.client.net.data)
    kover(projects.client.save.data)
    kover(projects.client.notifications.data)
    kover(projects.client.debug.data)
    kover(projects.client.debug.domain)
    kover(projects.client.tilt.data)
    kover(projects.client.tilt.domain)
}

kover {
    reports {
        filters {
            excludes {
                classes("*ComposableSingletons*", "*\$\$serializer")
                packages("dev.fardavide.oltre.sim")
                classes("dev.fardavide.oltre.server.MainKt")
            }
        }
        total {
            xml {
                onCheck = false
                xmlFile = layout.buildDirectory.file("reports/kover/report.xml")
            }
        }
    }
}

allprojects {
    tasks.withType<Test>().configureEach {
        testLogging {
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showCauses = true
            showExceptions = true
            showStackTraces = true
        }
    }
}
OVERLAY

cat > core/build.gradle.kts <<'OVERLAY'
// OVERLAY — generated by .claude/tools/gradle-without-agp.sh, never committed.
// The real file also declares an Android target and the iOS ones; the sim consumes the JVM target,
// and `commonMain` is identical either way.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    jvmToolchain(21)

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
OVERLAY

cat > protocol/build.gradle.kts <<'OVERLAY'
// OVERLAY — generated by .claude/tools/gradle-without-agp.sh, never committed.
// The real file also declares an Android target and the two iOS ones. `commonMain` is identical
// either way, and both consumers a cloud session can reach — the server and the tests — take the
// JVM one.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    jvmToolchain(21)

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(projects.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
OVERLAY

cat > client/design/text/build.gradle.kts <<'OVERLAY'
// OVERLAY — generated by .claude/tools/gradle-without-agp.sh, never committed.
// AGP is in the real file only to publish an Android target; nothing here knows Compose exists,
// which is exactly what its own header says and what makes it reachable from this script at all.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
OVERLAY

cat > client/design/format/build.gradle.kts <<'OVERLAY'
// OVERLAY — generated by .claude/tools/gradle-without-agp.sh, never committed.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(projects.client.design.text)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
OVERLAY

cat > client/net/data/build.gradle.kts <<'OVERLAY'
// OVERLAY — generated by .claude/tools/gradle-without-agp.sh, never committed.
// The real file also declares an Android target and the two iOS ones, each with its own Ktor
// engine. The desktop engine is the one a cloud session can run, and `commonMain` is identical
// either way.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(projects.protocol)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(projects.client.net.dataTesting)

            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
    }
}
OVERLAY

cat > client/net/data-testing/build.gradle.kts <<'OVERLAY'
// OVERLAY — generated by .claude/tools/gradle-without-agp.sh, never committed.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(projects.client.net.data)

            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
OVERLAY

cat > client/save/data/build.gradle.kts <<'OVERLAY'
// OVERLAY — generated by .claude/tools/gradle-without-agp.sh, never committed.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // For `Preferences`, exactly as in the real file. Missing here until `#112`, which is why
    // `PreferencesStore`'s `serializer()` stopped resolving in every cloud session that tried.
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(projects.core)

            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
OVERLAY

cat > client/notifications/data/build.gradle.kts <<'OVERLAY'
// OVERLAY — generated by .claude/tools/gradle-without-agp.sh, never committed.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            // `PendingNotification` carries a `TextRes` and `GameNotifications` takes the
            // `Translations` that resolves it, so this module does not compile without the
            // catalogue. Missing here until `#112`.
            api(projects.client.design.text)
            api(projects.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
OVERLAY

cat > client/debug/domain/build.gradle.kts <<'OVERLAY'
// OVERLAY — generated by .claude/tools/gradle-without-agp.sh, never committed.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(projects.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
OVERLAY

cat > client/debug/data/build.gradle.kts <<'OVERLAY'
// OVERLAY — generated by .claude/tools/gradle-without-agp.sh, never committed.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(projects.client.debug.domain)

            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
OVERLAY

cat > client/tilt/domain/build.gradle.kts <<'OVERLAY'
// OVERLAY — generated by .claude/tools/gradle-without-agp.sh, never committed.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
OVERLAY

cat > client/tilt/data/build.gradle.kts <<'OVERLAY'
// OVERLAY — generated by .claude/tools/gradle-without-agp.sh, never committed.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(projects.client.tilt.domain)

            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
OVERLAY

./gradlew "$@" --console=plain 2>&1 | grep -v "^Picked up JAVA_TOOL_OPTIONS"
exit "${PIPESTATUS[0]}"
