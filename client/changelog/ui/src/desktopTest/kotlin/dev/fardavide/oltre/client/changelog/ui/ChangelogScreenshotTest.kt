package dev.fardavide.oltre.client.changelog.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.design.text.TextRes
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// **The states that differ as drawings rather than as releases.** A page with one note and a page
// with three are two different cards; a page at 320dp is a third; and the sky is what every one of
// them is mostly made of.
//
// Captured as the page rather than through the sheet, for `AlertSheetScreenshotTest`'s reason: a
// picture of what a page says has no business also depending on a pager having settled. The one
// frame of the whole sheet is the exception and it earns it — the peek and the rail are the two
// things that only exist at sheet level.
//
// **There is no frame of a mark on its own**, deliberately, and Claude Design asked for that: *"a
// 20dp screenshot diff cannot state where the ink is."* What the sky has to be right about is
// asserted in `VersionSkyTest`, over every version the project could reach, in numbers a failure can
// print.
@OptIn(ExperimentalTestApi::class)
class ChangelogScreenshotTest {

    // The tallest page there can be: three notes, each of them the full 90 characters, plus a
    // headline that takes both its lines.
    @Test
    fun `a page with three notes`() {
        capturePage(name = "changelog_page_three_notes", index = 2)
    }

    // The shortest, which is what most of the first week looks like. A one-note page is a short card
    // rather than an empty one, and that is the whole reason the card hugs its release.
    @Test
    fun `a page with one note`() {
        capturePage(name = "changelog_page_one_note", index = 3)
    }

    // The oldest release in the run, in a Slide Over pane: 262dp of mark instead of 319, and the
    // sky is three hollow rings because 0.0.3 has no minor line to have settled.
    @Test
    fun `the oldest page in a narrow window`() {
        capturePage(name = "changelog_page_narrow", index = 3, width = SLIDE_OVER_WIDTH)
    }

    // **The one frame of the whole sheet**, and what it is for is the two things a page cannot show:
    // 18dp of the next card peeking past this one, and the rail with a tick per minor line. Nothing
    // peeks to the left, which is how the sheet says this is the newest release.
    @Test
    fun `the sheet at the newest release`() {
        runDesktopComposeUiTest(width = PHONE_WIDTH, height = SHEET_HEIGHT) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        ChangelogSheetContent(uiState = testChangelogUiState(), compact = false)
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/changelog_sheet.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    // The door from settings, at the one size it is ever drawn: 29dp of mark beside two lines of
    // type, in a 44dp row.
    @Test
    fun `the build row`() {
        // Taller than the row it photographs, for the reason every frame in this file is: a frame
        // that clips cannot show a line that overflowed. The first cut of this one was 90dp and cut
        // the headline off, which made it a picture of a version number rather than of the row.
        runDesktopComposeUiTest(width = PHONE_WIDTH, height = 120) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            BuildRow(
                                uiState = BuildRowUiState(
                                    label = TextRes("BUILD"),
                                    version = testPages().first().version,
                                    headline = TextRes("The gear opens something"),
                                    spoken = TextRes("Version 0.18.0 — The gear opens something."),
                                ),
                                onOpenChangelog = {},
                            )
                        }
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/changelog_build_row.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    private fun capturePage(name: String, index: Int, width: Int = PHONE_WIDTH) {
        val column = if (width == SLIDE_OVER_WIDTH) NARROW_COLUMN else COLUMN
        runDesktopComposeUiTest(width = width, height = PAGE_HEIGHT) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        // The page inside the padding the pager gives it, so the card is the width it
                        // is on the sheet rather than the width of the window.
                        Box(modifier = Modifier.padding(horizontal = if (width == SLIDE_OVER_WIDTH) 18.dp else 26.dp)) {
                            ChangelogPage(uiState = testPages()[index], column = column.dp)
                        }
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

        // The card's content column at each width: the page less 11dp of card padding each side.
        const val COLUMN = 319
        const val NARROW_COLUMN = 262

        // Taller than any card, so a frame is a picture of the page rather than of the window — the
        // finding `AlertSheetScreenshotTest` records, where three frames of four clipped their last
        // line and a control that had overflowed could not be seen.
        const val PAGE_HEIGHT = 620
        const val SHEET_HEIGHT = 741
    }
}
