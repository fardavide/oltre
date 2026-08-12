package dev.fardavide.oltre.client.shipyard.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.core.BuildShipsResult
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.buildShips
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import org.junit.Test

// **The whole tab against a real `GameState`, mapper included.** Every other test in this module
// splits the work in half: `ShipyardUiStateTest` drives the mapper with no screen, and the fixtures
// in `TestShipyardUiState` drive the screen with no mapper — which is right for a baseline, because
// a screenshot should move when the screen moves and never when a balance constant does.
//
// What neither can see is the seam. A mapper that produced the wrong figure and a screen that drew
// the right one from a hand-written fixture would both pass, and the tab would still be wrong. This
// renders what a player would actually be shown, from the state the app would actually hold.
@OptIn(ExperimentalTestApi::class)
class ShipyardFromStateBehaviourTest {

    @Test
    fun `a new colony is offered its second skiff at the price the curve says`() {
        // given the granted skiff and genesis stocks — 500 metal buys the second at 120
        val state = GameState.initial(SEED)
        val second = FleetBalance.shipCost(ShipType.SKIFF, alreadyOwned = 1)

        shipyard(uiState = state.toShipyardUiState()) {
            assertReads("1 hull")
            assertCardReads(ShipType.SKIFF, "1 owned · 1 idle")
            assertCardReads(ShipType.SKIFF, second.metal.toString())
            assertOffersToBuild(ShipType.SKIFF)
        }
    }

    @Test
    fun `buying a hull moves the tab on to the next rung`() {
        // The seam this test exists for: the state changes, the mapper re-reads it, and the screen
        // has to show the *next* price rather than the one that was just paid.
        val rich = GameState.initial(SEED).copy(resources = Resources.of(metal = 10_000, crystal = 10_000))
        val after = assertIs<BuildShipsResult.Started>(
            buildShips(rich, Ships.of(ShipType.SKIFF, 1), at = EPOCH),
        ).state
        val third = FleetBalance.shipCost(ShipType.SKIFF, alreadyOwned = 2)

        shipyard(uiState = after.toShipyardUiState()) {
            assertReads("2 hulls")
            assertCardReads(ShipType.SKIFF, "2 owned · 2 idle")
            assertCardReads(ShipType.SKIFF, third.metal.toString())
        }
    }

    @Test
    fun `a colony that cannot pay is shown a wait rather than a dead button`() {
        val broke = GameState.initial(SEED).copy(resources = Resources.of())

        shipyard(uiState = broke.toShipyardUiState()) {
            // The exact wait is the colony's arithmetic and is pinned in `ShipyardUiStateTest`;
            // what this asserts is that whatever it says reaches the button rather than a "Build"
            // the verb would refuse.
            assertCardDoesNotRead(ShipType.SKIFF, "Build")
        }
    }

    @Test
    fun `the tap reaches the verb and the verb reaches the pool`() {
        // End to end through the real mapper, the real screen and the real `core` verb — the one
        // test in the module where nothing is a fixture.
        val rich = GameState.initial(SEED).copy(resources = Resources.of(metal = 10_000, crystal = 10_000))
        var state = rich

        runDesktopComposeUiTest(width = PHONE_WIDTH, height = 852) {
            setContent {
                OltreTheme {
                    Surface {
                        ShipyardScreen(
                            uiState = state.toShipyardUiState(),
                            onBuild = { type ->
                                state = assertIs<BuildShipsResult.Started>(
                                    buildShips(state, Ships.of(type, 1), at = EPOCH),
                                ).state
                            },
                        )
                    }
                }
            }
            onNodeWithTag(ShipyardTestTags.action(ShipType.SKIFF), useUnmergedTree = true).performClick()
        }

        assertEquals(Ships.of(ShipType.SKIFF, 2), state.ships)
        assertEquals(rich.resources - FleetBalance.shipCost(ShipType.SKIFF, alreadyOwned = 1), state.resources)
    }

    private companion object {
        val SEED = GalaxySeed(20_260_807L)
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
