package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.galaxy.ui.GalaxyBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.MapCaptionTrailingUiState
import dev.fardavide.oltre.client.galaxy.ui.MapHourUiState
import dev.fardavide.oltre.client.galaxy.ui.MapNameTone
import dev.fardavide.oltre.client.galaxy.ui.MapStarMark
import dev.fardavide.oltre.client.galaxy.ui.MapStarUiState
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.StarClass
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.starClassAt
import dev.fardavide.oltre.core.startSurvey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// **The fold, and what it is allowed to say.** Every claim below is one of two kinds, and the file
// reads as two files stacked.
//
// The first kind is about the **drawing**: ten bands, 250 stars in index order, no drift wider than
// half a pitch, no size outside its class band. Those are the propositions the whole design rests
// on — `looks-near-is-near`'s promise is that a distance on the drawing is a distance in the game,
// and a star that wandered into its neighbour's lane, or grew until it read as the next class up,
// would be the picture contradicting the thing it pictures.
//
// The second kind is about **you**: which stars are ringed, which carry a name, and where the
// probe's hour marks fall. Those are the knowledge tiers, and they are the ones with a live trap
// under them — `GalaxyState.hasSurveyed` answers *true* about a system with no worlds in it, so the
// mapper reads the survey set instead and there is a test here holding it to that.
//
// Like `GalaxyUiStateTest`, all of it runs against a real generated galaxy rather than a fixture:
// what the screen draws *is* what the seed produced, and a hand-written band would agree with itself
// and with nothing else. Home for seed 20_260_807 is 3:171, which is the figure every hour mark
// below is worked out from.
class GalaxyMapUiStateTest {

    @Test
    fun `the fold is ten bands and each one is a region of its own galaxy`() {
        // given
        val state = fresh()

        // when
        val bands = state.mapAt(state.homeSelection()).map.bands

        // then — one band per region, region 1 first. The fold is the coordinate space rather than a
        // list that grows: twenty-five indices are a region and ten regions are the galaxy, so a
        // band cannot be missing without the drawing being of somewhere else.
        assertEquals((1..GalaxyBalance.REGIONS_PER_GALAXY).toList(), bands.map { it.region })
        // Ten names, all different, and band 7 is the one the system view already prints as the
        // region of 3:171. The two surfaces name a region identically or the map is a second galaxy.
        assertEquals("Elyutis Reach", bands[6].name)
        assertEquals(bands.size, bands.map { it.name }.distinct().size, bands.map { it.name }.toString())
        // The temperaments are a **permutation** of the fixed multiset rather than ten draws, so
        // every galaxy holds four Deeps, two Settleds and four Burnings. That is what lets the game
        // promise there is a Deep in yours, and it is why ten tinted fields read as three weathers
        // rather than as ten arbitrary hues.
        assertEquals(GalaxyBalance.REGION_TEMPERAMENTS.sorted(), bands.map { it.temperament }.sorted())
    }

    @Test
    fun `exactly one band is lit and it is the one the selection stands in`() {
        // given a selection in the third region — systems 51 to 75, which is the fold's own
        // arithmetic and not a lookup
        val state = fresh()

        // when
        val bands = state.mapAt(SystemSelection(galaxy = HOME_GALAXY, system = 60)).map.bands

        // then — never none and never two. The map opens with home selected and a tap can only move
        // the selection, so "a band is lit" is the same fact as "a system is selected".
        assertEquals(3, bands.single { it.lit }.region)
        // Home's own band goes dark while you read somewhere else: the lit band tracks the selection
        // and the home *ring* tracks home, which is what keeps the two from standing in for each
        // other.
        assertEquals(7, state.mapAt(state.homeSelection()).map.bands.single { it.lit }.region)
    }

