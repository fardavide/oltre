package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.SurveyBalance
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.startSurvey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// The band that replaced the ±1 stepper. The stepper was a lens one system wide; this is the same
// lens widened until it holds the galaxy, with the free half of the charted tier drawn on it and an
// axis measured in hours rather than in coordinates.
//
// A dispatch is bought with time, so time is what the axis has to be.
class ReachBandUiStateTest {

    @Test
    fun `the strip is the whole galaxy at one tick a system`() {
        // given
        val band = wealthy().reachBandAt(system = 165)

        // then — 250 ticks is the coordinate space, not a window on it. The strip picks a
        // neighbourhood; the lens picks the star.
        assertEquals(GalaxyBalance.SYSTEMS_PER_GALAXY, band.ticks.size)
        assertEquals((1..GalaxyBalance.SYSTEMS_PER_GALAXY).toList(), band.ticks.map { it.system })
    }

    @Test
    fun `the two things that are yours are the only coloured marks on it`() {
        // given a probe out at a system 52 away from home
        val state = wealthy()
        val target = SystemAddress(galaxy = state.galaxy.home.galaxy, system = state.galaxy.home.system - 52)
        val dispatched = assertIs<StartSurveyResult.Started>(startSurvey(state, target, at = EPOCH)).state

        // when
        val band = dispatched.reachBandAt(system = state.galaxy.home.system)

        // then — your star is the accent tick and a probe in flight is an amber one. Nothing else
        // is coloured, so the two things that are yours are findable in a field of 250 without a
        // legend.
        assertEquals(
            listOf(state.galaxy.home.system),
            band.ticks.filter { it.mark == ReachTick.ORIGIN }.map { it.system },
        )
        assertEquals(
            listOf(target.system),
            band.ticks.filter { it.mark == ReachTick.PROBE }.map { it.system },
        )
    }

    @Test
    fun `every other tick carries the one charted fact that is free`() {
        // Star class, in height and alpha. It is O(1) per system and generates no worlds at all,
        // which is what lets the strip draw all 250 at once.
        val band = wealthy().reachBandAt(system = 165)
        val classes = setOf(ReachTick.DIM, ReachTick.STANDARD, ReachTick.BRIGHT)

        assertTrue(band.ticks.filter { it.mark in classes }.size >= GalaxyBalance.SYSTEMS_PER_GALAXY - 1)
    }

    @Test
    fun `the ruler is marked in hours of flight and never in coordinates`() {
        // given a flight of 30 minutes plus a minute a system
        val state = wealthy()
        val home = state.galaxy.home.system

        // when
        val band = state.reachBandAt(system = home)

        // then the 1h mark sits 30 systems out, because that is where a flight first costs an hour
        val oneHour = band.marks.filter { it.label == "1h" }.map { it.system }.sorted()
        assertEquals(listOf(home - 30, home + 30).filter { it in 1..GalaxyBalance.SYSTEMS_PER_GALAXY }, oneHour)
        assertTrue(band.marks.all { it.label.endsWith("h") }, "was ${band.marks.map { it.label }}")
    }

    @Test
    fun `the ruler agrees with the price the footer charges`() {
        // The one thing that would make the band a lie: an axis that disagreed with the duration
        // the dispatch actually books. Both come from `SurveyBalance`, and this is what says so.
        val state = wealthy()
        val home = SystemAddress.of(state.galaxy.home)
        val band = state.reachBandAt(system = home.system)

        for (mark in band.marks) {
            val flight = SurveyBalance.duration(from = home, to = SystemAddress(home.galaxy, mark.system))
            assertEquals(mark.label, "${flight.inWholeMinutes / 60}h", "mark at ${mark.system}")
        }
    }

