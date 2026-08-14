package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.galaxy.ui.GalaxyBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.LedgerSort
import dev.fardavide.oltre.client.galaxy.ui.ReachDotUiState
import dev.fardavide.oltre.client.galaxy.ui.ReachTick
import dev.fardavide.oltre.client.galaxy.ui.RegionStripUiState
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.SurveyBalance
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.systemNameAt
import dev.fardavide.oltre.core.worldAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// The band that replaced the ±1 stepper. The stepper was a lens one system wide; this is the same
// lens widened until it holds the galaxy, with the free half of the charted tier drawn on it and an
// axis measured in hours rather than in coordinates.
//
// A dispatch is bought with time, so time is what the axis has to be. What 0.11 adds is identity:
// the ticks gained a pin, and the cells gained the names the map now generates — so the picker went
// from a row of coordinates to a row of places.
class RegionStripUiStateTest {

    @Test
    fun `the strip is the whole galaxy at one tick a system`() {
        // given
        val strip = wealthy().stripAt(system = 165)

        // then — 250 ticks is the coordinate space, not a window on it. The strip picks a
        // neighbourhood; the lens picks the star.
        assertEquals(GalaxyBalance.SYSTEMS_PER_GALAXY, strip.ticks.size)
        assertEquals((1..GalaxyBalance.SYSTEMS_PER_GALAXY).toList(), strip.ticks.map { it.system })
    }

    @Test
    fun `your star and your probe are the only marks on a strip with nothing pinned`() {
        // given a probe out at a system 52 away from home
        val state = wealthy()
        val target = SystemAddress(galaxy = state.galaxy.home.galaxy, system = state.galaxy.home.system - 52)
        val dispatched = assertIs<StartSurveyResult.Started>(startSurvey(state, target, at = EPOCH)).state

        // when
        val strip = dispatched.stripAt(system = state.galaxy.home.system)

        // then — your star is the accent tick and a probe in flight is an amber one. Nothing else
        // is coloured until a pin is put down, so the things that are yours are findable in a field
        // of 250 without a legend.
        assertEquals(
            listOf(state.galaxy.home.system),
            strip.ticks.filter { it.mark == ReachTick.ORIGIN }.map { it.system },
        )
        assertEquals(
            listOf(target.system),
            strip.ticks.filter { it.mark == ReachTick.PROBE }.map { it.system },
        )
        assertTrue(strip.ticks.none { it.mark == ReachTick.PIN })
    }

    @Test
    fun `every other tick carries the one charted fact that is free`() {
        // Star class, in height and alpha. It is O(1) per system and generates no worlds at all,
        // which is what lets the strip draw all 250 at once.
        val strip = wealthy().stripAt(system = 165)
        val classes = setOf(ReachTick.DIM, ReachTick.STANDARD, ReachTick.BRIGHT)

        assertTrue(strip.ticks.filter { it.mark in classes }.size >= GalaxyBalance.SYSTEMS_PER_GALAXY - 1)
    }

    @Test
    fun `a system you pinned takes a mark of its own`() {
        // given a probe that landed and one of the worlds it found pinned. A pin is a bookmark into
        // what you know — `GalaxyState` refuses one on an unsurveyed world — so the fixture has to
        // fly the probe rather than assert the set.
        val state = wealthy()
        val found = firstWorldAwayFromHome(state)
        val landed = state.surveying(SystemAddress.of(found))
        val pinned = landed.copy(galaxy = landed.galaxy.copy(pinned = setOf(found)))

        // when
        val strip = pinned.stripAt(system = state.galaxy.home.system)

        // then — white where the class underneath it would have been dim, standard or bright. The
        // pin is the only mark on the strip that is neither a star class nor a job in progress, and
        // a player who put one down has to be able to find it without remembering the number.
        assertEquals(
            listOf(found.system),
            strip.ticks.filter { it.mark == ReachTick.PIN }.map { it.system },
        )
    }

