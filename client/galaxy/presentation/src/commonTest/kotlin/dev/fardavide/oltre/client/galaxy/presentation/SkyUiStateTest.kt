package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.dispatch.presentation.DispatchSelection
import dev.fardavide.oltre.client.dispatch.ui.DispatchUiState
import dev.fardavide.oltre.client.galaxy.ui.MapCaptionTrailingUiState
import dev.fardavide.oltre.client.galaxy.ui.SkyDepth
import dev.fardavide.oltre.client.galaxy.ui.SkyFlightUiState
import dev.fardavide.oltre.client.galaxy.ui.SkyHourUiState
import dev.fardavide.oltre.client.galaxy.ui.SkySelection
import dev.fardavide.oltre.client.galaxy.ui.SkyStarInk
import dev.fardavide.oltre.client.galaxy.ui.SkyUiState
import dev.fardavide.oltre.client.world.ui.WorldPortraitUiState
import dev.fardavide.oltre.core.EmpireId
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartRunResult
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.WorldOwnership
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.epithetFor
import dev.fardavide.oltre.core.startRun
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// **One sky, and what it is allowed to say at each depth.** The mapper hands the canvas every star
// of every galaxy once, and the bar, the count and the caption change with the selection and the
// depth the pinch has reached — so half of this file is about the drawing's tiers (what is grain,
// what is charted, where a world may be drawn) and the other half is about the four lines of text
// under and over it.
//
// Against a real generated galaxy rather than a fixture, like every galaxy test before it: home for
// seed 20_260_807 is 6:137, Teshezon, in Torux Blaze, with five worlds, and the light at genesis
// runs 107…167 — see the pin in core's `GameSaveTest`, which is where the address moved when the
// universe grew to nine galaxies.
class SkyUiStateTest {

    @Test
    fun `nine galaxies each of 250 stars in index order`() {
        val sky = fresh().sky(SkySelection.Galaxy(HOME_GALAXY), SkyDepth.UNIVERSE)

        assertEquals((1..GalaxyBalance.GALAXIES).toList(), sky.galaxies.map { it.galaxy })
        for (galaxy in sky.galaxies) {
            assertEquals((1..GalaxyBalance.SYSTEMS_PER_GALAXY).toList(), galaxy.stars.map { it.system })
            assertEquals((1..GalaxyBalance.REGIONS_PER_GALAXY).toList(), galaxy.regions.map { it.region })
        }
        assertEquals(HOME, sky.home)
    }

    @Test
    fun `the home galaxy reads how much of it is charted and the others read uncharted`() {
        val sky = fresh().sky(SkySelection.Galaxy(HOME_GALAXY), SkyDepth.UNIVERSE)

        assertEquals("61 of 250 charted", English.resolve(sky.galaxies[HOME_GALAXY - 1].known))
        assertTrue(sky.galaxies[HOME_GALAXY - 1].charted)
        assertEquals("uncharted", English.resolve(sky.galaxies[0].known))
        assertEquals(listOf(HOME_GALAXY), sky.galaxies.filter { it.charted }.map { it.galaxy })
    }

    @Test
    fun `a star inside the light is charted and one outside it is grain`() {
        val stars = fresh().sky(SkySelection.System(SystemAddress.of(HOME)), SkyDepth.REGION)
            .galaxies[HOME_GALAXY - 1].stars

        assertEquals((107..167).toList(), stars.filter { it.ink is SkyStarInk.Charted }.map { it.system })
        assertIs<SkyStarInk.Grain>(stars[239].ink)
        // Home is surveyed at genesis and it is the only star that is; names are a charted fact, and
        // the surveyed stars are the ones worth one.
        assertEquals(listOf(HOME.system), stars.filter { it.surveyed }.map { it.system })
        assertEquals("Teshezon", English.resolve(requireNotNull(stars[HOME.system - 1].name)))
        assertNull(stars[159].name)
    }

    @Test
    fun `a region the light has touched is named and lit and the others carry their range`() {
        val regions = fresh().sky(SkySelection.Galaxy(HOME_GALAXY), SkyDepth.GALAXY)
            .galaxies[HOME_GALAXY - 1].regions

        assertEquals(listOf(5, 6, 7), regions.filter { it.lit }.map { it.region })
        assertEquals("Torux Blaze", English.resolve(regions[5].name))
        assertEquals("1–25", English.resolve(regions[0].name))
    }