    @Test
    fun `every system is a star in index order carrying the class the seed gave it`() {
        // given
        val state = fresh()

        // when
        val stars = state.mapAt(state.homeSelection()).map.stars

        // then — 250 ascending, which is the design's load-bearing claim rather than a convenience:
        // path order is index order, so the eye measures the same thing the arithmetic does.
        assertEquals((1..GalaxyBalance.SYSTEMS_PER_GALAXY).toList(), stars.map { it.system })
        for (star in stars) {
            assertEquals(starClassAt(state.galaxy.seed, HOME_GALAXY, star.system), star.starClass)
        }
        // Read off the header the system view prints for 3:171. A star class is astronomy — free
        // from the first launch — so the two screens cannot be allowed to disagree about one.
        assertEquals(StarClass.STANDARD, stars.at(state.galaxy.home.system).starClass)
        // All three classes really reach the drawing, or "size is class" is a channel carrying one
        // value and the assertion above holds vacuously.
        assertEquals(StarClass.entries.size, stars.map { it.starClass }.distinct().size)
    }

    @Test
    fun `no star drifts out of its lane or grows out of its class`() {
        // **The two bounds the whole drawing rests on.** Both are generated in `core`'s `layoutAt`
        // and both are restated here because this is where they become a picture:
        //
        // Drift is capped at half the band pitch, so a star can never cross the midpoint between
        // itself and its neighbour — without that cap the drawing could put system 100 to the right
        // of system 101, which is the one lie this map exists not to tell.
        //
        // Size runs 0.82 to 1.18 of the class radius and stops well short of the next class, because
        // size *is* class here: a wobble that read as a promotion would be the map's only
        // measurement saying something false.
        val state = fresh()

        val stars = state.mapAt(state.homeSelection()).map.stars

        assertEquals(
            emptyList(),
            stars.filter { it.driftPermille !in -HALF_PITCH_PERMILLE..HALF_PITCH_PERMILLE }
                .map { it.system to it.driftPermille },
        )
        assertEquals(
            emptyList(),
            stars.filter { it.sizePermille !in SIZE_FLOOR_PERMILLE..SIZE_CEILING_PERMILLE }
                .map { it.system to it.sizePermille },
        )
        // Both really vary across the sky. A bound holds trivially over a constant, and a constant is
        // exactly what this would be if the generator ever stopped answering.
        assertTrue(stars.any { it.driftPermille < 0 } && stars.any { it.driftPermille > 0 })
        assertTrue(stars.map { it.sizePermille }.distinct().size > 1)
    }

    @Test
    fun `home and the selection wear their own marks at once`() {
        // given somewhere that is not home, so the two rings are told apart rather than assumed
        val state = fresh()

        // when
        val stars = state.mapAt(SystemSelection(galaxy = HOME_GALAXY, system = 60)).map.stars

        // then — a set rather than a precedence, because the facts are genuinely independent: your
        // own star stays your own star while you are reading somewhere else, and genesis surveyed it
        // so it wears that ring too.
        assertEquals(setOf(MapStarMark.HOME, MapStarMark.SURVEYED), stars.at(state.galaxy.home.system).marks)
        assertEquals(setOf(MapStarMark.SELECTED), stars.at(60).marks)
        // And standing on home, one star carries both — which is the case a precedence would have
        // hidden one half of.
        val fromHome = state.mapAt(state.homeSelection()).map.stars.at(state.galaxy.home.system)
        assertTrue(MapStarMark.SELECTED in fromHome.marks && MapStarMark.HOME in fromHome.marks, "$fromHome")
    }

    @Test
    fun `a probe in flight rings the system it is aimed at`() {
        // given a real dispatch rather than a job written into the save by hand: `startSurvey` is
        // what puts a probe in flight, and a fabricated one could ring a system the game would have
        // refused to aim at in the first place
        val state = wealthy()
        val dispatched = assertIs<StartSurveyResult.Started>(startSurvey(state, TARGET, at = EPOCH)).state

        // when
        val stars = dispatched.mapAt(dispatched.homeSelection()).map.stars

        // then — out, and still unread. The pair is the reason these are two rings rather than one
        // "you have dealt with this" state: a probe in the air is a commitment, not a reading.
        assertEquals(setOf(MapStarMark.IN_FLIGHT), stars.at(TARGET.system).marks)
    }

