package dev.fardavide.oltre.client.colony.ui

import dev.fardavide.oltre.client.design.component.VerdictUiState
import dev.fardavide.oltre.core.BuildingType
import kotlin.test.assertEquals
import org.junit.Test

// What the second half of a row does, from the outside. The verdict is one clause and the sheet is
// the rest of the sentence, so what a screen test can say is exactly that: the card opens it, it
// repeats what the card said, and the decision can be taken without going back.
class FacilitySheetBehaviourTest {

    @Test
    fun `tapping the card opens the sheet`() {
        colonyScreen(rows = listOf(roboticsFacilityRow)) {
            assertTheSheetIsClosed()

            tapTheCardOn(BuildingType.ROBOTICS_FACTORY)

            assertTheSheetIsOpen()
        }
    }

    // The whole reason a row nobody can act on is worth a tap: the Nanite Factory's argument is
    // stated on day one, and day one is when the card is 42% dim and has no button at all.
    @Test
    fun `a locked card opens its sheet too`() {
        colonyScreen(rows = listOf(testColonyUiState.facilities.last())) {
            tapTheCardOn(BuildingType.NANITE_FACTORY)

            assertTheSheetIsOpen()
        }
    }

    @Test
    fun `the sheet repeats what the row said and then says the rest`() {
        facilitySheet(roboticsFacilityRow) {
            // the clause a narrow row keeps
            assertTheSheetReads("−20m per build")
            // the arithmetic behind it, which the row has nowhere to put
            assertTheSheetReads("Your next Metal Mine takes 1h 37m")
            // and the clause the row dropped, said in full
            assertTheSheetReads("Nanite Factory · 2,000 metal")
        }
    }

    // A verdict of "nothing" is the honest reading and the least useful one, so the sheet it opens
    // ends on the row to read instead.
    @Test
    fun `a row that buys nothing points at the row that buys the most`() {
        facilitySheet(inertPlantFacilityRow) {
            assertTheSheetReads("Metal Mine")
            assertTheSheetReads("LV 5 · back in 2h 06m")
        }
    }

    @Test
    fun `the decision can be taken from inside the sheet`() {
        var acts = 0

        facilitySheet(roboticsFacilityRow, onAct = { acts++ }) {
            tapTheSheetAction()
        }

        assertEquals(1, acts)
    }

    // No disabled state here either: a row still filling its stores offers the wait rather than a
    // dead button, exactly as the card does.
    @Test
    fun `a row still filling its stores offers the wait rather than an action`() {
        var acts = 0

        facilitySheet(inertPlantFacilityRow, onAct = { acts++ }) {
            tapTheSheetAction()
        }

        assertEquals(0, acts)
    }

    @Test
    fun `a row in flight draws no verdict even when it is handed one`() {
        // given the running row from the shared colony, told a verdict it must not print
        val running = testColonyUiState.facilities.first().copy(
            verdict = VerdictUiState(label = "+281/h metal · back in 6h 40m", compactLabel = "+281/h metal"),
        )

        // then the slot belongs to the arrow and the countdown: nobody is choosing on this row
        facilityRow(running) {
            assertReads("→ LV 13 · done 11:23")
            assertNothingReads("+281/h metal")
        }
    }

    @Test
    fun `a narrow window drops the second clause rather than cutting it`() {
        facilityRow(roboticsFacilityRow, compact = true) {
            assertReads("−20m per build")
            assertNothingReads("LV 10 → Nanite")
        }
    }

    @Test
    fun `a phone-wide window says both clauses`() {
        facilityRow(roboticsFacilityRow) {
            assertReads("−20m per build · LV 10 → Nanite")
        }
    }
}
