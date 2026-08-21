package dev.fardavide.oltre.client.research.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.component.RowSheetContent
import dev.fardavide.oltre.client.design.component.RowSheetUiState
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.Technology

// A behaviour test says what the player did and what they should see; the Robot owns how. Keeping
// the node queries here is what makes a testTag rename one file's problem rather than every test's
// — the convention the taxonomy asks for, and which MainScaffoldBehaviourTest still predates.
@OptIn(ExperimentalTestApi::class)
internal fun researchScreen(
    uiState: ResearchUiState,
    width: Int = PHONE_WIDTH,
    onStartResearch: (Technology) -> Unit = {},
    onStartAdaptation: (AdaptationTechnology) -> Unit = {},
    onToggleTechnologyWatch: (Technology) -> Unit = {},
    onToggleAdaptationWatch: (AdaptationTechnology) -> Unit = {},
    block: ResearchRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = width, height = 852) {
        setContent {
            OltreTheme {
                Surface {
                    ResearchScreen(
                        uiState = uiState,
                        onStartResearch = onStartResearch,
                        onStartAdaptation = onStartAdaptation,
                        onToggleTechnologyWatch = onToggleTechnologyWatch,
                        onToggleAdaptationWatch = onToggleAdaptationWatch,
                    )
                }
            }
        }
        ResearchRobot(this).block()
    }
}

// A sheet's contents with no sheet around them, for every assertion about what a sheet *says* —
// exactly as `DebugRobot` does next door, and for the same reason: a claim about a sentence has no
// business also depending on a popup being reachable and an enter animation settling. The one test
// that is about the sheet arriving at all goes through `researchScreen` above.
@OptIn(ExperimentalTestApi::class)
internal fun researchSheet(
    uiState: RowSheetUiState,
    width: Int = PHONE_WIDTH,
    onAct: () -> Unit = {},
    block: ResearchRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = width, height = 852) {
        setContent {
            OltreTheme {
                Surface {
                    RowSheetContent(
                        uiState = uiState,
                        onAct = onAct,
                        modifier = Modifier.testTag(ResearchTestTags.SHEET),
                        actionModifier = Modifier.testTag(ResearchTestTags.SHEET_ACTION),
                    )
                }
            }
        }
        ResearchRobot(this).block()
    }
}

internal const val PHONE_WIDTH = 393
internal const val SLIDE_OVER_WIDTH = 320

// **`useUnmergedTree` everywhere, and it is the card's doing.** A card body that opens a sheet is a
// `clickable`, and a `clickable` sets `mergeDescendants = true` — so every Text and every tag inside
// the card is folded into the card's own node and stops resolving on its own. The unmerged tree is
// the row as it is actually built, which is what these lookups have always meant.
@OptIn(ExperimentalTestApi::class)
internal class ResearchRobot(private val test: ComposeUiTest) {

    fun startResearching(technology: Technology) = apply {
        scrollTo(ResearchTestTags.row(technology))
        test.onNodeWithTag(ResearchTestTags.action(technology), useUnmergedTree = true).performClick()
    }

    // Overloads rather than one widened signature, for the reason `ResearchTestTags` is overloaded:
    // a caller cannot ask about a row that does not exist, and the compiler says so.
    fun startResearching(technology: AdaptationTechnology) = apply {
        scrollTo(ResearchTestTags.row(technology))
        test.onNodeWithTag(ResearchTestTags.action(technology), useUnmergedTree = true).performClick()
    }

    fun assertBranchShows(technology: AdaptationTechnology) = apply {
        scrollTo(ResearchTestTags.row(technology))
        test.onNodeWithTag(ResearchTestTags.row(technology), useUnmergedTree = true).assertIsDisplayed()
    }

    fun assertOffersResearch(technology: AdaptationTechnology) = apply {
        scrollTo(ResearchTestTags.row(technology))
        test.onNodeWithTag(ResearchTestTags.action(technology), useUnmergedTree = true)
            .assertTextEquals("Research")
    }

    fun assertWaits(technology: AdaptationTechnology, label: String) = apply {
        scrollTo(ResearchTestTags.row(technology))
        test.onNodeWithTag(ResearchTestTags.action(technology), useUnmergedTree = true).assertTextEquals(label)
    }

    fun assertCountsDown(technology: AdaptationTechnology, countdown: String) = apply {
        scrollTo(ResearchTestTags.row(technology))
        test.onNodeWithTag(ResearchTestTags.action(technology), useUnmergedTree = true).assertTextEquals(countdown)
    }

    fun assertOffersNothing(technology: AdaptationTechnology) = apply {
        test.onNodeWithTag(ResearchTestTags.action(technology), useUnmergedTree = true).assertDoesNotExist()
    }

    // **A row that has an action node but not the verb.** `assertOffersNothing` is for a *locked*
    // row, which draws no action at all; a running row and a waiting row both draw one — a countdown,
    // a ghost time — and what has to be true of them is that neither is the word a player can press.
    //
    // It reads rather than taps, deliberately. Tapping a row with no button on it lands on the card,
    // which opens the sheet, and every tap after that is aimed at whatever the sheet put under the
    // finger — so a test that "tapped every row and asserted nothing started" was asserting something
    // about a modal it did not know was open.
    fun assertDoesNotOfferResearch(technology: AdaptationTechnology) = apply {
        scrollTo(ResearchTestTags.row(technology))
        test.onNodeWithTag(ResearchTestTags.action(technology), useUnmergedTree = true)
            .assert(hasText("Research").not())
    }

