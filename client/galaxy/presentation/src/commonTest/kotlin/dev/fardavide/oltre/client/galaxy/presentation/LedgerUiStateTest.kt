package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.galaxy.ui.LedgerBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.LedgerFilter
import dev.fardavide.oltre.client.galaxy.ui.LedgerHeadUiState
import dev.fardavide.oltre.client.galaxy.ui.LedgerMode
import dev.fardavide.oltre.client.galaxy.ui.LedgerSort
import dev.fardavide.oltre.client.galaxy.ui.WorldVerdictUiState
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.World
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.regionNameAt
import dev.fardavide.oltre.core.regionOf
import dev.fardavide.oltre.core.systemNameAt
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import dev.fardavide.oltre.core.worldNameAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// **The screen the Galaxy tab opens on**, and the half of the slice with the most arithmetic in it:
// `surveyed` filtered by five chips and a query, ordered four ways, split into pins and the rest,
// with a discovery section derived from the event log and a sentence for when it all comes back
// empty.
//
// Nothing here is stored except the pins — the query, the chips and the sort live on
// `GalaxyNavigation` and die with the check-in — so every test below hands the mapper a navigation
// rather than a saved screen.
//
// **No coordinate is hardcoded.** The galaxy is generated, so `[3:171:7]` would be an assertion
// about the generator rather than about the ledger, and it would go quietly vacuous the day genesis
// moves — which it did at 0.5.1. Each fixture scans in coordinate order instead, which picks the
// same world on every run and still picks *a* world after a reseed.
class LedgerUiStateTest {

    @Test
    fun `the ledger is everything the player has a reading on and nothing else`() {
        // given
        val state = fresh()

        // when
        val body = state.ledgerBody(nav = state.nav(), now = EPOCH)

        // then — `surveyed` is the source, which is the whole point of the slice: until 0.11 exactly
        // one function in the codebase read that set, and the rest of the galaxy is 98% of nothing.
        assertEquals(
            state.galaxy.surveyed.map { it.label() }.toSet(),
            body.rows.map { it.coordinate }.toSet(),
        )
        assertEquals(state.galaxy.surveyed.size, body.rows.size, "a world is a row once")
    }

    @Test
    fun `a full world name returns exactly one row`() {
        // given — names are unique inside a galaxy, which is what makes one row the right answer
        val state = fresh()
        val name = state.unmistakableName()

        // when
        val body = state.ledgerBody(nav = state.nav(query = name), now = EPOCH)

        // then
        assertEquals(listOf(name), body.rows.map { it.name })
    }

    @Test
    fun `a system name returns the worlds of that system and stops there`() {
        // given a ledger holding two systems — with one it would be true by having nothing to be
        // false about
        val genesis = fresh()
        val elsewhere = genesis.firstWorldWhere { it.at.system != genesis.galaxy.home.system }
        val state = genesis.surveying(
            genesis.worldsOf(SystemSelection(galaxy = elsewhere.at.galaxy, system = elsewhere.at.system)),
        )
        val home = state.galaxy.home

        // when the home system is asked for by the name the seed gave it
        val body = state.ledgerBody(
            nav = state.nav(query = systemNameAt(state.galaxy.seed, home.galaxy, home.system)),
            now = EPOCH,
        )

        // then — a world name is its system's name plus a numeral, so one query answers "show me
        // that place" and the other system's rows are not in it
        assertEquals(
            genesis.galaxy.surveyed.map { it.label() }.toSet(),
            body.rows.map { it.coordinate }.toSet(),
        )
        assertTrue(body.rows.size < state.galaxy.surveyed.size, "the second system is in the ledger")
    }

    @Test
    fun `a query that names nothing is a sentence rather than an empty list`() {
        // given a string no palette can spell: every head is three or four consonant-ended letters
        // and no tail doubles a z
        val state = fresh()

        // when
        val body = state.ledgerBody(nav = state.nav(query = "Zzz"), now = EPOCH)

        // then — the empty state explains the *rule* rather than apologising, because a player who
        // typed half a name needs to know that halves work and that whole ones are unambiguous
        assertTrue(body.rows.isEmpty())
        val emptiness = checkNotNull(body.emptiness)
        assertEquals("No world you know is called that.", emptiness.headline)
        assertEquals("Names are unique in a galaxy, so a full name finds one place.", emptiness.detail)
    }

