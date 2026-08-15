package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.client.galaxy.ui.BlockedAxisUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyRowUiState
import dev.fardavide.oltre.client.galaxy.ui.PHONE_WIDTH
import dev.fardavide.oltre.client.galaxy.ui.SLIDE_OVER_WIDTH
import dev.fardavide.oltre.client.galaxy.ui.WorldVerdictUiState
import dev.fardavide.oltre.client.galaxy.ui.galaxyPage
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.HostilityAxis
import dev.fardavide.oltre.core.World
import dev.fardavide.oltre.core.epithetFor
import dev.fardavide.oltre.core.worldAt
import dev.fardavide.oltre.core.worldNameAt
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test

// **The system view, which is where a player goes to acquire a reading they do not have.** The tab
// opens on the ledger since 0.11 and this is the other half of the switch: one system filling the
// screen, its strip, its map and one row per occupied slot.
//
// Driven through the Robot, never through a raw node query — the shape `ResearchRobot` set.
//
// **What it deliberately does not restore.** The file this replaces died with the model it was
// written against, and three of its subjects died with it rather than needing a new home: the orbit
// band (`hot · temperate · cold` retired when the disc took the leading position at higher
// resolution), the HOME pill (the header's second row went, and home is carried by the astronomy
// line's own words), and the per-row round trip (identical for all fifteen slots of a system, so the
// astronomy line says it once). A test for any of those would be a test that the redesign had not
// happened. The fourth is the word `Unsurveyed`, and that one is asserted *as an absence* below.
//
// Nothing here states a figure the seed produced. Names, epithets, tolerances and ladders are all
// read back out of `core` or off the frame, because the mapper's arithmetic already has a unit test
// and what this file is for is the seam after it: that what the mapper decided is what a player is
// actually shown.
@OptIn(ExperimentalTestApi::class)
class GalaxyPageBehaviourTest {

    @Test
    fun `the strip is drawn at both widths`() {
        // 250 ticks, an hour ruler and a row of named cells, drawn once per selection change. It is
        // the only thing on the screen that draws the *galaxy* rather than a system, and at 320dp it
        // trades two cells for the names rather than going away.
        galaxyPage(uiState = homeSystemUiState) { assertTheBandIsDrawn() }
        galaxyPage(uiState = homeSystemUiState, width = SLIDE_OVER_WIDTH) { assertTheBandIsDrawn() }
    }

    @Test
    fun `the cell beside the lit one is what the stepper was`() {
        // Asserted on the stateful screen rather than on a handed frame, because what a *step* is is
        // that the page is somewhere else afterwards — a callback only says the tap was heard. The
        // cell says where it goes before you go there, which is what the ±1 stepper never did.
        galaxyScreen(state = testGameState) {
            openTheMap()
            assertTheHeaderNames("${home.galaxy}:${home.system}")

            openSystem(home.system + 1)

            assertTheHeaderNames("${home.galaxy}:${home.system + 1}")
        }
    }

    @Test
    fun `tapping a galaxy asks for that galaxy`() {
        // given
        val opened = mutableListOf<Int>()

        // when
        galaxyPage(uiState = homeSystemUiState, onSelectGalaxy = { opened += it }) {
            openGalaxy(1)
            openGalaxy(4)
        }

        // then — four fixed choices, so crossing the map is one tap rather than 250
        assertEquals(listOf(1, 4), opened.toList())
    }

    @Test
    fun `the home system draws a row per occupied slot and nothing for the empty ones`() {
        // The occupied set is read off the *seed* rather than off the frame: the claim is that the
        // page draws what the generator put there, and a list taken from the same frame it is
        // asserted against could only ever agree with itself.
        val occupied = frameState.worldsOf(frameState.homeSelection()).map { it.at }
        val empty = (1..GalaxyBalance.SLOTS_PER_SYSTEM).map { homeAt(it) } - occupied.toSet()
        // Both halves are really in the sample, or the loops below prove nothing.
        assertTrue(occupied.isNotEmpty(), "genesis puts the colony in a system with worlds in it")
        assertTrue(empty.isNotEmpty(), "no system fills all ${GalaxyBalance.SLOTS_PER_SYSTEM} slots")

        galaxyPage(uiState = homeSystemUiState) {
            assertTheMapIsDrawn()
            occupied.forEach { assertShowsWorld(it) }
            empty.forEach { assertShowsNoWorld(it) }
        }
    }

