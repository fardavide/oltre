package dev.fardavide.oltre.client.settings.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Italian
import dev.fardavide.oltre.client.design.text.Translations
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// The settings sheet, in the states that differ from each other as *drawings* rather than as
// settings. Captured as `AlertSheetContent` rather than through the modal, for the reason
// `FacilitySheetScreenshotTest` states: a picture of what the sheet says has no business also
// depending on a popup being reachable and an entrance settling.
//
// **Every frame is taller than its sheet, deliberately**, and the first cut of this file was not:
// three of the four cut the last line off, which made the baseline a picture of the window rather
// than of the sheet. A frame that clips cannot show a control that overflowed — and one of them had,
// which is how `One per category` was found reading `One per`.
@OptIn(ExperimentalTestApi::class)
class AlertSheetScreenshotTest {

    // How it ships: every kind of news announcing itself, arriving as one notification. The panel is
    // seven rows and one of them carries a second line.
    @Test
    fun `the sheet a new colony opens`() {
        capture(
            name = "alert_sheet_by_category",
            mode = AlertMode.BY_CATEGORY,
            delivery = AlertDelivery.TOTAL,
            height = 700,
        )
    }

    // **The state the design says proves the control works**, because seven identical lit squares is
    // the one arrangement that does not: two off, and one of them is the row whose second line
    // changes with it.
    @Test
    fun `two switches off and the price line saying so`() {
        capture(
            name = "alert_sheet_two_off",
            mode = AlertMode.BY_CATEGORY,
            delivery = AlertDelivery.PER_CATEGORY,
            off = setOf(AlertCategory.PROBES, AlertCategory.PRICE_REACHED),
            height = 700,
        )
    }

    // **The panel is not collapsed here — it does not exist**, because the option that owns it is not
    // chosen. The ladder has not moved, which is the whole of what a baseline of this state is for.
    // It is also the one state with no timing line, since `One each` needs no explaining.
    @Test
    fun `per item is two ladders and two lines`() {
        capture(
            name = "alert_sheet_per_item",
            mode = AlertMode.PER_ITEM,
            delivery = AlertDelivery.EACH,
            height = 300,
        )
    }

    // 288dp of content will not hold three chips, so the Delivery ladder stacks — the same move the
    // colony's watch square makes rather than clipping a name. Italian at the same time, because its
    // chip labels are the longest the catalogue holds and the narrow width is where that shows.
    @Test
    fun `the delivery ladder stacks in a Slide Over window`() {
        capture(
            name = "alert_sheet_narrow_it",
            mode = AlertMode.BY_CATEGORY,
            delivery = AlertDelivery.TOTAL,
            width = SLIDE_OVER_WIDTH,
            height = 780,
            translations = Italian,
        )
    }

    private fun capture(
        name: String,
        mode: AlertMode,
        delivery: AlertDelivery,
        height: Int,
        off: Set<AlertCategory> = emptySet(),
        width: Int = PHONE_WIDTH,
        translations: Translations = English,
    ) {
        runDesktopComposeUiTest(width = width, height = height) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme(translations = translations) {
                    Surface {
                        AlertSheetContent(
                            uiState = alertSheetUiState(mode = mode, delivery = delivery, off = off),
                            // The one thing the sheet measures for itself, and the only reason these
                            // four frames are not one.
                            compact = width < COMPACT_WIDTH,
                            onSelectMode = {},
                            onToggleCategory = {},
                            onSelectDelivery = {},
                            // Empty, so these four baselines keep photographing exactly the two
                            // controls they were recorded for. The build row has a frame of its own
                            // in `:client:changelog:ui`.
                            build = {},
                        )
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

    private companion object {
        const val PHONE_WIDTH = 393
        const val SLIDE_OVER_WIDTH = 320
        const val COMPACT_WIDTH = 360
    }
}
