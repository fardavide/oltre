package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.galaxy.ui.GalaxyBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyHeadsUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyRowUiState
import dev.fardavide.oltre.client.galaxy.ui.LedgerBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.MapMark
import dev.fardavide.oltre.client.galaxy.ui.WorldVerdictUiState
import dev.fardavide.oltre.client.world.ui.WorldPortraitUiState
import dev.fardavide.oltre.core.EmpireId
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.WorldOwnership
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// The mapper runs against a real generated galaxy rather than a fake one: the whole point of the
// screen is that it reads what the seed produced, and a fixture would let the two drift. Since 0.11
// that argument is stronger rather than weaker — a name and an epithet are *content* generated from
// the same seed as the traits beside them, so a hand-written row could agree with itself and with
// nothing else.
class GalaxyUiStateTest {

    @Test
    fun `the system header leads with the name and keeps the address under it`() {
        // given
        val state = fresh()

        // when
        val header = state.systemAt(state.homeSelection()).header

        // then — the headline is the place and the coordinate is demoted rather than deleted: the
        // arithmetic, the ledger's key and the eventual multiplayer chat all still need the address.
        assertEquals("Elyotis", English.resolve(header.system))
        assertEquals("3:171", English.resolve(header.coordinate))
        // **The same string against a different destination, which is the one thing 0.12 moved on
        // this page.** Until the fold arrived the region name was the way *into* the region index —
        // a list of ten rows that no longer exists — and it is now the way back *out* to the map,
        // framed on the system you were reading, because a band of the fold *is* a region. So what
        // is pinned here is unchanged and what it means is not: it is still the header's only accent
        // string, and accent still says "go tap this", but where it lands is the drawing rather than
        // a screen that has been deleted.
        assertEquals("Elyutis Reach", English.resolve(header.region))
        // Lower case in the model since #86 and drawn in capitals by `SystemHead`, which is what
        // it always did — the star class used to arrive here as the enum's own `STANDARD` and be
        // uppercased again on the way out. Which is also what the map caption had to lower-case to
        // use, so the two surfaces now read one entry.
        assertEquals("standard · 7 worlds", English.resolve(header.detail))
        assertTrue(header.isHome)
    }

    @Test
    fun `only your own system prices a round trip as a range`() {
        // Within one system a run's distance metric is world-to-world, so it is the *slot* gap that
        // varies; a hop to any other system is priced identically for all fifteen slots. The spread
        // is therefore the choice a player is making at home, and one figure everywhere else — which
        // is the whole reason this line can live under the header instead of on fifteen rows.
        val state = fresh()

        val home = state.systemAt(state.homeSelection()).header.shortAstronomy
        val away = state.systemAt(state.unsurveyedSelection()).header.shortAstronomy

        // Both of these overflow the 54-character budget — home because it states a range and this
        // neighbour because its flight is hours — so both are drawn in the short form, which drops
        // "from here". What goes is always a noun and never a figure, and the first clause has
        // already said what the band is measured from.
        //
        // **Which of the two the screen picks is `SystemHead`'s since #86**, so what is asserted here
        // is the reading it picks rather than the picking — see `astronomyFor`.
        assertEquals("Your own system · danger 0 · 20–26m out and back", English.resolve(home))
        assertEquals("945 units out · danger 2 · 3h 28m out and back", English.resolve(away))
        assertTrue('–' !in English.resolve(away), "only home spreads the trip across its slots: $away")
    }

    @Test
    fun `a row leads with the generated world name and writes its address once`() {
        // given the world genesis put the colony on, whose neighbours are the only rows a player
        // meets before their first probe lands
        val state = fresh()

        // when
        val row = state.rowAt(state.aNeighbourOfHome())

        // then the name carries the system and the slot's numeral, which is the one numbering on the
        // screen — the map already spaces bodies by rank and labels them by slot, so a second ordinal
        // would make those two disagree.
        assertEquals("Elyotis I", English.resolve(row.name))
        assertEquals("[3:171:1]", English.resolve(row.coordinate))
        // The address is rendered once. It moves between the subtitle line and the headline's tail
        // depending on whether there is an epithet, and a row that printed it in both places would be
        // saying the same thing twice on the tightest line in the app.
        val printed = listOfNotNull(row.name, row.coordinate, row.epithet, row.note) +
            row.requirements.map { it.label }
        assertEquals(
            1,
            printed.count { English.resolve(row.coordinate) in English.resolve(it) },
            printed.toString(),
        )
    }