    @Test
    fun `the reachable chip drops what a ship cannot get to inside the window`() {
        // given a world in the next galaxy in the ledger. A galaxy hop is 2,700 units against a
        // system hop's 95, so it is the one distance that overruns every rung of the ladder — but
        // the fixture asserts that rather than assuming it, because the metric is `FleetBalance`'s
        // to change.
        val genesis = fresh()
        val far = genesis.firstWorldWhere(
            inGalaxy = genesis.galaxy.home.galaxy % GalaxyBalance.GALAXIES + 1,
        ) { true }
        val state = genesis.surveying(listOf(far))
        assertTrue(
            FleetBalance.roundTrip(from = state.galaxy.home, to = far.at) > 6.hours,
            "the fixture has to be outside the window it is filtered by",
        )

        // when
        val body = state.ledgerBody(
            nav = state.nav(filters = setOf(LedgerFilter.ReachableWithin(hours = 6))),
            now = EPOCH,
        )

        // then everything that fits stays and the one that does not is gone
        assertEquals(
            genesis.galaxy.surveyed.map { it.label() }.toSet(),
            body.rows.map { it.coordinate }.toSet(),
        )
    }

    @Test
    fun `the settleable chip leaves only the verdict it names`() {
        // given the rarest verdict in the game. Nothing produces one outside the home system until a
        // probe lands, so the coordinate goes into the survey set the way a landing puts it there.
        val (state, settleable) = fresh().withFirstSurveyedWorldWhere { it is WorldVerdict.Settleable }

        // when
        val body = state.ledgerBody(
            nav = state.nav(filters = setOf(LedgerFilter.Verdict(WorldVerdictUiState.SETTLEABLE))),
            now = EPOCH,
        )

        // then — the chip is matched against the *rendered* verdict, so what it filters by is what
        // the row prints and the two cannot drift
        assertTrue(settleable.at.label() in body.rows.map { it.coordinate })
        assertTrue(
            body.rows.all { it.verdict == WorldVerdictUiState.SETTLEABLE },
            "was ${body.rows.map { it.verdict }}",
        )
    }

    @Test
    fun `the still-holding chip drops a world that has been stripped`() {
        // given a world with nothing left in the ground. Both liftable resources have to go: a world
        // with crystal left is still worth a run, and deuterium is never consulted at all because a
        // run cannot lift it.
        val genesis = fresh()
        val stripped = genesis.galaxy.surveyed.first { it != genesis.galaxy.home }
        val emptied = genesis.galaxy
            .withTaken(
                target = stripped,
                gathering = ResourceKind.METAL,
                taken = genesis.galaxy.remaining(stripped, ResourceKind.METAL, EPOCH),
                at = EPOCH,
            )
            .let {
                it.withTaken(
                    target = stripped,
                    gathering = ResourceKind.CRYSTAL,
                    taken = it.remaining(stripped, ResourceKind.CRYSTAL, EPOCH),
                    at = EPOCH,
                )
            }
        val state = genesis.copy(galaxy = emptied)

        // when
        val body = state.ledgerBody(
            nav = state.nav(filters = setOf(LedgerFilter.StillHolding)),
            now = EPOCH,
        )

        // then
        assertTrue(body.rows.none { it.coordinate == stripped.label() })
        assertEquals(genesis.galaxy.surveyed.size - 1, body.rows.size)
    }

    @Test
    fun `the region chip keeps the region the player is standing in`() {
        // given a ledger holding a world from another region
        val genesis = fresh()
        val home = genesis.galaxy.home
        val elsewhere = genesis.firstWorldWhere { regionOf(it.at.system) != regionOf(home.system) }
        val state = genesis.surveying(listOf(elsewhere))
        // The chip as the head actually offers it. It is the one filter whose label and subject move
        // with where the player is standing, so building one by hand here would be a different chip.
        val region = state.availableFiltersFor(SystemSelection(galaxy = home.galaxy, system = home.system))
            .filterIsInstance<LedgerFilter.Region>()
            .single()

        // when
        val body = state.ledgerBody(nav = state.nav(filters = setOf(region)), now = EPOCH)

        // then
        assertEquals(
            genesis.galaxy.surveyed.map { it.label() }.toSet(),
            body.rows.map { it.coordinate }.toSet(),
        )
        assertTrue(elsewhere.at.label() !in body.rows.map { it.coordinate })
    }