    @Test
    fun `a system you know a world in wears the surveyed ring`() {
        // given that same flight, landed
        val landed = surveyed(TARGET)

        // when
        val stars = landed.mapAt(landed.homeSelection(), now = LANDED).map.stars

        // then the ring the flight left behind — and the flight's own ring gone with it, because the
        // probe is no longer in the air.
        assertEquals(setOf(MapStarMark.SURVEYED), stars.at(TARGET.system).marks)
    }

    @Test
    fun `a system with no worlds is never rung as surveyed`() {
        // **The defect the mapper's own comment is about, pinned.** `hasSurveyed` asks whether every
        // occupied world of a system is in the survey set — and every one of an empty system's zero
        // worlds is, so it answers *true* about a star nobody has ever aimed anything at. A map built
        // on it would ring hundreds of places nobody has been, which is precisely the thing the ring
        // is for. The mapper tests membership of `galaxy.surveyed` instead, which is also what keeps
        // the whole draw inside the free tier.
        val state = fresh()
        val empty = firstWorldlessSystem(state.galaxy.seed)

        // The trap is really here rather than argued about on paper: core says yes about this star.
        assertTrue(state.galaxy.hasSurveyed(empty), "$empty is the vacuous case this test exists for")

        // when
        val stars = state.mapAt(SystemSelection(galaxy = empty.galaxy, system = empty.system)).map.stars

        // then — the map says no. A system has a ring when you know a world in it, which is what a
        // player means by having been somewhere.
        assertFalse(MapStarMark.SURVEYED in stars.at(empty.system).marks, "$empty was rung")
    }

    @Test
    fun `a name appears only on home the selection and a pin`() {
        // **A pin is what makes a name appear, and that is the whole of search on a map.** All 250
        // names cost 144 µs, which the drawing could afford and the screen cannot — so the three
        // tones are three reasons for one mechanism, and every other star stays a dot.
        //
        // The pin is a world of the system the probe above landed on, because that is the only way a
        // pin can exist: `GalaxyState` refuses one on a world nobody has surveyed.
        val landed = surveyed(TARGET)
        val pin = landed.galaxy.surveyed.filter { it.system == TARGET.system }.minBy { it.slot }
        val state = landed.copy(galaxy = landed.galaxy.copy(pinned = setOf(pin)))

        // when — reading a fourth place, so all three tones are on screen at once
        val names = state.mapAt(SystemSelection(galaxy = HOME_GALAXY, system = 60), now = LANDED).map.names

        // then — three names out of 250, in index order rather than in the order the reasons were
        // collected: they are labels on a drawing, so the drawing's order is the only one that reads.
        assertEquals(listOf(60, state.galaxy.home.system, TARGET.system), names.map { it.system })
        assertEquals(
            listOf(MapNameTone.SELECTED, MapNameTone.HOME, MapNameTone.PINNED),
            names.map { it.tone },
        )
        // Real names off the same generator the system header reads — read off the run and pinned
        // here rather than derived, because a test that asked `systemNameAt` for its expectation
        // would agree with whatever answer it was given.
        assertEquals(listOf("Torodra", "Elyotis", "Raxezon"), names.map { it.name })
    }

    @Test
    fun `the hour marks are the probe's clock measured from home`() {
        // **Worked by hand, and the arithmetic is the test.** `SurveyBalance` is thirty minutes plus
        // a minute a system, so a flight from home at 3:171 has already spent 30m before it has
        // moved. The first whole hour is therefore thirty systems out — 141 to the left and 201 to
        // the right; two hours is ninety, so 81 and 261, and 261 is off the end of a 250-system
        // galaxy; three hours is a hundred and fifty, so 21 and 321, of which only 21 survives; four
        // hours is 210, and both 171 − 210 and 171 + 210 are off the map, so the ruler stops without
        // being told where to.
        //
        // Four marks, three of them to the left. **That asymmetry is the reading**: you live near one
        // end of your galaxy, and the map says so without a word.
        val state = fresh()

        // when
        val hours = state.mapAt(state.homeSelection()).map.hours

        // then
        assertEquals(
            listOf(
                MapHourUiState(system = 141, label = "1h"),
                MapHourUiState(system = 201, label = "1h"),
                MapHourUiState(system = 81, label = "2h"),
                MapHourUiState(system = 21, label = "3h"),
            ),
            hours,
        )
    }