    @Test
    fun `worlds sit only beside a surveyed star`() {
        // **A world is never drawn under an unsurveyed star.** The five at home are portraits; a
        // charted star nobody has been to carries nothing unless it is the selection; grain carries
        // nothing at all.
        val stars = fresh().sky(SkySelection.System(SystemAddress.of(HOME)), SkyDepth.SYSTEM)
            .galaxies[HOME_GALAXY - 1].stars

        val home = stars[HOME.system - 1]
        assertEquals(5, home.worlds.size)
        assertTrue(home.worlds.all { it.portrait is WorldPortraitUiState.Surveyed })
        assertEquals(listOf(HOME.slot), home.worlds.filter { it.home }.map { it.slot })
        assertEquals(emptyList(), stars.filter { it.system != HOME.system && it.worlds.isNotEmpty() }.map { it.system })
    }

    @Test
    fun `the selected charted star no probe has been to carries sockets`() {
        val at = SystemAddress(galaxy = HOME_GALAXY, system = 160)
        val star = fresh().sky(SkySelection.System(at), SkyDepth.SYSTEM).galaxies[HOME_GALAXY - 1].stars[159]

        assertEquals(worldsIn(fresh().galaxy.seed, at), star.worlds.size)
        assertTrue(star.worlds.isNotEmpty())
        assertTrue(star.worlds.all { it.portrait is WorldPortraitUiState.Unsurveyed })
        assertTrue(star.worlds.all { it.metal == null && it.crystal == null })
    }

    @Test
    fun `the address is one step per depth the selection reaches`() {
        val state = fresh()

        assertEquals(
            listOf("universe", "galaxy 6"),
            state.sky(SkySelection.Galaxy(HOME_GALAXY), SkyDepth.UNIVERSE).steps.map { English.resolve(it.label) },
        )
        assertEquals(
            listOf("universe", "galaxy 6", "Torux Blaze", "Teshezon"),
            state.sky(SkySelection.System(SystemAddress.of(HOME)), SkyDepth.SYSTEM).steps.map { English.resolve(it.label) },
        )
        val world = state.sky(SkySelection.World(HOME), SkyDepth.WORLD).steps
        assertEquals(SkyDepth.entries, world.map { it.depth })
        // A region in the dark is its range, and a star in the dark is its address.
        assertEquals(
            listOf("universe", "galaxy 6", "226–250", "[6:240]"),
            state.sky(SkySelection.System(SystemAddress(HOME_GALAXY, 240)), SkyDepth.SYSTEM).steps.map { English.resolve(it.label) },
        )
    }

    @Test
    fun `the count line at the universe is nine galaxies and which one is yours`() {
        assertEquals(
            "9 galaxies · yours is 6",
            English.resolve(fresh().sky(SkySelection.Galaxy(HOME_GALAXY), SkyDepth.UNIVERSE).count),
        )
    }

    @Test
    fun `the count line inside a galaxy is what the light and the probes have reached`() {
        val out = assertIs<StartSurveyResult.Started>(startSurvey(wealthy(), TARGET, at = EPOCH)).state

        assertEquals(
            "61 of 250 charted · 1 surveyed · 1 fleet out",
            English.resolve(out.sky(SkySelection.Region(HOME_GALAXY, 7), SkyDepth.REGION).count),
        )
        // Nothing out reads as nothing, rather than as a zero.
        assertEquals(
            "61 of 250 charted · 1 surveyed",
            English.resolve(fresh().sky(SkySelection.System(SystemAddress.of(HOME)), SkyDepth.GALAXY).count),
        )
    }

    @Test
    fun `a probe into the next galaxy is that galaxy's fleet out and not yours`() {
        val hop = SystemAddress(galaxy = 7, system = HOME.system)
        val out = assertIs<StartSurveyResult.Started>(startSurvey(wealthy(), hop, at = EPOCH)).state

        // The count and the amber path both belong to the galaxy the probe is bound for: the home
        // galaxy's line stays as it was, and its arm carries no flight.
        assertEquals("0 of 250 charted · 0 surveyed · 1 fleet out", English.resolve(out.sky(SkySelection.System(hop), SkyDepth.REGION).count))
        assertEquals("61 of 250 charted · 1 surveyed", English.resolve(out.sky(SkySelection.Region(HOME_GALAXY, 6), SkyDepth.REGION).count))
        assertEquals(emptyList(), out.sky(SkySelection.System(hop), SkyDepth.REGION).galaxies[HOME_GALAXY - 1].flights)
    }