    @Test
    fun `the one-level-away chip names the worlds the next level of a ladder would land`() {
        // given an empire three levels up Thermal. The level matters: a filter that read the base
        // band instead of the empire's own would answer for a ladder nobody is on — the same defect
        // 0.0.17 shipped on the world row — and at level 0 the two readings are identical.
        val climbed = fresh().let {
            it.copy(research = it.research.withLevel(AdaptationTechnology.THERMAL, TechLevel(3)))
        }
        val levels = climbed.research.adaptationLevels()
        val here = GalaxyBalance.tolerance(levels).temperature
        val next = GalaxyBalance.tolerance(levels.copy(thermal = levels.thermal + 1)).temperature
        val world = climbed.firstWorldWhere {
            it.traits.temperature.celsius !in here && it.traits.temperature.celsius in next
        }
        val state = climbed.surveying(listOf(world))

        // when
        val body = state.ledgerBody(
            nav = state.nav(filters = setOf(LedgerFilter.OneLevelAway(AdaptationTechnology.THERMAL))),
            now = EPOCH,
        )

        // then exactly the worlds the fourth level of Thermal would open and none the third already
        // has — the chip that turns the research tab into a shopping list from the other direction
        val expected = state.galaxy.surveyed
            .mapNotNull { worldAt(state.galaxy.seed, it) }
            .filter { it.traits.temperature.celsius !in here && it.traits.temperature.celsius in next }
            .map { it.at.label() }
        assertTrue(world.at.label() in expected, "the derived world is one the chip should name")
        assertEquals(expected.toSet(), body.rows.map { it.coordinate }.toSet())
    }

    @Test
    fun `nearest first puts the shortest round trip at the top`() {
        // given
        val state = spread()
        val worlds = state.surveyedWorldsByLabel()

        // when
        val rows = state.ledgerBody(nav = state.nav(sort = LedgerSort.NEAREST), now = EPOCH).rows

        // then — the round trip rather than the distance, because the trip is what the player is
        // spending and it is the figure the row itself prints
        val trips = rows.map { FleetBalance.roundTrip(from = state.galaxy.home, to = worlds.getValue(it.coordinate).at) }
        assertEquals(trips.min(), trips.first())
        assertEquals(trips.sorted(), trips)
    }

    @Test
    fun `richest puts the highest yield at the top`() {
        // given
        val state = spread()
        val worlds = state.surveyedWorldsByLabel()

        // when
        val rows = state.ledgerBody(nav = state.nav(sort = LedgerSort.RICHEST), now = EPOCH).rows

        // then the yield score core decides a verdict by, not a richness of its own: the ledger's
        // order and the row's `worth it at 0.92` have to be reading the same number.
        val scores = rows.map { GalaxyBalance.yieldScore(worlds.getValue(it.coordinate).traits).perMillion }
        assertEquals(scores.max(), scores.first())
        assertEquals(scores.sortedDescending(), scores)
    }

    @Test
    fun `most left puts the deepest remaining deposits at the top`() {
        // given
        val state = spread()
        val worlds = state.surveyedWorldsByLabel()

        // when
        val rows = state.ledgerBody(nav = state.nav(sort = LedgerSort.MOST_LEFT), now = EPOCH).rows

        // then metal plus crystal and never deuterium — a run cannot lift it, so counting it would
        // sort the list by an amount no ship can come back with
        val left = rows.map { row ->
            val at = worlds.getValue(row.coordinate).at
            state.galaxy.remaining(at, ResourceKind.METAL, EPOCH) +
                state.galaxy.remaining(at, ResourceKind.CRYSTAL, EPOCH)
        }
        assertEquals(left.max(), left.first())
        assertEquals(left.sortedDescending(), left)
    }