    @Test
    fun `the portrait and the epithet are one permission`() {
        // **The single most important claim in this file.** A disc is drawn exactly where an epithet
        // is drawn and both are readouts of the same three traits, so they are gated by one condition
        // — the survey set — rather than by two that could drift. Anything else is a trait leaking
        // onto a row nobody paid for.
        val state = fresh()
        val rows = state.worldRowsAt(state.homeSelection()) + state.worldRowsAt(state.unsurveyedSelection())

        // Both halves are really in the sample, or the loop below would pass vacuously.
        assertTrue(rows.any { it.portrait is WorldPortraitUiState.Surveyed }, "genesis surveys home")
        assertTrue(rows.any { it.portrait is WorldPortraitUiState.Unsurveyed }, "249 systems in 250")
        for (row in rows) {
            assertEquals(
                row.portrait is WorldPortraitUiState.Surveyed,
                row.epithet != null,
                "${row.coordinate} draws ${row.portrait} beside '${row.epithet}'",
            )
        }
    }

    @Test
    fun `an unsurveyed world is an empty socket rather than a set of nulls`() {
        // The state is a case rather than three absent traits, which is what let the word
        // `Unsurveyed` leave the row entirely — an empty socket next to a filled one says it.
        val state = fresh()
        val row = state.worldRowsAt(state.unsurveyedSelection()).first()

        assertEquals(WorldPortraitUiState.Unsurveyed, row.portrait)
        assertNull(row.epithet)
        assertEquals(WorldVerdictUiState.UNSURVEYED, row.verdict)
        // Nothing is priced either: a hold cannot be quoted from a world nobody has looked at.
        assertNull(row.deposits)
    }

    @Test
    fun `a surveyed world carries the epithet its three axes derive`() {
        // Derived and never rolled, so the word cannot disagree with the readings the disc draws from
        // — which is what makes it safe to put a *word* on a screen that otherwise prints only
        // measurements.
        val state = fresh()
        val row = state.rowAt(state.aNeighbourOfHome())

        assertIs<WorldPortraitUiState.Surveyed>(row.portrait)
        assertEquals("veiled furnace", English.resolve(checkNotNull(row.epithet)))
    }

    @Test
    fun `each of the six verdicts reaches the row as its own word`() {
        // One row shape serves all six, so the enum is the only thing that tells them apart — and
        // each one here is *found* in the generated galaxy rather than fabricated, except the holder
        // that nothing produces until the three scripted empires land.
        val fresh = fresh()
        val taken = fresh.aNeighbourOfHome()
        val held = fresh.copy(
            galaxy = fresh.galaxy.copy(
                ownership = fresh.galaxy.ownership + WorldOwnership(taken, EmpireId("kepler")),
            ),
        )
        val (blocked, blockedAt) = firstSurveyedWorldWhere { it is WorldVerdict.Blocked }
        val (barren, barrenAt) = firstSurveyedWorldWhere { it == WorldVerdict.Barren }
        val (settleable, settleableAt) = firstSurveyedWorldWhere { it is WorldVerdict.Settleable }

        assertEquals(WorldVerdictUiState.HOME, fresh.rowAt(fresh.galaxy.home).verdict)
        assertEquals(WorldVerdictUiState.OCCUPIED, held.rowAt(taken).verdict)
        assertEquals(WorldVerdictUiState.UNSURVEYED, fresh.worldRowsAt(fresh.unsurveyedSelection()).first().verdict)
        assertEquals(WorldVerdictUiState.BLOCKED, blocked.rowAt(blockedAt).verdict)
        assertEquals(WorldVerdictUiState.BARREN, barren.rowAt(barrenAt).verdict)
        assertEquals(WorldVerdictUiState.SETTLEABLE, settleable.rowAt(settleableAt).verdict)
    }

