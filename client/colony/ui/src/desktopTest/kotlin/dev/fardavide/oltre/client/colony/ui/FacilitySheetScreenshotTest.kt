package dev.fardavide.oltre.client.colony.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.component.RowSheetContent
import dev.fardavide.oltre.client.design.component.SheetLine
import dev.fardavide.oltre.client.design.component.words
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
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

    // **The only place in the app a number is drawn that will not happen.** `5h 40m → 4h 52m` in the
    // duration slot, in the rate line's own →-pair, with the two sentences that attribute it
    // directly above. Read against `facility_sheet_robotics`: the delta is two lines and one number.
    @Test
    fun `the sheet behind a row an alliance is shortening`() {
        captureSheet(
            row = roboticsFacilityRow.copy(
                baseDuration = TextRes("5h 40m"),
                duration = TextRes("4h 52m"),
                detail = roboticsFacilityRow.detail.copy(
                    lines = roboticsFacilityRow.detail.lines +
                        SheetLine(listOf(words(Strings.alliancePerkBuild(level = 7, percent = 14)))) +
                        SheetLine(listOf(words(Strings.alliancePerkBuildRunning()))),
                ),
            ),
            name = "facility_sheet_alliance",
            height = 520,
        )
    }

    // The same sheet at 320dp, where the chips and the pair share a `FlowRow` and the pair wraps to
    // its own line rather than squeezing them.
    @Test
    fun `the alliance sheet at the narrowest width`() {
        captureSheet(
            row = roboticsFacilityRow.copy(
                baseDuration = TextRes("5h 40m"),
                duration = TextRes("4h 52m"),
                detail = roboticsFacilityRow.detail.copy(
                    lines = roboticsFacilityRow.detail.lines +
                        SheetLine(listOf(words(Strings.alliancePerkBuild(level = 7, percent = 14)))) +
                        SheetLine(listOf(words(Strings.alliancePerkBuildRunning()))),
                ),
            ),
            name = "facility_sheet_alliance_narrow",
            height = 560,
            width = 320,
        )
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

    private fun captureSheet(row: FacilityRowUiState, name: String, height: Int, width: Int = 393) {
        runDesktopComposeUiTest(width = width, height = height) {
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
