package dev.fardavide.oltre.client.shipyard.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.shipyard.ui.PHONE_WIDTH
import dev.fardavide.oltre.client.shipyard.ui.ShipyardUiState
import dev.fardavide.oltre.client.shipyard.ui.shipyard
import dev.fardavide.oltre.core.BuildShipsResult
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.YardJob
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.buildShips
import kotlin.time.Duration.Companion.hours
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.datetime.TimeZone
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
    fun `a new colony is shown the second skiff and has to earn it`() {
        // given the granted skiff and genesis stocks — 500 metal, against a hull that costs 800
        // since the 0.9.0 base raise. **This test asserted `assertOffersToBuild` until then**, and
        // the change is the point rather than a casualty: a hull is no longer something a colony
        // opens holding the price of, which is what "ships are WAY too cheap" was about. 0.10.1 made
        // the price flat and this is the rung it landed on — still out of reach at hour zero, which
        // is what the test is here for.
        val state = GameState.initial(SEED)
        val second = FleetBalance.shipCost(ShipType.SKIFF)

        shipyard(uiState = state.toShipyardUiState(now = NOW, timeZone = TimeZone.UTC)) {
            assertReads("1 hull")
            assertCardReads(ShipType.SKIFF, "1 owned · 1 idle")
            assertCardReads(ShipType.SKIFF, second.metal.groupedByThousands())
            assertCardDoesNotRead(ShipType.SKIFF, "Build")
        }
    }

    @Test
    fun `a colony that can pay is offered the verb`() {
        val rich = GameState.initial(SEED).copy(resources = Resources.of(metal = 10_000, crystal = 10_000))

        shipyard(uiState = rich.toShipyardUiState(now = NOW, timeZone = TimeZone.UTC)) {
            assertOffersToBuild(ShipType.SKIFF)
        }
    }

    @Test
    fun `a hull on the slipway counts down on its own card and the verb stays live`() {
        // The one drawing 0.9.0 added, from a real state: the yard footer exists, and the Build
        // button is still there beside it — a serial queue can always take another order.
        val rich = GameState.initial(SEED).copy(resources = Resources.of(metal = 100_000, crystal = 100_000))
        val ordered = assertIs<BuildShipsResult.Started>(
            buildShips(rich, Ships.of(ShipType.SKIFF, 2), at = EPOCH),
        ).state

        shipyard(uiState = ordered.toShipyardUiState(now = NOW, timeZone = TimeZone.UTC)) {
            assertCardReads(ShipType.SKIFF, "1 owned · 1 idle · 2 building")
            assertCardReads(ShipType.SKIFF, "1 queued")
            assertOffersToBuild(ShipType.SKIFF)
        }
    }

    @Test
    fun `buying a hull moves the tab on to the slipway rather than to a new price`() {
        // The seam this test exists for: the state changes and the mapper re-reads it. Until 0.10.1
        // what it re-read was the *next rung*; with a flat price what moves is the pool line and the
        // yard, and the chips holding still is the assertion rather than the absence of one.
        val rich = GameState.initial(SEED).copy(resources = Resources.of(metal = 10_000, crystal = 10_000))
        val after = assertIs<BuildShipsResult.Started>(
            buildShips(rich, Ships.of(ShipType.SKIFF, 1), at = EPOCH),
        ).state
        val next = FleetBalance.shipCost(ShipType.SKIFF)

        shipyard(uiState = after.toShipyardUiState(now = NOW, timeZone = TimeZone.UTC)) {
            // The fleet has not grown — the hull is on the slipway — and the price is where it was,
            // because `buildShips` charges the same thing every time.
            assertReads("1 hull")
            assertCardReads(ShipType.SKIFF, "1 owned · 1 idle · 1 building")
            assertCardReads(ShipType.SKIFF, next.metal.groupedByThousands())
        }
    }

    @Test
    fun `a hull already on the slipway reads its countdown, its bar and the clock it lands on`() {
        // **The slipway footer, from a real yard.** It was the screenshot fixture that drove this
        // branch of the mapper until 0.9.1 — `buildingUiState` derived itself from a `GameState` —
        // and the frames are stated by hand now, so what the image reached incidentally is reached
        // here on purpose. Read part-way through, because a job at 0% has no bar to speak of.
        val laidDown = Instant.parse("2026-08-13T11:00:00Z")
        val due = Instant.parse("2026-08-13T14:05:00Z")
        val building = GameState.initial(SEED).copy(
            resources = Resources.of(metal = 1_000_000, crystal = 1_000_000),
            ships = Ships.of(ShipType.SKIFF, 2),
            yard = listOf(
                YardJob(ship = ShipType.SKIFF, startedAt = laidDown, completesAt = due),
                YardJob(ship = ShipType.SKIFF, startedAt = due, completesAt = due + 3.hours),
            ),
        )

        shipyard(
            uiState = building.toShipyardUiState(
                now = Instant.parse("2026-08-13T12:04:00Z"),
                timeZone = TimeZone.UTC,
            ),
        ) {
            // What is on the slipway is visible in the pool line and nowhere else.
            assertCardReads(ShipType.SKIFF, "2 building")
            // The wall-clock instant the head job lands on, in the idiom a facility row already
            // spends, and the queue behind it stated as a queue.
            assertCardReads(ShipType.SKIFF, "14:05")
            assertCardReads(ShipType.SKIFF, "1 queued")
            // ...and the verb stays live beside it, which is the one thing here that is not the
            // Colony row's treatment: a serial yard can always be given another hull.
            assertOffersToBuild(ShipType.SKIFF)
        }
    }

    @Test
    fun `a colony that cannot pay is shown a wait rather than a dead button`() {
        val broke = GameState.initial(SEED).copy(resources = Resources.of())

        shipyard(uiState = broke.toShipyardUiState(now = NOW, timeZone = TimeZone.UTC)) {
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
        //
        // **Through the Robot like every other behaviour test, including the composing.** It used to
        // call `setContent` here and reach for a `testTag` in the test body, which is the one thing
        // this repository's behaviour tests are not allowed to do; it now hands the callback to
        // `shipyard(…)` and taps through `buy`. The state is still recomputed by the callback and
        // still asserted below — what went is a second copy of the harness, and with it this
        // module's last reason to apply the Compose compiler.
        val rich = GameState.initial(SEED).copy(resources = Resources.of(metal = 10_000, crystal = 10_000))
        var state = rich

        shipyard(
            uiState = rich.toShipyardUiState(now = NOW, timeZone = TimeZone.UTC),
            onBuild = { type ->
                state = assertIs<BuildShipsResult.Started>(
                    buildShips(state, Ships.of(type, 1), at = EPOCH),
                ).state
            },
        ) {
            buy(ShipType.SKIFF)
        }

        assertEquals(Ships.of(ShipType.SKIFF, 1), state.ships)
        assertEquals(listOf(ShipType.SKIFF), state.yard.map { it.ship })
        assertEquals(rich.resources - FleetBalance.shipCost(ShipType.SKIFF), state.resources)
    }

    private companion object {
        val SEED = GalaxySeed(20_260_807L)
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)

        // Every state here is built at `EPOCH` and read at `EPOCH`, so a yard job is at 0% and its
        // countdown is its whole length. The tab's clock is `ShipyardUiStateTest`'s subject; what
        // this file is about is the seam between a real state and a real screen.
        val NOW: Instant = EPOCH
    }
}
