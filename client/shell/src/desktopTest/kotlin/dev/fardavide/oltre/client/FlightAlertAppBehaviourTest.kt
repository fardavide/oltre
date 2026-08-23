package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.galaxy.ui.LedgerMode
import dev.fardavide.oltre.core.AlertSettings
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import kotlin.test.Test

// **The whole of what 0.15.4 is, driven end to end**, and the only place in the repository that can
// see it. `FlightAlertTest` proves what `core` does with the flag and with a dispatch;
// `GameNotificationsTest` proves which alerts a state produces; `DispatchSheetBehaviourTest` proves
// the bell calls back. None of them can see the three links between — the lambda in `App` that
// toggles and commits, `startRun` reading the flag it toggled, and the `notifications.sync` inside
// the commit that is the only thing which actually books anything.
//
// **Counting alerts is what makes this an assertion about the feature rather than about a control.**
// A bell that lights and books nothing is exactly the failure an unconditional commit exists to
// prevent, and — because the ask is stamped rather than read live — a bell that books the alert for
// the *next* run instead of this one would light, book, and be wrong in a way no screen would show.
class FlightAlertAppBehaviourTest {

    @Test
    fun `a run sent with the bell dark comes home without a word`() {
        // The change this version is, from the outside: a fleet return fired unasked until now.
        app(saved = snapshot(withHulls())) {
            open(OltreTab.GALAXY)
            openTheWorldsList()
            openTheWorld(runnable)
            sendTheRun()

            assertAlertsBooked(0)
        }
    }

    @Test
    fun `a run sent with the bell lit books the one alert it is about`() {
        app(saved = snapshot(withHulls())) {
            open(OltreTab.GALAXY)
            openTheWorldsList()
            openTheWorld(runnable)
            // Nothing is booked by the tap itself — there is no flight yet to be told about. What
            // the commit behind it saves is the position of the control.
            tapTheBellOnTheSheet()
            assertAlertsBooked(0)

            sendTheRun()
            assertAlertsBooked(1)
        }
    }

    @Test
    fun `the bell keeps its position for the run after it`() {
        // The other half of Davide's call: the ask is per flight and the control is standing. Two
        // runs, one tap — and the second is announced because the bell was still lit when it left,
        // which is the property that makes a per-flight ask cheap enough to be worth having.
        app(saved = snapshot(withHulls())) {
            open(OltreTab.GALAXY)
            openTheWorldsList()
            openTheWorld(runnable)
            tapTheBellOnTheSheet()
            // **One hull, so there is a fleet left for the second run.** The sheet opens on the
            // manifest it derived, which here is the whole idle pool — the fixture obeying the game
            // rather than working around it.
            sendOneFewer()
            sendTheRun()

            // **A second world, not the same one twice**, for the same reason: the first run's hold
            // is clamped and debited at dispatch, so a world one skiff can empty has nothing left to
            // offer and comes back as the waiting mode with no verb on it.
            openTheWorld(alsoRunnable)
            sendTheRun()

            assertAlertsBooked(2)
        }
    }

    // Two hulls, so the second dispatch has something to fly, and the metal is beside the point: a
    // run costs nothing per flight — the hull was the price and it was paid at the Shipyard.
    private fun withHulls(): GameState = seeded
        .copy(resources = Resources.of(metal = 10_000, crystal = 10_000), ships = Ships.of(ShipType.SKIFF, 2))

    private fun snapshot(state: GameState): GameSnapshot = GameSnapshot(lastUpdatedAt = TEST_NOW, state = state)

    private companion object {

        // **`CARRIED_FORWARD` rather than genesis's own settings**, and it is the fixture obeying the
        // subject rather than working around a default. This file is about the bell beside Dispatch:
        // under `BY_CATEGORY` there is no bell — a flight is announced by its kind — so every tap
        // here would be a tap on a control the design deliberately removed.
        // `AlertSheetAppBehaviourTest` is where the other mode is driven end to end.
        val seeded: GameState = GameState.initial(GalaxySeed(20_260_807L))
            .copy(alerts = AlertSettings.CARRIED_FORWARD)

        // A world the ledger lists and a run may actually be sent to. Genesis surveys the whole home
        // system, so its worlds are on the ledger from the first launch — and read off the seed
        // rather than written down, because which of them is neither home nor held is the
        // generator's answer rather than this file's guess.
        private val runnableWorlds: List<GalaxyCoordinate> = seeded.galaxy.home.let { home ->
            seeded.galaxy.surveyed
                .filter { it.galaxy == home.galaxy && it.system == home.system }
                .sortedBy { it.slot }
                .filter { at ->
                    val world = worldAt(seeded.galaxy.seed, at)
                    world != null &&
                        verdictFor(world, seeded).let { it !is WorldVerdict.Home && it !is WorldVerdict.Occupied }
                }
        }

        val runnable: GalaxyCoordinate = runnableWorlds[0]
        val alsoRunnable: GalaxyCoordinate = runnableWorlds[1]
    }
}
