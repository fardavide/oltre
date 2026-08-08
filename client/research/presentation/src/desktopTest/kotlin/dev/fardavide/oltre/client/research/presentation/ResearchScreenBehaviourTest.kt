package dev.fardavide.oltre.client.research.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.Technology
import kotlin.test.assertEquals
import org.junit.Test

// The first interaction in the game that is actually driven. The colony's Upgrade button is still
// covered by core unit tests and by nothing that renders — every colony test passes onUpgrade = {}
// to nothing — so this is also the pattern that migration should follow.
@OptIn(ExperimentalTestApi::class)
class ResearchScreenBehaviourTest {

    @Test
    fun `tapping Research starts the technology that was tapped`() {
        // given
        val started = mutableListOf<Technology>()

        // when
        researchScreen(uiState = nothingRunningUiState, onStartResearch = { started += it }) {
            assertOffersResearch(Technology.PHOTOVOLTAICS)
            startResearching(Technology.PHOTOVOLTAICS)
        }

        // then
        assertEquals(listOf(Technology.PHOTOVOLTAICS), started.toList())
    }

    @Test
    fun `a row that cannot start yet has nothing to press`() {
        // given a colony short of the deuterium Extraction wants
        val started = mutableListOf<Technology>()

        // when the ghost is tapped anyway
        researchScreen(uiState = nothingRunningUiState, onStartResearch = { started += it }) {
            assertWaits(Technology.EXTRACTION, "in 1h 45m")
            startResearching(Technology.EXTRACTION)
        }

        // then — it carries a time, not an action
        assertEquals(emptyList<Technology>(), started.toList())
    }

    @Test
    fun `a locked technology offers no action at all`() {
        researchScreen(uiState = nothingRunningUiState) {
            assertBranchShows(Technology.ENRICHMENT)
            assertRowReads(Technology.ENRICHMENT, "Requires Extraction 3")
            assertOffersNothing(Technology.ENRICHMENT)
        }
    }

    @Test
    fun `the whole branch is on screen before a single level exists`() {
        // The flat list is the tech tree: all three are legible on day 1, with what they want.
        researchScreen(uiState = beforeTheGateUiState) {
            Technology.entries.forEach {
                assertBranchShows(it)
                assertOffersNothing(it)
            }
            assertRowReads(Technology.PHOTOVOLTAICS, "Requires Robotics 1")
            assertRowReads(Technology.EXTRACTION, "Requires Robotics 1")
            assertRowReads(Technology.ENRICHMENT, "Requires Extraction 3")
        }
    }

    @Test
    fun `the running row counts down and the others say when they can start`() {
        researchScreen(uiState = oneProjectInFlightUiState) {
            assertCountsDown(Technology.PHOTOVOLTAICS, "01:12:44")
            assertRowReads(Technology.PHOTOVOLTAICS, "→ LV 4 · done 11:23")
            assertWaits(Technology.EXTRACTION, "in 3h 55m")
            assertWaits(Technology.ENRICHMENT, "in 1h 13m")
        }
    }

    @Test
    fun `no row offers Research while a project is in flight`() {
        // given a project already running
        val started = mutableListOf<Technology>()

        // when every row is tapped
        researchScreen(uiState = oneProjectInFlightUiState, onStartResearch = { started += it }) {
            Technology.entries.forEach { startResearching(it) }
        }

        // then — the single-slot rule reaches the screen, not just the model
        assertEquals(emptyList<Technology>(), started.toList())
    }

    @Test
    fun `a running row shows when it finishes instead of what the next level buys`() {
        researchScreen(uiState = oneProjectInFlightUiState) {
            // What you want mid-project is when, not what — and "→ LV 4" already says what.
            assertRowReads(Technology.PHOTOVOLTAICS, "done 11:23")
            assertNothingReads("Solar Plant output")
        }
    }

    // ── The second branch, and the seam between them ─────────────────────────────────────────