    @Test
    fun `a galaxy you do not live in carries no mark inside four hours`() {
        // A hop is priced at 250 units before a single system of travel, so a probe to the *nearest*
        // star of the galaxy next door already costs 4h 40m. `1h` through `4h` fall behind the launch
        // and are dropped rather than drawn against the edge — which is why the mapper marks nine
        // hours and not the four the design drew: at four, a foreign galaxy would carry no ruler at
        // all.
        //
        // The marks are still symmetric about 171, because that is your own index projected across
        // the hop and distance is symmetric about it. Five hours is twenty minutes of travel past the
        // hop, so 151 and 191; six is eighty, so 91 and a 251 that is off the map; seven is a hundred
        // and forty, so 31 and a 311 that is off it too.
        val state = fresh()

        // when
        val hours = state.mapAt(SystemSelection(galaxy = 2, system = 125)).map.hours

        // then
        assertEquals(
            listOf(
                MapHourUiState(system = 151, label = "5h"),
                MapHourUiState(system = 191, label = "5h"),
                MapHourUiState(system = 91, label = "6h"),
                MapHourUiState(system = 31, label = "7h"),
            ),
            hours,
        )
    }

    @Test
    fun `the caption names the selected system and everything free to know about it`() {
        // given
        val state = fresh()

        // when
        val caption = state.mapAt(state.homeSelection()).caption

        // then — two lines, every word of it charted, so nothing here can leak what a survey is the
        // reward for. Class, region and world count are the same three the system header prints one
        // push down, which is what makes the caption a preview of that page rather than a summary of
        // its own.
        assertEquals("Elyotis", caption.system)
        assertEquals("[3:171]", caption.coordinate)
        // **The trailing noun is gone and the number is not**, which is the abbreviation rule the
        // system header and the world row already follow — and it is here because Claude Design's
        // one-line frame did not survive real text metrics: the full four words ellipsized inside
        // `worlds` at 393dp. At 320dp the region goes too, which is the one place the map is allowed
        // to drop a *name*: the band it sits in is lit and named directly above the bar.
        assertEquals("standard · Elyutis Reach · 7", caption.meta)
        assertEquals("standard · 7", caption.compactMeta)
        // The map's caption *is* the selection, so it takes the accent edge — accent means "go tap
        // this", and the thing it acts on is what the caption names.
        assertTrue(caption.own)
    }

    @Test
    fun `the caption offers a probe where nothing is known and quotes the run where something is`() {
        // **Stars are probe targets and worlds are run targets**, which is the rule the trailing
        // element exists to obey. It falls straight out of the knowledge tiers: a run is aimed at a
        // world, and worlds are what a survey pays for, so on a system you already know the map has
        // nothing left to aim and quotes the clock instead — the run itself is chosen per world on
        // the orbit page, and the caption's own tap is what takes you there.
        val unknown = wealthy()
        val known = surveyed(TARGET)

        // 170 systems from home at a minute each, plus the flat half hour.
        assertEquals(
            MapCaptionTrailingUiState.Dispatch("probe 3h 20m"),
            unknown.mapAt(SystemSelection(galaxy = HOME_GALAXY, system = 1)).caption.trailing,
        )
        // A note and not a second dispatch — and note that this state is *wealthier* than the one
        // above, which is the point: what closes the offer is the reading, never the price.
        assertEquals(
            MapCaptionTrailingUiState.Note("1h 18m out and back"),
            known.mapAt(SystemSelection(galaxy = HOME_GALAXY, system = TARGET.system), now = LANDED)
                .caption.trailing,
        )
    }

