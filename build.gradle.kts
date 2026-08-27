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
    kover(projects.protocol)
    kover(projects.sim)
    kover(projects.server)
    kover(projects.client.design.component)
    kover(projects.client.design.core)
    kover(projects.client.design.format)
    kover(projects.client.design.icon)
    kover(projects.client.design.text)
    kover(projects.client.shell)
    kover(projects.client.auth.data)
    kover(projects.client.auth.presentation)
    kover(projects.client.auth.ui)
    kover(projects.client.changelog.domain)
    kover(projects.client.changelog.presentation)
    kover(projects.client.changelog.ui)
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
    kover(projects.client.net.data)
    kover(projects.client.net.domain)
    kover(projects.client.notifications.data)
    kover(projects.client.player.ui)
    kover(projects.client.research.presentation)
    kover(projects.client.research.ui)
    kover(projects.client.save.data)
    kover(projects.client.settings.presentation)
    kover(projects.client.settings.ui)
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
                //
                // ── And the store, on exactly the same sentence ───────────────────────────────
                //
                // A unit test cannot render a composable; a unit test cannot open a connection to a
                // database. `PostgresColonyRepository.kt` and `PostgresDatabase.kt` hold every line
                // in `:server` that needs one — the pool, the DDL, the transaction and the seven
                // statements — and the seventh entry in this block covers those two files and
                // nothing else. Davide's call, 2026-08-25, on a failing report at #109: the unit
                // row fell **92.503% → 91.730%** line and **86.629% → 86.284%** branch, on 76 lines
                // and 18 branches that no test of this kind can reach.
                //
                // **#110 added a third file under this pattern without changing a character of it**,
                // and saying so is the point of this paragraph rather than an aside.
                // `PostgresPlayerRepository.kt` is the `players` table — four statements, one
                // transaction, and no branch on an identity, a provider or a token — so it meets the
                // condition below rather than merely matching the glob. Who a token says somebody is
                // is `IdTokens.kt`; whether a session is still good is `Sessions.kt`; what happens
                // when a player is not there is `Authenticator.kt`. All three are plain `…Test`s, and
                // a fourth file that drifted a decision back into the store would be quietly hidden
                // here — which is what the condition, and this note, exist to stop.
                //
                // **The condition is structural and checkable, which is what earns it**, like
                // `StepperGesture.kt`'s *"nothing in that file draws"*: **nothing in these two files
                // decides anything.** The row-to-colony mapping is `ColonyRow.kt` — including what a
                // row that will not decode means, which is a `when` with three arms and a unit test
                // per arm — and the compare-and-set's retry policy is `Endpoints.kt`. Both were put
                // there rather than inside the JDBC calls for exactly this reason, which is #108's
                // move made once more: *a decision belongs where the kind of test that judges it can
                // reach it.* A rule that drifted back into the store would leave this block quietly
                // hiding it, so the two files earn the exclusion by holding nothing but connections
                // and statements.
                //
                // Scoped to this pass like the entries around it, which is what keeps it safe: the
                // integration and unfiltered passes see every line and report the two files at
                // **99% and 98%**, so a statement no test runs at all still shows up in the row
                // whose job that is. This removes a number no unit test could ever move.
                //
                // ── And the drawing the annotation cannot see ─────────────────────────────────
                //
                // The third entry, added at #113 with Davide's say-so and on a failing report. The
                // rule above is *a unit test cannot render a composable*, and `annotatedBy` lands on
                // exactly the functions carrying the annotation — which is most of the drawing and
                // not all of it. What it misses is the drawing that is **not itself a composable**: a
                // `DrawScope` extension, a private draw helper, a `Path` built from published vector
                // data, a geometry function a `Canvas` calls. None of it can execute without a
                // composition, and all of it read 0% in this pass.
                //
                // Measured at #113: `SystemMapKt` **111 lines** of orbit geometry at 0%, `TabIconKt`
                // 55, `LedgerHeadKt` 20, `MotionKt` 17, and the icon files' path builders — 942 missed
                // lines in the pass, the overwhelming majority of them a drawing. Left in, the unit
                // row goes on measuring *what fraction of the repository is UI*, which is the very
                // sentence the first entry was written against; the annotation just did not reach far
                // enough to make it true.
                //
                // **By layer plus the two design modules that draw**, because that is where the
                // uncatchable half lives: every `*.ui`, `:client:design:icon` (glyphs, all of them
                // paths) and `:client:design:component` (the controls). `:client:design:core`,
                // `:format` and `:text` are *not* here — they are tokens, arithmetic and a table, and
                // unit tests cover them thoroughly.
                //
                // **What this costs is real and worth stating**: a ui module also holds the models it
                // renders, and unit tests do assert on those (`ShipyardUiStateTest` reads a
                // `HullUiState`). Those are data classes whose members are generated, so what leaves
                // the denominator is mostly `equals`/`hashCode`/`copy` — but not entirely, and a
                // `companion` with a real default in it would now be invisible here. The behaviour and
                // unfiltered passes still see all of it.
                //
                // **It steps the row up**, 91.34% → ~95.1% line and 86.22% → ~87.6% branch. That is
                // the new baseline, not a gain.
                if (testCategory == "unit") {
                    annotatedBy("androidx.compose.runtime.Composable")
                    classes("dev.fardavide.oltre.server.Postgres*")
                    packages("*.ui")
                    packages("*.design.icon", "*.design.component")
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
                //
                // **`StepperGesture.kt` is the same rule reaching something a layer name cannot
                // describe** — Davide's call, 2026-08-17, on a failing report. A screenshot test
                // renders a frame; it cannot *press* one. So a pointer-input handler is uncoverable
                // by this kind for exactly the reason a mapper is, and the fourteen lines of the
                // dispatch stepper's hold gesture took the screenshot rows down 0.22 and 0.14 on a
                // branch whose behaviour row went *up*.
                //
                // **Named by file rather than by layer, because there is no layer for a gesture.**
                // That makes it the narrowest entry in this block and the one that has to earn
                // itself hardest, so the condition is structural: **nothing in that file draws.** It
                // holds one `Modifier` extension and no composable that emits anything, which is why
                // it was split out of `DispatchSheet.kt` rather than annotated in place — a rule
                // that is enforced by what a file is allowed to contain, not by a comment.
                //
                // Scoped to this pass exactly like the two above, which is what keeps it safe: the
                // behaviour and unfiltered passes still see every line, and three behaviour tests in
                // `DispatchSheetBehaviourTest` are what actually prove the gesture works — a tap
                // steps once, a hold repeats, a hold on a disabled control does nothing. This
                // removes a number no test of this kind could ever move; it removes nothing the gate
                // could see.
                // **`App.kt` is the same rule again, reaching the composition root** — Davide's
                // call, 2026-08-21, on a failing report, and the fourth entry this block's own
                // condition asks to be earned.
                //
                // A screenshot test renders a *frame*. It cannot **launch the app**: `App` reads the
                // save, resumes the colony, books alerts and holds the session, none of which a
                // capture can drive. Nothing calls it — the shell's own baselines render
                // `MainScaffold`, `Starfield` and `TabBar` directly — so all **124** of its branches
                // are unreachable by this kind, for exactly the reason a mapper and a gesture are.
                //
                // **The trailing `*` is load-bearing and reaches nothing else**, which was checked
                // rather than assumed: 112 of those branches are `AppKt` itself and the remaining 12
                // are three of its own lambdas (`AppKt$App$7$1$4$1` and two siblings), the suspending
                // handlers behind act, alert, skip and reset. Every class the pattern removes is
                // `App`'s own body.
                //
                // **What made it load-bearing is that it taxes the seams.** Every parameter on `App`
                // is a seam that lets a test stop depending on the machine it runs on, and the
                // Compose compiler emits `$changed` bookkeeping per parameter — so *adding one costs
                // six uncoverable branches here*, whether it is defaulted or required (both were
                // measured). The screenshot row fell 51.6% → 51.4% on the change that gave the shell
                // a clock, a change whose whole purpose was to make the coverage numbers
                // reproducible at all. Left as it was, this row charges a fee for every future seam
                // and pays it out of a number that was never measuring the drawings.
                //
                // Scoped to the pass like the three above, which is what keeps it safe: the
                // behaviour and unfiltered passes still see every line of `App`, and the behaviour
                // pass is where it is actually driven — `AppBehaviourTest` launches it end to end.
                // This removes a number no test of this kind could ever move.
                //
                // **It steps the row up**, 51.4% → ~54.1%, because 112 branches leave the
                // denominator at once. That is the new baseline, not a gain.
                // **`:client:debug:ui` is the fifth entry, and it is the only one here excluded by a
                // ruling rather than by an argument** — Davide's, at #113, on a failing report, and
                // the ruling it agrees with is his own from 2026-08-09 in `session-roles.md`: the
                // debug panel *"carries no screenshot test at all, deliberately, because a baseline
                // asserts that a drawing still looks the way it was drawn and nobody drew this."*
                //
                // So it is 154 lines and 100 branches at 0% in this pass, permanently and on purpose,
                // and it is the single largest item in the row. Every other entry above says *this
                // kind of test cannot reach it*; this one says *there is nothing here to assert*, and
                // the report was reading the second as a shortfall of the first.
                //
                // **The condition is the ruling, so a designed surface must not be added under it.**
                // The day the panel is drawn rather than assembled, this line comes off and a baseline
                // goes in — which is what makes it checkable rather than a permanent hiding place.
                //
                // Measured: screenshot **92.24% → 94.75%** line and **56.31% → 58.59%** branch, which
                // is the new baseline rather than a gain.
                if (testCategory == "screenshot") {
                    packages("*.presentation", "*.domain", "*.data")
                    classes("dev.fardavide.oltre.core.**")
                    classes("dev.fardavide.oltre.client.dispatch.ui.StepperGestureKt*")
                    classes("dev.fardavide.oltre.client.AppKt*")
                    packages("*.debug.ui")
                }
                // ── The drawing and the mapping, and **only while measuring the integration
                // pass** ─────────────────────────────────────────────────────────────────────
                //
                // The third pass to be narrowed, and the last one that had not been. Davide's call,
                // 2026-08-27, on a failing report at #113: the row fell **14.9% → 13.46%** on a slice
                // that added an integration test and covered forty-five more lines than `main` did.
                //
                // **The row was measuring the size of the repository.** An integration test crosses
                // one real boundary — a file, a socket, a platform adapter — and there are eight of
                // them, so an unnarrowed pass reports *what fraction of the whole codebase eight
                // boundary tests happen to execute*. `Strings` at 0%, `English` at 0%, `StringId` at
                // 0%, every screen at 0%: 13,721 missed lines, of which the overwhelming majority are
                // a drawing or a table. That number falls on every screen that ships and every string
                // that is written, and no better integration test can recover it — which is the unit
                // row's defect and the screenshot row's defect, in the one pass that had not yet been
                // given the same treatment.
                //
                // **The rule is the screenshot pass's, with the sign flipped once more.** An
                // integration test renders nothing: there is no composition, so a composable cannot
                // execute and neither can the mapper that feeds one. So `*.presentation` and `*.ui`
                // go, and every `@Composable` wherever it lives — which is what reaches `App` and the
                // design system, neither of which is in one of those two packages.
                //
                // **`core` deliberately stays**, and saying so is the point of this paragraph rather
                // than an aside. It is pure and has no boundary of its own, so the symmetry would put
                // it here — and dropping it takes the row **13.46% → 7.78%**, because `core` is
                // exactly what the server's replay tests execute across a socket and a database.
                // *Reached across a boundary* is the test, not *contains one*; a rule written from
                // the symmetry rather than from the report would have hidden the one thing this pass
                // measures best.
                //
                // Scoped to this pass like the four above, which is what keeps it safe: the unit and
                // unfiltered passes see every mapper and every composable, so a screen no test
                // reaches at all still shows up in the row whose job that is.
                //
                // **It steps the row up**, 13.46% → ~22%, because eleven thousand unreachable lines
                // leave the denominator at once. That is the new baseline, not a gain.
                if (testCategory == "integration") {
                    annotatedBy("androidx.compose.runtime.Composable")
                    packages("*.presentation", "*.ui")
                }
                // ── The catalogue, and **only while measuring a pass that renders** ──────────
                //
                // The third of these, added at #86 with Davide's say-so and on the argument the two
                // above make. `:client:design:text` is `TextRes`, the `Strings` catalogue and
                // `English`: 309 one-line entries and a `when` with a branch per `StringId`. It
                // draws nothing and decides nothing — it is the *table* of what the game says.
                //
                // Left in, the screenshot row stops measuring how well the drawings are covered and
                // starts measuring **what fraction of the table a set of frames happens to quote** —
                // and the behaviour row the same, one step less sharply. Neither is a property of
                // the tests: a frame photographs one screen and quotes a dozen entries, so the
                // number falls on every entry the catalogue gains and cannot be recovered by drawing
                // or driving anything better. Measured at #86: the screenshot row fell 87.1% → 82.8%
                // on a change that moved no baseline by a byte.
                //
                // **Scoped to the two passes that render, which is what makes it safe**, exactly as
                // above. The unit pass sees every line of it and covers all of them — `CatalogueTest`
                // resolves every entry the catalogue can produce — and the unfiltered pass sees it
                // too. So nothing becomes invisible: an entry no test reaches at all still shows up
                // in the row whose job that is.
                if (testCategory == "screenshot" || testCategory == "behaviour") {
                    packages("*.design.text")
                }
                // ── The wire, and **only while measuring a pass that renders** ───────────────
                //
                // The fifth entry in this block, added at #107 with Davide's say-so and on a report
                // rather than an argument by analogy. `:protocol` is the client/server contract:
                // twelve verbs as data, an envelope, a sync pair, a rejection taxonomy and a
                // version. It draws nothing, decides nothing and performs no I/O.
                //
                // **The screenshot half is `core`'s exclusion three lines up, word for word.** A
                // screenshot test renders a frame; it cannot reach a wire contract, for exactly the
                // reason it cannot reach a `core` rule. That half is permanent.
                //
                // **The behaviour half came out at #113, exactly as this said it would.** The
                // condition was *"once the shell holds a fake transport, the behaviour suite drives
                // every verb through this module"* — and 0.21 is that slice: `App` takes an
                // `OltreApi`, every tap becomes a `ClientVerb`, and the suite drives all twelve of
                // them through `FakeOltreApi`. Left in after that it would hide coverage the tests
                // are genuinely producing.
                //
                // Its removal was self-checking in the way `client.tilt.data`'s was — the row goes
                // *up* when it goes, or the tests owed were never written — and the measurement is in
                // the pull request.
                //
                // **The screenshot half is permanent and is `core`'s exclusion above, word for word.**
                // A screenshot test renders a frame; it cannot reach a wire contract, for exactly the
                // reason it cannot reach a `core` rule.
                //
                // Measured when it went in (#107), five passes, `--no-build-cache`: screenshot
                // **93.222% → 91.125%** line and **57.529% → 55.666%** branch. `integration` fell too
                // and was deliberately never listed: 2.477% → 2.453% rounds to the same tenth, so the
                // gate does not see it, and an exclusion that buys nothing is 0.4.2's whole lesson.
                if (testCategory == "screenshot") {
                    classes("dev.fardavide.oltre.protocol.**")
                }
                // ── The server, and **only while measuring a pass that renders** ─────────────
                //
                // The sixth entry, added at #108 with Davide's say-so and on a report. `:server` is
                // two Ktor routes, the replay that drives `core`'s twelve verbs, and a colony store.
                // It draws nothing, and the only thing that will ever speak to it is a socket.
                //
                // **Both halves are permanent, which is where it differs from `:protocol` directly
                // above.** That module's behaviour exclusion comes out at #113, because the shell
                // will hold a fake transport and drive every verb through the contract. Nothing on
                // the client will ever reach *this* code — the fake transport #112 landed exists
                // precisely so the suite never talks to a server — so a behaviour test can no more
                // reach a route handler than a screenshot can reach a `core` rule, and neither will
                // change.
                //
                // Measured on the branch that added the module, five passes, `--no-build-cache`:
                // screenshot **93.222% → 90.447%** line and **57.529% → 54.928%** branch, behaviour
                // **92.259% → 90.839%** line and **68.832% → 67.414%** branch — four gated rows on a
                // PR that deleted no test and drew nothing less. With this entry all four land back
                // on the baseline to three decimals, because the package contributes 0 covered and
                // 172 missed lines to each of those passes and nothing else moved.
                //
                // Scoped to the two passes that render, which is what makes it safe: the unit,
                // integration and unfiltered passes see every line and report the package at
                // **93%, 76% and 99.4%**. So this removes a number no test of these kinds could
                // move, and removes nothing the gate could see. What is uncovered in the unit pass
                // is `OltreServer.kt` — `install`, `routing`, `respond` — and that file holds no
                // decisions by construction: they were moved into `Endpoints.kt` and `Genesis.kt`
                // for exactly this reason, rather than hidden here. See `decisions.md`.
                if (testCategory == "screenshot" || testCategory == "behaviour") {
                    classes("dev.fardavide.oltre.server.**")
                }
                // ── The sign-in, and **only while measuring the behaviour pass** ─────────────
                //
                // The seventh entry, added at #113 with Davide's say-so and on a failing report. It is
                // `:server`'s behaviour half one module over and on the identical sentence: **a
                // behaviour test cannot reach the implementation its own test double replaces.**
                //
                // `App` takes a `ProviderSignIn` and a `Set<AuthProvider>` as parameters and the suite
                // passes fakes for both — which is not a convenience, it is the seam that makes the
                // gate testable at all. A behaviour test that reached this module would be one that
                // opened a browser and waited for a person, which is exactly what the seam exists to
                // prevent. So the module reads 0% here and always will: 249 lines of it, and the row
                // was reporting that as untested code.
                //
                // **It is tested, and by two kinds.** `EncodingTest` and `OAuthFlowTest` drive the
                // PKCE derivation, the nonce shapes, the authorize URL and the redirect parse against
                // published vectors; `DesktopLoopbackIntegrationTest` drives the loopback flow end to
                // end across two real sockets. The unit, integration and unfiltered passes see every
                // line, so nothing becomes invisible — which is the property that separates this from
                // hiding something.
                //
                // **The screenshot half is unnecessary rather than declined**: `*.data` is already out
                // of that pass by the layer rule three entries up.
                //
                // Measured: behaviour **90.53% → 92.65%** line and **68.42% → 69.91%** branch.
                if (testCategory == "behaviour") {
                    classes("dev.fardavide.oltre.client.auth.data.**")
                }
                // ── `:client:net:data` had an entry here and it came out at #113 ─────────────
                //
                // Added at #112 on the same condition `:protocol`'s behaviour half carried: a
                // behaviour test drives Compose, Compose reaches a `data` module through a
                // **consumer**, and that module had none until the shell cutover. 0.21 is the
                // cutover — `App` holds an `OltreApi`, an `Outbox` and a `SessionKeeper`, and the
                // suite drives all three through `FakeOltreApi` — so the entry went with it.
                //
                // Kept as a paragraph rather than deleted, because the *reason* it was temporary is
                // the thing worth reading twice: an exclusion that names a module with no consumer
                // is describing the module graph rather than the tests, and the day the graph changes
                // it stops being true. The measurement is in the pull request.
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
                // **The `*` is #113's one-character correction and not a widening.** `onReceive`
                // launches a coroutine, Kotlin compiles that body to `BootReceiver$onReceive$1`, and
                // a pattern with no trailing `*` matches the receiver and not the six lines inside
                // it — so the entry has been excluding the empty half of the class it names since it
                // was written. Every neighbour in this block already carries the `*` for exactly this
                // reason; this one was the odd one out.
                classes("dev.fardavide.oltre.client.BootReceiver*")
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
// Only those four names are layers. `:core`, `:protocol`, `:sim`, `:server`, `:client:design` and
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
// `changelog` joined at 0.19 and it passes `world`'s test rather than inheriting it. The mark is
// drawn twice — at 319dp as a page's whole picture, and at 29dp on the settings sheet's build row —
// so `:client:settings:ui` reaches it and the cross-feature warning would fire on that edge forever.
// What makes the exclusion safe is the property this list actually asks for: **nothing points out of
// it.** `:client:changelog:*` depends on `core`, on its own domain and on the design system, and on
// no feature at all, so it cannot become the back door one tab reaches another through.
// `net` joined at 0.20 and it demonstrates that same property rather than inheriting it. Since the
// colony moved off the phone, *is this control held?* is a question every screen has to ask —
// `:client:net:domain` answers it, and eight presentation modules reach it, so the warning would fire
// eight times on every clean build. **Nothing points out of it:** `:client:net:domain` depends on
// `:protocol` alone and `:client:net:data` on those two and Ktor, so neither can carry one feature
// into another. Note the direction that makes the edge legal at all — a `presentation` may see a
// `domain` and may never see a `data`, which is why the projection is in the layer it is in.
private val sharedSurfaces = setOf("design", "dispatch", "world", "changelog", "net")

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
