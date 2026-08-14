package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.galaxy.ui.DepositReadingUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyUiState
import dev.fardavide.oltre.client.galaxy.ui.MapBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.MapMark
import dev.fardavide.oltre.client.galaxy.ui.OrbitBand
import dev.fardavide.oltre.client.galaxy.ui.SystemMapUiState
import dev.fardavide.oltre.client.galaxy.ui.VerdictUiState
import dev.fardavide.oltre.client.galaxy.ui.WorldRowUiState
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.EmpireId
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GalaxyState
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
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

        assertEquals("3:171", uiState.coordinate)
        assertEquals("STANDARD · 7 worlds", uiState.detail)
        // A Slide Over pane drops the noun rather than truncating it — a width decision, not a
        // change of voice: the star class and the count both survive.
        assertEquals("STANDARD · 7", uiState.compactDetail)
        assertTrue(uiState.isHome)
        assertEquals("250 systems", uiState.scope)
    }

    @Test
    fun `only the slots that hold something become rows`() {
        // Eight of fifteen slots are empty, and the map is where that shows — the list carries
        // only what is there, or it would be eight rows saying nothing.
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val slots = uiState.bands.flatMap { it.rows }.map { it.slot }

        assertEquals(listOf(1, 2, 4, 7, 8, 10, 11), slots)
    }

    // The orbit view draws one ellipse per occupied slot and nothing at all for the empty ones —
    // the trade Davide took at 0.3.0, recorded in `SystemMapUiState`. What is asserted here is that
    // the map and the list can never disagree about what the system holds.
    @Test
    fun `the map draws one orbit for each thing the system holds and none for a gap`() {
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)

        assertEquals(listOf(1, 2, 4, 7, 8, 10, 11), uiState.map.bodies.map { it.slot })
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

        assertEquals(listOf(0f, 1f / 6f, 2f / 6f, 3f / 6f, 4f / 6f, 5f / 6f, 1f), orbits)
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

        assertEquals(listOf(OrbitBand.HOT, OrbitBand.TEMPERATE, OrbitBand.COLD), uiState.bands.map { it.band })
        assertEquals(listOf(1, 2), uiState.bands.first().rows.map { it.slot })
        assertEquals("Temperate · slots 4–10", OrbitBand.TEMPERATE.heading)
    }

    @Test
    fun `a band with nothing in it is dropped rather than shown empty`() {
        // The home system used to be the example — it held nothing in the hot band — and since
        // 0.5.1 genesis picks a system with somewhere to go rather than the first tolerable world
        // it walks past, so this seed's home fills all three. The claim is about the mapper rather
        // than about one system, so the system is now *found* instead of assumed: the first in
        // galaxy 1 whose occupied slots miss a band, scanned in coordinate order so it is the same
        // one every run.
        val galaxy = galaxy()
        val (system, missing) = (1..GalaxyBalance.SYSTEMS_PER_GALAXY).firstNotNullOf { system ->
            // A relay is a row too, so a system carrying one could fill the band this is looking
            // for. Skipping those keeps the scan asking about worlds alone.
            if (dev.fardavide.oltre.core.relayAt(galaxy.seed, galaxy = 1, system = system) != null) return@firstNotNullOf null
            val bands = (1..GalaxyBalance.SLOTS_PER_SYSTEM)
                .filter { slot -> worldAt(galaxy.seed, GalaxyCoordinate(1, system, slot)) != null }
                .map { slot -> OrbitBand.of(slot) }
                .toSet()
            if (bands.isEmpty()) return@firstNotNullOf null
            OrbitBand.entries.firstOrNull { it !in bands }?.let { system to it }
        }

        val uiState = state(galaxy).toGalaxyUiState(
            at = SystemSelection(galaxy = 1, system = system),
            now = EPOCH,
            timeZone = TimeZone.UTC,
        )

        assertTrue(uiState.bands.none { it.rows.isEmpty() })
        assertTrue(uiState.bands.none { it.band == missing }, "band $missing has nothing in system 1:$system")
    }

    @Test
    fun `the home world shows the three axes every blocked row is measured against`() {
        // It is the reference row: "you tolerate 1.40 g" on five other rows means nothing except
        // against the world the player is standing on, so they meet it on the first launch.
        //
        // **Its yield and its hazards left this line at treatment 1b**, which is a subtraction
        // rather than a slip: the note is one line, and the only yields left on the screen are
        // Barren's threshold and Settleable's, both of which quote the bar directly.
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val home = uiState.bands.flatMap { it.rows }.first { it.slot == 7 }

        // The space before each unit is U+00A0, written as an escape here so the expectation is
        // legible in a diff — a value and its unit must never be split across a wrap.
        val verdict = assertIs<VerdictUiState.Home>(home.verdict)
        assertEquals("+35 °C · 1.21 g · 1.95 atm · 159 fields", verdict.note)
        assertEquals("[3:171:7]", home.coordinate)
    }

    @Test
    fun `a blocked world names each failing axis with the level that closes it`() {
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val blocked = assertIs<VerdictUiState.Blocked>(
            uiState.bands.flatMap { it.rows }.first { it.slot == 11 }.verdict,
        )

        assertEquals(listOf("temperature", "gravity"), blocked.failures.map { it.axis })
        assertEquals("1.48", blocked.failures[1].reading)
        assertEquals("1.40 g", blocked.failures[1].tolerated.breakable())
        assertEquals("Gravitic 1", blocked.failures[1].label)
        assertEquals(AdaptationTechnology.GRAVITIC, blocked.failures[1].technology)
    }

    @Test
    fun `a blocked world leads with what a hold is worth rather than with the verdict`() {
        // **Treatment 1b in one assertion.** A blocked world's verdict is not an offer — you cannot
        // live there and no ladder you can afford this week changes that — so the headline goes to
        // the thing you *can* act on today, which is the two numbers that price a hold. That is what
        // stops 98% of the galaxy reading as a wall.
        //
        // What this replaced: a yield and a "Fails 2 of 3 bands, worth it at 0.92" calibration line.
        // Both were added at 0.0.18 for a real reason and both are Design's to move, because the
        // reason — make a run of bad answers read as a scale rather than as bad luck — is now
        // carried by a row that says what the world *is* good for.
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val blocked = assertIs<VerdictUiState.Blocked>(
            uiState.bands.flatMap { it.rows }.first { it.slot == 11 }.verdict,
        )

        // The richness pair left this row at 0.9 and the deposits took its place — see
        // `DepositReadingUiState`. An untouched world reads in words rather than figures, which is
        // what keeps a galaxy nobody has worked a shape the eye skips.
        val row = uiState.bands.flatMap { it.rows }.first { it.slot == 11 }
        assertEquals("metal full", row.deposits?.metal)
        assertEquals("crystal full", row.deposits?.crystal)
    }

    @Test
    fun `a stripped world reads empty rather than a fraction of nothing`() {
        val stripped = state().let { state ->
            val target = state.galaxy.surveyed.first { it != state.galaxy.home }
            val cap = state.galaxy.depositCap(target, ResourceKind.METAL)!!
            state.copy(galaxy = state.galaxy.withTaken(target, ResourceKind.METAL, cap, at = EPOCH))
        }
        val slot = stripped.galaxy.surveyed.first { it != stripped.galaxy.home }.slot

        val row = stripped.toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
            .bands.flatMap { it.rows }.first { it.slot == slot }

        assertEquals("metal empty", row.deposits?.metal)
    }

    @Test
    fun `a deposit reading is present exactly where a run is legal`() {
        val rows = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
            .bands.flatMap { it.rows }

        for (row in rows) {
            val legal = row.verdict is VerdictUiState.Blocked ||
                row.verdict is VerdictUiState.Barren ||
                row.verdict is VerdictUiState.Settleable
            assertEquals(legal, row.deposits != null, "${row.coordinate} reads ${row.verdict}")
        }
    }

    @Test
    fun `a worked world states what is left against what it holds when full`() {
        // 120 of 600 and 120 of 2,400 are the same number and not the same target, so the row never
        // prints a bare figure — the denominator is the only place a cap is visible on the map.
        val worked = state().let { state ->
            val target = state.galaxy.surveyed.first { it != state.galaxy.home }
            val cap = state.galaxy.depositCap(target, ResourceKind.METAL)!!
            state.copy(galaxy = state.galaxy.withTaken(target, ResourceKind.METAL, cap / 2, at = EPOCH))
        }
        val slot = worked.galaxy.surveyed.first { it != worked.galaxy.home }.slot

        val row = worked.toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
            .bands.flatMap { it.rows }.first { it.slot == slot }

        assertTrue(row.deposits!!.metal.contains("/"), row.deposits!!.metal)
        assertEquals("crystal full", row.deposits?.crystal)
    }

    @Test
    fun `a row states its own hazards and never the danger total`() {
        // The split is epistemic rather than visual. Hazards are per-world and need a survey, so
        // they sit on the row carrying their own arithmetic; the distance band is astronomy and is
        // identical for all fifteen slots, so it is stated once under the system header. A row
        // printing `danger 2` could not say which half it came from — and on the 98% of rows that
        // are unsurveyed it would be claiming knowledge nobody has paid for.
        // Asserted over every reading the home system produces rather than over one chosen slot: the
        // seed decides which worlds carry a hazard, so naming a slot here would be asserting the
        // seed and would go quietly vacuous the day genesis moves — which it did at 0.5.1.
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val readings = uiState.bands.flatMap { it.rows }.mapNotNull { row ->
            when (val verdict = row.verdict) {
                is VerdictUiState.Blocked -> verdict.reading
                is VerdictUiState.Barren -> verdict.reading
                else -> null
            }
        }

        assertTrue(readings.isNotEmpty(), "the home system is surveyed at genesis")
        readings.forEach { reading ->
            // Either it names none, or it names them and adds up only its own.
            val ownArithmeticOnly = reading.hazards == "no hazards" || reading.hazards.endsWith(" danger")
            assertTrue(ownArithmeticOnly, reading.hazards)
            assertTrue(!reading.hazards.startsWith("danger "), "a row never prints the sum: $reading")
        }
    }

    @Test
    fun `a row states the round trip a hold would take`() {
        // Read from `FleetBalance` rather than restated, so the row and the sheet it raises cannot
        // disagree about how far away a world is.
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val blocked = assertIs<VerdictUiState.Blocked>(
            uiState.bands.flatMap { it.rows }.first { it.slot == 11 }.verdict,
        )

        assertEquals("24m out and back", blocked.reading.reach)
    }

    // The fix 0.0.17 left behind, and the one thing on this screen that could have shipped silently
    // wrong: the mapper used to default an `AdaptationLevels` to `NONE`, so every world would have
    // stayed exactly as blocked as it was at genesis however deep the empire had climbed. The
    // failing shape is a screen that quietly refuses to show what the player bought.
    @Test
    fun `a world's verdict reads the empire's real adaptation levels`() {
        val atGenesis = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val climbed = climbed(AdaptationTechnology.THERMAL, to = 12).toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)

        // Slot 1 fails all three axes at level 0, and the hottest of them wants Thermal 10 — so an
        // empire at Thermal 12 is past it and the row is down to two failures.
        val before = assertIs<VerdictUiState.Blocked>(atGenesis.rowAt(slot = 1).verdict)
        val after = assertIs<VerdictUiState.Blocked>(climbed.rowAt(slot = 1).verdict)

        assertEquals(listOf("temperature", "gravity", "pressure"), before.failures.map { it.axis })
        assertEquals(listOf("gravity", "pressure"), after.failures.map { it.axis })
    }

    // The row still names the level to *buy* rather than the one already held, which is what keeps
    // it a shopping list: an empire at Thermal 3 facing a world that wants 10 reads "Thermal 10".
    @Test
    fun `a partly climbed ladder still names the level that would land the world`() {
        val uiState = climbed(AdaptationTechnology.THERMAL, to = 3).toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val blocked = assertIs<VerdictUiState.Blocked>(uiState.rowAt(slot = 1).verdict)

        assertEquals("Thermal 10", blocked.failures.first { it.axis == "temperature" }.label)
    }

    @Test
    fun `the unit is written once on the tolerance and not on the reading`() {
        // Both numbers are the same axis and therefore the same unit, and the four characters that
        // saves are what keep the technology on the line at 393dp.
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val blocked = assertIs<VerdictUiState.Blocked>(
            uiState.bands.flatMap { it.rows }.first { it.slot == 1 }.verdict,
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
            uiState.bands.flatMap { it.rows }.first { it.slot == 1 }.verdict,
        )

        assertEquals(listOf("temperature", "gravity", "pressure"), blocked.failures.map { it.axis })
    }

    @Test
    fun `the technology drops the word Adaptation`() {
        val uiState = state().toGalaxyUiState(at = homeSelection(), now = EPOCH, timeZone = TimeZone.UTC)
        val blocked = assertIs<VerdictUiState.Blocked>(
            uiState.bands.flatMap { it.rows }.first { it.slot == 1 }.verdict,
        )

        assertTrue(blocked.failures.none { it.label.contains("Adaptation") })
        assertEquals(listOf("Thermal 10", "Gravitic 2", "Atmospheric 3"), blocked.failures.map { it.label })
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

        assertEquals(VerdictUiState.Occupied(note = "Held by kepler"), row.verdict)
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
        // The yield against the bar, in the slot the blocked axes take on a blocked row — Barren
        // fails no band at all, it fails the threshold, so it has one clause where Blocked has a
        // list. Naming the bar is what makes a run of these read as calibration rather than as bad
        // luck, and Barren is designed to be a common answer.
        assertTrue(verdict.threshold.startsWith("yield 0."), verdict.threshold)
        assertTrue(verdict.threshold.endsWith("worth it at 0.92"), verdict.threshold)
        // ...and it still carries the reach, because a world too thin to settle is perfectly good
        // ground to send a hold to and the trip is what that costs.
        assertTrue(verdict.reading.reach.endsWith("out and back"), verdict.reading.reach)
    }

    @Test
    fun `a surveyed world over the threshold reads Settleable and names the richness behind it`() {
        val (surveyed, at) = firstSurveyedWorldWhere { it is WorldVerdict.Settleable }

        val row = state(surveyed).toGalaxyUiState(at = SystemSelection(at.galaxy, at.system), now = EPOCH, timeZone = TimeZone.UTC)
            .bands.flatMap { it.rows }.first { it.slot == at.slot }

        // The rarest verdict in the game and the only one still an offer to a settler, so it keeps
        // its badge and puts everything else on one note line. Deuterium left that line with
        // treatment 1b: a run may never carry it, so on a screen whose other rows are now priced for
        // a fleet it was the one richness with nothing to compare against.
        val verdict = assertIs<VerdictUiState.Settleable>(row.verdict)
        assertTrue(verdict.note.startsWith("Yield "), verdict.note)
        assertTrue(verdict.note.contains("· metal "), verdict.note)
        assertTrue(verdict.note.contains("· crystal "), verdict.note)
        assertTrue(verdict.note.endsWith(" fields"), verdict.note)
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
