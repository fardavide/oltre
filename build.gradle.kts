// Every plugin is declared here (apply false) so the Kotlin/Android Gradle plugins load in a
// single classloader — applying them per-module only leads to BuildService class-cast clashes.
// Kover is the exception that proves the rule: the root project *is* the aggregator, so it is
// the one plugin applied here rather than declared and left to the modules.
plugins {
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
                // `:client:design` has no tests at all — and a filtered run that matches nothing
                // there is the expected outcome, not a failure.
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
dependencies {
    kover(projects.core)
    kover(projects.sim)
    kover(projects.server)
    kover(projects.client.design)
    kover(projects.client.shell)
    kover(projects.client.colony.presentation)
    kover(projects.client.notifications.data)
    kover(projects.client.research.presentation)
    kover(projects.client.save.data)
}

kover {
    reports {
        filters {
            excludes {
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
                classes("dev.fardavide.oltre.sim.MainKt", "dev.fardavide.oltre.server.MainKt")
            }
        }
        total {
            // Reporting only — no thresholds. A coverage gate is a design decision with a
            // number attached, and numbers are Davide's; the PR comment exists so the trend is
            // visible before anyone picks one.
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

// ── Rules 2–4: which layer may see which ──────────────────────────────────────────────────────
//
// A module's layer is the last segment of its Gradle path, so `:client:save:data` is data and
// `:client:colony:presentation` is presentation. Three edges are forbidden, and each is forbidden
// for its own reason rather than for symmetry:
//
//   domain       -> data, presentation   domain is the feature's rules; it defines the interfaces
//                                        data implements and knows nothing of a screen.
//   presentation -> data                 a screen talks to domain, never to a store or a socket;
//                                        the day a feature grows a domain layer, a presentation
//                                        that reached past it has to be rewritten, not rewired.
//   data         -> presentation         obvious, and cheap to keep obvious.
//
// Only those three names are layers. `:core`, `:sim`, `:server`, `:client:design` and
// `:client:shell` are not, and are deliberately unconstrained here: the composition root is the
// one module that may see every layer — that is the whole of its job — and the graph already
// stops it from being anything else, because nothing depends on it. Rule 1 (a module cannot
// contain a module) is checked in `settings.gradle.kts`, where it fails the sync earliest.
//
// Checked at configuration time, not in a task, so a violation fails the IDE sync as well as the
// build. Configuration is skipped on a configuration-cache hit, which is the behaviour we want:
// the check re-runs exactly when a build script changes, which is exactly when an edge can appear.
val forbiddenLayerDependencies = mapOf(
    "domain" to setOf("data", "presentation"),
    "presentation" to setOf("data"),
    "data" to setOf("presentation"),
)

// A testing module is a sibling named after what it doubles — `:client:save:data-testing` beside
// `:client:save:data` — so it carries that module's layer and that module's restrictions. Without
// this, `presentation-testing` is not `presentation`, and a presentation module reaches data
// through its own fakes: the rule holds on the direct edge and leaks on the one hop through.
// It reads the right way round too — a fake of a domain interface has no more business knowing
// about a store than the domain does.
fun layerOf(projectPath: String): String? = projectPath
    .substringAfterLast(':')
    .removeSuffix("-testing")
    .takeIf { it in setOf("domain", "data", "presentation") }

// `:client:save:data` -> `save`. Null for anything that is not a client feature module, including
// `:client:design` and `:client:shell` — those live directly under `client/`, so they have no
// feature directory above them and cannot be one feature reaching into another.
fun featureOf(projectPath: String): String? = projectPath
    .removePrefix(":")
    .split(':')
    .takeIf { it.size >= 3 && it.first() == "client" }
    ?.get(1)

gradle.projectsEvaluated {
    // Edge -> the configurations that declare it. A module dependency is usually declared once,
    // but a KMP source-set pair (`commonMain` + `commonTest`) declares two, and naming both is
    // what makes the message point at a line rather than at a module.
    val edges = linkedMapOf<Pair<String, String>, MutableSet<String>>()
    rootProject.subprojects.forEach { subproject ->
        subproject.configurations.forEach { configuration ->
            configuration.dependencies.withType<ProjectDependency>().forEach { dependency ->
                edges.getOrPut(subproject.path to dependency.path) { sortedSetOf() }
                    .add(configuration.name)
            }
        }
    }

    // Test source sets count. A presentation module that reaches a data module only from
    // `commonTest` still compiles against it, still couples to it, and is exactly as expensive to
    // unpick later.
    val violations = edges.keys.filter { (from, to) ->
        val fromLayer = layerOf(from) ?: return@filter false
        layerOf(to) in forbiddenLayerDependencies.getValue(fromLayer)
    }

    if (violations.isNotEmpty()) {
        throw GradleException(
            buildString {
                appendLine("Module dependency rules violated:")
                appendLine()
                violations.forEach { edge ->
                    val (from, to) = edge
                    appendLine("  $from -> $to")
                    appendLine("    ${layerOf(from)} may not depend on ${layerOf(to)}")
                    appendLine("    declared in: ${edges.getValue(edge).joinToString()}")
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
