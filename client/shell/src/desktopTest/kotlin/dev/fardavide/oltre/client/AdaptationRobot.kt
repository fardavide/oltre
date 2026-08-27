package dev.fardavide.oltre.client

import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.fleets.presentation.toFleetsUiState
import dev.fardavide.oltre.client.fleets.presentation.FleetsScreen
import dev.fardavide.oltre.client.galaxy.presentation.GalaxyLanding
import dev.fardavide.oltre.client.galaxy.presentation.GalaxyScreen
import dev.fardavide.oltre.client.research.presentation.toResearchUiState
import dev.fardavide.oltre.client.research.ui.ResearchScreen
import dev.fardavide.oltre.client.shipyard.presentation.toShipyardUiState
import dev.fardavide.oltre.client.shipyard.ui.ShipyardScreen
import dev.fardavide.oltre.client.tilt.domain.Tilt
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.StartAdaptationResult
import dev.fardavide.oltre.core.StartResearchResult
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.startAdaptation
import dev.fardavide.oltre.core.startResearch
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// The one interaction that spans two screens, so it is the one test the shell has to own: neither
// feature module can see the other, and the seam between them — a blocked world naming a ladder,
// and the tab that sells it — is exactly what the composition root is for.
//
// The clock is the test's rather than the system's. `App` reads `Clock.System`, which a behaviour
// test cannot wind forward three hours, so this harness is the same wiring with an instant it can
// move. What it must not do is fake `core`: every transition below is the real `startAdaptation`
// and the real `advance`, and the verdict that changes is computed by the real `verdictFor`.
internal class TestGame(initial: GameState, start: Instant) {

    var state by mutableStateOf(initial)
        private set

    var now by mutableStateOf(start)
        private set

    fun start(technology: AdaptationTechnology) {
        val result = startAdaptation(state, technology, at = now)
        state = (result as StartAdaptationResult.Started).state
    }

    // **The applied branch, driven for real since 0.12.2.** The harness used to wire `onStartResearch`
    // to nothing, which was right while this file's one journey was a ladder: an unwired callback is a
    // screen whose taps cannot change the colony. It stopped being right when the two branches came
    // apart, because the seam under test is now precisely whether starting one leaves the other free —
    // a question no harness that can only start ladders can ask.
    //
    // The cast is the assertion: a refused start fails here, by name, rather than as a missing row
    // three lines later.
    fun start(technology: Technology) {
        val result = startResearch(state, technology, at = now)
        state = (result as StartResearchResult.Started).state
    }

