package dev.fardavide.oltre.client.galaxy.presentation

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
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.epithetFor
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.worldAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
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
    fun `the caption on a socket prices the probe that would fill it`() {
        val at = SystemAddress(galaxy = HOME_GALAXY, system = 160)
        val socket = wealthy().sky(SkySelection.System(at), SkyDepth.SYSTEM).galaxies[HOME_GALAXY - 1].stars[159].worlds.first()

        val caption = wealthy().sky(SkySelection.World(GalaxyCoordinate(HOME_GALAXY, 160, socket.slot)), SkyDepth.SYSTEM).caption

        assertEquals("[6:160:${socket.slot}]", English.resolve(caption.coordinate))
        assertTrue(English.resolve(caption.meta).startsWith("unsurveyed · slot ${socket.slot}"), English.resolve(caption.meta))
        assertIs<MapCaptionTrailingUiState.Dispatch>(caption.trailing)
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