    @Test
    fun `the count line at the system depth names the system and prices the trip`() {
        val state = fresh()

        assertEquals(
            "Teshezon · 5 worlds · your own system",
            English.resolve(state.sky(SkySelection.System(SystemAddress.of(HOME)), SkyDepth.SYSTEM).count),
        )
        val landed = surveyed(TARGET)
        assertEquals(
            "Teshazon · ${worldsIn(landed.galaxy.seed, TARGET)} worlds · 2h 18m out and back",
            English.resolve(landed.sky(SkySelection.System(TARGET), SkyDepth.SYSTEM, now = LANDED).count),
        )
    }

    @Test
    fun `the count line at the world depth is the verdict`() {
        val traits = requireNotNull(worldAt(fresh().galaxy.seed, HOME)).traits

        assertEquals(
            "Home · ${English.resolve(Strings.worldEpithet(epithetFor(traits)))} · ${English.resolve(Strings.hazards(traits.hazards.size))}",
            English.resolve(fresh().sky(SkySelection.World(HOME), SkyDepth.WORLD).count),
        )
    }

    @Test
    fun `the count line on a socket is unsurveyed and prices the probe that would fill it`() {
        // Twenty-three systems out, plus the flat half hour: a socket's verdict is the flight, since
        // nothing else about the world is known.
        val at = SystemAddress(galaxy = HOME_GALAXY, system = 160)
        val socket = fresh().sky(SkySelection.System(at), SkyDepth.SYSTEM).galaxies[HOME_GALAXY - 1].stars[159].worlds.first()

        assertEquals(
            "unsurveyed · slot ${socket.slot} · probe 53m",
            English.resolve(fresh().sky(SkySelection.World(GalaxyCoordinate(HOME_GALAXY, 160, socket.slot)), SkyDepth.WORLD).count),
        )
    }

    @Test
    fun `the count line on a world somebody else holds is occupied and its epithet`() {
        val fresh = fresh()
        val taken = fresh.aNeighbourOfHome()
        val held = fresh.copy(
            galaxy = fresh.galaxy.copy(ownership = fresh.galaxy.ownership + WorldOwnership(taken, EmpireId("kepler"))),
        )
        val traits = requireNotNull(worldAt(fresh.galaxy.seed, taken)).traits

        // No hazard count: a world you cannot run to has no danger worth pricing.
        assertEquals(
            "Occupied · ${English.resolve(Strings.worldEpithet(epithetFor(traits)))}",
            English.resolve(held.sky(SkySelection.World(taken), SkyDepth.WORLD).count),
        )
    }

    @Test
    fun `the count line on a blocked world names the axis the reading and the bound`() {
        val (blocked, at) = firstSurveyedWorldWhere { it is WorldVerdict.Blocked }
        val traits = requireNotNull(worldAt(blocked.galaxy.seed, at)).traits

        val count = English.resolve(blocked.sky(SkySelection.World(at), SkyDepth.WORLD).count)

        // The requirement *is* the reason, so the line carries the figures rather than an epithet.
        assertTrue(count.startsWith("Blocked · "), count)
        assertTrue(", you tolerate " in count, count)
        assertTrue(count.endsWith(" · ${English.resolve(Strings.hazards(traits.hazards.size))}"), count)
    }

    @Test
    fun `the count line on a barren world reads its yield against the bar`() {
        val (barren, at) = firstSurveyedWorldWhere { it == WorldVerdict.Barren }
        val traits = requireNotNull(worldAt(barren.galaxy.seed, at)).traits

        val count = English.resolve(barren.sky(SkySelection.World(at), SkyDepth.WORLD).count)

        // Barren fails the bar and no band, so the line is the yield and the bar it fell under.
        assertTrue(count.startsWith("Barren · Yield "), count)
        assertTrue(", worth it at " in count, count)
        assertTrue(count.endsWith(" · ${English.resolve(Strings.hazards(traits.hazards.size))}"), count)
    }

    @Test
    fun `the count line on a settleable world is the word the epithet and the danger`() {
        val (settleable, at) = firstSurveyedWorldWhere { it is WorldVerdict.Settleable }
        val traits = requireNotNull(worldAt(settleable.galaxy.seed, at)).traits

        assertEquals(
            "Settleable · ${English.resolve(Strings.worldEpithet(epithetFor(traits)))} · ${English.resolve(Strings.hazards(traits.hazards.size))}",
            English.resolve(settleable.sky(SkySelection.World(at), SkyDepth.WORLD).count),
        )
    }

