package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.core.AdaptationTechnology
import kotlin.test.assertEquals
import org.junit.Test

// Driven through the Robot, never through a raw node query — the shape `ResearchRobot` set.
@OptIn(ExperimentalTestApi::class)
class GalaxyPageBehaviourTest {

    @Test
    fun `tapping the cell beside the lit one is what the stepper was`() {
        // given the neighbouring system, which is still one tap — what it stops being is the
        // *only* way across, and what it gains is saying where it goes before you go there
        val opened = mutableListOf<Int>()

        // when
        galaxyPage(uiState = homeSystemUiState, onSelectSystem = { opened += it }) {
            val lit = homeSystemUiState.reach.lens.cells.first { it.selected }.system
            openSystem(lit + 1)
        }

        // then
        assertEquals(listOf(homeSystemUiState.reach.lens.cells.first { it.selected }.system + 1), opened.toList())
    }

    @Test
    fun `the lens reaches further than one system in either direction`() {
        // The whole of question 2 in one assertion: the band's cells are three systems out on each
        // side, so crossing a neighbourhood is a tap rather than three of them.
        val opened = mutableListOf<Int>()

        galaxyPage(uiState = homeSystemUiState, onSelectSystem = { opened += it }) {
            val cells = homeSystemUiState.reach.lens.cells.map { it.system }
            openSystem(cells.first())
            openSystem(cells.last())
        }

        val cells = homeSystemUiState.reach.lens.cells.map { it.system }
        assertEquals(listOf(cells.first(), cells.last()), opened.toList())
    }