    @Test
    fun `a probe outranks a pin because a job running now is what busy means`() {
        // given a system carrying both. The two cannot meet in normal play — `startSurvey` refuses a
        // system whose worlds are all known and a pin has to be on a world you know — so the state is
        // assembled at the seam `GalaxyState.surveyed` already anticipates: it is a set of *worlds*
        // because surveying becomes a per-world fleet action, and a per-world survey is exactly what
        // leaves a system half known with a probe still out.
        val state = wealthy()
        val found = firstWorldAwayFromHome(state)
        val target = SystemAddress.of(found)
        val dispatched = assertIs<StartSurveyResult.Started>(startSurvey(state, target, at = EPOCH)).state
        val both = dispatched.copy(
            galaxy = dispatched.galaxy.copy(
                surveyed = dispatched.galaxy.surveyed + found,
                pinned = setOf(found),
            ),
        )

        // when
        val strip = both.stripAt(system = state.galaxy.home.system)

        // then — amber, not white. A pin is a standing note to yourself and a probe is a job running
        // right now, so the tick that changes on its own wins the one that does not.
        assertEquals(ReachTick.PROBE, strip.ticks.first { it.system == target.system }.mark)
        assertTrue(strip.ticks.none { it.mark == ReachTick.PIN })
    }

    @Test
    fun `the ruler is marked in hours of flight and never in coordinates`() {
        // given a flight of 30 minutes plus a minute a system
        val state = wealthy()
        val home = state.galaxy.home.system

        // when
        val strip = state.stripAt(system = home)

        // then the 1h mark sits 30 systems out, because that is where a flight first costs an hour
        val oneHour = strip.marks.filter { it.label == "1h" }.map { it.system }.sorted()
        assertEquals(listOf(home - 30, home + 30).filter { it in 1..GalaxyBalance.SYSTEMS_PER_GALAXY }, oneHour)
        assertTrue(strip.marks.all { it.label.endsWith("h") }, "was ${strip.marks.map { it.label }}")
    }

    @Test
    fun `the ruler agrees with the price the footer charges`() {
        // The one thing that would make the strip a lie: an axis that disagreed with the duration
        // the dispatch actually books. Both come from `SurveyBalance`, and this is what says so.
        val state = wealthy()
        val home = SystemAddress.of(state.galaxy.home)
        val strip = state.stripAt(system = home.system)

        for (mark in strip.marks) {
            val flight = SurveyBalance.duration(from = home, to = SystemAddress(home.galaxy, mark.system))
            assertEquals(mark.label, "${flight.inWholeMinutes / 60}h", "mark at ${mark.system}")
        }
    }

    @Test
    fun `a galaxy hop renumbers the ruler rather than breaking it`() {
        // given a home in one galaxy and the strip showing another. A hop is priced at 250 systems,
        // so every flight over there costs 4h 40m plus the distance from your own index — which is
        // also the honest picture of why you would not bother.
        val state = wealthy()
        val home = state.galaxy.home
        // The *neighbouring* galaxy, deliberately: a hop is priced per galaxy crossed, so two
        // galaxies over the cheapest flight is 8h 50m and the near marks are gone for a second
        // reason. One hop is the case the ruler has to keep readable.
        val elsewhere = neighbouringGalaxy(state)

        // when
        val strip = state.stripAt(galaxy = elsewhere, system = 140)

        // then the near marks are gone: nothing over there is reachable in under five hours
        assertTrue(strip.marks.none { it.label == "1h" || it.label == "4h" }, "was ${strip.marks.map { it.label }}")
        assertTrue(strip.marks.any { it.label == "5h" }, "was ${strip.marks.map { it.label }}")
        // and the origin loses its accent, because the tick over there is not your star
        assertTrue(strip.ticks.none { it.mark == ReachTick.ORIGIN })
        assertEquals(
            listOf(home.system),
            strip.ticks.filter { it.mark == ReachTick.FOREIGN_ORIGIN }.map { it.system },
        )
    }

    @Test
    fun `the lens is five cells at a phone and three in a Slide Over`() {
        // **The cell size holds and the count gives**, which is the trade 0.11 made: 65dp a cell
        // fits a nine-character name where 46dp did not, so the picker bought the name with two
        // cells. A target is still never under 44dp.
        val strip = wealthy().stripAt(system = 165)

        assertEquals(5, strip.lens.cells.size)
        assertEquals(3, strip.compactLens.cells.size)
    }

    @Test
    fun `the lens centres on the system you are looking at`() {
        // given
        val strip = wealthy().stripAt(system = 165)

        // then — and the cell beside the lit one is what the ±1 stepper used to be, still one tap
        assertEquals(listOf(163, 164, 165, 166, 167), strip.lens.cells.map { it.system })
        assertEquals(listOf(165), strip.lens.cells.filter { it.selected }.map { it.system })
        assertEquals(listOf(164, 165, 166), strip.compactLens.cells.map { it.system })
    }