    @Test
    fun `the caption on your own galaxy says so and offers to open it`() {
        val caption = fresh().sky(SkySelection.Galaxy(HOME_GALAXY), SkyDepth.UNIVERSE).caption

        assertEquals("Galaxy 6", English.resolve(caption.system))
        assertEquals("yours", English.resolve(caption.coordinate))
        assertEquals("61 of 250 charted · 1 surveyed", English.resolve(caption.meta))
        assertEquals(MapCaptionTrailingUiState.Open(Strings.openWord()), caption.trailing)
        assertTrue(caption.own)
    }

    @Test
    fun `the caption on a galaxy nobody has entered prices the cheapest probe into it`() {
        val caption = fresh().sky(SkySelection.Galaxy(7), SkyDepth.UNIVERSE).caption

        assertEquals("Galaxy 7", English.resolve(caption.system))
        assertEquals("250 units out", English.resolve(caption.coordinate))
        // 250 units of hop plus the flat half hour.
        assertEquals("uncharted · probe 4h 40m", English.resolve(caption.meta))
        assertEquals(MapCaptionTrailingUiState.Open(Strings.openWord()), caption.trailing)
    }

    @Test
    fun `the caption on a region counts what a probe has found in it`() {
        val caption = fresh().sky(SkySelection.Region(HOME_GALAXY, 6), SkyDepth.GALAXY).caption

        assertEquals("Torux Blaze", English.resolve(caption.system))
        assertEquals("galaxy 6 · 126–150", English.resolve(caption.coordinate))
        // Four veins: the five worlds at home less the colony itself, which no run may work.
        assertEquals("25 systems · 1 surveyed · 4 veins · yours", English.resolve(caption.meta))
        assertEquals("25 systems · 1 surveyed", English.resolve(caption.compactMeta))
        assertEquals(MapCaptionTrailingUiState.Open(Strings.openWord()), caption.trailing)
        // A dark region prices the flight to its far edge: 250 − 137 = 113 systems, plus the half
        // hour, is 2h 23m.
        val dark = fresh().sky(SkySelection.Region(HOME_GALAXY, 10), SkyDepth.GALAXY).caption
        assertEquals("226–250", English.resolve(dark.system))
        assertEquals("25 systems · uncharted · 2h 23m to its edge", English.resolve(dark.meta))
    }

    @Test
    fun `a dark region prices the edge farther from home`() {
        // Region 1 runs 1–25 and home is 137: the far edge is system 1, 136 systems out, which with
        // the half hour reads 2h 46m — the longest probe the region can ask for.
        val caption = fresh().sky(SkySelection.Region(HOME_GALAXY, 1), SkyDepth.GALAXY).caption

        assertEquals("25 systems · uncharted · 2h 46m to its edge", English.resolve(caption.meta))
    }

    @Test
    fun `a region one galaxy over is never yours whatever its range`() {
        // Region 6 of galaxy 7 spans the same 126–150 that holds home in galaxy 6.
        val caption = fresh().sky(SkySelection.Region(7, 6), SkyDepth.GALAXY).caption

        assertEquals("galaxy 7 · 126–150", English.resolve(caption.coordinate))
        assertTrue(English.resolve(caption.meta).startsWith("25 systems · uncharted · "), English.resolve(caption.meta))
        assertFalse(caption.own)
    }

    @Test
    fun `the caption on an uncharted star prices what the flight would chart and offers it`() {
        val caption = wealthy().sky(SkySelection.System(SystemAddress(HOME_GALAXY, 240)), SkyDepth.REGION).caption

        assertEquals("[6:240]", English.resolve(caption.system))
        assertEquals("103 systems out", English.resolve(caption.coordinate))
        // The light is one span per galaxy, so a landing at 240 stretches 107…167 to 107…250: 83
        // systems it did not hold.
        assertEquals("uncharted · charts 83 systems", English.resolve(caption.meta))
        assertEquals(
            MapCaptionTrailingUiState.Dispatch(Strings.probeFlight(Strings.durationHoursMinutes(2, 13))),
            caption.trailing,
        )
    }

