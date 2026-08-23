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
import dev.fardavide.oltre.core.AlertSettings
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

    // **The same sheet from the other door, with the bell lit.** Photographed here as well as on the
    // Galaxy side, and not because the two frames would differ: it is that they must not. Module rule
    // 5 stops either screen seeing the other, so the only thing keeping one door from drifting away
    // from the other is that both are drawn.
    @Test
    fun `a worked row raises the sheet with the flight asked about`() {
        capture(state = idle.copy(announceFlights = true), name = "fleets_dispatch_announced")
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
                            onToggleAnnounce = {},
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

        // **`CARRIED_FORWARD`, for the reason `testGameState` states on the Galaxy side.** A new
        // colony asks about alerts by kind and the sheet then carries no bell at all — so left
        // inherited, `fleets_dispatch_announced`, which exists to photograph the bell *lit*, would
        // have become a picture of no bell.
        val idle: GameState = GameState.initial(SEED).copy(
            ships = Ships.of(ShipType.SKIFF, 1),
            eventLog = landings,
            alerts = AlertSettings.CARRIED_FORWARD,
        )

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
