package dev.fardavide.oltre.client

import dev.fardavide.oltre.core.BuildShipsResult
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.buildShips
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
            // The hull this colony already bought, and the price of the next.
            assertReads("1 owned · 1 idle")

            buyAHull()

            // Through `act` into `buildShips` and back out through the mapper: the queue the screen
            // reads is the queue the verb wrote. **The fleet has not grown**, which is the whole of
            // what 0.9.0 changed about this tap — the hull is paid for and on the slipway.
            assertReads("1 owned · 1 idle · 1 building")
            assertReads("1 hull")
        }
    }

    @Test
    fun `a hull bought on one tab is not yet a hull on the other`() {
        // The two tabs are two readings of one pool and must not be able to disagree — which is only
        // checkable from here, because a feature module cannot see the other feature. A hull on the
        // slipway is the case that could make them: the Shipyard has just charged for it and the
        // Fleets tab must not count it, because it cannot be sent.
        app(saved = snapshot(rich())) {
            open(OltreTab.SHIPYARD)
            buyAHull()

            open(OltreTab.FLEETS)
            assertReads("0 of 1 away")
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
        val now = TEST_NOW
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

    @Test
    fun `a hull ordered before you closed the app is in the fleet when you open it`() {
        // **The whole reason the yard has a clock, driven end to end.** A launch advances from the
        // saved instant to now, which is the only path in the app that runs `advance` over a yard
        // job — the tap above cannot, because a purchase and its delivery are hours apart now.
        val ordered = assertIs<BuildShipsResult.Started>(
            buildShips(rich(), Ships.of(ShipType.SKIFF, 1), at = TEST_NOW - 12.hours),
        ).state
        // Twelve hours covers the second skiff's 2h 32m at Robotics 0 several times over, so this
        // does not become a test about the duration curve.
        val closed = GameSnapshot(lastUpdatedAt = TEST_NOW - 12.hours, state = ordered)

        app(saved = closed) {
            open(OltreTab.SHIPYARD)
            assertReads("2 hulls")
            assertReads("2 owned · 2 idle")

            // And the Fleets tab agrees, which is the pair that could disagree: the hull did not
            // exist when the save was written and does now.
            open(OltreTab.FLEETS)
            assertReads("0 of 2 away")
        }
    }

    @Test
    fun `a queue you left running is still running when you come back`() {
        // The serial half. Three hulls ordered, long enough away for the first to have landed and
        // not the rest — so the launch has to apply one completion, leave two, and re-book the
        // alerts for them.
        val ordered = assertIs<BuildShipsResult.Started>(
            buildShips(veryRich(), Ships.of(ShipType.SKIFF, 3), at = TEST_NOW - 3.hours),
        ).state
        val closed = GameSnapshot(lastUpdatedAt = TEST_NOW - 3.hours, state = ordered)

        app(saved = closed) {
            open(OltreTab.SHIPYARD)
            assertReads("2 hulls")
            assertReads("2 owned · 2 idle · 2 building")
            assertReads("1 queued")
        }
    }

    // **A colony that can pay *and* already owns one hull**, and the hull is stated here rather than
    // inherited from genesis, which stopped granting one at 0.11.3. Every test in this class is about
    // the loop between the two tabs — buy, queue, land, dispatch — and each of them needs a fleet
    // that is already non-zero to be able to see it change: "the fleet has not grown" and "the hull
    // is out, so the Shipyard says so" are both readings against a pool, not against nothing. The
    // *opening* state, with no hull at all, is `ShipyardFromStateBehaviourTest`'s subject.
    // **The day-one loop, end to end, and the one this slice exists for.** A colony owns no hulls;
    // a probe flies a `SCOUT`; so the first thing a new player must be able to do is buy one and
    // survey with it. Every half of that is tested somewhere — `BuildShipsTest` for the purchase,
    // `StartSurveyTest` for the consumption, `ProbeActionUiState` for the footer — and none of them
    // can see the wiring, which is exactly where 0.15 broke: `FleetBalance` sold the scout and the
    // Shipyard drew no card for it, so the loop was unreachable with every other test green.
    @Test
    fun `a colony buys its first scout and the Galaxy tab stops asking for one`() {
        app(saved = snapshot(GameState.initial(GalaxySeed(20_260_807L)))) {
            // **The tab a new colony lands on offers no probe**, because it has nothing to fly one
            // with. The map's caption withholds the verb and keeps the flight, so what is on screen
            // is a price rather than a dead button — `GalaxyMapUiStateTest` pins which.
            open(OltreTab.GALAXY)
            assertDoesNotRead("Dispatch")

            // The Shipyard is where that is answered, and the scout is the card it opens on.
            open(OltreTab.SHIPYARD)
            assertReads("Scout")
            buyAHull(ShipType.SCOUT)

            // Paid for and on the slipway — the fleet has not grown yet, which is what the yard is,
            // and it is the same rule the skiff has followed since 0.9.0.
            assertReads("1 building")
        }
    }

    // **The whole of what 0.15.3 is, driven end to end**, and the only test in the repository that
    // can see it. `HullAlertTest` proves what `core` does with a tap and `GameNotificationsTest`
    // proves which alerts a state produces; neither can see the wiring between them — the lambda in
    // `App` that cycles the ask, advances, and commits, and the `notifications.sync` inside that
    // commit which is the only thing that actually books anything.
    //
    // Counting alerts is what makes it an assertion about the feature rather than about the control:
    // a square that lights and books nothing is precisely the failure the unconditional commit
    // exists to prevent, and it is invisible from inside the Shipyard module.
    @Test
    fun `the square on a hull card cycles through both ways of being told`() {
        // Three hulls ordered, far enough back that none has landed and far enough ahead that all
        // three are still in the air at `TEST_NOW`.
        val ordered = assertIs<BuildShipsResult.Started>(
            buildShips(veryRich(), Ships.of(ShipType.SKIFF, 3), at = TEST_NOW - 1.hours),
        ).state

        app(saved = GameSnapshot(lastUpdatedAt = TEST_NOW - 1.hours, state = ordered)) {
            open(OltreTab.SHIPYARD)
            // Nothing asked for, so nothing booked — the change this version is.
            assertAlertsBooked(0)

            // One tap: the order as a whole, which is one alert at the last hull's instant.
            tapTheAlertOn(ShipType.SKIFF)
            assertAlertsBooked(1)

            // Two: every hull, which is three.
            tapTheAlertOn(ShipType.SKIFF)
            assertAlertsBooked(3)

            // Three: silence, and the undo is the same control it always is.
            tapTheAlertOn(ShipType.SKIFF)
            assertAlertsBooked(0)
        }
    }

    @Test
    fun `a card with an empty slipway offers no square to press`() {
        // The absence of a control rather than a disabled one, asserted from the composition root
        // because that is where the state and the screen actually meet: this colony can afford a
        // hull and has none on order.
        app(saved = snapshot(rich())) {
            open(OltreTab.SHIPYARD)
            assertNoAlertOn(ShipType.SKIFF)

            // And it appears the moment there is something to wait for.
            buyAHull()
            assertOffersAlertOn(ShipType.SKIFF)
        }
    }

    private fun rich(): GameState = GameState.initial(GalaxySeed(20_260_807L))
        .copy(resources = Resources.of(metal = 10_000, crystal = 10_000), ships = Ships.of(ShipType.SKIFF, 1))

    private fun veryRich(): GameState = GameState.initial(GalaxySeed(20_260_807L))
        .copy(resources = Resources.of(metal = 100_000, crystal = 100_000), ships = Ships.of(ShipType.SKIFF, 1))

    private fun snapshot(state: GameState): GameSnapshot =
        GameSnapshot(lastUpdatedAt = TEST_NOW, state = state)
}