    @Test
    fun `the caption offers no probe without a hull to fly it`() {
        val caption = wealthy().copy(ships = Ships.NONE)
            .sky(SkySelection.System(SystemAddress(HOME_GALAXY, 240)), SkyDepth.REGION).caption

        assertNull(caption.trailing)
        // The flight is still printed, so nothing is hidden — only the verb is withheld.
        assertEquals("probe 2h 13m", English.resolve(requireNotNull(caption.detail)))
    }

    @Test
    fun `a probe out in the dark reads its clock rather than offering a second one`() {
        val out = assertIs<StartSurveyResult.Started>(startSurvey(wealthy(), TARGET, at = EPOCH)).state

        val caption = out.sky(SkySelection.System(TARGET), SkyDepth.REGION).caption

        assertNull(caption.trailing)
        assertEquals("probe lands in 1h 10m", English.resolve(requireNotNull(caption.detail)))
        assertEquals(listOf(SkyFlightUiState(from = HOME.system, to = TARGET.system)), out.sky(SkySelection.System(TARGET), SkyDepth.REGION).galaxies[HOME_GALAXY - 1].flights)
    }

    @Test
    fun `the caption on a surveyed star opens it until the orbit view is in`() {
        val landed = surveyed(TARGET)

        val far = landed.sky(SkySelection.System(TARGET), SkyDepth.REGION, now = LANDED).caption
        assertEquals("Teshazon", English.resolve(far.system))
        assertEquals("[6:177]", English.resolve(far.coordinate))
        assertEquals(MapCaptionTrailingUiState.Open(Strings.openWord()), far.trailing)

        val near = landed.sky(SkySelection.System(TARGET), SkyDepth.SYSTEM, now = LANDED).caption
        assertNull(near.trailing)
    }

    @Test
    fun `the caption on a charted star with nothing about it offers neither verb`() {
        // A probe sent there would come back with the same answer, so the caption prices the trip
        // and stops: no clock, no probe, no dive.
        val state = wealthy()
        val empty = firstWorldlessSystem(state.galaxy.seed)
        val charted = state.copy(galaxy = state.galaxy.withCharted(empty))

        val caption = charted.sky(SkySelection.System(empty), SkyDepth.REGION).caption

        assertTrue(" · no worlds · " in English.resolve(caption.meta), English.resolve(caption.meta))
        assertNull(caption.detail)
        assertNull(caption.trailing)
    }

    @Test
    fun `the caption on a socket prices the probe that would fill it`() {
        val at = SystemAddress(galaxy = HOME_GALAXY, system = 160)
        val socket = wealthy().sky(SkySelection.System(at), SkyDepth.SYSTEM).galaxies[HOME_GALAXY - 1].stars[159].worlds.first()

        val caption = wealthy().sky(SkySelection.World(GalaxyCoordinate(HOME_GALAXY, 160, socket.slot)), SkyDepth.SYSTEM).caption

        assertEquals("[6:160:${socket.slot}]", English.resolve(caption.coordinate))
        assertTrue(English.resolve(caption.meta).startsWith("unsurveyed · slot ${socket.slot}"), English.resolve(caption.meta))
        assertIs<MapCaptionTrailingUiState.Dispatch>(caption.trailing)
    }

    @Test
    fun `a socket under a probe in flight reads the probe's clock and offers nothing`() {
        val at = SystemAddress(galaxy = HOME_GALAXY, system = 160)
        val out = assertIs<StartSurveyResult.Started>(startSurvey(wealthy(), at, at = EPOCH)).state
        val socket = out.sky(SkySelection.System(at), SkyDepth.SYSTEM).galaxies[HOME_GALAXY - 1].stars[159].worlds.first()

        val caption = out.sky(SkySelection.World(GalaxyCoordinate(HOME_GALAXY, 160, socket.slot)), SkyDepth.SYSTEM).caption

        assertNull(caption.trailing)
        assertEquals("probe lands in 53m", English.resolve(requireNotNull(caption.detail)))
    }

    @Test
    fun `a socket offers no probe without a hull to fly it`() {
        val at = SystemAddress(galaxy = HOME_GALAXY, system = 160)
        val grounded = wealthy().copy(ships = Ships.NONE)
        val socket = grounded.sky(SkySelection.System(at), SkyDepth.SYSTEM).galaxies[HOME_GALAXY - 1].stars[159].worlds.first()

        val caption = grounded.sky(SkySelection.World(GalaxyCoordinate(HOME_GALAXY, 160, socket.slot)), SkyDepth.SYSTEM).caption

        assertNull(caption.trailing)
        // The flight is still printed, so nothing is hidden — only the verb is withheld.
        assertEquals("probe 53m", English.resolve(requireNotNull(caption.detail)))
    }

