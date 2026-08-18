// Every plugin is declared here (apply false) so the Kotlin/Android Gradle plugins load in a
// single classloader — applying them per-module only leads to BuildService class-cast clashes.
// Kover is the exception that proves the rule: the root project *is* the aggregator, so it is
// the one plugin applied here rather than declared and left to the modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.kover)
}

val oltreVersion = libs.versions.oltre.get()

// Which kind of test to run. Absent — the normal case, and what `check` does — means all of
// them. Set to one of the four categories, coverage is measured for that category alone:
// `./gradlew koverXmlReport -Poltre.testCategory=behaviour` answers "what do the behaviour
// tests actually reach?", which a single blended number never can. See the `test-coverage`
// skill for the taxonomy and why it is drawn where it is.
val testCategory: String? = providers.gradleProperty("oltre.testCategory").orNull

// A category is a class-name suffix, because the suffix is the one piece of metadata that is
// visible in the file tree, in a stack trace and in a CI log alike — an annotation is none of
// those. `unit` is the residue: everything ending in `Test` that is not one of the other three.
val categorySuffixes = mapOf(
    "integration" to "IntegrationTest",
    "screenshot" to "ScreenshotTest",
    "behaviour" to "BehaviourTest",
)

require(testCategory == null || testCategory == "unit" || testCategory in categorySuffixes) {
    "Unknown -Poltre.testCategory=$testCategory. " +
        "Expected one of: unit, ${categorySuffixes.keys.joinToString()}."
}