    @Test
    fun `the strip is on the screen at both widths`() {
        // 250 ticks and four labels, drawn once per selection change. It is the only thing on the
        // screen that draws the galaxy rather than a system.
        galaxyPage(uiState = homeSystemUiState) { assertTheBandIsDrawn() }
        galaxyPage(uiState = homeSystemUiState, width = SLIDE_OVER_WIDTH) { assertTheBandIsDrawn() }
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
    fun `Home is an action away from home and a label once you are there`() {
        // given
        val wentHome = mutableListOf<Unit>()

        // when the player is somewhere else
        galaxyPage(uiState = unsurveyedSystemUiState, onGoHome = { wentHome += Unit }) {
            goHome()
        }
        assertEquals(1, wentHome.size)

        // then — on the home system it stops being a button, so the tab you open to has no
        // control on it that does nothing
        galaxyPage(uiState = homeSystemUiState, onGoHome = { wentHome += Unit }) {
            goHome()
        }
        assertEquals(1, wentHome.size)
    }

    @Test
    fun `the home system shows its seven worlds and nothing for the eight empty slots`() {
        galaxyPage(uiState = homeSystemUiState) {
            assertTheMapIsDrawn()
            listOf(1, 2, 4, 7, 8, 10, 11).forEach { assertShowsWorld(it) }
            listOf(3, 5, 6, 9, 12, 13, 14, 15).forEach { assertShowsNoWorld(it) }
        }
    }

    @Test
    fun `a blocked world names the axis the gap and the technology that closes it`() {
        // The design's load-bearing detail, and the only thing on this screen pointing at another
        // tab. Every empire is at level 0, so it is a promise about a purchase that does not exist.
        // The unit is cut off each expectation because the mapper joins it with a non-breaking
        // space, which is invisible in a diff and would make these read as flaky.
        galaxyPage(uiState = homeSystemUiState) {
            assertRowReads(11, "gravity 1.48, you tolerate 1.40")
            assertRowReads(11, "Gravitic 1")
            assertRowReads(11, "temperature −141, you tolerate −30")
            assertRowReads(11, "Thermal 8")
        }
    }

    @Test
    fun `a world that fails three axes says so three times in axis order`() {
        // Temperature, gravity, pressure — the order section 1 lists them in, not the size of the
        // gap, so the third line is in the same place on every three-axis world.
        galaxyPage(uiState = homeSystemUiState) {
            assertRowReads(1, "temperature +135")
            assertRowReads(1, "gravity 1.53")
            assertRowReads(1, "pressure 5.23")
            assertRowReads(1, "Atmospheric 3")
        }
    }

    @Test
    fun `the technology drops the word Adaptation that Research spells out`() {
        // Same object, two strings, and the reason is width: all three technologies end in the
        // same word, so it carries nothing and costs eleven characters this row does not have.
        // Scoped to the row, because "Gravitic" appears on all five of the home system's blocked
        // worlds — at four different levels, which is itself the shopping list working.
        galaxyPage(uiState = homeSystemUiState) {
            assertRowReads(1, "Gravitic 2")
            assertRowReads(2, "Gravitic 7")
            assertNothingReads("Gravitic Adaptation")
        }
    }

    @Test
    fun `Blocked keeps its badge again and says what is in the ground underneath it`() {
        // **Treatment 1b, revised by Claude Design at 0.9 rather than undone.** 1b's rule was *a row
        // leads with what you can do about it today*, and it gave this header to the richness pair
        // because the verdict was not an offer. What you can do is still send a hold — but the
        // numbers that price one are now the *stocks*, and two labelled fractions do not fit a slot
        // that cannot wrap. So they moved to a line of their own, the header took the verdict word
        // back, and the row went from two shapes to one.
        //
        // The two absences 1b bought are still pinned: no yield, no "Fails 2 of 3 bands".
        galaxyPage(uiState = homeSystemUiState) {
            assertRowReads(11, "BLOCKED")
            assertRowReads(11, "metal full")
            assertRowReads(11, "temperature")
            assertNothingReads("Blocked · temperature")
            assertNothingReads("yield 1.06")
            assertNothingReads("Fails 2 of 3 bands")
        }
    }

    @Test
    fun `a row states the round trip and its own hazards and never the danger total`() {
        // The row's half of the split. The distance band is astronomy — identical for all fifteen
        // slots — so it is stated once under the header; the hazards need a survey, so they sit on
        // the row carrying their own arithmetic. Neither ever prints the sum, because a row that did
        // could not say which half it came from.
        galaxyPage(uiState = homeSystemUiState) {
            assertRowReads(11, "24m out and back")
            assertRowReads(11, "no hazards")
            assertTheAstronomyReads("Your own system")
            // "from here" is dropped exactly where a *range* of round trips appears, which is the
            // home system and nowhere else: with it the line is 359dp against the 361dp the column
            // has, and the one screen every player opens on wrapped to two lines by two dp.
            assertTheAstronomyReads("danger 0 · 20")
            assertTheAstronomyReads("out and back")
        }
        // and away from home the same line prices the trip in the unit distance is measured in
        galaxyPage(uiState = unsurveyedSystemUiState) {
            assertTheAstronomyReads("units out")
            assertTheAstronomyReads("danger 1 from here")
        }
    }

    // The inverse of the test this replaces. 0.0.16 put a PLACEHOLDER line in the header saying the
    // ladders were unbuilt; 0.0.18 builds them, so the line is deleted rather than reworded — it
    // accounted for an absence, and nothing succeeds an absence that has ended. Pinned as an
    // absence because a header that quietly grew the sentence back would be the screen lying again.
    @Test
    fun `the header no longer excuses a branch that now exists`() {
        galaxyPage(uiState = homeSystemUiState) {
            assertNothingReads("Adaptation research lands later.")
            assertNothingReads("You are at level 0.")
        }
    }

    // The other half of the same decision: the remedy is accent and tappable, or neither. An accent
    // string that is not a target breaks the colour rule harder than 0.0.16's demotion did.
    @Test
    fun `tapping a blocked row's remedy asks for the tab that sells it`() {
        var opened = 0

        galaxyPage(uiState = homeSystemUiState, onOpenResearch = { opened++ }) {
            tapTheRemedy(slot = 11, technology = AdaptationTechnology.GRAVITIC)
        }

        assertEquals(1, opened)
    }

    // The target is the string, not the row: the row belongs to the world — survey now, claim
    // later — so a whole-row deep link would take that away from what the player usually wants.
    @Test
    fun `tapping the rest of a blocked row asks for nothing`() {
        var opened = 0

        galaxyPage(uiState = homeSystemUiState, onOpenResearch = { opened++ }) {
            tapTheWorld(slot = 11)
        }

        assertEquals(0, opened)
    }

    @Test
    fun `an unsurveyed world gives away nothing but its coordinate and its orbit`() {
        // The honest default: on the day this ships almost every world reads exactly this, and a
        // row that leaked a trait would have performed the survey the player has not paid for.
        // The slot comes from the fixture rather than being written down, because the fixture is
        // the real generated system — hardcoding a slot here would be asserting the seed.
        val row = unsurveyedSystemUiState.bands.flatMap { it.rows }.first()
        galaxyPage(uiState = unsurveyedSystemUiState) {
            assertShowsWorld(row.slot)
            assertRowReads(row.slot, row.coordinate)
            assertRowReads(row.slot, "UNSURVEYED")
            assertNothingReads("you tolerate")
            assertNothingReads("yield")
            assertNothingReads("fields")
        }
    }

    @Test
    fun `Barren states the threshold it missed without restating its own verdict`() {
        // Barren is designed to be the common answer, so naming the threshold on the row is what
        // makes a run of them read as calibration rather than as bad luck. **The verdict clause
        // retired at 0.9**: the header carries the word again, so an opening "Barren · " would be
        // the row saying it twice.
        galaxyPage(uiState = everyVerdictUiState) {
            assertRowReads(9, "BARREN")
            assertRowReads(9, "yield 0.81, worth it at 0.92")
            assertNothingReads("Barren · yield")
        }
    }

    @Test
    fun `every verdict carries its badge and only a legal target carries a deposit`() {
        // **One row shape, six verdicts** — Design's Decision 1, and the reason `Settleable` stopped
        // needing a special case. The badge is back on all of them; what varies is the line under it,
        // which is present exactly where a run is legal. Absent on Unsurveyed, because a hold cannot
        // be priced from a world nobody has looked at, and on Home and Occupied, because a run there
        // is refused outright.
        galaxyPage(uiState = everyVerdictUiState) {
            assertRowReads(4, "HOME")
            assertRowReads(5, "OCCUPIED")
            assertRowReads(6, "UNSURVEYED")
            assertRowReads(8, "BLOCKED")
            assertRowReads(9, "BARREN")
            assertRowReads(11, "SETTLEABLE")
            assertRowReads(3, "RELAY")
            assertRowReads(8, "metal full")
            assertRowReads(9, "metal full")
            assertRowReads(11, "metal full")
        }
    }

    @Test
    fun `a relay states its effect and offers nothing to do about it`() {
        // No holding mechanic exists until multiplayer, so the row is a point of interest rather
        // than a destination — it never gets the card surface every tappable thing in the app has.
        galaxyPage(uiState = everyVerdictUiState) {
            assertRowReads(3, "CONTESTED")
            assertRowReads(3, "+18% range while held")
        }
    }

    @Test
    fun `the orbit band is on every row because position is a trait`() {
        galaxyPage(uiState = everyVerdictUiState) {
            assertRowReads(4, "TEMPERATE")
            assertRowReads(11, "COLD")
            // ...except the relay, which is not a world and carries no orbit: it states its effect
            // and stops.
            assertRowReads(3, "CONTESTED")
        }
        // and on a system nobody has surveyed, where it is one of the only two things known
        val row = unsurveyedSystemUiState.bands.flatMap { it.rows }.first()
        galaxyPage(uiState = unsurveyedSystemUiState) {
            assertRowReads(row.slot, row.band.label.uppercase())
        }
    }

    @Test
    fun `nothing truncates or changes voice in a Slide Over pane`() {
        // 320dp is narrower than any phone and reachable since the app became a real iPad app. The
        // blocked card grows by a line rather than dropping one, and no string changes.
        galaxyPage(uiState = homeSystemUiState, width = SLIDE_OVER_WIDTH) {
            assertRowReads(1, "temperature +135, you tolerate +45")
            assertRowReads(1, "Atmospheric 3")
            // The header drops the trailing noun rather than letting an ellipsis eat it: at 320dp
            // "DIM · 7 WORLDS" does not fit, and "DIM · 7 WO…" is the layout admitting defeat.
            assertReads("DIM · 7")
            assertNothingReads("DIM · 7 WORLDS")
        }
        // and it keeps the noun wherever there is room for it
        galaxyPage(uiState = homeSystemUiState, width = PHONE_WIDTH) {
            assertReads("DIM · 7 WORLDS")
        }
    }

    @Test
    fun `the dispatch button aims at the system on screen`() {
        // The page *is* the target — a probe is aimed at the star the screen is about, which is why
        // the footer needs no target picker and no world row carries a button.
        var dispatched = 0

        galaxyPage(uiState = unsurveyedSystemUiState, onDispatchProbe = { dispatched++ }) {
            dispatchAProbe()
        }

        assertEquals(1, dispatched)
    }

    @Test
    fun `the screen never offers a flight the model would refuse`() {
        // Four of the six states are sentences rather than controls, and this is what says the
        // screen and `startSurvey` agree about which. A card that offered a dispatch it could not
        // honour is the worst failure this footer has available to it.
        galaxyPage(uiState = homeSystemUiState) { assertOffersNoFlight() }
        galaxyPage(uiState = probeInFlightUiState) { assertOffersNoFlight() }
        galaxyPage(uiState = probeLandedUiState) { assertOffersNoFlight() }
        galaxyPage(uiState = probeNothingToSurveyUiState) { assertOffersNoFlight() }
    }

    @Test
    fun `an unaffordable dispatch is a ghost carrying the wait rather than a dead button`() {
        // The committed idiom, and the tightest reading on the screen: two durations share a row
        // and only one of them has a preposition.
        var dispatched = 0

        galaxyPage(uiState = probeUnaffordableUiState, onDispatchProbe = { dispatched++ }) {
            assertTheFooterReads("in 1h 06m")
            dispatchAProbe()
        }

        assertEquals(0, dispatched, "a ghost is a reading, not a control")
    }

    @Test
    fun `home says it was surveyed at genesis and never that a probe went there`() {
        galaxyPage(uiState = homeSystemUiState) {
            assertTheFooterReads("Surveyed at genesis")
            assertNothingReads("Probe landed")
        }
    }

    @Test
    fun `a star with nothing around it says so and never says already surveyed`() {
        // One system in 390. `hasSurveyed` is vacuously true where there is nothing to survey, so
        // this is the state most at risk of claiming a flight happened that never did.
        galaxyPage(uiState = probeNothingToSurveyUiState) {
            assertTheFooterReads("nothing to survey")
            assertNothingReads("Surveyed at genesis")
            assertNothingReads("Probe landed")
        }
    }

    @Test
    fun `a landing states the count and what cleared the bar in one breath`() {
        // Saying "none settleable" beside the count is what keeps a run of these reading as
        // calibration rather than as bad luck — the job the Barren row's threshold already does.
        galaxyPage(uiState = probeLandedUiState) {
            assertTheFooterReads("Probe landed 12:20")
            assertTheFooterReads("5 worlds surveyed")
            assertTheFooterReads("none settleable")
        }
        galaxyPage(uiState = probeSettleableUiState) { assertTheFooterReads("1 settleable") }
        galaxyPage(uiState = probeNearMissUiState) { assertTheFooterReads("1 blocked at one axis") }
    }

    @Test
    fun `a probe in flight counts down where the thing it is doing lives`() {
        galaxyPage(uiState = probeInFlightUiState) {
            assertTheFooterReads("00:47:12")
            assertTheFooterReads("lands 12:20")
        }
    }

    @Test
    fun `the footer drops two words at 320dp and neither of its figures`() {
        // "metal" off the chip, "flight" off the duration, "probe" off the button. 150 stays and
        // the flight stays — the colour does the work the word did.
        galaxyPage(uiState = unsurveyedSystemUiState, width = SLIDE_OVER_WIDTH) {
            assertNothingReads("Dispatch probe")
            assertReads("Dispatch")
            assertNothingReads("flight ")
        }
        galaxyPage(uiState = unsurveyedSystemUiState, width = PHONE_WIDTH) {
            assertReads("Dispatch probe")
            assertReads("flight ")
        }
    }

    @Test
    fun `the second deposit leaves the row at 320dp rather than being cut by it`() {
        // The same rule the richness pair used to spend, now spent on the pair that replaced it: an
        // ellipsis would land on a *figure*, which is the one thing on this row a player compares
        // against another world. Abbreviation may drop a noun; it may never truncate a number, so
        // what goes is the whole of the second deposit. Both are still on the sheet the row raises.
        //
        // A node query cannot see an ellipsis — Compose semantics carry the whole string whatever is
        // painted — so this asserts the *structural* fix instead. The baselines watch the pixels.
        galaxyPage(uiState = everyVerdictUiState, width = SLIDE_OVER_WIDTH) {
            assertRowReads(9, "metal full")
            assertNothingReads("crystal full")
            assertRowReads(9, "yield 0.81, worth it at 0.92")
        }
        galaxyPage(uiState = everyVerdictUiState, width = PHONE_WIDTH) {
            assertRowReads(9, "metal full")
            assertRowReads(9, "crystal full")
        }
    }
}