    @Test
    fun `newest is stable because nothing records when a world was surveyed`() {
        // **A known weak proxy and flagged as one.** No instant is stored against a world's survey —
        // the event log carries the *system* — so the mapper falls back to coordinate order reversed
        // and the only thing worth pinning is that two renders of one state agree. A real "newest"
        // wants a survey instant on the save, which is a schema hop rather than a mapper change, so
        // asserting which row lands first here would pin the workaround as if it were the design.
        val state = spread()

        // when
        val once = state.ledgerBody(nav = state.nav(sort = LedgerSort.NEWEST), now = EPOCH).rows
        val twice = state.ledgerBody(nav = state.nav(sort = LedgerSort.NEWEST), now = EPOCH).rows

        // then
        assertEquals(once.map { it.coordinate }, twice.map { it.coordinate })
        // ...and it is a sort rather than a filter: the order moves and the rows do not.
        val nearest = state.ledgerBody(nav = state.nav(sort = LedgerSort.NEAREST), now = EPOCH).rows
        assertEquals(nearest.map { it.coordinate }.toSet(), once.map { it.coordinate }.toSet())
    }

    @Test
    fun `a pinned world sits in its own section and never in both`() {
        // given the one thing on this screen that reaches the save
        val genesis = fresh()
        val pin = genesis.galaxy.surveyed.first { it != genesis.galaxy.home }
        val state = genesis.copy(galaxy = genesis.galaxy.copy(pinned = setOf(pin)))

        // when
        val body = state.ledgerBody(nav = state.nav(), now = EPOCH)

        // then — a pin changes *where a thing is*, which is what a pin means; drawn in both lists it
        // would instead be a row the player has to notice is the same row twice
        assertEquals(listOf(pin.label()), body.pinned.map { it.coordinate })
        assertTrue(body.rows.none { it.coordinate == pin.label() }, "a pinned world was drawn twice")
        assertEquals(genesis.galaxy.surveyed.size, body.pinned.size + body.rows.size)
    }

    @Test
    fun `an empty ledger names the chip doing the excluding and what dropping it returns`() {
        // given two chips of which one excludes everything: every world the player knows is in the
        // home system, so a region chip raised somewhere else empties the list and the reach chip is
        // what gives them all back.
        val state = fresh()
        val elsewhere = state.firstWorldWhere { regionOf(it.at.system) != regionOf(state.galaxy.home.system) }
        val otherRegion = state
            .availableFiltersFor(SystemSelection(galaxy = elsewhere.at.galaxy, system = elsewhere.at.system))
            .filterIsInstance<LedgerFilter.Region>()
            .single()

        // when
        val body = state.ledgerBody(
            nav = state.nav(filters = setOf(LedgerFilter.ReachableWithin(hours = 6), otherRegion)),
            now = EPOCH,
        )

        // then the `time-until-affordable` pattern applied to a query — never a dead end, always the
        // next number. Which single chip gives the most back, stated as a count rather than as
        // advice, because the count is what the player is deciding against.
        assertTrue(body.rows.isEmpty())
        val emptiness = checkNotNull(body.emptiness)
        assertEquals("No world matches all 2.", emptiness.headline)
        assertEquals(
            "${state.galaxy.surveyed.size} worlds match without ${otherRegion.name.substringBefore(' ').lowercase()}.",
            emptiness.detail,
        )
    }

    @Test
    fun `a system surveyed since the tab was last looked at arrives as a discovery`() {
        // given a landing after the boundary. **"New" is not a flag on a world** — it is *surveyed
        // since you last had this tab open*, which the event log already answers and which costs the
        // save nothing: the worlds of a surveyed system are regenerable from the seed, so the event
        // names only the system.
        val genesis = fresh()
        val state = genesis.copy(
            eventLog = genesis.eventLog + Event.SurveyCompleted(
                target = SystemAddress.of(genesis.galaxy.home),
                worldsFound = genesis.galaxy.surveyed.size,
                at = EPOCH + 1.days,
            ),
        )

        // when
        val body = state.ledgerBody(
            nav = state.nav(seenAt = EPOCH),
            now = EPOCH + 1.days + 3.hours,
        )

        // then one card per world the system holds
        assertEquals(
            genesis.galaxy.surveyed.map { worldNameAt(state.galaxy.seed, it) }.toSet(),
            body.discoveries.map { it.world }.toSet(),
        )
        // "found 3h 00m ago" and **not** "found day 9": nothing in `GameState` carries a genesis
        // instant, so a day number is not derivable where elapsed-since is.
        assertTrue(body.discoveries.all { it.found == "found 3h 00m ago" }, "was ${body.discoveries.map { it.found }}")
    }