    @Test
    fun `a row leads with the world's name`() {
        // **The slice in one assertion.** Until 0.11 the first column of this list was `[3:171:1]`,
        // and the design rejected that outright — *"a list is scanned down its first column"*, and an
        // index is not a place. The order asserted here is the whole header block: the name, then the
        // verdict word, then the epithet and the address demoted onto the line under it.
        val name = worldNameAt(frameState.galaxy.seed, blocked.at)

        galaxyPage(uiState = homeSystemUiState) {
            assertTheRowReadsInOrder(
                blocked.at,
                name,
                WorldVerdictUiState.BLOCKED.word.orEmpty().uppercase(),
                epithetFor(world(blocked.at).traits).toString(),
                blocked.coordinate,
            )
        }
        // ...and on a row nobody has surveyed, where the name is the *only* thing in the leading
        // position: there is no epithet to demote the address, so it trails the name instead.
        galaxyPage(uiState = unsurveyedSystemUiState) {
            assertTheRowReadsInOrder(
                unsurveyed.at,
                worldNameAt(frameState.galaxy.seed, unsurveyed.at),
                unsurveyed.coordinate,
            )
        }
    }

    @Test
    fun `a blocked row names the axis the gap and the technology that closes it`() {
        // The design's load-bearing detail, and the only thing on this screen pointing at another
        // tab. Every empire is at level 0 on the frame this runs against, so each of these is a
        // promise about a purchase that does not exist yet.
        galaxyPage(uiState = homeSystemUiState) {
            blocked.requirements.forEach { requirement ->
                assertRowReads(blocked.at, requirement.clause())
                assertRowReads(blocked.at, requirement.label)
            }
        }
    }

    @Test
    fun `a world that fails three axes says so three times in axis order`() {
        // Temperature, gravity, pressure — `HostilityAxis`'s own order rather than the size of the
        // gap, so the third line is in the same place on every three-axis world. The expectation is
        // built from the enum and not from the row: reading the order off the thing under test is how
        // an order test passes while reversed.
        val inAxisOrder = HostilityAxis.entries.map { axis ->
            threeAxis.requirements.first { it.axis == axis.name.lowercase() }.clause()
        }

        galaxyPage(uiState = homeSystemUiState) {
            assertTheRowReadsInOrder(threeAxis.at, *inAxisOrder.toTypedArray())
        }
    }

    @Test
    fun `the technology drops the word Adaptation that Research spells out`() {
        // Same object, two strings, and the reason is width: all three technologies end in the same
        // word, so it carries nothing and costs eleven characters this row does not have. Asserted
        // across the whole screen, because the row is not the only place a ladder could be named.
        galaxyPage(uiState = homeSystemUiState) {
            assertRowReads(blocked.at, blocked.requirements.first().label)
            assertNothingReads("Adaptation")
        }
    }

    @Test
    fun `tapping a blocked row's remedy asks for the tab that sells it`() {
        // The other half of the accent rule: the remedy is accent *and* tappable, or neither. An
        // accent string that is not a target breaks the colour rule harder than demoting it would.
        var opened = 0

        galaxyPage(uiState = homeSystemUiState, onOpenResearch = { opened++ }) {
            tapTheRemedy(at = blocked.at, technology = blocked.requirements.first().technology)
        }

        assertEquals(1, opened)
    }

    @Test
    fun `tapping the rest of a blocked row asks for nothing`() {
        // The target is the string and not the row: the row belongs to the world — send a hold now,
        // settle it later — so a whole-row deep link into Research would take that away from what the
        // player usually wants. What the rest of the row *does* do is `DispatchSheetBehaviourTest`'s.
        var opened = 0

        galaxyPage(uiState = homeSystemUiState, onOpenResearch = { opened++ }) {
            tapTheWorld(blocked.at)
        }

        assertEquals(0, opened)
    }

