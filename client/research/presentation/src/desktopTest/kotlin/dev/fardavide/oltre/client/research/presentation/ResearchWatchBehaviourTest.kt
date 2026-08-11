package dev.fardavide.oltre.client.research.presentation

import dev.fardavide.oltre.client.design.component.WatchUiState
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.Technology
import org.junit.Test
import kotlin.test.assertEquals

// The watch on the screen that shares it. The slot is one, so the interesting facts here are about
// the heading rather than about a row: it names the watched thing even when the watched thing is a
// facility on the other tab, which is the only defence against a square going out on a screen the
// player is not looking at.
class ResearchWatchBehaviourTest {

    @Test
    fun `tapping the square asks for the watch on that technology`() {
        val asked = mutableListOf<Technology>()
        researchScreen(nothingRunningUiState, onToggleTechnologyWatch = { asked += it }) {
            tapTheWatchOn(Technology.EXTRACTION)
        }

        assertEquals(listOf(Technology.EXTRACTION), asked)
    }

    @Test
    fun `tapping a ladder's square asks for the watch on the ladder`() {
        val asked = mutableListOf<AdaptationTechnology>()
        researchScreen(gateOpenUiState, onToggleAdaptationWatch = { asked += it }) {
            tapTheWatchOn(AdaptationTechnology.GRAVITIC)
        }

        assertEquals(listOf(AdaptationTechnology.GRAVITIC), asked)
    }

    @Test
    fun `a row the empire can already pay for has no square`() {
        researchScreen(nothingRunningUiState) {
            assertHasNoWatch(Technology.PHOTOVOLTAICS)
        }
    }

    // The whole answer to a slot shared with the colony: a watch set on a facility is still named
    // here, on a screen with no facility on it.
    @Test
    fun `the heading names a watched facility from the other tab`() {
        researchScreen(nothingRunningUiState.copy(watching = "watching Metal Mine")) {
            assertReads("watching Metal Mine")
            assertNothingReads("one project at a time")
        }
    }

    @Test
    fun `with nothing watched the heading goes back to the single-slot rule`() {
        researchScreen(nothingRunningUiState) {
            assertReads("one project at a time")
        }
    }

    // What the square costs, paid where the design says to pay it. A phone has room for the noun
    // until a square takes 36dp of the row; then the choice is a word or an ellipsis mid-word.
    @Test
    fun `a row carrying a square drops the trailing noun even on a phone`() {
        researchScreen(nothingRunningUiState) {
            assertRowReads(Technology.EXTRACTION, "metal · crystal")
            assertRowReads(Technology.PHOTOVOLTAICS, "Solar Plant output")
        }
    }

    @Test
    fun `the watched row says the instant it named`() {
        val watched = nothingRunningUiState.copy(
            technologies = nothingRunningUiState.technologies.map { row ->
                if (row.watch == null) row else row.copy(watch = WatchUiState.Booked("→ affordable 19:51"))
            },
        )
        researchScreen(watched) {
            assertRowReads(Technology.EXTRACTION, "→ affordable 19:51")
        }
    }
}
