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
import dev.fardavide.oltre.client.design.text.Translations
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// **Every frame goes through `PlayerStripContent` with the notice as a parameter**, never through a
// tap. A `performClick` before a capture bakes the press indication into the baseline and pins it
// there forever — and the notice has a lifetime, so a captured tap would also be a race.
@OptIn(ExperimentalTestApi::class)
class PlayerStripScreenshotTest {

    @Test
    fun `player strip at rest`() {
        capture(name = "player_strip")
    }

    // The narrowest window the app has to survive. The gauge is what gives; the name does not, and
    // the height does not move.
    @Test
    fun `player strip in a Slide Over window`() {
        capture(name = "player_strip_slide_over", width = SLIDE_OVER_WIDTH)
    }

    @Test
    fun `player strip while the settings are said to be coming`() {
        capture(name = "player_strip_coming_soon", noticeShown = true)
    }

    // The notice is the one string here whose two languages differ in width, at the width where that
    // could bite. `Prossimamente` is longer than `Coming soon` and the cluster it displaces is wider
    // than either, which is what makes the displacement safe rather than lucky.
    @Test
    fun `player strip saying the settings are coming in Italian`() {
        capture(
            name = "player_strip_coming_soon_it",
            width = SLIDE_OVER_WIDTH,
            noticeShown = true,
            translations = Italian,
        )
    }

    // A colony several levels in. Drawn at 0.16 as the state the slice was building toward, when no
    // launch could reach it; from 0.17 a played save does, and the frame is the one that says a
    // gauge with something in it still fits between the name and the gear.
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

    private fun capture(
        name: String,
        width: Int = PHONE_WIDTH,
        uiState: PlayerStripUiState = newColonyPlayerStrip,
        noticeShown: Boolean = false,
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
                            PlayerStripContent(
                                uiState = uiState,
                                noticeShown = noticeShown,
                                onOpenSettings = {},
                            )
                        }
                    }
                }
            }
            // Past the one-shot fill, so the gauge is photographed where it settles rather than
            // wherever the first frame caught it.
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    private companion object {

        // Taller than the 38dp strip by a clear band of window, so nothing is clipped and the
        // hairline along the bottom edge has background under it to be a hairline against.
        const val STRIP_FRAME_HEIGHT = 60
    }
}