    @Test
    fun `a landing the player has already seen is not new a second time`() {
        // given the same landing on the far side of the boundary. `seenAt` moves forward when the
        // ledger is looked at, which is the whole of what makes a discovery impossible to meet twice
        // — no seen-set is stored and none has to be.
        val genesis = fresh()
        val state = genesis.copy(
            eventLog = genesis.eventLog + Event.SurveyCompleted(
                target = SystemAddress.of(genesis.galaxy.home),
                worldsFound = genesis.galaxy.surveyed.size,
                at = EPOCH + 1.days,
            ),
        )

        // when
        val body = state.ledgerBody(
            nav = state.nav(seenAt = EPOCH + 2.days),
            now = EPOCH + 3.days,
        )

        // then
        assertTrue(body.discoveries.isEmpty(), "was ${body.discoveries.map { it.world }}")
    }

    @Test
    fun `the head is a map with no query controls when the view is not the ledger`() {
        // given
        val state = fresh()

        for (view in listOf(GalaxyView.SYSTEM, GalaxyView.REGIONS)) {
            // when
            val head = state.ledgerHead(nav = state.nav(view = view, query = "Cal"), now = EPOCH)

            // then — a filter row over a map is a control with nothing to act on and the count is a
            // count of what a query left, so both are absent rather than stale
            assertEquals(LedgerMode.MAP, head.mode, "$view")
            assertTrue(head.chips.isEmpty(), "$view")
            assertEquals(null, head.count, "$view")
            // The search field is always visible and never a mode, so what was typed survives the
            // switch and going back is not retyping.
            assertEquals("Cal", head.query, "$view")
        }
    }

    @Test
    fun `the head counts what the query and the chips left`() {
        // given
        val state = fresh()

        // when
        val all = state.ledgerHead(nav = state.nav(), now = EPOCH)
        val one = state.ledgerHead(nav = state.nav(query = state.unmistakableName()), now = EPOCH)

        // then — it gates the sort control with it, because a sort of nothing is not a control
        assertEquals(LedgerMode.WORLDS, all.mode)
        assertEquals("${state.galaxy.surveyed.size} worlds", all.count)
        // Never "1 worlds": the count sits under the search field and is read as a sentence.
        assertEquals("1 world", one.count)
    }

    @Test
    fun `every chip the ledger offers is drawn with the ones that are on marked`() {
        // given
        val state = fresh()
        val home = state.galaxy.home
        val region = regionNameAt(state.galaxy.seed, home.galaxy, regionOf(home.system)).substringBefore(' ')

        // when
        val head = state.ledgerHead(
            nav = state.nav(filters = setOf(LedgerFilter.StillHolding)),
            now = EPOCH,
        )

        // then the five the design settles, in its order, unselected except the one that was tapped
        // — the tab opens with no filter to undo
        assertEquals(
            listOf("reachable 6h", "settleable", "one level away", "still holding", region),
            head.chips.map { it.label },
        )
        assertEquals(listOf(false, false, false, true, false), head.chips.map { it.on })
        // The tap is keyed by the filter rather than by the label it prints, so a copy change cannot
        // silently stop a chip working.
        assertEquals(LedgerFilter.StillHolding, head.chips[3].filter)
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────

    private fun fresh(): GameState = GameState.initial(GalaxySeed(20_260_807))

    // The seven fields the tab carries and none of which reach the save. A helper rather than seven
    // arguments at each call site, so what a test is about is the one field it names — and the chip
    // set is asked of the state rather than fixed here, because one of the five names the region the
    // player is standing in.
    private fun GameState.nav(
        view: GalaxyView = GalaxyView.LEDGER,
        query: String = "",
        filters: Set<LedgerFilter> = emptySet(),
        sort: LedgerSort = LedgerSort.NEAREST,
        seenAt: Instant = EPOCH,
        at: SystemSelection = SystemSelection(galaxy = galaxy.home.galaxy, system = galaxy.home.system),
    ): GalaxyNavigation = GalaxyNavigation(
        view = view,
        at = at,
        query = query,
        filters = filters,
        sort = sort,
        seenAt = seenAt,
        availableFilters = availableFiltersFor(at),
    )

    // What a landing does, without flying anything: surveying is a fleet action, so a reading on a
    // world outside the home system is reached by putting its coordinate in the set.
    private fun GameState.surveying(worlds: Iterable<World>): GameState =
        copy(galaxy = galaxy.copy(surveyed = galaxy.surveyed + worlds.map { it.at }))

    // A ledger spanning three systems and two galaxies. Without it a sort would be ordering the rows
    // of one system, where all fifteen slots are within a few minutes of each other and the order
    // says nothing.
    private fun spread(): GameState {
        val genesis = fresh()
        val near = genesis.firstWorldWhere { it.at.system != genesis.galaxy.home.system }
        val far = genesis.firstWorldWhere(
            inGalaxy = genesis.galaxy.home.galaxy % GalaxyBalance.GALAXIES + 1,
        ) { true }
        return genesis.surveying(
            genesis.worldsOf(SystemSelection(galaxy = near.at.galaxy, system = near.at.system)) + far,
        )
    }

    private fun GameState.firstWorldWhere(
        inGalaxy: Int = galaxy.home.galaxy,
        predicate: (World) -> Boolean,
    ): World {
        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            for (slot in 1..GalaxyBalance.SLOTS_PER_SYSTEM) {
                val at = GalaxyCoordinate(galaxy = inGalaxy, system = system, slot = slot)
                val world = worldAt(galaxy.seed, at) ?: continue
                if (predicate(world)) return world
            }
        }
        error("galaxy $inGalaxy held no world matching")
    }