    @Test
    fun `the lens stays on the map at either edge of the galaxy`() {
        // given the two places a centred window would fall off
        val first = wealthy().stripAt(system = 1)
        val last = wealthy().stripAt(system = GalaxyBalance.SYSTEMS_PER_GALAXY)

        // then it slides rather than clipping, so the count of taps never changes with position
        assertEquals(listOf(1, 2, 3, 4, 5), first.lens.cells.map { it.system })
        assertEquals(listOf(1), first.lens.cells.filter { it.selected }.map { it.system })
        assertEquals((246..250).toList(), last.lens.cells.map { it.system })
        assertEquals(listOf(250), last.lens.cells.filter { it.selected }.map { it.system })
    }

    @Test
    fun `every cell names its system rather than only numbering it`() {
        // **The cell stopped being a coordinate and started being a place.** A number tells you where
        // a system is filed and a name is what you remember it by — and the whole slice is that
        // trade, so the lens is where it has to show.
        val state = wealthy()
        val body = state.systemBodyAt(system = 165)
        val seed = state.galaxy.seed
        val galaxy = state.galaxy.home.galaxy

        for (cell in body.strip.lens.cells + body.strip.compactLens.cells) {
            // Against the generator rather than against a literal: the seed decides what a system is
            // called, and a fixture that spelled one out would be asserting the seed.
            assertEquals(systemNameAt(seed, galaxy, cell.system), cell.name)
            assertTrue(cell.name.isNotBlank(), "system ${cell.system} was drawn nameless")
        }
        // Five distinct names, and this lens is inside one region — where uniqueness is structural
        // rather than likely. A mapper that named every cell after the *selected* system would pass
        // every assertion above and fail this one.
        assertEquals(5, body.strip.lens.cells.map { it.name }.toSet().size)
        // And the lit cell agrees with the header above it, which is the one place the two could
        // disagree about what the player is standing on.
        assertEquals(body.header.system, body.strip.lens.cells.first { it.selected }.name)
    }

    @Test
    fun `a cell says how many worlds a system holds and home says it is home`() {
        // given
        val state = wealthy()
        val strip = state.stripAt(system = state.galaxy.home.system)

        // then — the dot is sized by the count, which is 15 generations a system: the lens counts
        // five and the strip counts none, against the 3,750 a whole-galaxy count would need
        val home = strip.lens.cells.first { it.system == state.galaxy.home.system }
        assertEquals(ReachDotUiState.Home, home.dot)

        // Against the generator rather than against a range. `1..SLOTS_PER_SYSTEM` cannot fail —
        // `dotFor` only builds `Worlds` when the count is non-zero and `worldsIn` only counts
        // fifteen slots — so a mapper that returned `Empty` for every system would have passed it.
        for (cell in strip.lens.cells.filter { it.system != state.galaxy.home.system }) {
            val at = SystemAddress(galaxy = state.galaxy.home.galaxy, system = cell.system)
            val real = worldsIn(state.galaxy.seed, at)
            when (val dot = cell.dot) {
                ReachDotUiState.Home -> error("only home is home")
                ReachDotUiState.Empty -> assertEquals(0, real, "system ${cell.system} was drawn empty")
                is ReachDotUiState.Worlds -> assertEquals(real, dot.count, "system ${cell.system}")
            }
        }
        assertTrue(
            strip.lens.cells.any { it.dot is ReachDotUiState.Worlds },
            "the fixture needs at least one system with worlds, or the assertion above is vacuous",
        )
    }

    @Test
    fun `a probe outranks the index a foreign galaxy is measured from`() {
        // given a probe aimed at the *same system number* as home, one galaxy over. Switching
        // galaxy keeps the number, so this is the first thing a player sees over there rather than
        // a one-in-250 coincidence — and the tick that says "your probe is here" must not be eaten
        // by the tick that says "flights are measured from this index".
        val state = wealthy()
        val home = state.galaxy.home
        val elsewhere = neighbouringGalaxy(state)
        val target = SystemAddress(galaxy = elsewhere, system = home.system)
        val dispatched = assertIs<StartSurveyResult.Started>(startSurvey(state, target, at = EPOCH)).state

        // when
        val strip = dispatched.stripAt(galaxy = elsewhere, system = home.system)

        // then
        assertEquals(ReachTick.PROBE, strip.ticks.first { it.system == home.system }.mark)
        assertTrue(strip.ticks.none { it.mark == ReachTick.FOREIGN_ORIGIN })
    }

