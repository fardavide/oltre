package dev.fardavide.oltre.client.fleets.ui

import androidx.compose.ui.test.ExperimentalTestApi
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
    fun `what has landed is listed under its own heading`() {
        fleets(uiState = threeRunsUiState) {
            assertReads("LANDED")
            assertLandingReads(0, "+588 crystal")
            assertLandingReads(3, "+132 metal")
            assertHasNoLanding(4)
        }
    }

    @Test
    fun `a colony nothing has ever come back to has no ledger rather than an empty one`() {
        // A heading over nothing is a section claiming there is a history when there is not.
        fleets(uiState = firstRunUiState) {
            assertShowsRun(0)
            assertNothingReads("LANDED")
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
}