allprojects {
    // The group carries the project's parent path, so a module's coordinates are unique.
    // A flat "dev.fardavide.oltre" is not: the module layout puts a `data` (and a
    // `presentation`, and a `domain`) under every feature directory, so `:client:save:data` and
    // `:client:notifications:data` both became `dev.fardavide.oltre:data:<version>`. Gradle
    // reads two identical coordinates as one component, conflict-resolves them, and one of the
    // two silently leaves the compile classpath — see `.claude/docs/decisions.md`.
    group = "dev.fardavide.oltre" + path.substringBeforeLast(':').replace(':', '.')
    version = oltreVersion

    // Gradle's default console format prints only the exception class and the line it came from,
    // so a failed assertion reaches CI as a bare `java.lang.AssertionError` with its message —
    // the part that says what the numbers actually were — nowhere in the log. On a machine that
    // cannot open the HTML report (any CI run, any remote agent) that is the difference between
    // diagnosing a failure and re-running the build to guess at it.
    tasks.withType<Test>().configureEach {
        testLogging {
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showCauses = true
            showExceptions = true
            showStackTraces = true
        }

        if (testCategory != null) {
            filter {
                // Most modules hold no test of a given category — `:core` has no screenshots,
                // `:client:design:core` has no tests at all — and a filtered run that matches
                // nothing there is the expected outcome, not a failure.
                isFailOnNoMatchingTests = false
                if (testCategory == "unit") {
                    includeTestsMatching("*Test")
                    categorySuffixes.values.forEach { excludeTestsMatching("*$it") }
                } else {
                    includeTestsMatching("*${categorySuffixes.getValue(testCategory)}")
                }
            }
        }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
}

// Every module reports into the root aggregate, so `koverXmlReport` at the root is the whole
// project. A module missing from this list is silently absent from the report, which is exactly
// the failure mode the per-package table in the PR comment is there to expose.
//
// `:androidApp` is absent on purpose and is the one module that can be: it holds a manifest, a
// theme and the launcher icons, and not a line of Kotlin. There is nothing there to measure.
//
// **Every `-testing` module is absent too, and that is 0.9.1's change rather than an oversight.**
// A `-testing` module's main source set is test scaffolding by construction — rule 5 already says
// nothing but a test source set may reach one — so counting it measures the fixtures rather than
// the app. It was harmless while the only one was `:client:design:screenshot-testing` and thirty
// lines of Roborazzi options; the layer split added three robots and, in
// `:client:galaxy:ui-testing`, three thousand lines of hand-written frames. Listed, those frames
// are production code that no unit test can reach, and the unit row fell **86.3% → 52.3%** on a
// change that deleted no test and covered no less of the app.
//
// This is an omission from the aggregate rather than an entry in the `excludes` filter below, and
// the difference matters: the filter's rule is that a fourth entry needs a failing report and
// Davide's explicit say-so, because an exclusion removes the gate's ability to *see*. This removes
// nothing the gate could see — a robot is exercised by every behaviour test that drives it, and
// what it would report is how thoroughly the tests test themselves.
dependencies {
    kover(projects.core)
    kover(projects.sim)
    kover(projects.server)
    kover(projects.client.design.component)
    kover(projects.client.design.core)
    kover(projects.client.design.format)
    kover(projects.client.design.icon)
    kover(projects.client.shell)
    kover(projects.client.colony.presentation)
    kover(projects.client.colony.ui)
    kover(projects.client.debug.data)
    kover(projects.client.debug.domain)
    kover(projects.client.debug.ui)
    kover(projects.client.dispatch.domain)
    kover(projects.client.dispatch.presentation)
    kover(projects.client.dispatch.ui)
    kover(projects.client.fleets.presentation)
    kover(projects.client.fleets.ui)
    kover(projects.client.galaxy.presentation)
    kover(projects.client.galaxy.ui)
    kover(projects.client.notifications.data)
    kover(projects.client.research.presentation)
    kover(projects.client.research.ui)
    kover(projects.client.save.data)
    kover(projects.client.shipyard.presentation)
    kover(projects.client.shipyard.ui)
    kover(projects.client.tilt.data)
    kover(projects.client.tilt.domain)
    kover(projects.client.world.ui)
}

kover {
    reports {
        filters {
            excludes {
                // ── Composables, and **only while measuring the unit pass** ───────────────────
                //
                // A unit test cannot render a composable. That is not a gap to be closed, it is
                // what the kind *is*: `…Test` runs in-process with no Compose, so every `@Composable`
                // in the repository reads 0% in this pass and always will. Left in, the unit row
                // stops measuring how well the logic is tested and starts measuring **what fraction
                // of the repository is UI** — a number that falls on every screen that ships and can
                // never be recovered by writing a better test. 0.8.0 is where that became load-
                // bearing: two new tabs put 296 lines and 100 branches of drawing into the pass, all
                // of them unreachable, and the unit row fell 51.1% → 50.1% on a slice that added
                // twenty-eight unit tests.
                //
                // **Scoped to the pass, which is the whole of what makes this safe.** The behaviour,
                // screenshot and unfiltered passes still see every composable, so nothing becomes
                // invisible — the table keeps its ability to say whether a screen is rendered by any
                // test at all, which is the 0.4.2 lesson (an exclusion "does not lower the gate, it
                // removes the gate's ability to see"). Read the behaviour row for that; this only
                // stops the unit row answering a question it was never able to answer.
                //
                // **By annotation rather than by path**, so it lands on exactly the functions that
                // cannot be reached and nothing else: a ui-state mapper, a `cardState()` or a
                // `tint()` sitting in the same file is still counted and still has to be tested.
                // A path or package rule would have hidden those too.
                if (testCategory == "unit") {
                    annotatedBy("androidx.compose.runtime.Composable")
                }
                // ── Everything that is not a drawing, and **only while measuring the screenshot
                // pass** ─────────────────────────────────────────────────────────────────────
                //
                // The mirror image of the rule above, and it arrives for the same reason. A
                // screenshot test renders a `ui` module — since 0.9.1 it is handed a declarative
                // model and it draws it, which is the whole point of the layer split. It cannot
                // reach a mapper, a store or a `core` rule, and that is not a gap to be closed.
                //
                // Left in, the screenshot row stops measuring how well the drawings are covered
                // and starts measuring **what fraction of the repository is not drawable** — the
                // unit row's defect with the sign flipped. 0.9.1 is where that became load-bearing:
                // the frames stopped deriving themselves from `toGalaxyUiState`, so the mappers
                // left the screenshot pass's reach and the row fell 62.1% -> 47.0% on a change that
                // deleted no screenshot test, moved no baseline by a byte, and drew nothing less.
                // Chasing that number back would mean screenshot tests that map a real `GameState`
                // — exactly the coupling the split removed, bought back to satisfy a measurement.
                //
                // **Scoped to the pass, which is what makes it safe**, exactly as above. The
                // behaviour and unfiltered passes still see every mapper, so nothing becomes
                // invisible — a mapper that no test reaches at all still shows up there, which is
                // the property the 0.4.2 lesson is about.
                //
                // **By layer rather than by module list.** The last segment of a package is the
                // layer, so this is the same fact the build already enforces on the module graph,
                // read off the other end. What survives is what draws: every `*.ui`, the design
                // system, and `:client:shell`, which holds the chrome and has baselines of its own.
                //
                // **`core` needs `classes(… .**)` and not `packages(…)`, which is worth the line it
                // costs**: a Kover package filter matches that package and not what is under it,
                // and a single `*` in a class pattern does not cross a dot. `packages("…core")` and
                // `classes("…core.*")` were both tried and both left all 1,077 of `core`'s branches
                // in the denominator, silently — the report simply still listed them. Only `.**`
                // empties it. The layer patterns above work because every layer package *is* a
                // leaf.
                if (testCategory == "screenshot") {
                    packages("*.presentation", "*.domain", "*.data")
                    classes("dev.fardavide.oltre.core.**")
                }
                // Compiler- and plugin-generated classes. Counting them measures the Compose
                // compiler and kotlinx-serialization, not this project's tests.
                classes("*ComposableSingletons*", "*\$\$serializer")
                // compose-resources' generated accessors (the bundled font lives behind them).
                // The first report showed `client.shell.generated.resources` at 0% across all
                // four kinds and `client.design.generated.resources` at 29% — a number no test
                // can move, because nothing in there is written by hand.
                packages("*.generated.resources")
                classes("*.generated.resources.*")
                // `main()`. Both are process entry points exercised by running them
                // (`:sim:run`, the server) — there is nothing for a test to hold on to, so
                // leaving them in only depresses the total permanently.
                //
                // The **whole** sim package, not just its `MainKt`. `:sim` is a balancing harness
                // that never ships; its output is read by a human and pasted into
                // `.claude/docs/balance-log.md`, and every line of it is either a `println` or the
                // arithmetic feeding one. Naming the file's class was too narrow: 0.1.1 added three
                // top-level private types to `Main.kt` (a blocker enum, a ledger, an options
                // holder), Kotlin compiled them to sibling class files rather than into `MainKt`,
                // and they landed in the report as a brand-new package at 0% — 14 uncovered lines
                // that failed the gate on a PR that had not touched a line of shipping code. A
                // package exclusion says what was always meant.
                packages("dev.fardavide.oltre.sim")
                classes("dev.fardavide.oltre.server.MainKt")
                // Android's entry points, excluded on exactly the grounds above. Android has
                // three rather than one, because it is the only platform where the process can
                // start without a screen: the Application fills the two slots the platform
                // cannot derive, the Activity hosts `App()`, and the boot receiver re-derives
                // the alarm schedule the system dropped. All three are exercised by the system
                // starting them and by nothing else — there is no seam for a test, and left in
                // they are a permanent drag on a total the merge gate compares against `main`.
                classes("dev.fardavide.oltre.client.MainActivity")
                classes("dev.fardavide.oltre.client.OltreApplication")
                classes("dev.fardavide.oltre.client.BootReceiver")
                // The Android half of the notification scheduler, which is the same kind of thing
                // as the three above and was missed when they were listed: `AlarmManager`, a
                // `BroadcastReceiver` the system instantiates, and the `Context` slot the platform
                // cannot derive. `decisions.md` already argues the policy — these are platform
                // edges with no seam a test can reach without Robolectric or an instrumented run,
                // neither of which this repository has — but only the shell's three were excluded,
                // so the notifications package arrived at 39.8% and failed the gate by 1.9 points
                // on a branch that had covered everything above the edge.
                //
                // What replaces the test is an install: 0.2.1 booked an alarm on a device, watched
                // it survive a reboot and re-book itself, and confirmed the save lived through an
                // update. That is the check these lines get, and it is a local session's job.
                classes("dev.fardavide.oltre.client.notifications.data.NotificationReceiver*")
                classes("dev.fardavide.oltre.client.notifications.data.AndroidNotificationScheduler*")
                classes("dev.fardavide.oltre.client.notifications.data.AndroidNotificationHost")
                classes("dev.fardavide.oltre.client.notifications.data.DefaultNotificationScheduler_androidKt")
                // The accelerometer, which is the same kind of thing again: a `SensorManager`, a
                // listener the platform calls, and the `Context` slot Android cannot derive. What
                // the shake *means* is `ShakeMonitor` in `:client:debug:domain`, which is pure and
                // covered by eight tests — deliberately, so that what is excluded here is only the
                // wiring that reads a device and divides by gravity. The iOS half is not listed
                // because Kover never sees a Kotlin/Native target at all.
                classes("dev.fardavide.oltre.client.debug.data.AndroidShakeDetector*")
                classes("dev.fardavide.oltre.client.debug.data.AndroidShakeHost")
                classes("dev.fardavide.oltre.client.debug.data.DefaultShakeDetector_androidKt")
                // **`:client:tilt:data`'s Android half is deliberately absent from this list**, and
                // saying why is the point of this comment. 0.4.2 added it here — three `classes(…)`
                // lines written in the same commit as the code, before any report existed — and the
                // next PR took them back out and let the job measure what they had been hiding.
                // **Twenty-six lines**: the package reads 3.7% rather than the 100.0% the exclusion
                // was reporting, and the total went 96.9% -> 96.3% against a 95.0% floor. So the gate
                // passes with one and a third points to spare, and the exclusion had bought nothing
                // it was not already given. An exclusion that buys nothing still costs the one thing
                // this filter can never give back, which is the gate's ability to notice.
                //
                // The three entries above it are real and predate that. **A fourth needs a failing
                // report to point at and Davide's explicit say-so** — see the `test-coverage` skill,
                // which said so already.
            }
        }
        total {
            // No `koverVerify` rule here, and not for lack of a threshold: the gate is a
            // comparison against the last `main` run, which Kover cannot see. It lives in
            // `.github/scripts/coverage.py`, which has the baseline. See the `test-coverage` skill.
            xml {
                onCheck = false
                xmlFile = layout.buildDirectory.file("reports/kover/report.xml")
            }
            html {
                onCheck = false
                htmlDir = layout.buildDirectory.dir("reports/kover/html")
            }
        }
    }
}

// ── Rules 2–8: who may depend on whom ─────────────────────────────────────────────────────────
//
// Three shapes, checked together because they are all one question about the module graph:
//
//   2–4  which layer may see which
//   5    fakes do not ship
//   6–8  the graph points inward, and both ends of it are sealed
//
// A module's layer is the last segment of its Gradle path, so `:client:save:data` is data and
// `:client:colony:presentation` is presentation. Each forbidden edge is forbidden for its own
// reason rather than for symmetry:
//
//   domain       -> data, presentation,   domain is the feature's rules; it defines the interfaces
//                  ui                     data implements and knows nothing of a screen.
//   presentation -> data                  a screen talks to domain, never to a store or a socket;
//                                         the day a feature grows a domain layer, a presentation
//                                         that reached past it has to be rewritten, not rewired.
//   data         -> presentation, ui      obvious, and cheap to keep obvious.
//   ui           -> data, presentation    `ui` draws and decides nothing, so it is a leaf: the
//                                         mapping into what it renders is `presentation`'s, and
//                                         `presentation` depends on `ui` rather than the reverse.
//
// **`ui` is the fourth layer, added at 0.9.1, and the direction of the one new edge is the whole
// of it.** A ui module holds composables and the models they render; a presentation module holds
// the mapping from `core` or domain state into those models. `presentation -> ui` is legal and is
// how a screen is assembled; `ui -> presentation` is not, because a leaf that could see its own
// mapper is not a leaf. `ui` is also the one layer that is *optional in the other direction*: a
// feature with nothing to decide is a ui module and no more — see `:client:debug:ui`, which has no
// presentation because its logic already lives in `:client:debug:domain`.
//
// Only those four names are layers. `:core`, `:sim`, `:server`, `:client:design` and
// `:client:shell` are not, and are deliberately unconstrained here: the composition root is the
// one module that may see every layer — that is the whole of its job — and the graph already
// stops it from being anything else, because nothing depends on it. Rule 1 (a module cannot
// contain a module) is checked in `settings.gradle.kts`, where it fails the sync earliest.
//
// Checked at configuration time, not in a task, so a violation fails the IDE sync as well as the
// build. Configuration is skipped on a configuration-cache hit, which is the behaviour we want:
// the check re-runs exactly when a build script changes, which is exactly when an edge can appear.
val forbiddenLayerDependencies = mapOf(
    "domain" to setOf("data", "presentation", "ui"),
    "presentation" to setOf("data"),
    "data" to setOf("presentation", "ui"),
    "ui" to setOf("data", "presentation"),
)

// The only modules allowed through rule 7. A list of names rather than a rule about shapes,
// because nothing can check that a module *stays* an entry point — see rule 7 below for the
// argument, which the next module wanting through should have to make again rather than inherit.
val platformEntryPoints = setOf(":androidApp")

// A testing module is a sibling named after what it doubles — `:client:save:data-testing` beside
// `:client:save:data` — so it carries that module's layer and that module's restrictions. Without
// this, `presentation-testing` is not `presentation`, and a presentation module reaches data
// through its own fakes: the rule holds on the direct edge and leaks on the one hop through.
// It reads the right way round too — a fake of a domain interface has no more business knowing
// about a store than the domain does.
fun layerOf(projectPath: String): String? = projectPath
    .substringAfterLast(':')
    .removeSuffix("-testing")
    .takeIf { it in setOf("domain", "data", "presentation", "ui") }

fun isTestingModule(projectPath: String): Boolean =
    projectPath.substringAfterLast(':').endsWith("-testing")

// Matched on the camel hump rather than on `contains("test")`, so a source set called `latest`
// does not quietly become a place fakes are allowed. Covers every shape in the build:
// `testImplementation` (JVM), `commonTestImplementation` / `desktopTestApi` (KMP),
// `androidHostTestImplementation` (AGP), and `testFixtures*` — fixtures exist to be consumed by
// tests, so they may hold fakes too.
fun isTestConfiguration(name: String): Boolean = name.startsWith("test") || name.contains("Test")

// `:client:save:data` -> `save`. Null for `:client:shell`, which lives directly under `client/` and
// so has no feature directory above it.
//
// `design` is excluded by name, and has to be. Since 0.0.14 the design system is a directory of
// layer modules — `:client:design:core`, `:icon`, `:component`, … — which is structurally
// indistinguishable from a feature, so the path alone reads it as one. It is the opposite of a
// feature: shared vocabulary that every feature is *meant* to depend on. Left in, it made the
// cross-feature warning fire nine times on a clean build, which is how a warning stops being read.
//
// **`dispatch` joins it at 0.13, and the test it passes is `design`'s rather than a new one.** The
// dispatch sheet is one verb raised from two tabs — a world row on Galaxy, a landing on Fleets — so
// every consumer of it is a cross-feature edge by construction and the warning would fire on each of
// them, forever, on a graph nobody should be looking at twice. What makes that safe here is what
// makes it safe for `design`: nothing points *out* of it. `:client:dispatch:*` depends on `core` and
// the design system and on no feature at all, so it cannot become the back door one tab reaches
// another through. **A third name on this list needs that same property demonstrated, not assumed.**
private val sharedSurfaces = setOf("design", "dispatch", "world")

fun featureOf(projectPath: String): String? = projectPath
    .removePrefix(":")
    .split(':')
    .takeIf { it.size >= 3 && it.first() == "client" }
    ?.get(1)
    ?.takeIf { it !in sharedSurfaces }

gradle.projectsEvaluated {
    // Edge -> the configurations that declare it. A module dependency is usually declared once,
    // but a KMP source-set pair (`commonMain` + `commonTest`) declares two, and naming both is
    // what makes the message point at a line rather than at a module.
    //
    // Subprojects only. The root is the build, not a module — and it is the aggregator, so it
    // holds a `kover(...)` dependency on every module including `:client:shell`, which rule 7
    // would otherwise read as something depending on the composition root.
    // Self-edges are dropped, and they are not hypothetical: Kover is applied to every subproject
    // and puts each one into its own `kover` configuration, so every module declares a dependency
    // on itself. Read literally that is `:core` depending on a module and something depending on
    // `:client:shell`, and rules 6 and 7 failed the whole build on it. A module depending on
    // itself is an aggregation artefact rather than a dependency, and cannot violate any rule here.
    //
    // The Kotlin plugin's SwiftPM export is the second artefact of this kind, and it is excluded by
    // name for the same reason the self-edge is dropped: it is not an edge anybody declared.
    // `swiftPMDependenciesForLockFilesMetadataClasspathDependencies` is hung on every module and
    // collects *every project in the build*, so `:core` was reported as depending on all nine client
    // modules and `./gradlew build` failed on a graph nobody had touched. It is only realised once
    // the iOS targets are, which is why `:core:jvmTest` stayed green and the whole build did not —
    // and why this went unnoticed: CI's own jobs never provoked it either.
    val ignoredConfigurations = setOf("swiftPMDependenciesForLockFiles")
    val edges = linkedMapOf<Pair<String, String>, MutableSet<String>>()
    rootProject.subprojects.forEach { subproject ->
        subproject.configurations
            .filterNot { configuration -> ignoredConfigurations.any { configuration.name.startsWith(it) } }
            .forEach { configuration ->
                configuration.dependencies.withType<ProjectDependency>()
                    .filter { it.path != subproject.path }
                    .forEach { dependency ->
                        edges.getOrPut(subproject.path to dependency.path) { sortedSetOf() }
                            .add(configuration.name)
                    }
            }
    }

    val violations = edges.entries.mapNotNull { (edge, configurations) ->
        val (from, to) = edge

        // Rules 6–8: the graph points inward, and the two ends of it are sealed. Each of the three
        // is true today and held only by nobody having written the line yet.

        // 6. `core` is the centre: every other module points at it, and it points at nothing.
        // Absolute rather than main-source-only, unlike rule 5 — "core depends on nothing" is the
        // invariant as written, and core hosts its own test helpers in `commonTest` already.
        if (from == ":core") {
            return@mapNotNull Triple(edge, "core may not depend on any module", configurations.toList())
        }

        // 7. The composition root is a sink, with exactly one name allowed through. The sink is
        // what makes the shell's exemption from rules 2–4 safe: it may see every layer precisely
        // because nothing sees it, so the layers it mixes cannot travel anywhere.
        //
        // `:androidApp` is the exception, settled at 0.2.0 when the wrapper this comment used to
        // anticipate actually landed. Three things decided it:
        //
        //   The edge is forced. AGP 9 stopped the Kotlin Multiplatform plugin working alongside
        //   `com.android.application`, so the shell cannot package itself for Android the way it
        //   already packages itself for desktop, and the wrapper has to reach `App()`.
        //
        //   The edge is not new. `iosApp/` links the same composition root and calls
        //   `MainViewController()`; it escapes this check only by being an Xcode project rather
        //   than a Gradle module. Android is the first platform whose wrapper the graph can see.
        //
        //   The edge carries nothing. Every project dependency the shell declares is
        //   `implementation`, so `:androidApp` sees `App()` and `MainActivity` and not one layer
        //   module — not a presentation, not a data, not `:core`. The property this rule defends
        //   survives the exception. What would *not* survive it is the literal alternative: an
        //   `:androidApp` depending on all nine feature and design modules and re-doing the
        //   composition, which is a second composition root mixing every layer with nothing
        //   protecting it.
        if (to == ":client:shell" && from !in platformEntryPoints) {
            return@mapNotNull Triple(
                edge,
                "nothing may depend on the composition root except a platform entry point " +
                    "(${platformEntryPoints.joinToString()})",
                configurations.toList(),
            )
        }

        // 8. The harness and the server run the simulation, not the app. Neither has any business
        // in a client module, and both would silently gain a Compose dependency by reaching one.
        if (from in setOf(":sim", ":server") && to.startsWith(":client")) {
            return@mapNotNull Triple(
                edge,
                "${from.removePrefix(":")} may not depend on a client module",
                configurations.toList(),
            )
        }

        // Rules 2–4. Test source sets count: a presentation module that reaches a data module only
        // from `commonTest` still compiles against it, still couples to it, and is exactly as
        // expensive to unpick later.
        val fromLayer = layerOf(from)
        if (fromLayer != null && layerOf(to) in forbiddenLayerDependencies.getValue(fromLayer)) {
            return@mapNotNull Triple(
                edge,
                "$fromLayer may not depend on ${layerOf(to)}",
                configurations.toList(),
            )
        }

        // Rule 5. Only the *shipping* configurations are the violation — a test source set reaching
        // a testing module is the entire reason testing modules exist, since a `commonTest` is
        // invisible to consumers and KMP cannot host a test-fixtures source set. What the rule
        // stops is `commonMain` doing it, which is how fakes end up inside the app: a plain module
        // is on the compile classpath of whoever asks, and unlike `testFixtures` nothing about the
        // dependency itself says "tests only".
        val shipping = configurations.filterNot(::isTestConfiguration)
        if (!isTestingModule(from) && isTestingModule(to) && shipping.isNotEmpty()) {
            return@mapNotNull Triple(
                edge,
                "a testing module may only be reached from a test source set — fakes must not ship",
                shipping,
            )
        }

        null
    }

    if (violations.isNotEmpty()) {
        throw GradleException(
            buildString {
                appendLine("Module dependency rules violated:")
                appendLine()
                violations.forEach { (edge, reason, configurations) ->
                    val (from, to) = edge
                    appendLine("  $from -> $to")
                    appendLine("    $reason")
                    appendLine("    declared in: ${configurations.joinToString()}")
                }
                append("See the `module-rules` skill.")
            },
        )
    }

    // Features never depend on each other — an existing architecture rule, and the one that sent
    // the tab bar and the resource rail into the shell. Reported rather than enforced, at Davide's
    // call (2026-08-07): it has real exceptions to weigh case by case, and a hard failure would
    // decide them in advance. It surfaces on the build that introduces the edge, because that is
    // the build whose script change invalidated the configuration cache.
    edges.keys
        .filter { (from, to) ->
            val fromFeature = featureOf(from) ?: return@filter false
            featureOf(to)?.let { it != fromFeature } == true
        }
        .forEach { (from, to) ->
            logger.warn(
                "Module rules: $from depends on $to, so the ${featureOf(from)} feature sees the " +
                    "${featureOf(to)} feature — features are meant not to. Worth a second look.",
            )
        }
}
