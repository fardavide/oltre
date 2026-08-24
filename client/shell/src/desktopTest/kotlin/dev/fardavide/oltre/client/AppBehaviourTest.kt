package dev.fardavide.oltre.client

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSave
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.StartResearchResult
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.experienceOf
import dev.fardavide.oltre.core.startResearch
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
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

    // **Opening the app is the answer to a tray that has already spoken** (Davide, 2026-08-24, #120).
    // Every alert sitting with the OS is about something this launch is now showing — the mine that
    // finished, the hull that left the yard — so leaving them there makes the player dismiss by hand
    // what the app has just told them properly. It is also the only lever the delivery target gives
    // us on `One in total`, where the stack is what accumulates.
    @Test
    fun `opening the app clears alerts the player has already been shown`() {
        app(saved = null) {
            assertTrayCleared(times = 1)
        }
    }

    // The other half, and the one that would bite: a clear folded into `commit` would run on every
    // tap and — because the tick loop outlives the foreground on Android — would wipe an alarm the
    // system had just posted, seconds before it was read. So the count must not move when the player
    // acts.
    @Test
    fun `acting inside the app does not clear the tray again`() {
        // A minute old and unfunded, for `tapping a square books the alert it promised`'s reason: an
        // affordable row carries an Upgrade button and no square to tap at all.
        app(saved = snapshot(state = colony(), agedBy = 1.minutes)) {
            assertTrayCleared(times = 1)

            tapTheWatchOn(BuildingType.METAL_MINE)

            // The tap committed — `tapping a square books the alert it promised` measures that — and
            // the tray was left exactly as the launch left it.
            assertAlertsBooked(1)
            assertTrayCleared(times = 1)
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
            startResearch(funded, Technology.PHOTOVOLTAICS, at = TEST_NOW - 1.days),
        ).state

        app(saved = snapshot(state = started, agedBy = 1.days)) {
            // when the player takes their time getting to Research
            pauseTheClock()
            open(OltreTab.RESEARCH)

            // then the row is there, still holding the level it started from while the band
            // crosses: eight of the eight rows read LV 0, where without an announcement to make only
            // seven of them would and Photovoltaics would already read LV 1. Eight since 0.15, when
            // the applied branch grew Propulsion — the count is both branches' size and moves with
            // them, which is why it is stated rather than derived.
            assertReads("Photovoltaics")
            assertRowsReading("LV 0", count = 8)

            // and it arrives at the level it reached while the app was closed, on its own schedule.
            // Reaching this at all is the assertion: the badge only counts up behind a band, and the
            // band only exists if the announcement survived the trip to this tab.
            letTheSweepFinish()
            assertReads("LV 1")
            assertRowsReading("LV 0", count = 7)
        }
    }

    @Test
    fun `every destination is reachable from the launch the app actually performs`() {
        app(saved = snapshot(state = colony(), agedBy = 3.hours)) {
            open(OltreTab.RESEARCH)
            assertReads("TECHNOLOGIES")
            open(OltreTab.GALAXY)
            // **The tab opens on the map since 0.12**, which puts this assertion back where it was
            // before 0.11 moved it: the galaxy's own scale is the reading that proves the tab
            // arrived. It went to `NEAREST FIRST` when the ledger became the landing screen, and
            // that string does not exist any more — the sort left with the filters.
            //
            // And the scale itself moved at 0.20: a bare length was the honest line while the map
            // was free, and the day it had to be earned the interesting number became how much of it
            // you have. The word that proves the tab arrived is still on the head's count line.
            assertReads("OF 250 CHARTED")
            // **The switch, and it is the one control on this tab that writes to disk.** Davide's
            // amendment to Claude Design's landing call: the tab opens on the map the first time and
            // on whichever list you last used after that. The write goes through the composition
            // root — the file stores the *name* of the landing, because a `data` module may not see
            // the `presentation` one that owns the enum — so this is the only place it can be driven
            // end to end.
            openTheWorldsList()
            // Asserted as the map's *absence* rather than as the list's presence: the word "worlds"
            // is on the switch itself either way, so the reading that separates the two screens is
            // the galaxy's scale line, which only the map has.
            assertDoesNotRead("250 SYSTEMS")
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

    // **The level on screen, end to end and through the save file.** The fixture is written through
    // `GameStore` like every other save in this file, so what this asserts includes the total
    // surviving the encode and the decode.
    //
    // `LV 4` is stated rather than derived. Twenty facility levels and four full systems come to
    // 8,200 points against a level-4 threshold of 6,560 — and a test that recomputed that with the
    // functions under test would agree with them however they moved.
    @Test
    fun `a colony that has finished things opens on the level it earned`() {
        val played = colony().let { it.copy(experience = experienceOf(aHistory()), eventLog = aHistory()) }

        app(saved = snapshot(state = played, agedBy = 1.minutes)) {
            assertThePlayerStripReads("LV 4")
        }
    }

    // The other half of the same claim, and the one that would have caught a mapper wired to a
    // constant: a colony with nothing behind it still reads zero.
    @Test
    fun `a first launch opens the strip at level zero`() {
        app(saved = null) {
            assertThePlayerStripReads("LV 0")
        }
    }

    // Twenty facility levels and four surveys, which is a few days of an ordinary colony. The
    // instants are the log's own and nothing reads them — `advance` never looks at the log — so they
    // are spaced by an hour only so the fixture reads like a history rather than like a heap.
    private fun aHistory(): List<Event> = buildList {
        for (level in 2..21) {
            add(Event.BuildCompleted(BuildingType.METAL_MINE, BuildingLevel(level), at = TEST_NOW - level.hours))
        }
        for (system in 1..4) {
            add(
                Event.SurveyCompleted(
                    SystemAddress(galaxy = 1, system = system),
                    worldsFound = 4,
                    at = TEST_NOW - system.hours,
                ),
            )
        }
    }

    // A save is stamped with the instant it was written, and the app advances from there to now. The
    // wall clock is the real one — there is no seam in `App` to inject a clock through, and putting
    // one there for a test would be inventing an API the game does not need.
    @Test
    fun `a colony saved before the map cost anything opens on the map it earned`() {
        // **The one thing in a release that rewrites a save a player already has**, driven through
        // the launch rather than through `GameSave` alone. `GameSaveTest` proves the 17 → 18 hop
        // produces the right span; what only this can prove is that the app then *renders* it — a
        // migration that produced a state no screen could draw would pass every test in core.
        //
        // The blob is a current encoding with its version dropped and its `charted` key stripped,
        // which is `GameSaveTest`'s own idiom: a fixture that composed its own JSON would be a blob
        // the store answers null to, and the app would start a brand new colony while this passed.
        val played = colony().let { state ->
            val far = GalaxyCoordinate(galaxy = state.galaxy.home.galaxy, system = 200, slot = 5)
            state.copy(galaxy = state.galaxy.copy(surveyed = state.galaxy.surveyed + far))
        }
        val spans = played.galaxy.charted.joinToString(",") { span ->
            """{"galaxy":${span.galaxy},"lo":${span.lo},"hi":${span.hi}}"""
        }
        val chartedKey = ""","charted":[$spans]"""
        val legacy = GameSave.encode(snapshot(state = played, agedBy = 3.hours))
            .replace(""""schemaVersion":18""", """"schemaVersion":17""")
            .replace(chartedKey, "")
        assertTrue("charted" !in legacy, "the fixture has to be a save from before the key existed")

        app(saved = null, legacy = legacy) {
            open(OltreTab.GALAXY)
            // Home is 171 and the colony reached 200, so the light runs 141…230 — ninety systems,
            // and none of it handed back. A colony that woke up charted around home alone would
            // read 61 here, which is the failure this exists to catch.
            assertReads("90 OF 250 CHARTED")
        }
    }

    private fun snapshot(state: GameState, agedBy: kotlin.time.Duration): GameSnapshot =
        GameSnapshot(lastUpdatedAt = TEST_NOW - agedBy, debugUsed = false, state = state)

    private fun colony(resources: Resources = Resources.of()): GameState =
        GameState.initial(GalaxySeed(20_260_807)).copy(resources = resources)
}
