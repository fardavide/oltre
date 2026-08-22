package dev.fardavide.oltre.client.colony.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.core.BuildingType
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// **The row sheet as a player actually meets it, which nothing photographed until now.**
// `FacilitySheetScreenshotTest` next door captures `RowSheetContent` — everything the sheet *says*,
// with no sheet around it — and it is right to: an assertion about wording has no business also
// depending on a popup being reachable and an entrance settling. What that leaves unphotographed is
// the half a player sees first: the modal itself, over the list it came from.
//
// **That half has shipped broken before, and this is the frame that would have caught it.** The
// dispatch sheet was a hand-rolled panel at 0.7.0 — a Column at the foot of the page's own layout,
// with a scrim of its own and a drawn grabber. On a device it stopped above the tab bar instead of
// covering it, a drag on it scrolled the list behind it, and the handle did not drag.
// `OltreBottomSheet` exists because of that, `RowSheet` is one of the three surfaces built on it,
// and the Galaxy tab's own sheet frames have had a baseline since 0.13 while these two had none.
//
// Raised by a tap rather than by a state parameter, because which row is open is `ColonyScreen`'s
// own — the same reason `GalaxyScreenHarness` exists one module over. The capture is scoped to the
// popup by what is inside it: a bottom sheet is a root of its own, so `onRoot` finds two and refuses
// to choose. `GalaxyScreenshotTest` set that shape and the argument for it is in `decisions.md`.
@OptIn(ExperimentalTestApi::class)
class RowSheetOnScreenScreenshotTest {

    // The first row of the list, chosen because what is under test is the modal rather than the
    // words: `testColonyUiState` is the populated screen, so this is the sheet with the whole list
    // behind it — which is the relationship no other baseline has. Phone width, which is every
    // width, since the sheet is full width wherever it opens.
    @Test
    fun `the sheet a facility row opens sits over the list it came from`() {
        captureSheetOver(BuildingType.METAL_MINE, name = "colony_row_sheet")
    }

    // The same modal at the narrowest window the app supports. The chrome is what is under test
    // here rather than the words — a sheet that stopped short of the foot, or lost its handle, is a
    // width bug and not a copy one.
    @Test
    fun `the sheet holds the foot of a Slide Over window`() {
        captureSheetOver(BuildingType.METAL_MINE, name = "colony_row_sheet_slide_over", width = 320)
    }

    private fun captureSheetOver(building: BuildingType, name: String, width: Int = 393) {
        runDesktopComposeUiTest(width = width, height = 852) {
            setContent {
                OltreTheme {
                    Surface {
                        ColonyScreen(uiState = testColonyUiState, onUpgrade = {}, onToggleWatch = {})
                    }
                }
            }
            onNodeWithTag(ColonyTestTags.card(building)).performScrollTo().performClick()
            // **Stopped only once the sheet is up.** The clock runs while the tap is made, because
            // a modal's entrance is a real animation with a real length and a frame captured part
            // way through it is a baseline of an arbitrary offset. Stopping first and winding by
            // hand is what every other sheet frame in the app does; doing it in this order is what
            // makes the popup exist to be found.
            mainClock.autoAdvance = false
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            waitForIdle()
            onNode(isRoot() and hasAnyDescendant(hasTestTag(ColonyTestTags.SHEET))).captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