    @Test
    fun `an unsurveyed row gives away nothing it has not paid for`() {
        // The honest default: on the day this ships almost every row in the galaxy reads exactly
        // this, and a row that leaked one of the readings below would have performed a survey the
        // player never bought. The name and the address are not among them — both are the seed's and
        // free from the first launch, which is what makes a probe a purchase rather than a paywall.
        galaxyPage(uiState = unsurveyedSystemUiState) {
            assertRowReads(unsurveyed.at, worldNameAt(frameState.galaxy.seed, unsurveyed.at))
            assertRowReads(unsurveyed.at, unsurveyed.coordinate)
            assertTheRowDoesNotRead(unsurveyed.at, "you tolerate")
            assertTheRowDoesNotRead(unsurveyed.at, "full")
            assertTheRowDoesNotRead(unsurveyed.at, "Yield")
        }
    }

    @Test
    fun `a surveyed row carries an epithet and an unsurveyed one carries none`() {
        // The epithet and the disc are one permission — both are readouts of the same three traits —
        // and this is the word half of it. **The picture half cannot be asserted here at all**: a
        // portrait is a `Canvas` and carries no semantics, so what an empty socket looks like beside
        // a filled one is the screenshot baselines' claim and `GalaxyUiStateTest`'s.
        //
        // Derived from `core` rather than read off the frame, so what has to reach the screen is the
        // generator's epithet rather than whatever the mapper felt like putting there.
        galaxyPage(uiState = homeSystemUiState) {
            assertRowReads(blocked.at, epithetFor(world(blocked.at).traits).toString())
        }
        galaxyPage(uiState = unsurveyedSystemUiState) {
            // The row is really on the screen, or the absence below is the absence of a row.
            assertRowReads(unsurveyed.at, unsurveyed.coordinate)
            assertNothingReads(epithetFor(world(unsurveyed.at).traits).toString())
        }
    }

    @Test
    fun `the word Unsurveyed is nowhere on the screen`() {
        // **The design's one subtraction, and the thing most likely to be quietly restored.** An
        // empty socket where every surveyed row has a body states it in the position where the state
        // belongs, and it buys back a colour, a ten-character reading and the right end of 98% of
        // rows. The enum keeps the case with a null word so the decision stays arguable — which is
        // exactly what makes putting the word back a one-line change nobody would notice.
        //
        // Asserted on the system where *every* row is in that state, and with a row asserted present
        // first, because an empty screen would satisfy all three of these.
        galaxyPage(uiState = unsurveyedSystemUiState) {
            assertShowsWorld(unsurveyed.at)
            assertNothingReads("UNSURVEYED")
            assertNothingReads("Unsurveyed")
            assertNothingReads("unsurveyed")
        }
    }

    @Test
    fun `Barren states the threshold it missed without restating its own verdict`() {
        // Barren is designed to be the common answer, so naming the threshold on the row is what
        // makes a run of them read as calibration rather than as bad luck. The verdict is said once —
        // the badge carries it — so the line underneath opens on the yield rather than on the word.
        galaxyPage(uiState = homeSystemUiState) {
            assertRowReads(barren.at, "BARREN")
            assertRowReads(barren.at, WORTH_IT_AT)
            assertTheRowDoesNotRead(barren.at, "Barren ")
        }
    }

    @Test
    fun `a relay states its effect and offers nothing to do about it`() {
        // No holding mechanic exists until multiplayer, so the row is a point of interest rather than
        // a destination: it never gets the card surface every tappable thing in the app has, and it
        // carries no deposit reading either — a relay has no hold for a fleet to fill. That it also
        // raises no sheet is `DispatchSheetBehaviourTest`'s assertion.
        galaxyPage(uiState = relaySystemUiState) {
            assertRowReads(relayCoordinate, "RELAY")
            assertRowReads(relayCoordinate, RELAY_EFFECT)
            assertTheRowDoesNotRead(relayCoordinate, "metal")
        }
    }

