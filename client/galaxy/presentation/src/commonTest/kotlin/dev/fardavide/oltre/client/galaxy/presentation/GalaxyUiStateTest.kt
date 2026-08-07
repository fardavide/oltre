package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.core.AdaptationLevels
import dev.fardavide.oltre.core.EmpireId
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GalaxyState
import dev.fardavide.oltre.core.WorldOwnership
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// The mapper runs against a real generated galaxy rather than a fake one: the whole point of the
// screen is that it reads what the seed produced, and a fixture would let the two drift.
class GalaxyUiStateTest {

    @Test
    fun `the home system reads as home and names its star and its worlds`() {
        val uiState = galaxy().toGalaxyUiState(at = homeSelection())

        assertEquals("3:165", uiState.coordinate)
        assertEquals("DIM · 4 worlds", uiState.detail)
        // A Slide Over pane drops the noun rather than truncating it — a width decision, not a
        // change of voice: the star class and the count both survive.
        assertEquals("DIM · 4", uiState.compactDetail)
        assertTrue(uiState.isHome)
        assertEquals("250 systems", uiState.scope)
    }

    @Test
    fun `only the slots that hold something become rows`() {
        // Eleven of fifteen slots are empty, and the map is where that shows — the list carries
        // only what is there, or it would be eleven rows saying nothing.
        val uiState = galaxy().toGalaxyUiState(at = homeSelection())
        val slots = uiState.bands.flatMap { it.rows }.map { it.slot }

        assertEquals(listOf(7, 8, 10, 13), slots)
    }

    @Test
    fun `the map carries every slot including the empty ones`() {
        val uiState = galaxy().toGalaxyUiState(at = homeSelection())

        assertEquals(GalaxyBalance.SLOTS_PER_SYSTEM, uiState.map.slots.size)
        assertEquals((1..15).toList(), uiState.map.slots.map { it.slot })
        assertEquals(11, uiState.map.slots.count { it.mark == MapMark.EMPTY })
        assertEquals(1, uiState.map.slots.count { it.mark == MapMark.HOME })
    }

    @Test
    fun `rows are grouped into the band their orbit falls in`() {
        val uiState = galaxy().toGalaxyUiState(at = homeSelection())

        assertEquals(listOf(OrbitBand.TEMPERATE, OrbitBand.COLD), uiState.bands.map { it.band })
        assertEquals(listOf(7, 8, 10), uiState.bands.first().rows.map { it.slot })
        assertEquals("Temperate · slots 4–10", OrbitBand.TEMPERATE.heading)
    }

    @Test
    fun `a band with nothing in it is dropped rather than shown empty`() {
        val uiState = galaxy().toGalaxyUiState(at = homeSelection())

        assertTrue(uiState.bands.none { it.rows.isEmpty() })
        assertTrue(uiState.bands.none { it.band == OrbitBand.HOT })
    }

    @Test
    fun `the home world shows its three axes and its yield`() {
        // It is the reference: every other yield on the screen is read against 0.87, so the player
        // meets it on the first launch rather than inferring it.
        val uiState = galaxy().toGalaxyUiState(at = homeSelection())
        val home = uiState.bands.flatMap { it.rows }.first { it.slot == 7 }

        // The space before each unit is U+00A0, written as an escape here so the expectation is
        // legible in a diff — a value and its unit must never be split across a wrap.
        val verdict = assertIs<VerdictUiState.Home>(home.verdict)
        assertEquals("−21 °C · 0.96 g · 0.77 atm", verdict.axes)
        assertEquals("142 fields · yield 0.87 · no hazards", verdict.detail)
        assertEquals("[3:165:7]", home.coordinate)
    }

    @Test
    fun `a blocked world names each failing axis with the level that closes it`() {
        val uiState = galaxy().toGalaxyUiState(at = homeSelection())
        val blocked = assertIs<VerdictUiState.Blocked>(
            uiState.bands.flatMap { it.rows }.first { it.slot == 8 }.verdict,
        )

        assertEquals(listOf("temperature", "gravity"), blocked.failures.map { it.axis })
        assertEquals("1.78", blocked.failures[1].reading)
        assertEquals("1.45 g", blocked.failures[1].tolerated.breakable())
        assertEquals("Gravitic 3", blocked.failures[1].technology)
    }

    @Test
    fun `the unit is written once on the tolerance and not on the reading`() {
        // Both numbers are the same axis and therefore the same unit, and the four characters that
        // saves are what keep the technology on the line at 393dp.
        val uiState = galaxy().toGalaxyUiState(at = homeSelection())
        val blocked = assertIs<VerdictUiState.Blocked>(
            uiState.bands.flatMap { it.rows }.first { it.slot == 13 }.verdict,
        )

        blocked.failures.forEach { failure ->
            assertTrue(failure.reading.none { it.isLetter() }, "the reading carries a unit: ${failure.reading}")
            assertTrue(failure.tolerated.any { it.isLetter() }, "the tolerance carries none: ${failure.tolerated}")
        }
    }