    @Test
    fun `a galaxy hop renumbers the ruler rather than breaking it`() {
        // given a home in one galaxy and the band showing another. A hop is priced at 250 systems,
        // so every flight over there costs 4h 40m plus the distance from your own index — which is
        // also the honest picture of why you would not bother.
        val state = wealthy()
        val home = state.galaxy.home
        // The *neighbouring* galaxy, deliberately: a hop is priced per galaxy crossed, so from
        // galaxy 3 to galaxy 1 the cheapest flight is 8h 50m and the near marks are gone for a
        // second reason. One hop is the case the ruler has to keep readable.
        val elsewhere = if (home.galaxy < GalaxyBalance.GALAXIES) home.galaxy + 1 else home.galaxy - 1

        // when
        val band = state.reachBandAt(galaxy = elsewhere, system = 140)

        // then the near marks are gone: nothing over there is reachable in under five hours
        assertTrue(band.marks.none { it.label == "1h" || it.label == "4h" }, "was ${band.marks.map { it.label }}")
        assertTrue(band.marks.any { it.label == "5h" }, "was ${band.marks.map { it.label }}")
        // and the origin loses its accent, because the tick over there is not your star
        assertTrue(band.ticks.none { it.mark == ReachTick.ORIGIN })
        assertEquals(
            listOf(home.system),
            band.ticks.filter { it.mark == ReachTick.FOREIGN_ORIGIN }.map { it.system },
        )
    }

    @Test
    fun `the lens is seven cells at a phone and five in a Slide Over`() {
        // The cell size holds and the count drops, so a target is never under 44dp.
        val band = wealthy().reachBandAt(system = 165)

        assertEquals(7, band.lens.cells.size)
        assertEquals(5, band.compactLens.cells.size)
    }

    @Test
    fun `the lens centres on the system you are looking at`() {
        // given
        val band = wealthy().reachBandAt(system = 165)

        // then — and the cell beside the lit one is what the ±1 stepper used to be, still one tap
        assertEquals(listOf(162, 163, 164, 165, 166, 167, 168), band.lens.cells.map { it.system })
        assertEquals(listOf(165), band.lens.cells.filter { it.selected }.map { it.system })
        assertEquals(listOf(163, 164, 165, 166, 167), band.compactLens.cells.map { it.system })
    }

    @Test
    fun `the lens stays on the map at either edge of the galaxy`() {
        // given the two places a centred window would fall off
        val first = wealthy().reachBandAt(system = 1)
        val last = wealthy().reachBandAt(system = GalaxyBalance.SYSTEMS_PER_GALAXY)

        // then it slides rather than clipping, so the count of taps never changes with position
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), first.lens.cells.map { it.system })
        assertEquals(listOf(1), first.lens.cells.filter { it.selected }.map { it.system })
        assertEquals((244..250).toList(), last.lens.cells.map { it.system })
        assertEquals(listOf(250), last.lens.cells.filter { it.selected }.map { it.system })
    }

    @Test
    fun `a cell says how many worlds a system holds and home says it is home`() {
        // given
        val state = wealthy()
        val band = state.reachBandAt(system = state.galaxy.home.system)

        // then — the dot is sized by the count, which is 15 generations a system: the lens counts
        // seven and the strip counts none, against the 3,750 a whole-galaxy count would need
        val home = band.lens.cells.first { it.system == state.galaxy.home.system }
        assertEquals(ReachDotUiState.Home, home.dot)
        for (cell in band.lens.cells.filter { it.system != state.galaxy.home.system }) {
            when (val dot = cell.dot) {
                ReachDotUiState.Home -> error("only home is home")
                ReachDotUiState.Empty -> Unit
                is ReachDotUiState.Worlds -> assertTrue(dot.count in 1..GalaxyBalance.SLOTS_PER_SYSTEM)
            }
        }
    }

    @Test
    fun `no cell prints a time because seven neighbours differ by a minute each`() {
        // Printing 1h 21m through 1h 27m across the lens is seven near-identical figures where the
        // ruler has already answered the question and the footer answers it exactly.
        val band = wealthy().reachBandAt(system = 165)

        assertTrue(band.lens.cells.all { it.label == "${it.system}" }, "was ${band.lens.cells.map { it.label }}")
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────

    private fun GameState.reachBandAt(system: Int, galaxy: Int = this.galaxy.home.galaxy): ReachBandUiState =
        toGalaxyUiState(
            at = SystemSelection(galaxy = galaxy, system = system),
            now = EPOCH,
            timeZone = TimeZone.UTC,
        ).reach

    private fun wealthy(): GameState =
        GameState.initial(GalaxySeed(20_260_807)).copy(resources = Resources.of(metal = 1_000_000))

    private companion object {
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