    @Test
    fun `tapping Research on a ladder starts the ladder that was tapped`() {
        // given
        val started = mutableListOf<AdaptationTechnology>()

        // when
        researchScreen(uiState = gateOpenUiState, onStartAdaptation = { started += it }) {
            assertOffersResearch(AdaptationTechnology.THERMAL)
            startResearching(AdaptationTechnology.THERMAL)
        }

        // then
        assertEquals(listOf(AdaptationTechnology.THERMAL), started.toList())
    }

    // The whole argument for one screen, driven: a technology in flight is what stops all three
    // ladders, and the number every one of them shows is the number the countdown four rows up is
    // counting. Nothing on the screen explains that, and nothing has to.
    @Test
    fun `a project in flight stops both branches with the same number on every row`() {
        // given
        val applied = mutableListOf<Technology>()
        val ladders = mutableListOf<AdaptationTechnology>()

        // when every row of both branches is tapped
        researchScreen(
            uiState = oneProjectInFlightUiState,
            onStartResearch = { applied += it },
            onStartAdaptation = { ladders += it },
        ) {
            assertCountsDown(Technology.PHOTOVOLTAICS, "01:12:44")
            AdaptationTechnology.entries.forEach {
                assertWaits(it, "in 1h 13m")
                startResearching(it)
            }
            Technology.entries.forEach { startResearching(it) }
        }

        // then — one slot, shared, and the screen refuses on both sides of the seam
        assertEquals(emptyList<Technology>(), applied.toList())
        assertEquals(emptyList<AdaptationTechnology>(), ladders.toList())
    }

    @Test
    fun `both branches are on screen before either gate opens`() {
        // Four of six rows dimmed is what a new player meets, and the second block is the branch
        // saying what it will want rather than a door being closed.
        researchScreen(uiState = beforeTheGateUiState) {
            AdaptationTechnology.entries.forEach {
                assertBranchShows(it)
                assertOffersNothing(it)
                // The locked row does not explain what a tolerance band is. The place that teaches
                // the concept is a blocked world on Galaxy, against a real reading.
                assertRowReads(it, "Requires Robotics 4")
            }
            assertReads("ADAPTATION")
            assertReads("the same slot")
        }
    }

    @Test
    fun `a ladder states the band it has and the band the next level buys`() {
        researchScreen(uiState = gateOpenUiState) {
            assertRowReads(AdaptationTechnology.THERMAL, "−30 … +45")
            assertRowReads(AdaptationTechnology.THERMAL, "−44 … +59")
            assertRowReads(AdaptationTechnology.THERMAL, "°C")
        }
    }

    // The applied line sheds "output" at 320dp; a band line has nothing to shed, so it is the same
    // string at both widths — as is the second section's rule, where "slot" is already the shortest
    // true noun.
    @Test
    fun `a band line and the second rule are unchanged in a Slide Over pane`() {
        researchScreen(uiState = gateOpenUiState, width = SLIDE_OVER_WIDTH) {
            assertRowReads(AdaptationTechnology.GRAVITIC, "0.65 … 1.40")
            assertRowReads(AdaptationTechnology.GRAVITIC, "0.60 … 1.52")
            assertRowReads(AdaptationTechnology.ATMOSPHERIC, "atm")
            assertReads("the same slot")
        }
    }

    @Test
    fun `the effect line keeps its percentages and drops only a noun in a Slide Over pane`() {
        // given the same state at both widths
        researchScreen(uiState = oneProjectInFlightUiState, width = PHONE_WIDTH) {
            assertRowReads(Technology.EXTRACTION, "metal · crystal output")
            assertReads("one project at a time")
        }

        // then — abbreviation is a width decision, not a change of voice
        researchScreen(uiState = oneProjectInFlightUiState, width = SLIDE_OVER_WIDTH) {
            assertRowReads(Technology.EXTRACTION, "+36%")
            assertRowReads(Technology.EXTRACTION, "+47%")
            assertRowReads(Technology.EXTRACTION, "metal · crystal")
            assertNothingReads("metal · crystal output")
            assertReads("one at a time")
            assertNothingReads("one project at a time")
        }
    }
}
