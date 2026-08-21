package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.galaxy.ui.GalaxyHeadsUiState
import dev.fardavide.oltre.client.galaxy.ui.LedgerBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.LedgerHeadUiState
import dev.fardavide.oltre.client.galaxy.ui.LedgerMode
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.World
import dev.fardavide.oltre.core.systemNameAt
import dev.fardavide.oltre.core.worldAt
import dev.fardavide.oltre.core.worldNameAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// **The worlds list**, which stopped being the screen the Galaxy tab opens on at 0.12 and lost its
// five filter chips and its four orders with the move. Both went for one reason: they narrowed and
// ordered a list of *worlds* when the question they were reached for is about *systems*, and a probe
// is aimed at a star. So the tests below are about the two jobs a list does better than a drawing —
// finding a place you have already been by name, and keeping the ones you marked at the top — plus
// the discovery section and the sentence for when it all comes back empty.
//
// Nothing here is stored except the pins — the query and the discovery boundary live on
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
        assertEquals(listOf(name), body.rows.map { English.resolve(it.name) })
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
        assertEquals("No world you know is called that.", English.resolve(emptiness.headline))
        assertEquals("Names are unique in a galaxy, so a full name finds one place.", English.resolve(emptiness.detail))
    }

    @Test
    fun `the worlds list is nearest first without being asked`() {
        // given a ledger spanning three systems and two galaxies. Inside one system all fifteen slots
        // sit within a few minutes of each other, so an order over them would agree with any other
        // by accident.
        val state = spread()
        val worlds = state.surveyedWorldsByLabel()

        // when — there is nothing to ask with. The sort control went at 0.12 because "where next" is
        // distance against class against region against what is still unknown, which is four axes and
        // a map's job; what is left is a list of places you *already hold*, and that has one obvious
        // reading rather than four.
        val rows = state.ledgerBody(nav = state.nav(), now = EPOCH).rows

        // then the round trip rather than the distance, because the trip is what the player is
        // spending and it is the figure the row itself prints
        val trips = rows.map {
            FleetBalance.roundTrip(
                from = state.galaxy.home,
                to = worlds.getValue(English.resolve(it.coordinate)).at,
                research = state.research,
            )
        }
        // Stated rather than trusted: an ordering assertion over a list that holds one figure is
        // green whichever way the mapper sorts, so the fixture has to be checked for the spread it
        // claims to have before the order below means anything.
        assertTrue(trips.distinct().size > 1, "the fixture has to hold worlds at different distances")
        assertEquals(trips.min(), trips.first())
        assertEquals(trips.sorted(), trips)
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
    fun `an empty list is either nothing surveyed or a name nothing answers to`() {
        // given a save with no reading on anything. **Two shapes where there were three**: the third
        // was *this chip is excluding everything and dropping it returns this many*, and it left with
        // the chips at 0.12 — with no filter in the head there is nothing that can narrow the list
        // except what was typed into it.
        val state = fresh().let { it.copy(galaxy = it.galaxy.copy(surveyed = emptySet())) }

        // when
        val unsurveyed = state.ledgerBody(nav = state.nav(), now = EPOCH)
        val unmatched = state.ledgerBody(nav = state.nav(query = "Zzz"), now = EPOCH)

        // then both end on the next thing to do rather than on an apology, and the query alone is
        // what tells them apart — an empty ledger with an empty field is a player who has flown
        // nothing yet, and the sentence says what would change that
        assertEquals("Every world a probe reaches lands here.", English.resolve(checkNotNull(unsurveyed.emptiness).headline))
        assertEquals("You have surveyed nothing yet.", English.resolve(checkNotNull(unsurveyed.emptiness).detail))
        assertEquals("No world you know is called that.", English.resolve(checkNotNull(unmatched.emptiness).headline))
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
            body.discoveries.map { English.resolve(it.world) }.toSet(),
        )
        // "found 3h 00m ago" and **not** "found day 9": nothing in `GameState` carries a genesis
        // instant, so a day number is not derivable where elapsed-since is.
        assertTrue(
            body.discoveries.all { English.resolve(it.found) == "found 3h 00m ago" },
            "was ${body.discoveries.map { English.resolve(it.found) }}",
        )
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
    fun `the head is a map with no query controls when the view is not the worlds list`() {
        // given
        val state = fresh()

        for (view in listOf(GalaxyView.MAP, GalaxyView.UNIVERSE)) {
            // when
            val head = state
                .toGalaxyUiState(nav = state.nav(view = view, query = "Cal"), now = EPOCH, timeZone = TimeZone.UTC)
                .head

            // then — **a stronger claim than the one it replaces.** Until 0.12 the map views were
            // handed the list's own head with its chip row empty and its count null, which is a
            // control the screen was trusted to leave blank; since the heads split in two there is no
            // query field on this side of the sealed type at all, so a search box over 250 stars is
            // not something the map can be given by mistake.
            assertIs<GalaxyHeadsUiState.Map>(head, "$view")
        }

        // and the orbit page, which is the one map that does keep the list's head: the search field
        // is always visible and never a mode, so what was typed survives the switch and going back is
        // not retyping.
        val system = state.ledgerHead(nav = state.nav(view = GalaxyView.SYSTEM, query = "Cal"), now = EPOCH)
        assertEquals(LedgerMode.MAP, system.mode)
        assertEquals("Cal", system.query)
    }

    @Test
    fun `the head counts what the query left`() {
        // given
        val state = fresh()

        // when
        val all = state.ledgerHead(nav = state.nav(), now = EPOCH)
        val one = state.ledgerHead(nav = state.nav(query = state.unmistakableName()), now = EPOCH)

        // then — the head prints the count of exactly what the body lists, which is why the mapper is
        // handed the matched list rather than searching the surveyed set a second time to agree with
        // itself. Since 0.12 the query is the only thing that can make the two numbers differ.
        assertEquals(LedgerMode.WORLDS, all.mode)
        assertEquals("${state.galaxy.surveyed.size} worlds", English.resolve(checkNotNull(all.count)))
        // Never "1 worlds": the count sits under the search field and is read as a sentence.
        assertEquals("1 world", English.resolve(checkNotNull(one.count)))
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────

    private fun fresh(): GameState = GameState.initial(GalaxySeed(20_260_807))

    // The four fields the tab carries, and not one of them reaches the save. A helper rather than
    // four arguments at each call site, so what a test is about is the one field it names.
    private fun GameState.nav(
        view: GalaxyView = GalaxyView.WORLDS,
        query: String = "",
        seenAt: Instant = EPOCH,
        at: SystemSelection = SystemSelection(galaxy = galaxy.home.galaxy, system = galaxy.home.system),
    ): GalaxyNavigation = GalaxyNavigation(
        view = view,
        at = at,
        query = query,
        seenAt = seenAt,
    )

    // What a landing does, without flying anything: surveying is a fleet action, so a reading on a
    // world outside the home system is reached by putting its coordinate in the set.
    private fun GameState.surveying(worlds: Iterable<World>): GameState =
        copy(galaxy = galaxy.copy(surveyed = galaxy.surveyed + worlds.map { it.at }))

    // A ledger spanning three systems and two galaxies. Without it the order would be over the rows
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

    // The rows print a coordinate rather than carrying the world they came from, so the order test
    // reads the metric back off the seed through this.
    private fun GameState.surveyedWorldsByLabel(): Map<String, World> = galaxy.surveyed
        .mapNotNull { worldAt(galaxy.seed, it) }
        .associateBy { English.resolve(it.at.label()) }

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

    // The mapper searches once and hands the same list to both halves — the head prints the count of
    // exactly what the body lists. These two put that wiring in one place so a test can ask for
    // either half on its own.
    private fun GameState.ledgerBody(nav: GalaxyNavigation, now: Instant): LedgerBodyUiState =
        toLedgerBodyUiState(nav = nav, matching = knownWorldsFor(nav, now), now = now)

    private fun GameState.ledgerHead(nav: GalaxyNavigation, now: Instant = EPOCH): LedgerHeadUiState =
        toLedgerHeadUiState(nav = nav, matching = knownWorldsFor(nav, now))
}
