package dev.fardavide.oltre.client.changelog.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.swipeLeft
import dev.fardavide.oltre.client.changelog.domain.ReleaseVersion
import dev.fardavide.oltre.client.design.core.OltreTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The sheet's contents, driven through a robot rather than raw node queries — the taxonomy's rule.
// Everything here is a gesture a finger can make: a swipe, a tap on the rail, a tap on the row.
@OptIn(ExperimentalTestApi::class)
internal fun changelogSheet(
    compact: Boolean = false,
    pages: List<ChangelogPageUiState> = testPages(),
    // **A width, because the two the design drew are not the two a phone has.** Everything here used
    // to run at exactly 393 and exactly 320 — the same two numbers the code had hardcoded — so the
    // suite could not see a page laid out against a width it did not have.
    width: Int = if (compact) 320 else 393,
    // The sheet's own height. 741 is the full-height sheet on the reference phone; a landscape phone
    // is 393, and the app supports that orientation on iPhone.
    height: Int = 741,
    block: ChangelogRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = width, height = height) {
        setContent {
            OltreTheme {
                Surface {
                    ChangelogSheetContent(uiState = testChangelogUiState(pages), compact = compact)
                }
            }
        }
        ChangelogRobot(this).block()
    }
}

@OptIn(ExperimentalTestApi::class)
internal class ChangelogRobot(private val test: ComposeUiTest) {

    fun swipeToNextPage(): ChangelogRobot = apply {
        test.onNodeWithTag(ChangelogTestTags.SHEET).performTouchInput { swipeLeft() }
        test.waitForIdle()
    }

    // **The rail is a control, and this is the gesture that makes it one.** Sixty-four swipes is not
    // a way to reach the first week, so a tap lands on the nearest release — the galaxy map's own
    // caption gesture.
    fun tapRailAtEnd(): ChangelogRobot = apply {
        test.onNodeWithTag(ChangelogTestTags.RAIL).performTouchInput {
            click(centerRight)
        }
        test.waitForIdle()
    }

    fun assertShowing(version: String): ChangelogRobot = apply {
        test.onNodeWithTag(page(version)).assertIsDisplayed()
    }

    // **A page that is not composed is a page nobody can swipe to.** With `beyondViewportPageCount`
    // at one the pager keeps a neighbour either side and nothing else, so the far end of a
    // sixty-five-page run must genuinely be absent until it is reached.
    fun assertNotShowing(version: String): ChangelogRobot = apply {
        test.onNodeWithTag(page(version)).assertDoesNotExist()
    }

    fun assertDepthShowing(): ChangelogRobot = apply {
        test.onNodeWithTag(ChangelogTestTags.DEPTH).assertIsDisplayed()
    }

    // **The mark is square and it is the width of the card's column**, which is the property a
    // hardcoded page width broke at every width but two: the sky was laid out over 319dp inside a
    // card that was 286 wide, and the limb — which spans the whole box by construction — drew past
    // the card's border.
    //
    // Asserted on the card and the Canvas together rather than on a number, so it holds at any width
    // rather than at the one it was written against.
    fun assertMarkFitsTheCard(version: String): ChangelogRobot = apply {
        val card = test.onNodeWithTag(page(version), useUnmergedTree = true).fetchSemanticsNode()
        val mark = test.onAllNodes(
            hasTestTag(ChangelogTestTags.MARK) and hasAnyAncestor(hasTestTag(page(version))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().single()

        assertTrue(
            mark.size.width <= card.size.width,
            "the mark is ${mark.size.width}px wide inside a ${card.size.width}px card",
        )
        assertEquals(mark.size.width, mark.size.height, "the mark is not square")
    }

    // **Nothing is cut off the top of the sheet.** The card is bottom-aligned, so a page that grows
    // past the viewport does not scroll and does not clip at the foot — it walks off the *top*,
    // taking the sky with it. The design's whole height budget is the claim that this cannot happen;
    // this is that claim, asked of the layout rather than of the arithmetic.
    fun assertPageNotClipped(version: String, lastNote: String): ChangelogRobot = apply {
        val card = test.onNodeWithTag(page(version), useUnmergedTree = true).fetchSemanticsNode()

        assertTrue(
            card.positionInRoot.y >= 0f,
            "the page starts ${card.positionInRoot.y}px above the top of the sheet",
        )
        // **And the last line of it is on the screen**, which is the half a position check cannot
        // see: a `Column` handed less height than it wants does not overflow, it starves its last
        // children — so the card can sit at y = 0 with its final note measured to nothing.
        test.onNodeWithText(lastNote, substring = true).assertIsDisplayed()
    }

    private fun page(version: String): String =
        ChangelogTestTags.page(requireNotNull(ReleaseVersion.parse(version)))
}

// The build row, on its own, because what it does is call back and that is all it does.
@OptIn(ExperimentalTestApi::class)
internal fun buildRow(onOpenChangelog: () -> Unit, block: BuildRowRobot.() -> Unit) {
    runDesktopComposeUiTest(width = 393, height = 120) {
        setContent {
            OltreTheme {
                Surface {
                    BuildRow(uiState = testBuildRowUiState(), onOpenChangelog = onOpenChangelog)
                }
            }
        }
        BuildRowRobot(this).block()
    }
}

@OptIn(ExperimentalTestApi::class)
internal class BuildRowRobot(private val test: ComposeUiTest) {

    fun tap(): BuildRowRobot = apply {
        test.onNodeWithTag(ChangelogTestTags.BUILD_ROW).performClick()
        test.waitForIdle()
    }

    fun assertShowing(): BuildRowRobot = apply {
        test.onNodeWithTag(ChangelogTestTags.BUILD_ROW).assertIsDisplayed()
    }
}