    @Test
    fun `the unsurveyed verdict is the one with no word`() {
        // The design's one deliberate subtraction, and the constant is kept rather than the case being
        // deleted so that the decision stays arguable: an empty socket where every surveyed row has a
        // body states the same thing and buys back a colour and the row's whole right end on 98% of
        // rows.
        assertNull(WorldVerdictUiState.UNSURVEYED.word)
        assertEquals(
            listOf(WorldVerdictUiState.UNSURVEYED),
            WorldVerdictUiState.entries.filter { it.word == null },
            "exactly one verdict is allowed to be wordless",
        )
    }

    @Test
    fun `a blocked row names the axis the reading the bound and the ladder that would land it`() {
        // `Blocked` naming its own remedy is the design's load-bearing detail — it is the only thing
        // connecting the galaxy and the research tab — so all four parts are pinned together: without
        // the ladder the row is a wall, and without the two figures the ladder is a guess.
        //
        // given the first world of the home system a gravity band turns away. Genesis surveys that
        // system whole, so this is a row every player of this colony meets on their first launch
        // rather than one a fixture had to arrange.
        val state = fresh()
        val row = state.worldRowsAt(state.homeSelection()).first { world ->
            world.requirements.any { English.resolve(it.axis) == "gravity" }
        }

        // then — the four parts, in the order the line prints them
        val gravity = row.requirements.first { English.resolve(it.axis) == "gravity" }
        assertEquals(WorldVerdictUiState.BLOCKED, row.verdict)
        assertEquals("1.53", English.resolve(gravity.reading))
        // The unit is written once and on the tolerance: both numbers are the same axis, and the four
        // characters that saves are what keep the technology on the line at 393dp. The space before it
        // is U+00A0 so a wrap never leaves "g" alone, normalised here so the expectation is legible.
        assertEquals("1.40 g", English.resolve(gravity.tolerated).breakable())
        // "Gravitic 2", never "Gravitic Adaptation 2": all three ladders end in the same word, so it
        // carries nothing and costs eleven characters the row does not have.
        assertEquals("Gravitic 2", English.resolve(gravity.label))
        assertTrue(row.requirements.none { "Adaptation" in English.resolve(it.label)}, row.requirements.toString())
    }

    @Test
    fun `a deposit reading is present exactly where a run is legal`() {
        // Not a coincidence and not a rendering choice: absent on `Unsurveyed` because a hold cannot
        // be priced from a world nobody has looked at, and absent on `Home` and `Occupied` because a
        // run there is refused outright. So the presence of the pair *is* the offer.
        val fresh = fresh()
        val taken = fresh.aNeighbourOfHome()
        val held = fresh.copy(
            galaxy = fresh.galaxy.copy(
                ownership = fresh.galaxy.ownership + WorldOwnership(taken, EmpireId("kepler")),
            ),
        )
        val rows = fresh.worldRowsAt(fresh.homeSelection()) +
            fresh.worldRowsAt(fresh.unsurveyedSelection()) +
            held.rowAt(taken)

        // All three of the refusing verdicts are really in the sample, or the loop proves nothing.
        val verdicts = rows.map { it.verdict }.toSet()
        assertTrue(
            verdicts.containsAll(
                listOf(
                    WorldVerdictUiState.HOME,
                    WorldVerdictUiState.OCCUPIED,
                    WorldVerdictUiState.UNSURVEYED,
                ),
            ),
            verdicts.toString(),
        )
        for (row in rows) {
            val legal = row.verdict in listOf(
                WorldVerdictUiState.BLOCKED,
                WorldVerdictUiState.BARREN,
                WorldVerdictUiState.SETTLEABLE,
            )
            assertEquals(legal, row.deposits != null, "${row.coordinate} reads ${row.verdict}")
        }
    }

