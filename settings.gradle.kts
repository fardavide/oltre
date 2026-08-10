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
// The debug menu, and the first feature in the build to hold all three layers: what an action
// decides is arithmetic (`domain`), the accelerometer is a device service (`data`), and the sheet
// is a screen (`presentation`).
include(":client:debug:data")
include(":client:debug:domain")
include(":client:debug:presentation")
// `:client:design` is a directory of layer modules, not a module — the same shape every feature
// directory has. Compose's own split is the model: tokens, components and icons are separate
// artifacts because they have different dependencies and different rates of change.
include(":client:design:component")
include(":client:design:core")
include(":client:design:format")
include(":client:design:icon")
include(":client:design:screenshot-testing")
include(":client:galaxy:presentation")
include(":client:notifications:data")
include(":client:research:presentation")
include(":client:save:data")
include(":client:shell")
// How the device is being held, and the second sensor in the build after the accelerometer. Two
// layers rather than one for the reason the debug menu has them: what a tilt *means* — where the
// centre is, how far is far enough, what a still hand should do — is arithmetic, and arithmetic
// belongs where it can be tested without a phone in somebody's hand.
include(":client:tilt:data")
include(":client:tilt:domain")
// The Android packaging of `:client:shell`, and the only thing in the build that depends on it
// — AGP 9 will not let a Kotlin Multiplatform module apply `com.android.application`, so the
// shell cannot be the Android app the way it already is the desktop one. Rule 7 names it by
// hand as a result; the argument is in `.claude/docs/decisions.md`.
include(":androidApp")
include(":server")

// ── Rule 1: a directory is either a folder or a module, never both ────────────────────────────
//
// A module may not contain another module. `client/save/data` holds a module, so nothing under it
// may be one; a second module for that feature is a *sibling* — `client/save/<other>` — not a
// child. The layer rules below are enforced from the root build script, but this one is checked
// here, in settings, because settings evaluation is the first thing Gradle does: a layout that
// breaks the rule fails the IDE sync itself, before a single module is configured.
//
// Read from disk rather than from the `include` list above, so a module directory that was created
// and never included is caught too — an un-included module still misleads every human and agent
// reading the tree. The root is exempt: `build.gradle.kts` at the top configures the build, and
// declares no sources of its own.
run {
    // Walked with explicit `listFiles()` calls rather than `File.walkTopDown()`, because only the
    // former is instrumented as a configuration-cache input: a stray module directory added while
    // no build script changed would otherwise be waved through on a cache hit. Verified — the walk
    // version reused the cache entry and missed exactly that case.
    val moduleDirs = buildList {
        val pending = ArrayDeque(listOf(rootDir))
        while (pending.isNotEmpty()) {
            val dir = pending.removeFirst()
            if (dir != rootDir && File(dir, "build.gradle.kts").isFile) add(dir)
            dir.listFiles().orEmpty()
                // `build` is output, and every dot-directory (`.git`, `.claude`, `.github`) is
                // metadata. Neither can hold a module, and walking them costs real time.
                .filter { it.isDirectory && it.name != "build" && !it.name.startsWith(".") }
                .forEach { pending.addLast(it) }
        }
    }

    val nested = moduleDirs.flatMap { outer ->
        moduleDirs
            .filter { inner -> inner.path.startsWith(outer.path + File.separator) }
            .map { inner -> outer.relativeTo(rootDir).path to inner.relativeTo(rootDir).path }
    }

    require(nested.isEmpty()) {
        buildString {
            appendLine("Module layout rule violated: a module cannot contain another module.")
            appendLine()
            nested.forEach { (outer, inner) ->
                appendLine("  $inner is a module inside the module $outer")
            }
            appendLine()
            appendLine("A directory is either a folder or a module. Turn the outer module's directory")
            appendLine("into a folder and make both modules siblings inside it:")
            appendLine("  dir/moduleA + dir/moduleA/moduleB  ->  dir/sub-dir/moduleA + dir/sub-dir/moduleB")
            append("See the `module-rules` skill.")
        }
    }
}
