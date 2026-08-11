package dev.fardavide.oltre.client

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.StartResearchResult
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.startResearch
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

// What opening the app does, as opposed to what any one screen draws. Everything here starts from a
// save on disk and a wall clock that has moved on since it was written — which is the only state
// this game is ever really in.
class AppBehaviourTest {

    @Test
    fun `a first launch opens on the colony with a colony in it`() {
        app(saved = null) {
            assertReads("Metal Mine")
        }
    }

    @Test
    fun `a colony resumed from a save opens with the rail already reading its rates`() {
        // given a colony saved two days ago. What it accrued in between is `resume`'s business and
        // `GameSessionTest`'s; what this asserts is that a launch reaches the rail at all.
        app(saved = snapshot(state = colony(), agedBy = 2.days)) {
            assertTheMetalCellReads("+90/h")
        }
    }

    // The seam the first version of this pass was broken at, end to end. A research project that
    // landed while the app was closed is announced by a row on a screen the app does not open on —
    // so the announcement has to outlive the launch by however long the player takes to find the
    // tab. It used to be dropped by a two-second timer, which made this unreachable in practice.
    @Test
    fun `a project that landed while the app was closed is still announced when the tab is opened`() {
        // given a colony that started Photovoltaics and was then closed for a day
        val funded = colony(resources = Resources.of(metal = 100_000, crystal = 100_000, deuterium = 100_000))
            .let { it.copy(buildings = it.buildings.withLevel(BuildingType.ROBOTICS_FACTORY, BuildingLevel(1))) }
        val started = assertIs<StartResearchResult.Started>(
            startResearch(funded, Technology.PHOTOVOLTAICS, at = Clock.System.now() - 1.days),
        ).state

        app(saved = snapshot(state = started, agedBy = 1.days)) {
            // when the player takes their time getting to Research
            pauseTheClock()
            open(OltreTab.RESEARCH)

            // then the row is there, still holding the level it started from while the band
            // crosses: six of the six rows read LV 0, where without an announcement to make only
            // five of them would and Photovoltaics would already read LV 1.
            assertReads("Photovoltaics")
            assertRowsReading("LV 0", count = 6)

            // and it arrives at the level it reached while the app was closed, on its own schedule.
            // Reaching this at all is the assertion: the badge only counts up behind a band, and the
            // band only exists if the announcement survived the trip to this tab.
            letTheSweepFinish()
            assertReads("LV 1")
            assertRowsReading("LV 0", count = 5)
        }
    }

    @Test
    fun `every destination is reachable from the launch the app actually performs`() {
        app(saved = snapshot(state = colony(), agedBy = 3.hours)) {
            open(OltreTab.RESEARCH)
            assertReads("TECHNOLOGIES")
            open(OltreTab.GALAXY)
            assertReads("SYSTEMS")
            open(OltreTab.SHIPYARD)
            assertReads("Shipyard")
            open(OltreTab.COLONY)
            assertReads("Metal Mine")
        }
    }

    // **The seam nothing below the composition root can see.** Asking for an alert writes no event,
    // so `hasNewEventsSince` is false and the ordinary action path would decline to save — and the
    // alert is only booked by the `notifications.sync` inside the commit. A square that lights up and
    // tells nobody is the whole feature failing, and it would fail silently.
    @Test
    fun `tapping a square books the alert it promised`() {
        // A minute rather than hours, and an empty store: the first row has to be one the colony
        // cannot pay for, because an affordable row carries an Upgrade button and no square at all.
        app(saved = snapshot(state = colony(), agedBy = 1.minutes)) {
            // Nothing in flight and nothing watched, so a launch books nothing.
            assertAlertsBooked(0)

            tapTheWatchOn(BuildingType.METAL_MINE)

            assertAlertsBooked(1)
            assertReads("watching Metal Mine")
        }
    }

    // A save is stamped with the instant it was written, and the app advances from there to now. The
    // wall clock is the real one — there is no seam in `App` to inject a clock through, and putting
    // one there for a test would be inventing an API the game does not need.
    private fun snapshot(state: GameState, agedBy: kotlin.time.Duration): GameSnapshot =
        GameSnapshot(lastUpdatedAt = Clock.System.now() - agedBy, debugUsed = false, state = state)

    private fun colony(resources: Resources = Resources.of()): GameState =
        GameState.initial(GalaxySeed(20_260_807)).copy(resources = resources)
}
