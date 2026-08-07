package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import kotlin.test.assertEquals
import org.junit.Test

// Driven through the Robot, never through a raw node query — the shape `ResearchRobot` set.
@OptIn(ExperimentalTestApi::class)
class GalaxyScreenBehaviourTest {

    @Test
    fun `stepping forward asks for the next system`() {
        // given
        val steps = mutableListOf<Int>()

        // when
        galaxyScreen(uiState = homeSystemUiState, onStepSystem = { steps += it }) {
            stepToTheNextSystem()
        }

        // then
        assertEquals(listOf(1), steps.toList())
    }

    @Test
    fun `stepping back asks for the previous one`() {
        // given
        val steps = mutableListOf<Int>()

        // when
        galaxyScreen(uiState = homeSystemUiState, onStepSystem = { steps += it }) {
            stepToThePreviousSystem()
        }

        // then
        assertEquals(listOf(-1), steps.toList())
    }

    @Test
    fun `the first system of a galaxy has nothing to step back to`() {
        // given the edge of the map, where the back step is disabled rather than hidden
        val steps = mutableListOf<Int>()

        // when it is tapped anyway
        galaxyScreen(uiState = edgeOfTheGalaxyUiState, onStepSystem = { steps += it }) {
            stepToThePreviousSystem()
        }

        // then — clamped at the edge rather than wrapping to system 250, which would be a
        // different move than the one the button looks like
        assertEquals(emptyList<Int>(), steps.toList())
    }