    @Test
    fun `your own star still outranks everything because it can never hold a probe`() {
        // The other half of that order. `startSurvey` refuses a surveyed system, and home is
        // surveyed at genesis, so nothing is given up by putting ORIGIN first.
        val state = wealthy()
        val strip = state.stripAt(system = state.galaxy.home.system)

        assertEquals(ReachTick.ORIGIN, strip.ticks.first { it.system == state.galaxy.home.system }.mark)
    }

    @Test
    fun `no cell prints a time because five neighbours differ by a minute each`() {
        // Printing 1h 21m through 1h 25m across the lens is five near-identical figures where the
        // ruler has already answered the question and the footer answers it exactly. The name is
        // what the cell spends its width on instead.
        val strip = wealthy().stripAt(system = 165)

        assertTrue(strip.lens.cells.all { it.label == "${it.system}" }, "was ${strip.lens.cells.map { it.label }}")
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────

    private fun GameState.stripAt(system: Int, galaxy: Int = this.galaxy.home.galaxy): RegionStripUiState =
        systemBodyAt(system = system, galaxy = galaxy).strip

    private fun GameState.systemBodyAt(
        system: Int,
        galaxy: Int = this.galaxy.home.galaxy,
    ): GalaxyBodyUiState.System = assertIs<GalaxyBodyUiState.System>(
        toGalaxyUiState(
            nav = navAt(SystemSelection(galaxy = galaxy, system = system)),
            now = EPOCH,
            timeZone = TimeZone.UTC,
        ).body,
    )

    // Everything the tab remembers, at the state a check-in reaches the map in: nothing typed,
    // nothing filtered, the default sort. Only `at` ever varies here, because the strip reads the
    // selection and nothing else — the query and the chips belong to the ledger, and the head drops
    // them outside it.
    private fun navAt(at: SystemSelection): GalaxyNavigation = GalaxyNavigation(
        view = GalaxyView.SYSTEM,
        at = at,
        query = "",
        filters = emptySet(),
        sort = LedgerSort.NEAREST,
        seenAt = EPOCH,
        availableFilters = emptyList(),
    )

    // Two days is longer than any flight inside a galaxy, so the probe has landed and its job is
    // gone from `surveys` — which is what makes this the "surveyed" fixture rather than the
    // "in flight" one.
    private fun GameState.surveying(target: SystemAddress): GameState = advance(
        assertIs<StartSurveyResult.Started>(startSurvey(this, target, at = EPOCH)).state,
        from = EPOCH,
        to = EPOCH + 2.days,
    )

    // The nearest world out from home, found by asking the generator rather than by hardcoding a
    // coordinate the seed happens to hold. It has to be a real world: a probe aimed at an empty
    // system surveys nothing, and a pin needs something to sit on.
    private fun firstWorldAwayFromHome(state: GameState): GalaxyCoordinate {
        val home = state.galaxy.home
        for (away in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            val up = home.system + away
            val system = if (up <= GalaxyBalance.SYSTEMS_PER_GALAXY) up else home.system - away
            if (system < 1) continue
            val slot = (1..GalaxyBalance.SLOTS_PER_SYSTEM).firstOrNull { slot ->
                worldAt(
                    state.galaxy.seed,
                    GalaxyCoordinate(galaxy = home.galaxy, system = system, slot = slot),
                ) != null
            } ?: continue
            return GalaxyCoordinate(galaxy = home.galaxy, system = system, slot = slot)
        }
        error("seed ${state.galaxy.seed} generated no world outside the home system")
    }

    // The galaxy next door, whichever side of the map home landed on.
    private fun neighbouringGalaxy(state: GameState): Int = state.galaxy.home.galaxy.let {
        if (it < GalaxyBalance.GALAXIES) it + 1 else it - 1
    }

    private fun wealthy(): GameState =
        GameState.initial(GalaxySeed(20_260_807)).copy(resources = Resources.of(metal = 1_000_000))

    private companion object {
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