    @Test
    fun `the caption on a world at the world depth offers a run where a deposit remains`() {
        val landed = surveyed(TARGET)
        val world = landed.galaxy.surveyed.first { it.system == TARGET.system }

        val caption = landed.sky(SkySelection.World(world), SkyDepth.WORLD, now = LANDED).caption

        assertEquals(MapCaptionTrailingUiState.Run(Strings.runVerb()), caption.trailing)
        assertTrue(English.resolve(requireNotNull(caption.detail)).startsWith("metal full"), English.resolve(caption.detail!!))
        // Further out it opens instead: a run is a decision made in front of the world.
        assertEquals(
            MapCaptionTrailingUiState.Open(Strings.openWord()),
            landed.sky(SkySelection.World(world), SkyDepth.SYSTEM, now = LANDED).caption.trailing,
        )
    }

    @Test
    fun `the caption on home is your colony and offers no run`() {
        val caption = fresh().sky(SkySelection.World(HOME), SkyDepth.WORLD).caption

        assertEquals("[6:137:${HOME.slot}]", English.resolve(caption.coordinate))
        assertEquals("your colony", English.resolve(requireNotNull(caption.detail)))
        assertNull(caption.trailing)
        assertTrue(caption.own)
    }

    @Test
    fun `the caption on a world your fleet is bound for says when it is home`() {
        val landed = surveyed(TARGET).copy(ships = Ships.of(ShipType.SKIFF, 1))
        val world = landed.galaxy.surveyed.first { it.system == TARGET.system }
        val out = assertIs<StartRunResult.Started>(
            startRun(landed, world, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), window = 12.hours, at = LANDED),
        ).state

        val caption = out.sky(SkySelection.World(world), SkyDepth.WORLD, now = LANDED).caption