    @Test
    fun `an untouched world reads in words rather than in a fraction of itself`() {
        // Roughly 98% of the galaxy has never been worked, and "full" is the honest reading of it —
        // where "left" would assert that somebody had taken some. A word at each end and a working
        // fraction between them is what keeps an untouched galaxy a shape the eye skips.
        val state = fresh()
        val row = state.worldRowsAt(state.homeSelection()).first { it.deposits != null }
        val deposits = assertNotNull(row.deposits)

        assertEquals("full", English.resolve(assertNotNull(deposits.metal).reading))
        assertEquals("full", English.resolve(assertNotNull(deposits.crystal).reading))
    }

    @Test
    fun `the map draws exactly the bodies the rows draw`() {
        // The two can never disagree about what a system holds, because they are built from one scan
        // of the fifteen slots. A map that showed a world the list omitted would be the screen
        // contradicting itself on the one question it exists to answer.
        val state = fresh()
        val system = state.systemAt(state.homeSelection())

        assertEquals(system.rows.map { it.at.slot }, system.map.bodies.map { it.slot })
        assertEquals(0, system.map.bodies.count { it.mark == MapMark.EMPTY })
        assertEquals(1, system.map.bodies.count { it.mark == MapMark.HOME })
    }

    @Test
    fun `a count belongs to the worlds list and never to one system`() {
        // given the two views that share one head. The orbit page takes the list head rather than
        // the map's — the switch and the search are what say *which worlds*, and a system is still
        // something you reached by choosing among them — so the head's *type* cannot tell the two
        // apart, and the count is the only field that does.
        val state = fresh()

        // when
        val onSystem = state.headOn(GalaxyView.SYSTEM)
        val onWorlds = state.headOn(GalaxyView.WORLDS)

        // then — null on the system, because one system is a reading rather than a list with a
        // length, and a count line there would be printing the size of a list that is not on screen.
        assertNull(assertIs<GalaxyHeadsUiState.Worlds>(onSystem).head.count)
        // The other half is asserted for the same reason the portrait test asserts both of its: a
        // count that was never present anywhere would satisfy the line above and say nothing.
        assertNotNull(assertIs<GalaxyHeadsUiState.Worlds>(onWorlds).head.count)
    }

