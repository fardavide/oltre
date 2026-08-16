package dev.fardavide.oltre.client.fleets.ui

import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.core.GalaxyCoordinate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

// What the tab shows, driven rather than photographed. The screen has no controls, so what these
// assert is that several runs really are several cards — which is the whole of what the Colony
// strip's `2 more away` has been pointing at since 0.7.0.
@OptIn(ExperimentalTestApi::class)
class FleetsScreenBehaviourTest {

    @Test
    fun `several runs in flight are several cards`() {
        fleets(uiState = threeRunsUiState) {
            assertShowsRun(0)
            assertShowsRun(1)
            assertShowsRun(2)
            assertHasNoRun(3)
        }
    }

    @Test
    fun `each card names its target and what it is bringing back`() {
        fleets(uiState = threeRunsUiState) {
            assertRunReads(0, "[3:171:13]")
            assertRunReads(0, "52 crystal")
            assertRunReads(1, "3 skiffs")
        }
    }

    @Test
    fun `each card carries its three legs in the order they happen`() {
        fleets(uiState = threeRunsUiState) {
            assertRunReads(2, "out 10m · on station 2h 40m · home 10m")
        }
    }

    @Test
    fun `the heading counts what is away against what is owned`() {
        fleets(uiState = threeRunsUiState) {
            assertReads("5 of 6 away")
        }
    }

    @Test
    fun `where you have been is a list of worlds under its own heading`() {
        // **The fold, at the screen.** Eleven runs are five rows, each naming a world and totalling
        // what it has paid — which is the claim the per-run ledger could not make.
        fleets(uiState = threeRunsUiState) {
            assertReads("WORLDS WORKED")
            assertReads("11 runs · newest first")
            assertWorkedReads(tashkir, "Tashkir IV")
            assertWorkedReads(tashkir, "1,176 crystal")
            assertWorkedReads(tashkir, "2 runs")
        }
    }

    @Test
    fun `a finished vein is the one reading that says a door leads nowhere`() {
        fleets(uiState = threeRunsUiState) {
            assertWorkedReads(finished, "empty")
        }
    }

    @Test
    fun `a landing with no world is a line at the foot and not a row`() {
        fleets(uiState = threeRunsUiState) {
            assertTheUnrecordedLineReads("3 earlier runs · 402 metal · no target recorded")
        }
    }

    @Test
    fun `tapping a world asks for that world and nothing else`() {
        val opened = mutableListOf<GalaxyCoordinate>()

        fleets(uiState = threeRunsUiState, onOpenWorld = { opened += it }) {
            tapTheWorld(tashkir)
        }

        assertEquals(listOf(tashkir), opened.toList())
    }

    @Test
    fun `the line with no world opens nothing`() {
        // A landing with no target is not a world, so in a list of worlds it is not a door — and the
        // missing disc is what says so.
        val opened = mutableListOf<GalaxyCoordinate>()

        fleets(uiState = threeRunsUiState, onOpenWorld = { opened += it }) {
            tapTheUnrecordedLine()
        }

        assertTrue(opened.isEmpty())
    }

    @Test
    fun `a colony nothing has ever come back to has no ledger rather than an empty one`() {
        // A heading over nothing is a section claiming there is a history when there is not.
        fleets(uiState = firstRunUiState) {
            assertShowsRun(0)
            assertNothingReads("WORLDS WORKED")
        }
    }

    @Test
    fun `a colony with nothing out says so rather than showing an empty list`() {
        fleets(uiState = nothingOutUiState) {
            assertHasNoRun(0)
            assertReads("Nothing is out.")
        }
    }

    @Test
    fun `three runs survive a Slide Over window`() {
        fleets(uiState = threeRunsUiState, width = SLIDE_OVER_WIDTH) {
            assertShowsRun(0)
            assertShowsRun(2)
            assertReads("5 of 6 away")
        }
    }

    private companion object {
        // The two worlds `day21` is read down to: the one that landed while you were away, and the
        // one whose crystal is finished. Named off the frame rather than written twice.
        val tashkir: GalaxyCoordinate = day21.rows.first().at
        val finished: GalaxyCoordinate = day21.rows.first { it.depositIsEmpty }.at
    }
}