        // The one figure that matters while the fleet is out is when it is back — in the clock the
        // colony keeps, which here is UTC two days after the epoch.
        assertEquals("your run · home 12:00", English.resolve(requireNotNull(caption.detail)))
    }

    @Test
    fun `the caption on a worked world reads what is left of the deposit as a fraction`() {
        val landed = surveyed(TARGET)
        val world = landed.galaxy.surveyed.first { it.system == TARGET.system }
        val cap = requireNotNull(landed.galaxy.depositCap(world, ResourceKind.METAL))
        val worked = landed.copy(
            galaxy = landed.galaxy.withTaken(target = world, gathering = ResourceKind.METAL, taken = 1, at = LANDED),
        )

        val detail = English.resolve(requireNotNull(worked.sky(SkySelection.World(world), SkyDepth.WORLD, now = LANDED).caption.detail))

        // A fraction between the two words, because 120 of 600 and 120 of 2,400 are not the same
        // target; the crystal side was never touched and still reads as the word.
        val fraction = English.resolve(Strings.depositFraction((cap - 1).groupedByThousands(), cap.groupedByThousands()))
        assertTrue(detail.startsWith("metal $fraction · crystal full"), detail)
    }

    @Test
    fun `the caption on a star in another galaxy measures the way in units`() {
        // Units rather than systems, because a hop is a flight cost and not a count: the same system
        // number one galaxy over is the hop and nothing else.
        val caption = fresh().sky(SkySelection.System(SystemAddress(galaxy = 7, system = HOME.system)), SkyDepth.REGION).caption

        assertEquals("250 units out", English.resolve(caption.coordinate))
    }

    @Test
    fun `hour rings and fleet paths exist only about home`() {
        val sky = fresh().sky(SkySelection.Galaxy(HOME_GALAXY), SkyDepth.GALAXY)

        assertEquals(
            listOf(
                SkyHourUiState(system = 107, label = Strings.durationHours(1)),
                SkyHourUiState(system = 167, label = Strings.durationHours(1)),
                SkyHourUiState(system = 47, label = Strings.durationHours(2)),
                SkyHourUiState(system = 227, label = Strings.durationHours(2)),
            ),
            sky.galaxies[HOME_GALAXY - 1].hours,
        )
        assertTrue(sky.galaxies.filter { it.galaxy != HOME_GALAXY }.all { it.hours.isEmpty() && it.flights.isEmpty() })
    }

    @Test
    fun `the words the canvas sets are the two deposit names and the fog's own`() {
        val words = fresh().sky(SkySelection.Galaxy(HOME_GALAXY), SkyDepth.UNIVERSE).words

        assertEquals("metal", English.resolve(words.metal))
        assertEquals("crystal", English.resolve(words.crystal))
        assertEquals("unsurveyed", English.resolve(words.alone))
    }

    @Test
    fun `the dispatch sheet is built for the world it was opened on`() {
        // A skiff beside the scout: the scout charted the world, and a scout is a hull that gathers
        // nothing, so the sheet would refuse the run before it read the selection.
        val landed = surveyed(TARGET).copy(ships = Ships(mapOf(ShipType.SKIFF to 1, ShipType.SCOUT to 1)))
        val world = landed.galaxy.surveyed.first { it.system == TARGET.system }

        val sky = landed.sky(
            SkySelection.World(world),
            SkyDepth.WORLD,
            now = LANDED,
            dispatch = DispatchSelection(at = world, gathering = null, ships = null, window = null),
        )

        assertEquals(world, assertIs<DispatchUiState.Offer>(sky.dispatch).at)
        assertNull(landed.sky(SkySelection.World(world), SkyDepth.WORLD, now = LANDED).dispatch)
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────

    // The nearest thing to home the player already has a reading on. Derived rather than named,
    // because which slots a system fills is the seed's business and a hardcoded one would go quietly
    // vacuous the day genesis moves — which it did at 0.5.1 and again at 0.29.0.
    private fun GameState.aNeighbourOfHome(): GalaxyCoordinate =
        galaxy.surveyed.filter { it != galaxy.home }.minBy { it.slot }

    // Nothing outside the home system is surveyed at genesis and no fleet in a unit test flies, so a
    // surveyed world of a wanted verdict is injected the same way ownership is. Scans the home galaxy
    // in coordinate order, so it picks the same world every run.
    private fun firstSurveyedWorldWhere(match: (WorldVerdict) -> Boolean): Pair<GameState, GalaxyCoordinate> {
        val base = fresh()
        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            for (slot in 1..GalaxyBalance.SLOTS_PER_SYSTEM) {
                val at = GalaxyCoordinate(galaxy = HOME_GALAXY, system = system, slot = slot)
                val world = worldAt(base.galaxy.seed, at) ?: continue
                val surveyed = base.copy(galaxy = base.galaxy.copy(surveyed = base.galaxy.surveyed + at))
                if (match(verdictFor(world, surveyed))) return surveyed to at
            }
        }
        error("the home galaxy held no world matching the wanted verdict")
    }

    // Roughly one system in 390 has nothing about its star — `ProbeOfferTest` has the arithmetic —
    // so the scan runs the whole universe rather than the home galaxy.
    private fun firstWorldlessSystem(seed: GalaxySeed): SystemAddress {
        for (galaxy in 1..GalaxyBalance.GALAXIES) {
            for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
                val address = SystemAddress(galaxy = galaxy, system = system)
                if (worldsIn(seed, address) == 0) return address
            }
        }
        error("seed $seed generated no empty system at all")
    }

    private fun GameState.sky(
        selection: SkySelection,
        depth: SkyDepth,
        now: Instant = EPOCH,
        dispatch: DispatchSelection? = null,
    ): SkyUiState = toSkyUiState(
        selection = selection,
        depth = depth,
        now = now,
        timeZone = TimeZone.UTC,
        dispatch = dispatch,
    )

    private fun surveyed(target: SystemAddress): GameState = advance(
        assertIs<StartSurveyResult.Started>(startSurvey(wealthy(), target, at = EPOCH)).state,
        from = EPOCH,
        to = LANDED,
    )

    private fun fresh(): GameState =
        GameState.initial(GalaxySeed(20_260_807)).copy(ships = Ships.of(ShipType.SCOUT, 1))

    private fun wealthy(): GameState = fresh().copy(resources = Resources.of(metal = 1_000_000))

    private companion object {
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
        val LANDED: Instant = EPOCH + 2.days
        const val HOME_GALAXY: Int = 6
        val HOME: GalaxyCoordinate = GameState.initial(GalaxySeed(20_260_807)).galaxy.home
        // Forty systems out, the same reach the old target had from the old home: an hour and ten
        // minutes for a probe, and a genesis colony can afford it.
        val TARGET: SystemAddress = SystemAddress(galaxy = HOME_GALAXY, system = 177)
    }
}
