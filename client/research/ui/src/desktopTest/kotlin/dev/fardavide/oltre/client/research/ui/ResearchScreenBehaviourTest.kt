package dev.fardavide.oltre.client.research.ui

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

    // A locked row used to be name, level and requirement, on the argument that a consequence you
    // cannot buy yet is noise. That argument is what this design's second hard case is about: the
    // only question a gate leaves open is whether it is worth pushing for, and a row that states
    // the requirement and stops has withheld the one fact that answers it. The verdict sits under
    // the requirement, at the same 42% dim as everything else on the card.
    @Test
    fun `a locked row still says what clearing the gate would be worth`() {
        researchScreen(uiState = beforeTheGateUiState) {
            assertRowReads(Technology.EXTRACTION, "Requires Robotics 1")
            assertRowReads(Technology.EXTRACTION, "+7/h metal")
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
            // What you want mid-project is when, not what — and "→ LV 4" already says what. The
            // claim used to be about the effect line; it is the verdict that occupies that slot
            // now, and the row in flight is the one state that carries neither.
            assertRowReads(Technology.PHOTOVOLTAICS, "done 11:23")
            assertRowDoesNotRead(Technology.PHOTOVOLTAICS, "back in")
        }
    }

    @Test
    fun `a row states what the level is worth where the effect line used to be`() {
        researchScreen(uiState = oneProjectInFlightUiState) {
            // One line of consequence rather than two, which is the design's single exception: two
            // lines of numbers about the same level is where a dense row becomes unreadable.
            assertRowReads(Technology.ENRICHMENT, "+6/h deuterium · back in 138h")
            assertRowDoesNotRead(Technology.ENRICHMENT, "deuterium output")
        }
    }

    @Test
    fun `a verdict drops its second clause in a Slide Over pane`() {
        // given the same state at both widths — abbreviation is a width decision and not a change
        // of voice, so what changes between these two is one clause and nothing else
        researchScreen(uiState = oneProjectInFlightUiState, width = PHONE_WIDTH) {
            assertRowReads(Technology.ENRICHMENT, "+6/h deuterium · back in 138h")
            assertReads("one project at a time")
        }

        // then — dropped rather than ellipsised mid-word, and recoverable from the sheet
        researchScreen(uiState = oneProjectInFlightUiState, width = SLIDE_OVER_WIDTH) {
            assertRowReads(Technology.ENRICHMENT, "+6/h deuterium")
            assertRowDoesNotRead(Technology.ENRICHMENT, "back in")
            // Twice, once per branch: both section rules shorten to the same three words at this
            // width, which since 0.12.1 is two rules that happen to read alike rather than one.
            assertReadsTimes("one at a time", times = 2)
            assertNothingReads("one project at a time")
        }
    }

    // ── The sheet a row opens ────────────────────────────────────────────────────────────────

    @Test
    fun `tapping the body of a row opens the sheet for that row`() {
        researchScreen(uiState = gateOpenUiState) {
            assertNoSheetIsOpen()
            openDetailOn(Technology.ENRICHMENT)
            assertSheetIsOpen()
            // The heading names the row it came from, so the sheet cannot be mistaken for the one
            // three rows down that says many of the same things.
            assertSheetReads("Enrichment")
        }
    }

    @Test
    fun `the sheet repeats the sentence the row was carrying`() {
        researchScreen(uiState = gateOpenUiState) {
            openDetailOn(Technology.ENRICHMENT)
            assertSheetReads("+6/h deuterium · back in 138h")
        }
    }

    @Test
    fun `a locked row opens its sheet too`() {
        // The whole argument for making a dimmed row tappable: it is the row with the least on it
        // and the most to explain.
        researchScreen(uiState = beforeTheGateUiState) {
            openDetailOn(AdaptationTechnology.THERMAL)
            assertSheetIsOpen()
            assertSheetReads("Requires Robotics 2")
        }
    }

    @Test
    fun `tapping the action does not open the sheet`() {
        // The action and the body are two targets on one card. The inner one wins — otherwise
        // every purchase would end on a panel nobody asked for.
        researchScreen(uiState = gateOpenUiState) {
            startResearching(Technology.ENRICHMENT)
            assertNoSheetIsOpen()
        }
    }

    @Test
    fun `the sheet says the arithmetic the verdict was a summary of`() {
        // Driven through the contents rather than the popup, exactly as `DebugRobot` does: an
        // assertion about a sentence has no business depending on an enter animation settling.
        researchSheet(uiState = inertSheetUiState) {
            assertSheetReads("Your plants supply 550 energy. The colony draws 380.")
            assertSheetReads("your output does not move")
            assertSheetReads("about 17 more mine levels away")
        }
    }

    @Test
    fun `a sheet whose verdict is nothing names the row to buy instead`() {
        researchSheet(uiState = inertSheetUiState) {
            assertSheetReads("Enrichment")
            assertSheetReads("LV 1 · back in 138h")
        }
    }

    @Test
    fun `the sheet carries the ladder of what the level opens`() {
        researchSheet(uiState = gatedSheetUiState) {
            assertSheetReads("LV 3")
            assertSheetReads("Enrichment · you have this")
        }
    }

    @Test
    fun `a locked sheet ends on what it requires and offers no action`() {
        researchSheet(uiState = lockedSheetUiState) {
            assertSheetReads("°C tolerance: −30 … +45 → −44 … +59.")
            assertSheetReads("Requires Robotics 2.")
            assertSheetDoesNotRead("Research")
        }
    }

    @Test
    fun `the action inside the sheet starts the row the sheet is about`() {
        // given
        val started = mutableListOf<Technology>()

        // when the card body is opened and the sheet's own button is pressed
        researchScreen(uiState = gateOpenUiState, onStartResearch = { started += it }) {
            openDetailOn(Technology.ENRICHMENT)
            actOnTheSheet()
        }

        // then — the sheet is somewhere a decision can be made rather than somewhere you read
        // about one and then go back
        assertEquals(listOf(Technology.ENRICHMENT), started.toList())
    }

    @Test
    fun `a ladder's sheet starts the ladder rather than a technology`() {
        // given — one slot, two callbacks, and the sheet has to know which of them it is holding
        val projects = mutableListOf<Technology>()
        val ladders = mutableListOf<AdaptationTechnology>()

        // when
        researchScreen(
            uiState = gateOpenUiState,
            onStartResearch = { projects += it },
            onStartAdaptation = { ladders += it },
        ) {
            openDetailOn(AdaptationTechnology.THERMAL)
            actOnTheSheet()
        }

        // then
        assertEquals(emptyList<Technology>(), projects.toList())
        assertEquals(listOf(AdaptationTechnology.THERMAL), ladders.toList())
    }

    @Test
    fun `a waiting row's sheet carries the wait rather than a button`() {
        researchSheet(uiState = gatedSheetUiState) {
            assertSheetOffers("in 1h 16m")
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

    // **The seam, driven from the screen — and it moved at 0.12.1.** A technology in flight used to
    // stop all three ladders too, and this test asserted that both halves refused. Two slots means
    // the refusal stops at the heading: every applied row is still dead while the countdown runs, and
    // every ladder is live. The two assertions below are the same two lines they always were, saying
    // the opposite thing about the second one — which is what makes this the test to read first when
    // asking what the split actually did to the player.
    @Test
    fun `a project in flight stops its own branch and leaves the ladders live`() {
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
            AdaptationTechnology.entries.forEach { startResearching(it) }
            Technology.entries.forEach { startResearching(it) }
        }

        // then
        assertEquals(emptyList<Technology>(), applied.toList())
        assertEquals(AdaptationTechnology.entries.toList(), ladders.toList())
    }

    // **The same seam from the other side**, and the state that could not be drawn before 0.12.1: a
    // ladder climbing while the applied branch is entirely free. It is the half of the split a player
    // meets after reading a `BLOCKED` world — buy the ladder the row named, and the colony's own
    // research is untouched by it.
    @Test
    fun `a ladder in flight stops its own branch and leaves the technologies live`() {
        // given
        val applied = mutableListOf<Technology>()
        val ladders = mutableListOf<AdaptationTechnology>()

        // when every row of both branches is tapped
        researchScreen(
            uiState = oneLadderInFlightUiState,
            onStartResearch = { applied += it },
            onStartAdaptation = { ladders += it },
        ) {
            assertCountsDown(AdaptationTechnology.THERMAL, "02:30:00")
            // The two it does stop read the countdown above them, which is the property the applied
            // branch has always had and the ladders now have on their own: the number verifies itself.
            assertWaits(AdaptationTechnology.GRAVITIC, "in 2h 30m")
            assertWaits(AdaptationTechnology.ATMOSPHERIC, "in 2h 30m")
            // Technologies first, and it is not cosmetic: scrolling from the last ladder back to the
            // first technology and clicking in one step drops that click, so a run that taps the
            // bottom of the list and then the top loses its first tap. Screen order avoids it.
            Technology.entries.forEach { startResearching(it) }
            AdaptationTechnology.entries.forEach { startResearching(it) }
        }

        // then
        assertEquals(emptyList<AdaptationTechnology>(), ladders.toList())
        assertEquals(Technology.entries.toList(), applied.toList())
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
                assertRowReads(it, "Requires Robotics 2")
            }
            assertReads("ADAPTATION")
            assertReads("one ladder at a time")
        }
    }

    @Test
    fun `a ladder states the band it has and the band the next level buys`() {
        // The band moved into the sheet with every other second line of numbers — the row keeps the
        // one sentence that says what the level is *worth*, and both halves of the band are stated
        // where there is width for them.
        researchScreen(uiState = gateOpenUiState) {
            assertRowReads(AdaptationTechnology.THERMAL, "Unlocks nothing you have surveyed")
        }
        researchSheet(uiState = lockedSheetUiState) {
            assertSheetReads("−30 … +45")
            assertSheetReads("−44 … +59")
            assertSheetReads("°C")
        }
    }

    // What a ladder's verdict drops at 320dp is the verb and nothing else: both counts survive,
    // which is the point — they are what the row is compared on. The applied branch drops a whole
    // clause at this width, so asserting the counts alone would pass on either string and say
    // nothing; the absence of "Unlocks" is what makes this a claim about the compact form.
    @Test
    fun `a ladder's verdict drops its verb and keeps both counts in a Slide Over pane`() {
        researchScreen(uiState = gateOpenUiState, width = SLIDE_OVER_WIDTH) {
            assertRowReads(AdaptationTechnology.GRAVITIC, "5 worlds, 1 worth taking")
            assertNothingReads("Unlocks 5 worlds")
            // The section rule shortens at this width now, where "the same slot" used to be short
            // enough to survive it. Asserted as an absence because at 320dp both headings read the
            // same three words — which is the correct reading, each section stating its own rule,
            // and is exactly why the long form is what a test can point at unambiguously.
            assertNothingReads("one ladder at a time")
        }
    }
}
