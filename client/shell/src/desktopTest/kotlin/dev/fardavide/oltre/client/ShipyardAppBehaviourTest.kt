package dev.fardavide.oltre.client

import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartRunResult
import dev.fardavide.oltre.core.startRun
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

// **The sixth verb, driven through the composition root.** `BuildShipsTest` proves what `core` does
// with a purchase and `ShipyardFromStateBehaviourTest` proves what the tab draws from a state; what
// neither can see is the wiring between them — the lambda in `App` that advances the colony to the
// instant of the tap, calls the verb, and commits the result.
//
// That seam is where a slice like this actually breaks. A verb wired to the wrong callback, a result
// whose `Started` branch was dropped, a commit that never fires: every one of those leaves both
// halves passing and the tap doing nothing. It is the same argument `AppBehaviourTest` opens with,
// applied to the one thing 0.8.0 added.
class ShipyardAppBehaviourTest {

    @Test
    fun `buying a hull on the Shipyard tab reaches core and comes back to the screen`() {
        app(saved = snapshot(rich())) {
            open(OltreTab.SHIPYARD)
            // The granted skiff, and the price of the second.
            assertReads("1 owned · 1 idle")

            buyAHull()

            // Through `act` into `buildShips` and back out through the mapper: the pool the screen
            // reads is the pool the verb wrote.
            assertReads("2 owned · 2 idle")
            assertReads("2 hulls")
        }
    }

    @Test
    fun `a hull bought on one tab is away on the other`() {
        // The two tabs are two readings of one pool and must not be able to disagree — which is
        // only checkable from here, because a feature module cannot see the other feature.
        app(saved = snapshot(rich())) {
            open(OltreTab.SHIPYARD)
            buyAHull()

            open(OltreTab.FLEETS)
            assertReads("0 of 2 away")
            assertReads("Nothing is out.")
        }
    }

    @Test
    fun `a run in flight is a card on the Fleets tab`() {
        // The Colony strip has pointed at this tab since 0.7.0 and there was nothing behind it. The
        // two feature modules cannot see each other, so that the strip and the card describe one run
        // is only checkable from the composition root.
        val state = rich()
        val target = state.galaxy.surveyed.filter { it != state.galaxy.home }.minByOrNull { it.slot }
            ?: error("the test seed's home system holds no world but home")
        val now = Clock.System.now()
        val dispatched = assertIs<StartRunResult.Started>(
            startRun(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 3.hours, at = now),
        ).state

        app(saved = GameSnapshot(lastUpdatedAt = now, state = dispatched)) {
            open(OltreTab.FLEETS)
            assertReads("1 of 1 away")
            assertReads("1 skiff")
            // The hull is out, so the Shipyard says so in the same breath from the other tab.
            open(OltreTab.SHIPYARD)
            assertReads("1 owned · 0 idle · 1 away")
        }
    }

    private fun rich(): GameState = GameState.initial(GalaxySeed(20_260_807L))
        .copy(resources = Resources.of(metal = 10_000, crystal = 10_000))

    private fun snapshot(state: GameState): GameSnapshot =
        GameSnapshot(lastUpdatedAt = Clock.System.now(), state = state)
}