    @Test
    fun `tapping a galaxy asks for that galaxy`() {
        // given
        val opened = mutableListOf<Int>()

        // when
        galaxyScreen(uiState = homeSystemUiState, onSelectGalaxy = { opened += it }) {
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
        galaxyScreen(uiState = unsurveyedSystemUiState, onGoHome = { wentHome += Unit }) {
            goHome()
        }
        assertEquals(1, wentHome.size)

        // then — on the home system it stops being a button, so the tab you open to has no
        // control on it that does nothing
        galaxyScreen(uiState = homeSystemUiState, onGoHome = { wentHome += Unit }) {
            goHome()
        }
        assertEquals(1, wentHome.size)
    }

    @Test
    fun `the home system shows its four worlds and nothing for the eleven empty slots`() {
        galaxyScreen(uiState = homeSystemUiState) {
            assertTheMapIsDrawn()
            listOf(7, 8, 10, 13).forEach { assertShowsWorld(it) }
            listOf(1, 2, 3, 4, 5, 6, 9, 11, 12, 14, 15).forEach { assertShowsNoWorld(it) }
        }
    }

    @Test
    fun `a blocked world names the axis the gap and the technology that closes it`() {
        // The design's load-bearing detail, and the only thing on this screen pointing at another
        // tab. Every empire is at level 0, so it is a promise about a purchase that does not exist.
        // The unit is cut off each expectation because the mapper joins it with a non-breaking
        // space, which is invisible in a diff and would make these read as flaky.
        galaxyScreen(uiState = homeSystemUiState) {
            assertRowReads(8, "gravity 1.78, you tolerate 1.40")
            assertRowReads(8, "Gravitic 4")
            assertRowReads(8, "temperature −40, you tolerate −30")
            assertRowReads(8, "Thermal 1")
        }
    }

    @Test
    fun `a world that fails three axes says so three times in axis order`() {
        // Temperature, gravity, pressure — the order section 1 lists them in, not the size of the
        // gap, so the third line is in the same place on every three-axis world.
        galaxyScreen(uiState = homeSystemUiState) {
            assertRowReads(13, "temperature −196")
            assertRowReads(13, "gravity 1.61")
            assertRowReads(13, "pressure 3.17")
            assertRowReads(13, "Atmospheric 1")
        }
    }

    @Test
    fun `the technology drops the word Adaptation that Research spells out`() {
        // Same object, two strings, and the reason is width: all three technologies end in the
        // same word, so it carries nothing and costs eleven characters this row does not have.
        // Scoped to the row, because "Gravitic" appears on all three of the home system's blocked
        // worlds — at three different levels, which is itself the shopping list working.
        galaxyScreen(uiState = homeSystemUiState) {
            assertRowReads(8, "Gravitic 4")
            assertRowReads(10, "Gravitic 3")
            assertNothingReads("Gravitic Adaptation")
        }
    }

    @Test
    fun `an unsurveyed world gives away nothing but its coordinate and its orbit`() {
        // The honest default: on the day this ships almost every world reads exactly this, and a
        // row that leaked a trait would have performed the survey the player has not paid for.
        // The slot comes from the fixture rather than being written down, because the fixture is
        // the real generated system — hardcoding a slot here would be asserting the seed.
        val row = unsurveyedSystemUiState.bands.flatMap { it.rows }.first()
        galaxyScreen(uiState = unsurveyedSystemUiState) {
            assertShowsWorld(row.slot)
            assertRowReads(row.slot, row.coordinate)
            assertRowReads(row.slot, "UNSURVEYED")
            assertNothingReads("you tolerate")
            assertNothingReads("yield")
            assertNothingReads("fields")
        }
    }

    @Test
    fun `Barren states the ratio and then the threshold it missed`() {
        // Barren is designed to be the common answer, so naming the threshold on the row is what
        // makes a run of them read as calibration rather than as bad luck.
        galaxyScreen(uiState = everyVerdictUiState) {
            assertRowReads(9, "yield 0.81")
            assertRowReads(9, "Passes every band, worth it at 0.92")
        }
    }

    @Test
    fun `every verdict has a word on the row`() {
        galaxyScreen(uiState = everyVerdictUiState) {
            assertRowReads(4, "HOME")
            assertRowReads(5, "OCCUPIED")
            assertRowReads(6, "UNSURVEYED")
            assertRowReads(8, "BLOCKED")
            assertRowReads(9, "BARREN")
            assertRowReads(11, "SETTLEABLE")
            assertRowReads(3, "RELAY")
        }
    }

    @Test
    fun `a relay states its effect and offers nothing to do about it`() {
        // No holding mechanic exists until multiplayer, so the row is a point of interest rather
        // than a destination — it never gets the card surface every tappable thing in the app has.
        galaxyScreen(uiState = everyVerdictUiState) {
            assertRowReads(3, "CONTESTED")
            assertRowReads(3, "+18% range while held")
        }
    }

    @Test
    fun `the orbit band is on every row because position is a trait`() {
        galaxyScreen(uiState = everyVerdictUiState) {
            assertRowReads(4, "TEMPERATE")
            assertRowReads(11, "COLD")
            // ...except the relay, which is not a world and carries no orbit: it states its effect
            // and stops.
            assertRowReads(3, "CONTESTED")
        }
        // and on a system nobody has surveyed, where it is one of the only two things known
        val row = unsurveyedSystemUiState.bands.flatMap { it.rows }.first()
        galaxyScreen(uiState = unsurveyedSystemUiState) {
            assertRowReads(row.slot, row.band.label.uppercase())
        }
    }

    @Test
    fun `nothing truncates or changes voice in a Slide Over pane`() {
        // 320dp is narrower than any phone and reachable since the app became a real iPad app. The
        // blocked card grows by a line rather than dropping one, and no string changes.
        galaxyScreen(uiState = homeSystemUiState, width = SLIDE_OVER_WIDTH) {
            assertRowReads(13, "temperature −196, you tolerate −30")
            assertRowReads(13, "Atmospheric 1")
            // The header drops the trailing noun rather than letting an ellipsis eat it: at 320dp
            // "DIM · 4 WORLDS" does not fit, and "DIM · 4 WO…" is the layout admitting defeat.
            assertReads("DIM · 4")
            assertNothingReads("DIM · 4 WORLDS")
        }
        // and it keeps the noun wherever there is room for it
        galaxyScreen(uiState = homeSystemUiState, width = PHONE_WIDTH) {
            assertReads("DIM · 4 WORLDS")
        }
    }

    @Test
    fun `a yield leaves the header at 320dp rather than being cut in half by it`() {
        // A coordinate, a verdict word, a yield and an orbit tag do not fit on one line at 320dp,
        // and the header's ellipsis landed on the *number* — "BARREN yield 0…". Abbreviation may
        // drop a noun; it may never truncate a figure, which is the one thing on the row a player
        // is comparing against another world.
        //
        // A node query cannot see an ellipsis — Compose semantics carry the whole string whatever
        // is painted — so this asserts the *structural* fix instead: at 320dp the yield is a line
        // of its own, and at 393dp it is not. The baselines are what watch the pixels.
        galaxyScreen(uiState = everyVerdictUiState, width = SLIDE_OVER_WIDTH) {
            assertRowReads(9, "yield 0.81")
            assertRowReads(9, "Passes every band, worth it at 0.92")
            assertRowReads(11, "yield 1.12")
            assertRowReads(11, "metal 1.21")
        }
        galaxyScreen(uiState = everyVerdictUiState, width = PHONE_WIDTH) {
            assertRowReads(9, "yield 0.81")
            assertRowReads(11, "yield 1.12")
        }
    }
}
