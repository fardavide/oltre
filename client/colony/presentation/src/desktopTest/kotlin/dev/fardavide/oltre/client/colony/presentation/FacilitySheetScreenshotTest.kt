package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.component.RowSheetContent
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.core.BuildingType
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// What a row opens, in the four shapes a facility can have. Captured as `RowSheetContent` rather
// than through the modal, for the reason `DebugSheet` states: a picture of what the sheet says has
// no business also depending on a popup being reachable and an entrance settling.
//
// Phone width, because the sheet is full width wherever it opens and 393dp is the narrowest window
// a phone hands it. The heights are generous on purpose — the sheet scrolls in the app, and a
// baseline that scrolled would be a baseline of an arbitrary offset.
@OptIn(ExperimentalTestApi::class)
class FacilitySheetScreenshotTest {

    // Prose, a ladder with two steps already held, and a live action. The one row on this screen
    // that gates anything, so the only sheet in the game with a ladder in it.
    @Test
    fun `the sheet behind the row that gates the rest of the game`() {
        captureSheet(row = roboticsFacilityRow, name = "facility_sheet_robotics", height = 460)
    }

    // The frame the whole design is about: a verdict that honestly reads "nothing", the three
    // sentences that show why, and the row worth reading instead.
    @Test
    fun `the sheet behind a row that buys nothing today`() {
        captureSheet(row = inertPlantFacilityRow, name = "facility_sheet_inert_plant", height = 400)
    }

    // An income row with a wait rather than a button: the ghost carries the same string the card
    // does, because a player who cannot afford the level is told when rather than told no.
    @Test
    fun `the sheet behind a row still filling its stores`() {
        captureSheet(
            row = testColonyUiState.facilities.first { it.building == BuildingType.DEUTERIUM_SYNTHESIZER },
            name = "facility_sheet_unaffordable",
            height = 360,
        )
    }

    // No footer at all, and a pointer instead: a locked row has no price yet, so it ends on the row
    // that moves its gate.
    @Test
    fun `the sheet behind the locked nanite factory`() {
        captureSheet(row = testColonyUiState.facilities.last(), name = "facility_sheet_locked", height = 400)
    }

    private fun captureSheet(row: FacilityRowUiState, name: String, height: Int) {
        runDesktopComposeUiTest(width = 393, height = height) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        RowSheetContent(uiState = row.toRowSheetUiState(), onAct = {})
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