    // A verdict is a function of the survey set as well as of the seed, so the candidate has to be
    // surveyed before it can be asked about — which is why this hands back the state it built rather
    // than only the world.
    private fun GameState.withFirstSurveyedWorldWhere(match: (WorldVerdict) -> Boolean): Pair<GameState, World> {
        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            for (slot in 1..GalaxyBalance.SLOTS_PER_SYSTEM) {
                val at = GalaxyCoordinate(galaxy = galaxy.home.galaxy, system = system, slot = slot)
                val world = worldAt(galaxy.seed, at) ?: continue
                val surveyed = copy(galaxy = galaxy.copy(surveyed = galaxy.surveyed + at))
                if (match(verdictFor(world, surveyed))) return surveyed to world
            }
        }
        error("galaxy ${galaxy.home.galaxy} held no world with the wanted verdict")
    }

    // The rows print a coordinate rather than carrying the world they came from, so the sort tests
    // read the metric back off the seed through this.
    private fun GameState.surveyedWorldsByLabel(): Map<String, World> = galaxy.surveyed
        .mapNotNull { worldAt(galaxy.seed, it) }
        .associateBy { it.at.label() }

    // A name no other name in the ledger contains. **The match is `contains` rather than an
    // equality**, and the slot numerals are substrings of each other — `Calanova I` is inside
    // `Calanova II`, `III`, `IV` and eight more — so "a full name returns one row" holds for the
    // numerals nothing extends and not for the ones it does. Derived rather than named, because the
    // seed decides which system is home and which of its slots hold worlds.
    private fun GameState.unmistakableName(): String {
        val names = galaxy.surveyed.map { worldNameAt(galaxy.seed, it) }
        return names.first { candidate -> names.count { candidate in it } == 1 }
    }

    private companion object {
        // Frozen. Only the discovery section and the deposit readings read the clock at all, and
        // both are handed their own instant where they need one.
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }

    // The mapper filters and sorts once and hands the same list to both halves — the head prints the
    // count of exactly what the body lists. These two put that wiring in one place so a test can ask
    // for either half on its own.
    private fun GameState.ledgerBody(nav: GalaxyNavigation, now: Instant): LedgerBodyUiState =
        toLedgerBodyUiState(nav = nav, matching = knownWorldsFor(nav, now), now = now)

    private fun GameState.ledgerHead(nav: GalaxyNavigation, now: Instant = EPOCH): LedgerHeadUiState =
        toLedgerHeadUiState(nav = nav, matching = knownWorldsFor(nav, now))
}
