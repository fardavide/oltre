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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.galaxy.presentation.GalaxyScreen
import dev.fardavide.oltre.client.research.presentation.ResearchScreen
import dev.fardavide.oltre.client.research.presentation.toResearchUiState
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.StartAdaptationResult
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.startAdaptation
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
                                onStartResearch = {},
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
                                // This harness is about the adaptation deep link — the Galaxy row
                                // that reaches Research — so the fourth verb is wired to nothing
                                // and the assertions stay about the one journey under test.
                                onDispatchProbe = {},
                            )
                        },
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

    // The colony below can afford exactly one project, so there is exactly one Research *button* —
    // which is the setup saying what the test is about rather than a lucky query. The tab bar says
    // "Research" too and always will, so the destination is excluded by tag rather than by hoping
    // the strings stay different.
    fun startTheOnlyProjectOffered() = apply {
        test.onNode(researchButton).performScrollTo().performClick()
    }

    fun assertNothingOffersResearch() = apply {
        test.onNode(researchButton).assertDoesNotExist()
    }

    // Scrolls first, because both screens are taller than a phone: the galaxy lists a system's
    // worlds under its map, and six research rows need about 105dp more than a 393x852 window has.
    // A player scrolls to read the sixth row too, so a test that refused to would be asserting
    // something no one experiences.
    fun assertReads(text: String) = apply {
        test.onNodeWithText(text, substring = true).performScrollTo().assertIsDisplayed()
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
    }
}