    fun assertRowReads(technology: AdaptationTechnology, text: String) = apply {
        scrollTo(ResearchTestTags.row(technology))
        test.onNodeWithTag(ResearchTestTags.row(technology), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(text, substring = true)))
    }

    fun assertBranchShows(technology: Technology) = apply {
        scrollTo(ResearchTestTags.row(technology))
        test.onNodeWithTag(ResearchTestTags.row(technology), useUnmergedTree = true).assertIsDisplayed()
    }

    fun assertOffersResearch(technology: Technology) = apply {
        scrollTo(ResearchTestTags.row(technology))
        test.onNodeWithTag(ResearchTestTags.action(technology), useUnmergedTree = true)
            .assertTextEquals("Research")
    }

    // The ghost carries a time, never a dead button.
    fun assertWaits(technology: Technology, label: String) = apply {
        scrollTo(ResearchTestTags.row(technology))
        test.onNodeWithTag(ResearchTestTags.action(technology), useUnmergedTree = true).assertTextEquals(label)
    }

    fun assertCountsDown(technology: Technology, countdown: String) = apply {
        scrollTo(ResearchTestTags.row(technology))
        test.onNodeWithTag(ResearchTestTags.action(technology), useUnmergedTree = true).assertTextEquals(countdown)
    }

    // A locked row is name, level and requirement — no costs and nothing to press.
    fun assertOffersNothing(technology: Technology) = apply {
        test.onNodeWithTag(ResearchTestTags.action(technology), useUnmergedTree = true).assertDoesNotExist()
    }

    // Scoped to a row, because three rows of the same shape say many of the same things: two of
    // them carry "Requires Robotics 1" before the gate, and an unscoped query would match both and
    // fail on the ambiguity rather than on the assertion.
    fun assertRowReads(technology: Technology, text: String) = apply {
        scrollTo(ResearchTestTags.row(technology))
        test.onNodeWithTag(ResearchTestTags.row(technology), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(text, substring = true)))
    }

    // **The branch stopped fitting a phone at 0.9.** Seven rows — four applied and three ladders —
    // are taller than a 393x852 viewport, so a row near the bottom is present but not displayed, and
    // both `assertIsDisplayed` and `performClick` fail on it. Scrolling here rather than in each test
    // is the Robot doing its job: a behaviour test says what the player did, and a player scrolls
    // without it being part of the story.
    private fun scrollTo(tag: String) {
        test.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
    }

    fun assertRowDoesNotRead(technology: Technology, text: String) = apply {
        test.onNodeWithTag(ResearchTestTags.row(technology), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(text, substring = true)).not())
    }

    // The card body, not the action beside it: the sheet is what a row opens when the player asks
    // it to explain itself rather than when they buy it.
    fun openDetailOn(technology: Technology) = apply {
        test.onNodeWithTag(ResearchTestTags.card(technology)).performClick()
    }

    fun openDetailOn(technology: AdaptationTechnology) = apply {
        test.onNodeWithTag(ResearchTestTags.card(technology)).performClick()
    }

    fun assertSheetIsOpen() = apply {
        test.onNodeWithTag(ResearchTestTags.SHEET, useUnmergedTree = true).assertIsDisplayed()
    }

    fun assertNoSheetIsOpen() = apply {
        test.onNodeWithTag(ResearchTestTags.SHEET, useUnmergedTree = true).assertDoesNotExist()
    }

    // Substring and scoped to the sheet, because a sentence the component composes from several
    // spans is still one line to the player — and because the row behind an open sheet is saying
    // some of the same words.
    fun assertSheetReads(text: String) = apply {
        test.onNodeWithTag(ResearchTestTags.SHEET, useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(text, substring = true)))
    }

    fun assertSheetDoesNotRead(text: String) = apply {
        test.onNodeWithTag(ResearchTestTags.SHEET, useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(text, substring = true)).not())
    }

    fun actOnTheSheet() = apply {
        test.onNodeWithTag(ResearchTestTags.SHEET_ACTION, useUnmergedTree = true).performClick()
    }

    fun assertSheetOffers(label: String) = apply {
        test.onNodeWithTag(ResearchTestTags.SHEET_ACTION, useUnmergedTree = true).assertTextEquals(label)
    }

    // The square carries no text, so these two are the only controls the Robot reaches by tag for a
    // reason other than ambiguity.
    fun tapTheWatchOn(technology: Technology) = apply {
        test.onNodeWithTag(ResearchTestTags.watch(technology), useUnmergedTree = true).performClick()
    }

    fun tapTheWatchOn(technology: AdaptationTechnology) = apply {
        test.onNodeWithTag(ResearchTestTags.watch(technology), useUnmergedTree = true).performClick()
    }

    fun assertHasNoWatch(technology: Technology) = apply {
        test.onNodeWithTag(ResearchTestTags.watch(technology), useUnmergedTree = true).assertDoesNotExist()
    }

    // Substring, because a line the screen composes from several Texts is still one line to the
    // player: the section rule renders as " · one project at a time" next to its label.
    fun assertReads(text: String) = apply {
        test.onNodeWithText(text, substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    fun assertNothingReads(text: String) = apply {
        test.onNodeWithText(text, substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    // **Several nodes can legitimately carry one string**, and since 0.12.2 the screen has a pair
    // that does: at 320dp both section rules shorten to "one at a time", one per branch, which is
    // the correct reading rather than a duplicate. `onNodeWithText` fails outright on two matches,
    // so asserting the short form through `assertReads` would report the design as a defect. A count
    // is the only claim that can be made about it, and it is the stronger one anyway — it says the
    // rule is on *both* headings rather than that it is somewhere on the screen.
    fun assertReadsTimes(text: String, times: Int) = apply {
        test.onAllNodesWithText(text, substring = true, useUnmergedTree = true).assertCountEquals(times)
    }
}
