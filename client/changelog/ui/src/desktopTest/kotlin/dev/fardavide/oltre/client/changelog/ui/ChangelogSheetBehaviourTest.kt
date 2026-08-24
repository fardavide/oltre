package dev.fardavide.oltre.client.changelog.ui

import androidx.compose.ui.test.ExperimentalTestApi
import kotlin.test.Test
import kotlin.test.assertTrue

// **The gestures, which are the whole of what this sheet does.** A screenshot can photograph a page
// beautifully whether or not the swipe works and whether or not the rail is connected to anything —
// so what is asserted here is the *effect*: which release is on screen after a finger has moved.
@OptIn(ExperimentalTestApi::class)
class ChangelogSheetBehaviourTest {

    @Test
    fun `the sheet opens on the newest release`() {
        changelogSheet {
            assertShowing("0.18.0")
            assertDepthShowing()
        }
    }

    @Test
    fun `swiping goes back through the run`() {
        changelogSheet {
            swipeToNextPage()

            assertShowing("0.17.1")
        }
    }

    @Test
    fun `the far end of the run is not composed until it is reached`() {
        // `beyondViewportPageCount = 1` keeps a neighbour either side and nothing more. On a
        // four-page fixture that is small; on the real sixty-five it is the difference between one
        // sky being drawn and sixty-five.
        changelogSheet {
            assertNotShowing("0.0.3")
        }
    }

    @Test
    fun `tapping the far end of the rail lands on the oldest release`() {
        // **The rail is a control**, and this is what makes it one rather than a picture of where you
        // are: sixty-four swipes is not a way to reach the first week.
        changelogSheet {
            tapRailAtEnd()

            assertShowing("0.0.3")
        }
    }

    @Test
    fun `the sheet works in a Slide Over pane`() {
        // Same gestures at 320dp, where the peek is 12dp instead of 18 and the card is 262 wide.
        changelogSheet(compact = true) {
            assertShowing("0.18.0")
            swipeToNextPage()
            assertShowing("0.17.1")
        }
    }

    @Test
    fun `the mark fits its card at every width a phone has`() {
        // **The two widths the design drew are not the two a phone has**, and the first cut of this
        // sheet took them for the only two: the column was a constant, right at 393 and at 320 and
        // wrong everywhere else. 360dp is the common Android width *and* the width `compact` flips
        // below, so it took the wide branch and laid a 319dp sky inside a 286dp card — with the limb,
        // which spans its whole box by construction, drawn past the card's border into the gap
        // toward the next page.
        //
        // Every width here is a real device or a real window: the two the design drew, the two most
        // common Android widths, an iPhone SE, and this app's own 560dp column cap.
        for (width in WIDTHS) {
            changelogSheet(width = width, compact = width < 360) {
                assertMarkFitsTheCard("0.18.0")
            }
        }
    }

    @Test
    fun `the tallest page fits the sheet at every width`() {
        // **The other half of measuring the page**, and the one the height budget is about: the mark
        // is a square of the column, so a wider sheet is a taller card, and the card is bottom-
        // aligned — a page that outgrows the viewport walks off the *top*, taking the sky with it,
        // because nothing here scrolls. 560dp is this app's own column cap and the widest a page can
        // ever be; three notes is the tallest a page can ever be.
        val clipped = WIDTHS.filter { width ->
            runCatching {
                changelogSheet(width = width, compact = width < 360, pages = listOf(tallestPage())) {
                    assertPageNotClipped("0.17.0", lastNote = TALLEST_NOTES.last())
                }
            }.isFailure
        }

        assertTrue(clipped.isEmpty(), "the tallest page is cut off at $clipped")
    }

    @Test
    fun `the tallest page keeps its words in a landscape window`() {
        // **The height the design never drew.** iPhone supports landscape, which is a 393dp-tall
        // window — barely half the sheet the budget was measured against — and nothing on a page
        // scrolls. What gives is the sky: the copy is measured first and the mark takes what is
        // left, so a short window costs a smaller picture rather than a missing line.
        changelogSheet(width = 852, height = LANDSCAPE_HEIGHT, pages = listOf(tallestPage())) {
            assertPageNotClipped("0.17.0", lastNote = TALLEST_NOTES.last())
            assertMarkFitsTheCard("0.17.0")
        }
    }

    @Test
    fun `a run of one release still draws a rail`() {
        // **The day the catalogue is one entry long is not hypothetical** — it is what a fork of this
        // project sees on its first release, and it is the input that divides by zero if the rail
        // measures a stop as `index / (count - 1)`. Nothing peeks on either side, which is the same
        // absence that says *newest* on a full run, said twice.
        changelogSheet(pages = testPages().take(1)) {
            assertShowing("0.18.0")
            assertDepthShowing()
            tapRailAtEnd()
            assertShowing("0.18.0")
        }
    }

    @Test
    fun `the build row calls back`() {
        // **The dead-control rule, met where it is cheapest to meet.** The row is the only door to the
        // changelog from settings, and a row that draws correctly and calls nothing is exactly the
        // defect a baseline cannot see.
        var opened = false

        buildRow(onOpenChangelog = { opened = true }) {
            assertShowing()
            tap()
        }

        assertTrue(opened, "the build row did not open the changelog")
    }

    private companion object {

        // Every width here is a real device or a real window: the two the design drew, the two
        // commonest Android widths, an iPhone SE, and this app's own 560dp column cap.
        val WIDTHS = listOf(320, 360, 375, 393, 412, 560)

        // A landscape iPhone, which is the shortest window the app can be asked to draw this in.
        const val LANDSCAPE_HEIGHT = 393
    }
}