    @Test
    fun `only a legal target carries a deposit reading`() {
        // Present exactly where a run is legal, which is not a coincidence: a hold cannot be priced
        // from a world nobody has looked at, and a run at your own world is refused outright — so the
        // pair of readings *is* the offer. `metal full` and not `metal left`: with no noun the row
        // asserts nothing about who took what, which is what lets "full" be honest on the ~98% of
        // worlds nobody has ever worked.
        galaxyPage(uiState = homeSystemUiState) {
            assertRowReads(blocked.at, "metal full")
            assertRowReads(barren.at, "metal full")
            assertTheRowDoesNotRead(ownWorld.at, "metal")
        }
        galaxyPage(uiState = unsurveyedSystemUiState) {
            assertTheRowDoesNotRead(unsurveyed.at, "metal")
        }
    }

    @Test
    fun `nothing truncates or changes voice in a Slide Over pane`() {
        // 320dp is narrower than any phone and reachable since the app became a real iPad app. **What
        // a narrow pane costs is a line of height and never a reading** — the clause wraps, the
        // deposit pair wraps under it, and the header's detail keeps its noun.
        //
        // This is the one place 0.11 *reversed* 0.9 rather than carrying it over: the old row dropped
        // `crystal` outright at 320dp to keep the pair on one line, and the row that replaced it grows
        // instead. Both widths assert the same strings, which is the whole claim.
        val requirement = blocked.requirements.first()
        val detail = assertIs<GalaxyBodyUiState.System>(homeSystemUiState.body).header.detail.uppercase()

        listOf(SLIDE_OVER_WIDTH, PHONE_WIDTH).forEach { width ->
            galaxyPage(uiState = homeSystemUiState, width = width) {
                assertRowReads(blocked.at, requirement.clause())
                assertRowReads(blocked.at, requirement.label)
                assertRowReads(blocked.at, "metal full")
                assertRowReads(blocked.at, "crystal full")
                assertReads(detail)
            }
        }
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────
    //
    // Which row a test is about is found in the frame rather than written down: which slots a system
    // fills and what each world turned out to be are the seed's business, and a hardcoded slot goes
    // quietly vacuous the day genesis moves — which it did at 0.5.1.

    private fun homeAt(slot: Int): GalaxyCoordinate =
        GalaxyCoordinate(galaxy = home.galaxy, system = home.system, slot = slot)

    // One helper for both systems since a row carries its whole address: what a test needs from the
    // seed is the world at a coordinate, and which system that coordinate is in is the row's business.
    private fun world(at: GalaxyCoordinate): World = checkNotNull(worldAt(frameState.galaxy.seed, at))

    // What one requirement line prints. Composed from the parts rather than typed out, because the
    // space before each unit is U+00A0 — invisible in a diff, and a typed expectation would read as
    // flaky rather than as wrong.
    private fun BlockedAxisUiState.clause(): String = "$axis $reading, you tolerate $tolerated"

    private companion object {

        val home: GalaxyCoordinate = frameState.galaxy.home

        val homeRows: List<GalaxyRowUiState.World> = assertIs<GalaxyBodyUiState.System>(homeSystemUiState.body)
            .rows.filterIsInstance<GalaxyRowUiState.World>()

        // Genesis surveys the home system whole, so its blocked worlds are rows every player of this
        // colony meets on their first launch rather than rows a fixture had to arrange.
        val blocked: GalaxyRowUiState.World = homeRows.first { it.requirements.isNotEmpty() }
        val threeAxis: GalaxyRowUiState.World = homeRows.first { it.requirements.size == 3 }
        val barren: GalaxyRowUiState.World = homeRows.first { it.verdict == WorldVerdictUiState.BARREN }
        val ownWorld: GalaxyRowUiState.World = homeRows.first { it.verdict == WorldVerdictUiState.HOME }

        // The first world of the untouched neighbour — 249 systems in 250 look like this one.
        val unsurveyed: GalaxyRowUiState.World =
            assertIs<GalaxyBodyUiState.System>(unsurveyedSystemUiState.body)
                .rows.filterIsInstance<GalaxyRowUiState.World>().first()
    }
}
