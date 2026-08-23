package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ExperienceTest {

    @Test
    fun `a colony that has done nothing is level zero on an empty gauge`() {
        // given a colony at genesis, whose log is empty by construction
        val state = GameState.initial()

        // when
        val progress = state.playerProgress()

        // then the strip reads exactly what 0.16 shipped as a constant
        assertEquals(PlayerLevel(0), progress.level)
        assertEquals(Experience(0), progress.earned)
        assertEquals(0, progress.percent)
    }

    // **The invariant the stored field rests on, and the only thing standing between it and a level
    // that is quietly wrong.** `experience` is a running total maintained by `GameState.logging`; the
    // fold is what it is a total *of*. Nothing checks they agree at runtime — a `require` in
    // `GameState.init` would fold the log on every construction including every decode, which is the
    // cost Davide rejected — so it is checked here, on a colony driven through every verb the game
    // has and every kind of completion `advance` can apply.
    @Test
    fun `the carried total is the log's own total at every step a verb can take`() {
        var state = GameState.initial().copy(
            resources = Resources.of(metal = 4_000_000, crystal = 2_000_000, deuterium = 1_000_000),
        )
        var now = EPOCH

        fun check(what: String) {
            assertEquals(experienceOf(state.eventLog), state.experience, "after $what")
        }

        // Every start verb in the game, then a span long enough for all of them to land.
        state = assertIs<StartUpgradeResult.Started>(startUpgrade(state, BuildingType.ROBOTICS_FACTORY, now)).state
        check("an upgrade started")
        state = assertIs<BuildShipsResult.Started>(buildShips(state, Ships.of(ShipType.SCOUT, 2), now)).state
        check("two hulls ordered")

        // Robotics 1 opens the research branch, and the ladders open at 4 — so the projects are
        // started after the first advance rather than before it.
        now += 2.days
        state = advance(state, from = EPOCH, to = now)
        check("two days of completions")

        state = assertIs<StartResearchResult.Started>(startResearch(state, Technology.EXTRACTION, now)).state
        check("a project started")
        val target = SystemAddress.of(state.galaxy.home).copy(system = state.galaxy.home.system + 1)
        state = assertIs<StartSurveyResult.Started>(startSurvey(state, target, now)).state
        check("a probe dispatched")

        now += 3.days
        state = advance(state, from = now - 3.days, to = now)
        check("the probe landing and the project finishing")

        val world = state.galaxy.surveyed.first { it != state.galaxy.home }
        state = assertIs<BuildShipsResult.Started>(buildShips(state, Ships.of(ShipType.SKIFF, 1), now)).state
        now += 1.days
        state = advance(state, from = now - 1.days, to = now)
        state = assertIs<StartRunResult.Started>(
            startRun(state, world, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 3.hours, now),
        ).state
        check("a run dispatched")

        state = advance(state, from = now, to = now + 1.days)
        check("the fleet coming home")

        // And the whole point of the exercise: the equality above is not the equality of two zeroes.
        assertTrue(state.experience > Experience.NONE, "the colony earned nothing to check")
    }

    @Test
    fun `a start pays nothing into the carried total either`() {
        // The awards table says a commitment is worth nothing; this says the *field* agrees, which is
        // the half a table cannot state on its own.
        val funded = GameState.initial().copy(resources = Resources.of(metal = 100_000, crystal = 100_000))
        val started = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.METAL_MINE, EPOCH),
        ).state

        assertEquals(Experience.NONE, started.experience)
        assertTrue(started.eventLog.isNotEmpty(), "the start was not logged at all")
    }

    @Test
    fun `a start awards nothing and only its completion pays`() {
        // given one of each kind of commitment — the tap rather than the payoff
        val starts = listOf(
            Event.BuildStarted(BuildingType.METAL_MINE, BuildingLevel(2), at = EPOCH),
            Event.ResearchStarted(Technology.EXTRACTION, TechLevel(1), at = EPOCH),
            Event.AdaptationStarted(AdaptationTechnology.THERMAL, TechLevel(1), at = EPOCH),
            Event.ShipsOrdered(Ships.of(ShipType.SKIFF, 4), at = EPOCH),
            Event.SurveyStarted(SystemAddress(galaxy = 1, system = 2), at = EPOCH),
            Event.FleetDispatched(
                target = GalaxyCoordinate(galaxy = 1, system = 2, slot = 3),
                gathering = ResourceKind.METAL,
                ships = Ships.of(ShipType.SKIFF, 1),
                at = EPOCH,
            ),
        )

        // then nothing a player has merely *begun* moves the gauge — the bar moves when the
        // thing lands, which is what makes it move while the app is closed
        assertEquals(Experience(0), experienceOf(starts))
    }

    @Test
    fun `every completion pays something`() {
        // then no completion in the log is worth nothing: a verb that awarded zero would be a verb
        // the level cannot see, and there is no such verb by design
        for (event in completions()) {
            assertTrue(
                ExperienceBalance.awardFor(event).points > 0,
                "$event awarded nothing",
            )
        }
    }

    @Test
    fun `a deeper level of the same facility is worth more`() {
        // given the same mine finished twice at different depths
        val shallow = Event.BuildCompleted(BuildingType.METAL_MINE, BuildingLevel(2), at = EPOCH)
        val deep = Event.BuildCompleted(BuildingType.METAL_MINE, BuildingLevel(20), at = EPOCH)

        // then a level that took two days is worth more than one that took two minutes
        assertTrue(ExperienceBalance.awardFor(deep) > ExperienceBalance.awardFor(shallow))
    }

    @Test
    fun `a hull is a fraction of the shallowest build there is`() {
        // Davide's call, 2026-08-22: per hull, and small. Hull purchases scale with income and
        // income compounds, so a hull priced anywhere near a facility level turns the level into a
        // fleet counter — the sim's month-thirty player owns over 1,700 of them.
        val hull = ExperienceBalance.awardFor(Event.ShipsBuilt(Ships.of(ShipType.SKIFF, 1), at = EPOCH))
        val firstBuild = ExperienceBalance.awardFor(
            Event.BuildCompleted(BuildingType.METAL_MINE, BuildingLevel(1), at = EPOCH),
        )

        assertTrue(hull.points * 4 < firstBuild.points, "a hull at ${hull.points} is not small against $firstBuild")
    }

    @Test
    fun `a manifest pays per hull rather than per delivery`() {
        // The yard serves one hull at a time and writes one event per hull, so this only ever holds
        // one in the wild. Pinned anyway: the day a delivery carries two, the answer is two awards.
        val one = ExperienceBalance.awardFor(Event.ShipsBuilt(Ships.of(ShipType.SKIFF, 1), at = EPOCH))
        val three = ExperienceBalance.awardFor(Event.ShipsBuilt(Ships.of(ShipType.SKIFF, 3), at = EPOCH))

        assertEquals(Experience(one.points * 3), three)
    }

    @Test
    fun `a probe that found more worlds is worth more than one that found none`() {
        val empty = Event.SurveyCompleted(SystemAddress(galaxy = 1, system = 2), worldsFound = 0, at = EPOCH)
        val rich = Event.SurveyCompleted(SystemAddress(galaxy = 1, system = 2), worldsFound = 6, at = EPOCH)

        assertTrue(ExperienceBalance.awardFor(rich) > ExperienceBalance.awardFor(empty))
        // A probe that finds nothing still flew — the base is what says the trip counted
        assertTrue(ExperienceBalance.awardFor(empty).points > 0)
    }

    @Test
    fun `the experience of a log is the sum of its parts`() {
        val events = completions()

        assertEquals(
            Experience(events.sumOf { ExperienceBalance.awardFor(it).points }),
            experienceOf(events),
        )
    }

    @Test
    fun `a fortnight of history is worth several levels`() {
        // What the 15 -> 16 hop hands an existing colony — Davide, 2026-08-22: *"make it so next time
        // I start the game it gives me experience for everything I did before."* The hop itself is
        // `GameSaveTest`'s; this is the arithmetic underneath it, and the assertion that matters is
        // that the answer is not a rounding error against the first level.
        val earned = experienceOf(aFortnightOfPlay())

        assertTrue(
            ExperienceBalance.levelFor(earned).value > 0,
            "a fortnight of play folded to $earned, which is not a level",
        )
    }

    @Test
    fun `the gauge is read off the carried total rather than off the log`() {
        // The performance call stated as a test — Davide, 2026-08-23: *"the more the player
        // progresses, the more it will be intensive to infer the level."* A state whose log and whose
        // total disagree is not one any verb can produce, so this is the only place it can be built:
        // `playerProgress` reads the field, and a fold on the read path would make this fail.
        val inconsistent = GameState.initial().copy(
            experience = Experience(50_000),
            eventLog = aFortnightOfPlay(),
        )

        assertEquals(Experience(50_000), inconsistent.playerProgress().earned)
    }

    @Test
    fun `experience never falls as the log grows`() {
        var log = emptyList<Event>()
        var last = Experience(0)
        for (event in aFortnightOfPlay()) {
            log = log + event
            val now = experienceOf(log)
            assertTrue(now >= last, "the log grew and the experience fell to $now")
            last = now
        }
    }

    @Test
    fun `the level a total buys is the number of spans it covers`() {
        // The closed form against the definition it is a shortcut for. `levelFor` solves a quadratic
        // with an integer root so it does not walk the ladder; this is the walk it must agree with.
        var level = 0
        var threshold = 0L
        while (level <= 60) {
            val span = ExperienceBalance.spanOf(PlayerLevel(level)).points
            assertEquals(Experience(threshold), ExperienceBalance.thresholdOf(PlayerLevel(level)))
            // The first point of a level and the last point before the next one both read as it
            assertEquals(PlayerLevel(level), ExperienceBalance.levelFor(Experience(threshold)))
            assertEquals(PlayerLevel(level), ExperienceBalance.levelFor(Experience(threshold + span - 1)))
            threshold += span
            level += 1
        }
    }

    @Test
    fun `a span is never zero so the gauge always has somewhere to go`() {
        for (level in 0..60) {
            assertTrue(
                ExperienceBalance.spanOf(PlayerLevel(level)).points > 0,
                "level $level costs nothing to leave",
            )
        }
    }

    @Test
    fun `the gauge starts empty at every level and never reads full`() {
        for (level in 0..40) {
            val start = ExperienceBalance.thresholdOf(PlayerLevel(level))
            val span = ExperienceBalance.spanOf(PlayerLevel(level))
            val justBefore = Experience(start.points + span.points - 1)

            assertEquals(0, ExperienceBalance.progressFor(start).percent, "level $level did not open empty")
            val last = ExperienceBalance.progressFor(justBefore)
            assertEquals(PlayerLevel(level), last.level)
            assertTrue(last.percent in 0..99, "level $level read ${last.percent}% one point short of the next")
        }
    }

    @Test
    fun `the gauge is the share of this level rather than of the whole game`() {
        val level = PlayerLevel(4)
        val start = ExperienceBalance.thresholdOf(level).points
        val span = ExperienceBalance.spanOf(level).points

        assertEquals(50, ExperienceBalance.progressFor(Experience(start + span / 2)).percent)
    }

    // The four marks Davide named on 2026-08-22 — *"a 1-day player must be around Lv 3, 1-week lv
    // 10, 2 weeks lv 15, 1 month lv 25"* — as the totals they cost. What a colony actually earns per
    // day is measured by `:sim:run`'s experience report, not asserted here; this pins the ladder the
    // measurement was fitted to, so a change to either constant shows up as a moved number rather
    // than as a slightly different feel nobody can point at.
    @Test
    fun `the published thresholds`() {
        assertEquals(Experience(1_100), ExperienceBalance.thresholdOf(PlayerLevel(1)))
        assertEquals(Experience(4_380), ExperienceBalance.thresholdOf(PlayerLevel(3)))
        assertEquals(Experience(27_200), ExperienceBalance.thresholdOf(PlayerLevel(10)))
        assertEquals(Experience(54_300), ExperienceBalance.thresholdOf(PlayerLevel(15)))
        assertEquals(Experience(135_500), ExperienceBalance.thresholdOf(PlayerLevel(25)))
    }

    @Test
    fun `neither a total nor a level can be negative`() {
        // Both are `Long`s and `Int`s underneath, and both are arrived at by arithmetic — a
        // subtraction in `progressFor`, a root in `levelFor`. A guard on the type is what turns an
        // arithmetic slip into a failure at the point it happened rather than a badge reading -1.
        assertFailsWith<IllegalArgumentException> { Experience(-1) }
        assertFailsWith<IllegalArgumentException> { PlayerLevel(-1) }
    }

    private fun completions(): List<Event> = listOf(
        Event.BuildCompleted(BuildingType.METAL_MINE, BuildingLevel(3), at = EPOCH),
        Event.ResearchCompleted(Technology.EXTRACTION, TechLevel(2), at = EPOCH),
        Event.AdaptationCompleted(AdaptationTechnology.THERMAL, TechLevel(1), at = EPOCH),
        Event.ShipsBuilt(Ships.of(ShipType.SKIFF, 1), at = EPOCH),
        Event.SurveyCompleted(SystemAddress(galaxy = 1, system = 2), worldsFound = 4, at = EPOCH),
        Event.FleetReturned(
            from = GalaxyCoordinate(galaxy = 1, system = 2, slot = 3),
            ships = Ships.of(ShipType.SKIFF, 1),
            cargo = Resources.of(metal = 1_200),
            at = EPOCH,
        ),
    )

    // A log with all five verbs in it and enough of each to clear several levels — the shape of a
    // save written by a build that had no experience system at all.
    private fun aFortnightOfPlay(): List<Event> = buildList {
        for (level in 1..12) {
            add(Event.BuildStarted(BuildingType.METAL_MINE, BuildingLevel(level), at = EPOCH + level.hours))
            add(Event.BuildCompleted(BuildingType.METAL_MINE, BuildingLevel(level), at = EPOCH + level.hours))
        }
        for (level in 1..5) {
            add(Event.ResearchCompleted(Technology.EXTRACTION, TechLevel(level), at = EPOCH + level.hours))
        }
        for (index in 1..8) {
            add(Event.ShipsBuilt(Ships.of(ShipType.SKIFF, 1), at = EPOCH + index.hours))
            add(
                Event.SurveyCompleted(
                    SystemAddress(galaxy = 1, system = index),
                    worldsFound = index % 5,
                    at = EPOCH + index.hours,
                ),
            )
            add(
                Event.FleetReturned(
                    from = null,
                    ships = Ships.of(ShipType.SKIFF, 1),
                    cargo = Resources.of(metal = 900),
                    at = EPOCH + index.hours,
                ),
            )
        }
    }

    private companion object {
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
