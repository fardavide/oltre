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
include(":client:colony:ui")
// The debug menu, and the first feature in the build to hold three layers: what an action decides
// is arithmetic (`domain`), the accelerometer is a device service (`data`), and the sheet is a
// screen (`ui`).
//
// **The one feature with no `presentation`, and it is the worked example rather than an omission.**
// A presentation module maps state into the models a screen renders; here `debugReport(...)` in
// `domain` already produces exactly what `DebugSheet` draws, so the layer would forward its
// arguments and no more. See `client/debug/ui/build.gradle.kts`.
include(":client:debug:data")
include(":client:debug:domain")
include(":client:debug:ui")
// `:client:design` is a directory of layer modules, not a module — the same shape every feature
// directory has. Compose's own split is the model: tokens, components and icons are separate
// artifacts because they have different dependencies and different rates of change.
include(":client:design:component")
include(":client:design:core")
include(":client:design:format")
include(":client:design:icon")
include(":client:design:screenshot-testing")
// What the game says, and the one design module that names no colour and draws nothing. It is here
// rather than in `:client:design:component` for `:client:design:format`'s reason, doubled: a string
// is built in a `presentation` module that has no Compose compiler, and a notification's string is
// built outside composition altogether. See its build file.
include(":client:design:text")
// The dispatch sheet, which belongs to no tab: Galaxy raises it from a world row and Fleets raises
// it from a landing. Davide's call, 2026-08-13 — *"We absolutely do not put code in shell! I'd
// suggest `client/dispatch/ui` with its UI state."* — so it is a directory of layer modules like a
// feature, and `featureOf` in the root build script excludes it by name for `design`'s reason: it is
// shared vocabulary every feature is meant to reach, and left in it would fire the cross-feature
// warning on every clean build.
// `domain` arrived at 0.13.1 for the reason the debug menu's did: what a held stepper *does* is a
// cadence, and a cadence is arithmetic. Four invented motion numbers and a ramp are a claim a test
// can check; the same four at the foot of `DispatchSheet.kt` are a comment nobody can run.
include(":client:dispatch:domain")
include(":client:dispatch:presentation")
include(":client:dispatch:ui")
// The two tabs that stopped saying "nothing here yet" at 0.8.0. They ship together on purpose: a
// shipyard that builds hulls with nowhere to send them is worse than the empty tab it replaces, and
// a fleets tab is a list that can never have two rows until hulls go on sale.
include(":client:fleets:presentation")
include(":client:fleets:ui")
// The robot both halves of the Fleets suite drive the screen through — see its build file.
include(":client:fleets:ui-testing")
include(":client:galaxy:presentation")
include(":client:galaxy:ui")
// The frame-driven half of the Galaxy robot — see its build file for why the other half is not here.
include(":client:galaxy:ui-testing")
include(":client:notifications:data")
include(":client:research:presentation")
include(":client:research:ui")
include(":client:save:data")
include(":client:shell")
include(":client:shipyard:presentation")
include(":client:shipyard:ui")
// The robot both halves of the Shipyard suite drive the screen through — see its build file.
include(":client:shipyard:ui-testing")
// How the device is being held, and the second sensor in the build after the accelerometer. Two
// layers rather than one for the reason the debug menu has them: what a tilt *means* — where the
// centre is, how far is far enough, what a still hand should do — is arithmetic, and arithmetic
// belongs where it can be tested without a phone in somebody's hand.
include(":client:tilt:data")
include(":client:tilt:domain")
// What a world looks like, and the third shared surface after `:client:design` and
// `:client:dispatch`. Two features draw the same disc now — the Galaxy row and the Fleets worked
// list — and Davide ruled out the design system as its home (2026-08-16): "Design system should
// not contain such full-ui components."
include(":client:world:ui")
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
