package dev.fardavide.oltre.client.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Italian
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.text.Translations
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// **Every frame goes through the strip's own state, never through a tap.** A `performClick` before a
// capture bakes the press indication into the baseline and pins it there forever — and since 0.18
// there is nothing on the strip a tap changes anyway: the gear reports, and the frame answers
// somewhere else.
@OptIn(ExperimentalTestApi::class)
class PlayerStripScreenshotTest {

    @Test
    fun `player strip at rest`() {
        capture(name = "player_strip")
    }

    // The narrowest window the app has to survive. **Nothing gives**, which is the point of the
    // frame: with the gauge on the bar's edge the name has the whole row at 320dp as well as at 393,
    // and there is no compact rule left here to photograph.
    @Test
    fun `player strip in a Slide Over window`() {
        capture(name = "player_strip_slide_over", width = SLIDE_OVER_WIDTH)
    }

    // A colony several levels in, and the only frame in which the edge under the strip is lit at all.
    // What it is for is the objection the design records against itself — that a line pinned under a
    // bar reads as loading — which can only be judged by looking at it.
    @Test
    fun `player strip once there is something to show`() {
        capture(
            name = "player_strip_levelled",
            uiState = PlayerStripUiState(
                name = Strings.playerDefaultName(),
                level = Strings.levelBadge(7),
                experiencePercent = 62,
            ),
        )
    }

    // The longest name the design drew, at the width where it would have been cut. With the 72dp
    // inline track this ellipsised; the frame is what says the edge gauge bought the whole name back.
    @Test
    fun `player strip carrying the longest name drawn, in a Slide Over window`() {
        capture(
            name = "player_strip_long_name",
            width = SLIDE_OVER_WIDTH,
            uiState = PlayerStripUiState(
                // The longest of the alternates the design drew, and a `Raw` rather than a catalogue
                // entry because it is not the app's name — it is the width case, and a name is
                // untranslatable by construction anyway.
                name = TextRes("Contingency Of Ash"),
                level = Strings.levelBadge(7),
                experiencePercent = 62,
            ),
        )
    }

    private fun capture(
        name: String,
        width: Int = PHONE_WIDTH,
        uiState: PlayerStripUiState = newColonyPlayerStrip,
        translations: Translations = English,
    ) {
        runDesktopComposeUiTest(width = width, height = STRIP_FRAME_HEIGHT) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme(translations = translations) {
                    Surface {
                        // Filling the window is what pins the capture: `captureRoboImage` on
                        // `onRoot()` photographs the root node's measured bounds rather than the
                        // window, so without this the image would be the strip's own height and a
                        // one-pixel disagreement between two machines would fail on dimensions
                        // before it ever compared a pixel. The rail's own baselines learned this.
                        Box(modifier = Modifier.fillMaxSize()) {
                            PlayerStrip(uiState = uiState, onOpenSettings = {})
                        }
                    }
                }
            }
            // Past the one-shot fill, so the edge is photographed where it settles rather than
            // wherever the first frame caught it.
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    private companion object {

        // Taller than the 40dp strip by a clear band of window, so nothing is clipped and the edge
        // along the bottom has background under it to be an edge against.
        const val STRIP_FRAME_HEIGHT = 60
    }
}

// **The notice's two baselines left with 0.18**, and so did the two files they photographed. They
// held a card that said `Coming soon` in two languages, at two widths — the gear has a sheet behind
// it now, and what is worth a baseline is that sheet. See `:client:settings:ui`.