    @Test
    fun `the caption is never empty and never says nothing is selected`() {
        // The map opens on home and a tap can only move the selection, so there is no "nothing
        // selected" state to design: no placeholder copy, no dead bar at the foot of the screen, and
        // the first thing a new player is shown is their own star with its own clock on it.
        //
        // Asserted across the whole galaxy rather than at one system, because the claim is about the
        // coordinate space — every one of the 250 has a name, an address, a line of astronomy and
        // something to say, including the empty ones, which say that they are empty.
        val state = fresh()

        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            val caption = state.mapAt(SystemSelection(galaxy = HOME_GALAXY, system = system)).caption

            assertTrue(caption.system.isNotBlank(), "3:$system is nameless")
            assertEquals("[$HOME_GALAXY:$system]", caption.coordinate)
            assertTrue(caption.meta.isNotBlank(), "3:$system reads blank")
            assertFalse(caption.trailing is MapCaptionTrailingUiState.None, "3:$system trails nothing")
        }
    }

    @Test
    fun `the universe is four discs priced by what a run costs to reach them`() {
        // **What four discs can mean today is one thing, and it is real: what it costs to get
        // there.** The four are not equidistant — two neighbours and one far corner — against 3h 22m
        // to cross your own galaxy end to end, so the near ones are already nearly three times the
        // longest journey you can make at home.
        //
        // A round trip rather than a flight, because a hop is a commitment rather than a journey: you
        // are deciding to be away, and the way back is half of what you are buying.
        val state = fresh()

        // when
        val discs = state.universeAt(state.homeSelection()).universe.discs

        // then
        assertEquals(listOf(1, 2, 3, 4), discs.map { it.galaxy })
        assertEquals(listOf("G1", "G2", "G3", "G4"), discs.map { it.label })
        // Home is priced as home: there is no run to where you already are, and a figure there would
        // be an offer the game does not make.
        assertEquals(listOf("run 18h 20m", "run 9h 20m", null, "run 9h 20m"), discs.map { it.cost })
        assertEquals(listOf(false, false, true, false), discs.map { it.home })
        assertEquals(listOf(false, false, true, false), discs.map { it.selected })
        // Systems and not worlds: genesis surveys seven worlds and they are all in one system, so the
        // line the empires will later share reads "1 surveyed" rather than "7".
        assertEquals(listOf("0 surveyed", "0 surveyed", "1 surveyed", "0 surveyed"), discs.map { it.known })
        // Four different galaxies rather than one drawing shown four times — each disc carries its
        // own seed's region names and its own temperament permutation.
        assertEquals(discs.size, discs.map { disc -> disc.map.bands.map { it.name } }.distinct().size)
        // And the home disc is the same fold the galaxy map draws, at a fifth of the size.
        assertEquals(
            state.mapAt(state.homeSelection()).map.bands.map { it.name },
            discs[HOME_GALAXY - 1].map.bands.map { it.name },
        )
    }

    @Test
    fun `a disc is the same drawing at a fifth of the size with nothing written on it`() {
        // Real rather than decorative: a disc is this same fold, so a Burning region is where the
        // small picture puts it and comparing two galaxies is comparing two skies. What comes off is
        // everything that needs room — a 148dp drawing has nowhere to put a word — and the hour
        // ruler, which measures a flight the card is not about.
        val state = fresh()

        // when
        val discs = state.universeAt(state.homeSelection()).universe.discs

        // then
        for (disc in discs) {
            assertTrue(disc.map.mini, "G${disc.galaxy} is drawn full size")
            assertEquals(emptyList(), disc.map.names, "G${disc.galaxy} carries names")
            assertEquals(emptyList(), disc.map.hours, "G${disc.galaxy} carries hour marks")
            assertEquals(GalaxyBalance.REGIONS_PER_GALAXY, disc.map.bands.size)
            assertEquals(GalaxyBalance.SYSTEMS_PER_GALAXY, disc.map.stars.size)
            // No selection and no lit band on a disc: the universe view selects a *galaxy*, and the
            // card's own border is what says which. A lit band here would be a second answer to a
            // question the frame has already answered.
            assertTrue(disc.map.stars.none { MapStarMark.SELECTED in it.marks }, "G${disc.galaxy} selects a star")
            assertTrue(disc.map.bands.none { it.lit }, "G${disc.galaxy} lights a band")
        }
        // And the drawing these are miniatures of is not mini, which is what makes the flag mean
        // anything at all.
        assertFalse(state.mapAt(state.homeSelection()).map.mini)
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────

    // Through the screen's own mapper rather than through `toGalaxyMapUiState` directly, so that the
    // view the tab lands on is wired to the fold it is supposed to draw — and so the caption arrives
    // beside the drawing it captions, which is the pairing the body type states.
    private fun GameState.mapAt(at: SystemSelection, now: Instant = EPOCH): GalaxyBodyUiState.Map =
        assertIs(toGalaxyUiState(nav = nav(GalaxyView.MAP, at), now = now, timeZone = TimeZone.UTC).body)

    private fun GameState.universeAt(at: SystemSelection, now: Instant = EPOCH): GalaxyBodyUiState.Universe =
        assertIs(toGalaxyUiState(nav = nav(GalaxyView.UNIVERSE, at), now = now, timeZone = TimeZone.UTC).body)

    // The other two fields are the worlds list's furniture and neither reaches a map: 250 stars
    // cannot be searched — the head above the map does not even carry a field — and a discovery
    // boundary is measured against rows.
    private fun nav(view: GalaxyView, at: SystemSelection): GalaxyNavigation = GalaxyNavigation(
        view = view,
        at = at,
        query = "",
        seenAt = EPOCH,
    )

    // `single` rather than `first`: the fold's whole contract is one star per system, so a helper
    // that quietly took the first of two would hide the failure the test above is looking for.
    private fun List<MapStarUiState>.at(system: Int): MapStarUiState = single { it.system == system }

    private fun GameState.homeSelection(): SystemSelection =
        SystemSelection(galaxy = galaxy.home.galaxy, system = galaxy.home.system)

    // A probe flown and landed, which is the only way a foreign system enters the survey set. Writing
    // coordinates into `galaxy.surveyed` by hand would test the mapper against a save the game cannot
    // produce, and the ring's whole meaning is that a flight paid for it.
    private fun surveyed(target: SystemAddress): GameState = advance(
        assertIs<StartSurveyResult.Started>(startSurvey(wealthy(), target, at = EPOCH)).state,
        from = EPOCH,
        to = LANDED,
    )

    // Roughly one system in 390 holds nothing at all, so a single galaxy is not guaranteed to have
    // one and the scan crosses all four — `ProbeActionUiStateTest` scans the same way after a fixture
    // that looked only at galaxy 1 went vacuous under this seed.
    private fun firstWorldlessSystem(seed: GalaxySeed): SystemAddress {
        for (galaxy in 1..GalaxyBalance.GALAXIES) {
            for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
                val address = SystemAddress(galaxy = galaxy, system = system)
                if (worldsIn(seed, address) == 0) return address
            }
        }
        error("seed $seed generated no empty system at all")
    }

    private fun fresh(): GameState = GameState.initial(GalaxySeed(20_260_807))

    private fun wealthy(): GameState = fresh().copy(resources = Resources.of(metal = 1_000_000))

    private companion object {
        // Frozen. Nothing here is about a countdown — the caption's one clock is a flight that has
        // not started — so one instant everywhere keeps that separation visible.
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)

        // Long enough that any probe in this file has landed: the longest flight inside one galaxy is
        // 4h 40m.
        val LANDED: Instant = EPOCH + 2.days

        // Home for seed 20_260_807 is 3:171, and half of what this file asserts is measured from it.
        const val HOME_GALAXY: Int = 3

        // Forty systems from home, which prices the round trip in the caption test at 1h 18m and puts
        // the probe's own flight at 70 minutes — both comfortably inside `LANDED`.
        val TARGET: SystemAddress = SystemAddress(galaxy = HOME_GALAXY, system = 211)

        // `core` keeps `DRIFT_LIMIT`, `SIZE_FLOOR` and `SIZE_CEILING` private to the generator, so
        // they are restated here rather than imported. That is deliberate: this is the drawing's side
        // of the contract, and a test that read the constant it is checking would pass whatever the
        // generator was changed to.
        const val HALF_PITCH_PERMILLE: Int = 500
        const val SIZE_FLOOR_PERMILLE: Int = 820
        const val SIZE_CEILING_PERMILLE: Int = 1_180
    }
}