    fun letTimePass(by: Duration) {
        val to = now + by
        state = advance(state, from = now, to = to)
        now = to
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun game(game: TestGame, block: AdaptationRobot.() -> Unit) {
    runDesktopComposeUiTest(width = 393, height = 852) {
        setContent {
            OltreTheme {
                Surface {
                    MainScaffold(
                        // Desktop has no motion sensor, so this is also what the app itself passes here.
                        tilt = { Tilt.NONE },
                        player = testPlayerStripUiState,
                        resources = game.state.toResourceRailUiState(),
                        // The colony is not what this test is about, and a screen it does not drive
                        // is a screen whose text could collide with an assertion.
                        colony = {},
                        research = { scroll ->
                            ResearchScreen(
                                scrollState = scroll,
                                uiState = game.state.toResearchUiState(
                                    now = game.now,
                                    timeZone = TimeZone.UTC,
                                ),
                                onStartResearch = { game.start(it) },
                                onStartAdaptation = { game.start(it) },
                                onToggleTechnologyWatch = {},
                                onToggleAdaptationWatch = {},
                            )
                        },
                        galaxy = { scroll, openResearch ->
                            GalaxyScreen(
                                scrollState = scroll,
                                state = game.state,
                                now = game.now,
                                timeZone = TimeZone.UTC,
                                onOpenResearch = openResearch,
                                // The worlds list rather than the map, because the journey under
                                // test starts on a blocked world's row and a row only exists on a
                                // list. Which of the two the tab lands on is a preference the screen
                                // is handed, so saying so here is cheaper and clearer than tapping
                                // the switch on the way past.
                                landing = GalaxyLanding.WORLDS,
                                onLandingChange = {},
                                // This harness is about the adaptation deep link — the Galaxy row
                                // that reaches Research — so the fourth and fifth verbs are wired to
                                // nothing and the assertions stay about the one journey under test.
                                onDispatchProbe = {},
                                onDispatchRun = { _, _, _, _ -> true },
                                onToggleAnnounce = {},
                            )
                        },
                        // The two tabs this harness never opens. Real screens rather than markers,
                        // because the journey under test crosses the tab bar and a destination that
                        // could not compose would be a trap the day it did.
                        shipyard = { scroll ->
                            ShipyardScreen(
                                scrollState = scroll,
                                uiState = game.state.toShipyardUiState(now = game.now, timeZone = TimeZone.UTC),
                                onBuild = {},
                                onToggleAlert = {},
                            )
                        },
                        fleets = { scroll ->
                            FleetsScreen(
                                scrollState = scroll,
                                state = game.state,
                                now = game.now,
                                timeZone = TimeZone.UTC,
                                // This harness is about the adaptation ladder reaching Research from
                                // a Galaxy row; the Fleets tab is here to exist, not to be driven.
                                onDispatchRun = { _, _, _, _ -> true },
                                onToggleAnnounce = {},
                            )
                        },
                        // Null: this harness is a colony with signal, which is the ordinary case and
                        // the one every frame that is not about the network wants.
                        offline = null,
                        // Never tapped: this harness is about the adaptation ladder reaching Research
                        // from a Galaxy row. `AlertSheetAppBehaviourTest` is where the gear is driven.
                        onOpenSettings = {},
                    )
                }
            }
        }
        AdaptationRobot(this, game).block()
    }
}

@OptIn(ExperimentalTestApi::class)
internal class AdaptationRobot(private val test: ComposeUiTest, private val game: TestGame) {

    fun open(tab: OltreTab) = apply {
        test.onNodeWithTag(ShellTestTags.tab(tab)).performClick()
    }

    fun assertShowing(tab: OltreTab) = apply {
        test.onNodeWithTag(ShellTestTags.tab(tab)).assertIsSelected()
    }

    // The blocked row's remedy, which is a tap target since 0.0.18. Addressed by the string the
    // player reads, because that string *is* the affordance — an accent word that names a level.
    fun tapTheRemedy(label: String) = apply {
        test.onNodeWithText(label).performScrollTo().performClick()
    }

    // **"The only" stopped being true at 0.9**, when the branch grew a fourth applied row: a colony
    // that could afford exactly one project can now afford two, because Prospecting opens behind
    // Extraction 1 and costs what the others cost. The test is about the *slot* rather than about
    // which row fills it, so it takes the first offered — and `assertNothingOffersResearch` below is
    // still the assertion that matters, and is unaffected, because it asserts that none match.
    //
    // The tab bar says "Research" too and always will, so the destination is excluded by tag rather
    // than by hoping the strings stay different.
    fun startTheFirstProjectOffered() = apply {
        test.onAllNodes(researchButton).onFirst().performScrollTo().performClick()
    }

    // Which row, by tag — because "the only one offered" stopped identifying a row at 0.9, and both
    // tests that used it are about a *particular* project rather than about the slot. Matching on the
    // word "Atmospheric" is not enough either: every row shares one screen, so an ancestor query
    // reaches all of them.
    //
    // The tag is spelled out rather than imported because `ResearchTestTags` is internal to
    // `:client:research:presentation` and this test lives across the seam in the shell. That is a
    // duplication with teeth rather than a silent one: rename the tag and this test fails by name on
    // the next run.
    fun startTheAtmosphericLadder() = apply {
        test.onNode(hasTestTag(ATMOSPHERIC_ACTION)).performScrollTo().performClick()
    }

    // The applied counterpart, by tag for the same reason and named for the same need: the test that
    // uses it is about *which* row started, so "the first one offered" would not identify it — and
    // after 0.12.2 a ladder no longer holds the applied branch, so which rows are offered while one
    // is running is exactly the thing under test rather than a fact the fixture can assume.
    fun startTheEnrichmentProject() = apply {
        test.onNode(hasTestTag(ENRICHMENT_ACTION)).performScrollTo().performClick()
    }

    fun assertNothingOffersResearch() = apply {
        test.onNode(researchButton).assertDoesNotExist()
    }

    // Scrolls first, because both screens are taller than a phone: the galaxy lists a system's
    // worlds under its map, and six research rows need about 105dp more than a 393x852 window has.
    // A player scrolls to read the sixth row too, so a test that refused to would be asserting
    // something no one experiences.
    //
    // **First rather than only**, since a row became tappable at 0.6.0. A clickable card sets
    // `mergeDescendants`, so a string inside one now satisfies both the card's merged semantics and
    // the column's own — two nodes, same words, same place on screen. Taking the first is not
    // loosening the assertion: the alternative reading, that two *different* rows say it, is what
    // `assertNothingReads` below and the row-scoped assertions on the screens themselves are for.
    fun assertReads(text: String) = apply {
        test.onAllNodesWithText(text, substring = true).onFirst().performScrollTo().assertIsDisplayed()
    }

    fun assertNothingReads(text: String) = apply {
        test.onNodeWithText(text, substring = true).assertDoesNotExist()
    }

    fun letTimePass(by: Duration) = apply {
        game.letTimePass(by)
        test.waitForIdle()
    }

    private companion object {
        val researchButton = hasText("Research") and !hasTestTag(ShellTestTags.tab(OltreTab.RESEARCH))

        // Mirrors `ResearchTestTags.action(AdaptationTechnology.ATMOSPHERIC)`, which is internal to
        // another module. See `startTheAtmosphericLadder`.
        const val ATMOSPHERIC_ACTION = "research-action-atmospheric"

        // And `ResearchTestTags.action(Technology.ENRICHMENT)`, mirrored for the same reason.
        const val ENRICHMENT_ACTION = "research-action-enrichment"
    }
}
