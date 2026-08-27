package dev.fardavide.oltre.client

import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.changelog.presentation.EnglishChangelog
import dev.fardavide.oltre.client.changelog.presentation.toChangelogUiState
import dev.fardavide.oltre.client.changelog.ui.BuildRowUiState
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.settings.presentation.toAlertSheetUiState
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import io.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.TimeZone
import org.junit.Test

// **The one sheet wearing each of its two faces**, which is the frame neither feature can take on
// its own: `:client:settings:ui` does not know the changelog exists, and `:client:changelog:ui` does
// not know it is ever raised beside a pair of ladders. The composition root is where they meet, so
// this is where the picture of them together belongs.
//
// **What it is actually for is the build row in place.** The settings baselines pass an empty foot —
// deliberately, so those four frames keep photographing the two controls they were recorded for — so
// until this file there was no frame anywhere showing the only door to the changelog sitting under
// the delivery ladder, at the foot of the column, where the design put it.
//
// Captured as the *face* rather than through the sheet that raises it, for the reason every sheet in
// this repository is: a `ModalBottomSheet` renders into a scene root of its own, so a capture through
// `onRoot()` would photograph the window behind it. `App` raises the chrome; this is everything
// inside it.
@OptIn(ExperimentalTestApi::class)
class SettingsSheetScreenshotTest {

    @Test
    fun `the settings face with the build row at the foot of it`() {
        capture(name = "settings_face_settings", face = SheetFace.SETTINGS, height = 760)
    }

    @Test
    fun `the changelog face in the same sheet`() {
        capture(name = "settings_face_changelog", face = SheetFace.CHANGELOG, height = 741)
    }

    // **The build row at the width it has least room**, which is the one thing about it the design
    // could not measure: the row is a 29dp mark, an 11dp gap and two lines of type, and in a Slide
    // Over pane the headline under the version has 262dp to fit a sentence written for 319.
    @Test
    fun `the settings face in a Slide Over pane`() {
        capture(
            name = "settings_face_narrow",
            face = SheetFace.SETTINGS,
            height = 860,
            width = SLIDE_OVER_WIDTH,
        )
    }

    // **Halfway through the swap**, which is the only frame that shows the transition the design
    // specified: 210ms, one-shot, 16dp in the direction of travel, with the chrome around it not
    // moving. A settled frame of either face says nothing about it — and a transition nothing
    // photographs is a transition that can quietly stop happening.
    @Test
    fun `the settings sheet halfway through the swap`() {
        capture(
            name = "settings_face_swapping",
            face = SheetFace.SETTINGS,
            swapTo = SheetFace.CHANGELOG,
            height = 760,
        )
    }

    private fun capture(
        name: String,
        face: SheetFace,
        height: Int,
        width: Int = PHONE_WIDTH,
        // When set, the frame is taken mid-transition rather than settled: the face is switched on
        // the first composition and the clock is wound to the middle of the 210ms.
        swapTo: SheetFace? = null,
    ) {
        runDesktopComposeUiTest(width = width, height = height) {
            mainClock.autoAdvance = false
            setContent {
                var shown by remember { mutableStateOf(face) }
                if (swapTo != null) {
                    LaunchedEffect(Unit) { shown = swapTo }
                }
                OltreTheme {
                    Surface {
                        SettingsSheetFace(
                            face = shown,
                            alerts = GameState.initial(GalaxySeed(SEED)).toAlertSheetUiState(
                                now = TEST_NOW,
                                timeZone = TimeZone.UTC,
                            ),
                            changelog = EnglishChangelog.toChangelogUiState(),
                            // **A fixture rather than the head of the catalogue**, so this baseline
                            // is not re-recorded on every release. The row's *drawing* is what the
                            // frame is about; that it names the running build is asserted in
                            // `ChangelogUiStateMapperTest`.
                            build = BuildRowUiState(
                                label = TextRes("BUILD"),
                                version = EnglishChangelog.releases.last().version,
                                headline = TextRes("The first commit"),
                                spoken = TextRes("Version 0.0.1 — The first commit. What changed."),
                            ),
                            compact = width < COMPACT_WIDTH,
                            onOpenChangelog = {},
                            onSelectMode = {},
                            onToggleCategory = {},
                            onSelectDelivery = {},
                            // **No account on these frames and none of the three callbacks reachable
                            // from them.** The two faces the account opens are their own baselines —
                            // see `DeleteFaceScreenshotTest` — and folding them in here would make
                            // four settings frames carry a section the sheet is not about.
                            delete = null,
                            onOpenDelete = {},
                            onKeepAccount = {},
                            onDeleteAccount = {},
                        )
                    }
                }
            }
            // Mid-swap frames stop at half the transition; everything else settles first.
            mainClock.advanceTimeBy(if (swapTo == null) SETTLED_MILLIS else HALF_SWAP_MILLIS)
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
        const val SEED = 20_260_807L

        // Half of the design's 210ms, which is where both faces are on screen at once.
        const val HALF_SWAP_MILLIS = 105L
    }
}
