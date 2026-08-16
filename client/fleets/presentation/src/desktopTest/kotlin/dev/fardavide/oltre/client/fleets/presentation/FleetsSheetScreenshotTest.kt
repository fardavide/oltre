package dev.fardavide.oltre.client.fleets.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.dispatch.presentation.DispatchSelection
import dev.fardavide.oltre.client.dispatch.ui.DispatchTestTags
import dev.fardavide.oltre.client.fleets.ui.FleetsPage
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartRunResult
import dev.fardavide.oltre.core.startRun
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import org.junit.Test

// **The two frames Claude Design asked to be judged by**, and the second is the one that matters:
// *"the refusal is the state to judge this by, because it is the common one."* A player reading the
// worked list is, by definition, usually a player whose ships are out — so the row treatment has to
// look right when the thing behind it is a countdown rather than a button.
//
// Derived from a real `GameState` through the real mappers, like every frame since 0.11. Nothing
// here performs a gesture: the sheet is up because the state says it is.
@OptIn(ExperimentalTestApi::class)
class FleetsSheetScreenshotTest {

    @Test
    fun `a worked row raises the sheet at its own defaults`() {
        capture(state = idle, name = "fleets_dispatch")
    }

    @Test
    fun `the same tap with every hull away is a countdown rather than a verb`() {
        capture(state = away, name = "fleets_dispatch_no_ships")
    }

    // The sheet is a popup and a popup is a root of its own, so `onRoot` finds two and refuses to
    // choose. The one to photograph is named by what is inside it — `GalaxyScreenshotTest` set this
    // shape and the argument for it is in `decisions.md`.
    private fun capture(state: GameState, name: String) {
        runDesktopComposeUiTest(width = 393, height = 852) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        FleetsPage(
                            uiState = state.toFleetsUiState(
                                now = EPOCH,
                                timeZone = TimeZone.UTC,
                                dispatch = DispatchSelection(at = worked, gathering = null, ships = null, window = null),
                            ),
                            onOpenWorld = {},
                            onCloseDispatch = {},
                            onSelectGathering = {},
                            onSelectShips = {},
                            onSelectWindow = {},
                            onDispatchRun = {},
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onNode(isRoot() and hasAnyDescendant(hasTestTag(DispatchTestTags.SHEET))).captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    private companion object {
        val SEED = GalaxySeed(20_260_807L)
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)

        val worked: GalaxyCoordinate = GameState.initial(SEED).galaxy.let { galaxy ->
            galaxy.surveyed.filter { it != galaxy.home }.minBy { it.slot }
        }

        val landings = listOf(
            Event.FleetReturned(
                from = worked,
                ships = Ships.of(ShipType.SKIFF, 1),
                cargo = Resources.of(metal = 132),
                at = EPOCH,
            ),
        )

        val idle: GameState = GameState.initial(SEED)
            .copy(ships = Ships.of(ShipType.SKIFF, 1), eventLog = landings)

        // **Every hull away because it was sent, not because it was deleted.** A colony with no idle
        // hull and no run in flight owns no hulls at all, which genesis forbids — and the refusal
        // built that way has no date to give, so it photographs "Nothing is idle and nothing is out"
        // instead of the countdown this frame exists for. Design's own frame is a fleet that is out.
        val away: GameState = assertIs<StartRunResult.Started>(
            startRun(
                state = idle,
                target = worked,
                gathering = ResourceKind.METAL,
                ships = Ships.of(ShipType.SKIFF, 1),
                window = 3.hours,
                at = EPOCH,
            ),
        ).state
    }
}
