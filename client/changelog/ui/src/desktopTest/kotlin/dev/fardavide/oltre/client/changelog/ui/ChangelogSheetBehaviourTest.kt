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
}