    @Test
    fun `the round trip travels with a ledger row and never with a system row`() {
        // The same world in both screens. In a system every row shares the header's astronomy line,
        // so a per-row trip would be the same figure printed fifteen times; in the ledger the rows
        // come from everywhere at once and nothing above them can say it.
        val state = fresh()
        val at = state.aNeighbourOfHome()
        val inSystem = state.rowAt(at)
        val inLedger = state.ledgerRows().first { it.coordinate == inSystem.coordinate }

        assertNull(inSystem.trailing)
        assertEquals("26m", English.resolve(checkNotNull(inLedger.trailing)))
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────

    private fun GameState.systemAt(at: SystemSelection): GalaxyBodyUiState.System = assertIs(
        toGalaxyUiState(nav = nav(GalaxyView.SYSTEM, at), now = EPOCH, timeZone = TimeZone.UTC).body,
    )

    private fun GameState.ledgerRows(): List<GalaxyRowUiState.World> = assertIs<GalaxyBodyUiState.Ledger>(
        toGalaxyUiState(nav = nav(GalaxyView.WORLDS, homeSelection()), now = EPOCH, timeZone = TimeZone.UTC).body,
    ).body.let { body: LedgerBodyUiState -> body.pinned + body.rows }

    // Always framed on home, and the selection is not a variable this fixture is hiding: which
    // system is on screen is the body's business, and the list head does not read `at` at all — its
    // count is of everything you have a reading on, wherever you are standing.
    private fun GameState.headOn(view: GalaxyView): GalaxyHeadsUiState =
        toGalaxyUiState(nav = nav(view, homeSelection()), now = EPOCH, timeZone = TimeZone.UTC).head

    private fun GameState.worldRowsAt(at: SystemSelection): List<GalaxyRowUiState.World> =
        systemAt(at).rows.filterIsInstance<GalaxyRowUiState.World>()

    private fun GameState.rowAt(at: GalaxyCoordinate): GalaxyRowUiState.World =
        worldRowsAt(SystemSelection(galaxy = at.galaxy, system = at.system)).first { it.at == at }

    // Everything the tab remembers, in one place, and it is four fields where it was seven: the
    // filters, the sort and the set of chips they were offered by all left with the ledger's controls
    // at 0.12. The two that a call site still varies are parameters; the two that are only ever what
    // the screen opens on are written below. The same subtraction is why this is no longer a
    // `GameState` extension — the available filters were the one field here derived from a save.
    private fun nav(view: GalaxyView, at: SystemSelection): GalaxyNavigation = GalaxyNavigation(
        view = view,
        at = at,
        // Blank throughout, because nothing below is about search: what a query does to the worlds
        // list is `LedgerUiStateTest`'s subject, and the orbit page draws the field but has no rows
        // it narrows — its fifteen slots come from the seed rather than from the surveyed set.
        query = "",
        // The epoch, so nothing this colony has surveyed is ever "new" — a discovery card in every
        // frame would be a card no assertion could see the absence of.
        seenAt = EPOCH,
    )

    private fun GameState.homeSelection(): SystemSelection =
        SystemSelection(galaxy = galaxy.home.galaxy, system = galaxy.home.system)

    // A system nobody has looked at, which is 249 in 250 on the day the slice ships — so it is the
    // screen rather than a stage before the screen. Found rather than assumed: the neighbouring index
    // may hold nothing at all, and a system with no rows would make an unsurveyed assertion vacuous.
    private fun GameState.unsurveyedSelection(): SystemSelection {
        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            if (system == galaxy.home.system) continue
            val at = SystemSelection(galaxy = galaxy.home.galaxy, system = system)
            if (worldsOf(at).isNotEmpty()) return at
        }
        error("the home galaxy generated no system besides home with a world in it")
    }

    // The nearest thing to home the player already has a reading on. Derived rather than named,
    // because which slots a system fills is the seed's business and a hardcoded one would go quietly
    // vacuous the day genesis moves — which it did at 0.5.1.
    private fun GameState.aNeighbourOfHome(): GalaxyCoordinate =
        galaxy.surveyed.filter { it != galaxy.home }.minBy { it.slot }

    // Nothing outside the home system is surveyed at genesis and no fleet in a unit test flies, so a
    // surveyed world of a wanted verdict is injected the same way ownership is. Scans the home galaxy
    // in coordinate order, so it picks the same world every run.
    private fun firstSurveyedWorldWhere(match: (WorldVerdict) -> Boolean): Pair<GameState, GalaxyCoordinate> {
        val base = fresh()
        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            for (slot in 1..GalaxyBalance.SLOTS_PER_SYSTEM) {
                val at = GalaxyCoordinate(galaxy = base.galaxy.home.galaxy, system = system, slot = slot)
                val world = worldAt(base.galaxy.seed, at) ?: continue
                val surveyed = base.copy(galaxy = base.galaxy.copy(surveyed = base.galaxy.surveyed + at))
                if (match(verdictFor(world, surveyed))) return surveyed to at
            }
        }
        error("the home galaxy held no world matching the wanted verdict")
    }

    // A value and its unit are joined by U+00A0 so a wrap never leaves "g" alone on a line. That is a
    // rendering decision rather than a content one, so the expectations above read in ordinary spaces
    // and the non-breaking ones are normalised away here — otherwise every expected string in this
    // file would contain a character nobody can see in a diff.
    private fun String.breakable(): String = replace(' ', ' ')

    private fun fresh(): GameState = GameState.initial(GalaxySeed(20_260_807))

    private companion object {
        // Frozen, and deliberately not what any of these tests are about: the countdowns and the
        // deposit clock have test classes of their own. One instant everywhere keeps the separation
        // visible.
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