    @Test
    fun `failing axes are listed in axis order rather than by the size of the gap`() {
        // A fixed order means the third line is in the same place on every three-axis world.
        val uiState = galaxy().toGalaxyUiState(at = homeSelection())
        val blocked = assertIs<VerdictUiState.Blocked>(
            uiState.bands.flatMap { it.rows }.first { it.slot == 13 }.verdict,
        )

        assertEquals(listOf("temperature", "gravity", "pressure"), blocked.failures.map { it.axis })
    }

    @Test
    fun `the technology drops the word Adaptation`() {
        val uiState = galaxy().toGalaxyUiState(at = homeSelection())
        val blocked = assertIs<VerdictUiState.Blocked>(
            uiState.bands.flatMap { it.rows }.first { it.slot == 13 }.verdict,
        )

        assertTrue(blocked.failures.none { it.technology.contains("Adaptation") })
        assertEquals(listOf("Thermal 12", "Gravitic 2", "Atmospheric 1"), blocked.failures.map { it.technology })
    }

    @Test
    fun `an unsurveyed world carries nothing but its coordinate and its orbit`() {
        // A neighbouring system, which is every system but one at ship time.
        val uiState = galaxy().toGalaxyUiState(at = SystemSelection(galaxy = 3, system = 164))
        val rows = uiState.bands.flatMap { it.rows }

        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.verdict == VerdictUiState.Unsurveyed })
        assertTrue(uiState.map.slots.none { it.mark == MapMark.BLOCKED || it.mark == MapMark.BARREN })
    }

    @Test
    fun `a world held by another empire reads occupied and carries the holder`() {
        // Nothing generates one in 0.2 — the three scripted empires are slice #9 — so this is the
        // one verdict the mapper has to be handed rather than shown.
        val taken = GalaxyCoordinate(galaxy = 3, system = 164, slot = 5)
        val state = galaxy().let { it.copy(ownership = it.ownership + WorldOwnership(taken, EmpireId("kepler"))) }

        val uiState = state.toGalaxyUiState(at = SystemSelection(galaxy = 3, system = 164))
        val row = uiState.bands.flatMap { it.rows }.first { it.slot == 5 }

        assertEquals(VerdictUiState.Occupied(holder = "Held by kepler"), row.verdict)
        assertEquals(MapMark.OCCUPIED, uiState.map.slots.first { it.slot == 5 }.mark)
    }

    @Test
    fun `the galaxy tabs are the four that exist with the current one selected`() {
        val uiState = galaxy().toGalaxyUiState(at = SystemSelection(galaxy = 2, system = 118))

        assertEquals(listOf("G1", "G2", "G3", "G4"), uiState.galaxies.map { it.label })
        assertEquals(listOf(false, true, false, false), uiState.galaxies.map { it.selected })
    }

    @Test
    fun `the edges of a galaxy are reported so the steppers can stop`() {
        val galaxy = galaxy()

        assertTrue(galaxy.toGalaxyUiState(at = SystemSelection(galaxy = 1, system = 1)).atFirstSystem)
        assertTrue(galaxy.toGalaxyUiState(at = SystemSelection(galaxy = 1, system = 250)).atLastSystem)
        val middle = galaxy.toGalaxyUiState(at = SystemSelection(galaxy = 1, system = 125))
        assertTrue(!middle.atFirstSystem && !middle.atLastSystem)
    }

    @Test
    fun `a relay is a row in the slot it occupies and holds no world`() {
        val galaxy = galaxy()
        val relaySystem = (1..GalaxyBalance.SYSTEMS_PER_GALAXY).first { system ->
            dev.fardavide.oltre.core.relayAt(galaxy.seed, galaxy = 1, system = system) != null
        }
        val relay = checkNotNull(dev.fardavide.oltre.core.relayAt(galaxy.seed, galaxy = 1, system = relaySystem))

        val uiState = galaxy.toGalaxyUiState(at = SystemSelection(galaxy = 1, system = relaySystem))
        val row = uiState.bands.flatMap { it.rows }.first { it.slot == relay.slot }

        assertIs<VerdictUiState.Relay>(row.verdict)
        assertEquals(MapMark.RELAY, uiState.map.slots.first { it.slot == relay.slot }.mark)
    }

    @Test
    fun `a surveyed world that passes every band but stays thin reads Barren`() {
        // Every seed is a different galaxy — the shell mints one per colony from the founding
        // instant — so this is not a state only some players reach. It is the answer the design
        // wants a survey to give *most* of the time, and the mapper's branch for it had no test
        // until one was written: the every-verdict frame is hand-written, so asserting against it
        // only ever proved that the renderer echoes a string the fixture already contained.
        val (state, at) = firstSurveyedWorldWhere { it is WorldVerdict.Barren }

        val row = state.toGalaxyUiState(at = SystemSelection(at.galaxy, at.system))
            .bands.flatMap { it.rows }.first { it.slot == at.slot }

        val verdict = assertIs<VerdictUiState.Barren>(row.verdict)
        assertTrue(verdict.yieldLabel.startsWith("yield 0."), verdict.yieldLabel)
        assertEquals("Passes every band, worth it at 0.90", verdict.threshold)
        assertTrue(verdict.detail.contains("fields"), verdict.detail)
    }

    @Test
    fun `a surveyed world over the threshold reads Settleable and names the richness behind it`() {
        val (state, at) = firstSurveyedWorldWhere { it is WorldVerdict.Settleable }

        val row = state.toGalaxyUiState(at = SystemSelection(at.galaxy, at.system))
            .bands.flatMap { it.rows }.first { it.slot == at.slot }

        val verdict = assertIs<VerdictUiState.Settleable>(row.verdict)
        assertTrue(verdict.yieldLabel.startsWith("yield "), verdict.yieldLabel)
        // The three resources in the order section 1 lists their axes, so a player reading two
        // settleable worlds compares like with like.
        assertTrue(verdict.richness.startsWith("metal "), verdict.richness)
        assertTrue(verdict.richness.contains("· crystal "), verdict.richness)
        assertTrue(verdict.richness.contains("· deut "), verdict.richness)
    }

    @Test
    fun `the worth-it threshold on a Barren row is the one core actually applies`() {
        // The threshold is quoted to the player, so it has to be the number the verdict was decided
        // by rather than a string that happens to look like it.
        val (state, at) = firstSurveyedWorldWhere { it is WorldVerdict.Barren }
        val verdict = assertIs<VerdictUiState.Barren>(
            state.toGalaxyUiState(at = SystemSelection(at.galaxy, at.system))
                .bands.flatMap { it.rows }.first { it.slot == at.slot }.verdict,
        )

        assertEquals(900_000, GalaxyBalance.WORTH_IT_THRESHOLD.perMillion)
        assertTrue(verdict.threshold.endsWith("0.90"), verdict.threshold)
    }

    // Surveying is a fleet action, so nothing in 0.2 produces a surveyed world outside the home
    // system — the coordinate is injected the same way the Occupied test injects ownership. Scans
    // galaxy 1 in coordinate order, so it picks the same world every run.
    private fun firstSurveyedWorldWhere(
        match: (WorldVerdict) -> Boolean,
    ): Pair<GalaxyState, GalaxyCoordinate> {
        val base = galaxy()
        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            for (slot in 1..GalaxyBalance.SLOTS_PER_SYSTEM) {
                val at = GalaxyCoordinate(galaxy = 1, system = system, slot = slot)
                val world = worldAt(base.seed, at) ?: continue
                val surveyed = base.copy(surveyed = base.surveyed + at)
                if (match(verdictFor(world, surveyed, AdaptationLevels.NONE))) return surveyed to at
            }
        }
        error("galaxy 1 held no world matching the wanted verdict")
    }

    @Test
    fun `a system with one world says world rather than worlds`() {
        val galaxy = galaxy()
        val single = (1..GalaxyBalance.SYSTEMS_PER_GALAXY).first { system ->
            (1..GalaxyBalance.SLOTS_PER_SYSTEM).count {
                worldAt(galaxy.seed, GalaxyCoordinate(galaxy = 1, system = system, slot = it)) != null
            } == 1
        }

        assertTrue(galaxy.toGalaxyUiState(at = SystemSelection(galaxy = 1, system = single)).detail.endsWith("1 world"))
    }

    // A value and its unit are joined by U+00A0 so a wrap never leaves "atm" alone on a line. That
    // is a rendering decision rather than a content one, so the expectations below read in ordinary
    // spaces and the non-breaking ones are normalised away here — otherwise every expected string
    // in this file would contain a character nobody can see in a diff.
    private fun String.breakable(): String = replace(' ', ' ')

    private fun galaxy(): GalaxyState = GalaxyState.initial(GalaxySeed(20_260_807))

    private fun homeSelection(): SystemSelection = galaxy().let {
        SystemSelection(galaxy = it.home.galaxy, system = it.home.system)
    }
}
