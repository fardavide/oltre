package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.EmpireId
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GalaxyState
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.WorldOwnership
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.startSurvey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// The mapper runs against a real generated galaxy rather than a fake one: the whole point of the
// screen is that it reads what the seed produced, and a fixture would let the two drift.
class GalaxyUiStateTest {

    @Test
    fun `the home system reads as home and names its star and its worlds`() {
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)

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
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val slots = uiState.bands.flatMap { it.rows }.map { it.slot }

        assertEquals(listOf(7, 8, 10, 13), slots)
    }

    // The orbit view draws one ellipse per occupied slot and nothing at all for the empty ones —
    // the trade Davide took at 0.3.0, recorded in `SystemMapUiState`. What is asserted here is that
    // the map and the list can never disagree about what the system holds.
    @Test
    fun `the map draws one orbit for each thing the system holds and none for a gap`() {
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)

        assertEquals(listOf(7, 8, 10, 13), uiState.map.bodies.map { it.slot })
        assertEquals(uiState.bands.flatMap { it.rows }.map { it.slot }, uiState.map.bodies.map { it.slot })
        assertEquals(0, uiState.map.bodies.count { it.mark == MapMark.EMPTY })
        assertEquals(1, uiState.map.bodies.count { it.mark == MapMark.HOME })
    }

    // The width of an orbit is the one thing the picture still says about the coordinate now that
    // the fifteen ticks are gone, so it has to be monotone in the slot and it has to reach both
    // ends of the range.
    @Test
    fun `an outer slot rides a wider orbit than an inner one`() {
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val orbits = uiState.map.bodies.map { it.orbit }

        assertEquals(orbits.sorted(), orbits)
        assertEquals(orbits.distinct(), orbits)
    }

    // Whatever the system holds, the bodies use the whole frame: the first is on the inner edge and
    // the last on the outer one. That is what keeps two worlds on neighbouring slots from landing on
    // the same pixel, which linear-in-slot spacing did — see `MapBodyUiState`.
    @Test
    fun `the bodies use the whole frame however many there are`() {
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val orbits = uiState.map.bodies.map { it.orbit }

        assertEquals(listOf(0f, 1f / 3f, 2f / 3f, 1f), orbits)
    }

    @Test
    fun `a probe out of home draws its flight on the home map and nowhere else`() {
        // given a probe in flight to a neighbouring system
        val at = SystemAddress(galaxy = 3, system = 164)
        val launched = assertIs<StartSurveyResult.Started>(
            startSurvey(state().copy(resources = Resources.of(metal = 1_000_000)), at, at = EPOCH),
        ).state

        // when the home system is on screen and when the target is
        val home = launched.toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val target = launched.toGalaxyUiState(
            at = SystemSelection(galaxy = 3, system = 164),
            now = EPOCH,
            timeZone = TimeZone.UTC,
        )

        // then the arc leaves from where the probe left from and is drawn nowhere else
        assertEquals("[3:164]", checkNotNull(home.map.trajectory).label.substringBefore(" ·"))
        assertEquals(null, target.map.trajectory)
    }

    @Test
    fun `a home system with no probe out draws no flight`() {
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)

        assertEquals(null, uiState.map.trajectory)
    }

    @Test
    fun `rows are grouped into the band their orbit falls in`() {
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)

        assertEquals(listOf(OrbitBand.TEMPERATE, OrbitBand.COLD), uiState.bands.map { it.band })
        assertEquals(listOf(7, 8, 10), uiState.bands.first().rows.map { it.slot })
        assertEquals("Temperate · slots 4–10", OrbitBand.TEMPERATE.heading)
    }

    @Test
    fun `a band with nothing in it is dropped rather than shown empty`() {
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)

        assertTrue(uiState.bands.none { it.rows.isEmpty() })
        assertTrue(uiState.bands.none { it.band == OrbitBand.HOT })
    }

    @Test
    fun `the home world shows its three axes and its yield`() {
        // It is the reference: every other yield on the screen is read against 0.87, so the player
        // meets it on the first launch rather than inferring it.
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
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
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val blocked = assertIs<VerdictUiState.Blocked>(
            uiState.bands.flatMap { it.rows }.first { it.slot == 8 }.verdict,
        )

        assertEquals(listOf("temperature", "gravity"), blocked.failures.map { it.axis })
        assertEquals("1.78", blocked.failures[1].reading)
        assertEquals("1.40 g", blocked.failures[1].tolerated.breakable())
        assertEquals("Gravitic 4", blocked.failures[1].label)
        assertEquals(AdaptationTechnology.GRAVITIC, blocked.failures[1].technology)
    }

    @Test
    fun `a blocked world states what it is worth and not only what it costs`() {
        // The pillar on one row: all three of the home system's blocked worlds out-yield the
        // worth-it threshold, so what stands between the player and them is a technology rather
        // than the world. A row that named the cost and never the worth left that unsaid.
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val blocked = assertIs<VerdictUiState.Blocked>(
            uiState.bands.flatMap { it.rows }.first { it.slot == 8 }.verdict,
        )

        // Over the 0.92 its own calibration line names, which is the pillar in one row.
        assertEquals("yield 1.05", blocked.yieldLabel)
    }

    @Test
    fun `a blocked world counts the bands it fails against the same bar Barren names`() {
        // Barren's threshold sentence is what makes a bad answer read as a scale rather than as
        // bad luck, and 98% of surveyed worlds are Blocked — so it is the verdict that needs the
        // calibration most. The bar is the one core actually applies, quoted identically on both.
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val rows = uiState.bands.flatMap { it.rows }

        val twoAxes = assertIs<VerdictUiState.Blocked>(rows.first { it.slot == 8 }.verdict)
        val threeAxes = assertIs<VerdictUiState.Blocked>(rows.first { it.slot == 13 }.verdict)

        assertEquals("Fails 2 of 3 bands, worth it at 0.92", twoAxes.calibration)
        assertEquals("Fails 3 of 3 bands, worth it at 0.92", threeAxes.calibration)
    }

    // The fix 0.0.17 left behind, and the one thing on this screen that could have shipped silently
    // wrong: the mapper used to default an `AdaptationLevels` to `NONE`, so every world would have
    // stayed exactly as blocked as it was at genesis however deep the empire had climbed. The
    // failing shape is a screen that quietly refuses to show what the player bought.
    @Test
    fun `a world's verdict reads the empire's real adaptation levels`() {
        val atGenesis = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val climbed = climbed(AdaptationTechnology.THERMAL, to = 12).toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)

        // Slot 13 fails all three axes at level 0, and the coldest of them wants Thermal 12.
        val before = assertIs<VerdictUiState.Blocked>(atGenesis.rowAt(slot = 13).verdict)
        val after = assertIs<VerdictUiState.Blocked>(climbed.rowAt(slot = 13).verdict)

        assertEquals(listOf("temperature", "gravity", "pressure"), before.failures.map { it.axis })
        assertEquals(listOf("gravity", "pressure"), after.failures.map { it.axis })
    }

    // The row still names the level to *buy* rather than the one already held, which is what keeps
    // it a shopping list: an empire at Thermal 3 facing a world that wants 12 reads "Thermal 12".
    @Test
    fun `a partly climbed ladder still names the level that would land the world`() {
        val uiState = climbed(AdaptationTechnology.THERMAL, to = 3).toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val blocked = assertIs<VerdictUiState.Blocked>(uiState.rowAt(slot = 13).verdict)

        assertEquals("Thermal 12", blocked.failures.first { it.axis == "temperature" }.label)
    }

    @Test
    fun `the unit is written once on the tolerance and not on the reading`() {
        // Both numbers are the same axis and therefore the same unit, and the four characters that
        // saves are what keep the technology on the line at 393dp.
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
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
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val blocked = assertIs<VerdictUiState.Blocked>(
            uiState.bands.flatMap { it.rows }.first { it.slot == 13 }.verdict,
        )

        assertEquals(listOf("temperature", "gravity", "pressure"), blocked.failures.map { it.axis })
    }

    @Test
    fun `the technology drops the word Adaptation`() {
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val blocked = assertIs<VerdictUiState.Blocked>(
            uiState.bands.flatMap { it.rows }.first { it.slot == 13 }.verdict,
        )

        assertTrue(blocked.failures.none { it.label.contains("Adaptation") })
        assertEquals(listOf("Thermal 12", "Gravitic 2", "Atmospheric 1"), blocked.failures.map { it.label })
        // The ladder itself travels beside its label, so the tap target is keyed by the enum rather
        // than by a string that moves every time the empire climbs a level.
        assertEquals(
            listOf(AdaptationTechnology.THERMAL, AdaptationTechnology.GRAVITIC, AdaptationTechnology.ATMOSPHERIC),
            blocked.failures.map { it.technology },
        )
    }

    @Test
    fun `an unsurveyed world carries nothing but its coordinate and its orbit`() {
        // A neighbouring system, which is every system but one at ship time.
        val uiState = state().toGalaxyUiState(at = SystemSelection(galaxy = 3, system = 164), now = EPOCH, timeZone = TimeZone.UTC)
        val rows = uiState.bands.flatMap { it.rows }

        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.verdict == VerdictUiState.Unsurveyed })
        assertTrue(uiState.map.bodies.none { it.mark == MapMark.BLOCKED || it.mark == MapMark.BARREN })
    }

    @Test
    fun `a world held by another empire reads occupied and carries the holder`() {
        // Nothing generates one in 0.2 — the three scripted empires are slice #9 — so this is the
        // one verdict the mapper has to be handed rather than shown.
        val taken = GalaxyCoordinate(galaxy = 3, system = 164, slot = 5)
        val held = galaxy().let { it.copy(ownership = it.ownership + WorldOwnership(taken, EmpireId("kepler"))) }

        val uiState = state(held).toGalaxyUiState(at = SystemSelection(galaxy = 3, system = 164), now = EPOCH, timeZone = TimeZone.UTC)
        val row = uiState.bands.flatMap { it.rows }.first { it.slot == 5 }

        assertEquals(VerdictUiState.Occupied(holder = "Held by kepler"), row.verdict)
        assertEquals(MapMark.OCCUPIED, uiState.map.bodies.first { it.slot == 5 }.mark)
    }

    @Test
    fun `the galaxy tabs are the four that exist with the current one selected`() {
        val uiState = state().toGalaxyUiState(at = SystemSelection(galaxy = 2, system = 118), now = EPOCH, timeZone = TimeZone.UTC)

        assertEquals(listOf("G1", "G2", "G3", "G4"), uiState.galaxies.map { it.label })
        assertEquals(listOf(false, true, false, false), uiState.galaxies.map { it.selected })
    }

    // What replaced `atFirstSystem` / `atLastSystem` when the ±1 steppers went. A lens slides
    // rather than clipping, so there is no edge to disable a control at — what an edge changes is
    // where the lit cell sits inside the window, and never how many cells there are.
    @Test
    fun `the edges of a galaxy slide the lens rather than shrinking it`() {
        val galaxy = galaxy()

        val first = state(galaxy).toGalaxyUiState(
            at = SystemSelection(galaxy = 1, system = 1),
            now = EPOCH,
            timeZone = TimeZone.UTC,
        )
        val last = state(galaxy).toGalaxyUiState(
            at = SystemSelection(galaxy = 1, system = GalaxyBalance.SYSTEMS_PER_GALAXY),
            now = EPOCH,
            timeZone = TimeZone.UTC,
        )
        val middle = state(galaxy).toGalaxyUiState(
            at = SystemSelection(galaxy = 1, system = 125),
            now = EPOCH,
            timeZone = TimeZone.UTC,
        )

        for (uiState in listOf(first, last, middle)) {
            assertEquals(LENS_CELLS, uiState.reach.lens.cells.size)
            assertEquals(1, uiState.reach.lens.cells.count { it.selected })
        }
        assertEquals(1, first.reach.lens.cells.first().system)
        assertEquals(GalaxyBalance.SYSTEMS_PER_GALAXY, last.reach.lens.cells.last().system)
        assertEquals(125, middle.reach.lens.cells.first { it.selected }.system)
    }

    @Test
    fun `a relay is a row in the slot it occupies and holds no world`() {
        val galaxy = galaxy()
        val relaySystem = (1..GalaxyBalance.SYSTEMS_PER_GALAXY).first { system ->
            dev.fardavide.oltre.core.relayAt(galaxy.seed, galaxy = 1, system = system) != null
        }
        val relay = checkNotNull(dev.fardavide.oltre.core.relayAt(galaxy.seed, galaxy = 1, system = relaySystem))

        val uiState = state(galaxy).toGalaxyUiState(at = SystemSelection(galaxy = 1, system = relaySystem), now = EPOCH, timeZone = TimeZone.UTC)
        val row = uiState.bands.flatMap { it.rows }.first { it.slot == relay.slot }

        assertIs<VerdictUiState.Relay>(row.verdict)
        assertEquals(MapMark.RELAY, uiState.map.bodies.first { it.slot == relay.slot }.mark)
    }

    @Test
    fun `a surveyed world that passes every band but stays thin reads Barren`() {
        // Every seed is a different galaxy — the shell mints one per colony from the founding
        // instant — so this is not a state only some players reach. It is the answer the design
        // wants a survey to give *most* of the time, and the mapper's branch for it had no test
        // until one was written: the every-verdict frame is hand-written, so asserting against it
        // only ever proved that the renderer echoes a string the fixture already contained.
        val (surveyed, at) = firstSurveyedWorldWhere { it is WorldVerdict.Barren }

        val row = state(surveyed).toGalaxyUiState(at = SystemSelection(at.galaxy, at.system), now = EPOCH, timeZone = TimeZone.UTC)
            .bands.flatMap { it.rows }.first { it.slot == at.slot }

        val verdict = assertIs<VerdictUiState.Barren>(row.verdict)
        assertTrue(verdict.yieldLabel.startsWith("yield 0."), verdict.yieldLabel)
        assertEquals("Passes every band, worth it at 0.92", verdict.threshold)
        assertTrue(verdict.detail.contains("fields"), verdict.detail)
    }

    @Test
    fun `a surveyed world over the threshold reads Settleable and names the richness behind it`() {
        val (surveyed, at) = firstSurveyedWorldWhere { it is WorldVerdict.Settleable }

        val row = state(surveyed).toGalaxyUiState(at = SystemSelection(at.galaxy, at.system), now = EPOCH, timeZone = TimeZone.UTC)
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
        val (surveyed, at) = firstSurveyedWorldWhere { it is WorldVerdict.Barren }
        val verdict = assertIs<VerdictUiState.Barren>(
            state(surveyed).toGalaxyUiState(at = SystemSelection(at.galaxy, at.system), now = EPOCH, timeZone = TimeZone.UTC)
                .bands.flatMap { it.rows }.first { it.slot == at.slot }.verdict,
        )

        assertEquals(920_000, GalaxyBalance.WORTH_IT_THRESHOLD.perMillion)
        assertTrue(verdict.threshold.endsWith("0.92"), verdict.threshold)
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
                if (match(verdictFor(world, state(surveyed)))) return surveyed to at
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

        assertTrue(state(galaxy).toGalaxyUiState(at = SystemSelection(galaxy = 1, system = single), now = EPOCH, timeZone = TimeZone.UTC).detail.endsWith("1 world"))
    }

    // A value and its unit are joined by U+00A0 so a wrap never leaves "atm" alone on a line. That
    // is a rendering decision rather than a content one, so the expectations below read in ordinary
    // spaces and the non-breaking ones are normalised away here — otherwise every expected string
    // in this file would contain a character nobody can see in a diff.
    private fun String.breakable(): String = replace(' ', ' ')

    private fun GalaxyUiState.rowAt(slot: Int): WorldRowUiState =
        bands.flatMap { it.rows }.first { it.slot == slot }

    private fun galaxy(): GalaxyState = GalaxyState.initial(GalaxySeed(20_260_807))

    // The mapper takes the whole state since 0.0.18, because a verdict is a function of what the
    // empire has researched as well as of the seed. Most of the assertions here are about the map
    // rather than about the empire, so they hand it a colony that has researched nothing — which is
    // the state every one of them was written against.
    private fun state(galaxy: GalaxyState = galaxy()): GameState =
        GameState.initial(galaxy.seed).copy(galaxy = galaxy)

    private fun climbed(technology: AdaptationTechnology, to: Int, galaxy: GalaxyState = galaxy()): GameState =
        state(galaxy).let { it.copy(research = it.research.withLevel(technology, TechLevel(to))) }

    private fun homeSelection(): SystemSelection = galaxy().let {
        SystemSelection(galaxy = it.home.galaxy, system = it.home.system)
    }

    private companion object {
        // Frozen, and deliberately not what any of these tests are about: the mapper grew a
        // countdown at 0.2.0 and that has a test class of its own. One instant everywhere keeps
        // the separation visible.
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
