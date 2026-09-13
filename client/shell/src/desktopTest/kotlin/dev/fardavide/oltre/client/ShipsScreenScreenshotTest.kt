package dev.fardavide.oltre.client

import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// The composition itself — the head over whichever half is showing — rather than either half's own
// content, which is `ShipyardScreenScreenshotTest`'s and `FleetsScreenScreenshotTest`'s and does not
// move here. `MainScaffoldBehaviourTest` drives the chip switch through a real interaction; this is
// the frame at rest, on the mode a fresh Ships destination always opens on.
@OptIn(ExperimentalTestApi::class)
class ShipsScreenScreenshotTest {

    @Test
    fun `the composed screen on the mode it opens on`() {
        runDesktopComposeUiTest(width = 393, height = 200) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        ShipsScreen(
                            scrollState = rememberScrollState(),
                            mode = ShipsMode.SHIPYARD,
                            onSelectMode = {},
                            shipyard = { Text("shipyard-under-test") },
                            fleets = { Text("fleets-under-test") },
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/ships_screen.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    // **A real chip tap, recomposing the screen it switches — not a second screen composed fresh.**
    // The test above hands `ShipsScreen` a fixed `mode`; this one hoists it the way `MainScaffold`
    // actually does, and taps through, because a static capture of two different `mode` values is
    // two separate compositions and cannot be the thing that recomposing this screen looks like.
    @Test
    fun `tapping Fleets recomposes the screen rather than replacing it`() {
        runDesktopComposeUiTest(width = 393, height = 200) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        var mode by remember { mutableStateOf(ShipsMode.SHIPYARD) }
                        ShipsScreen(
                            scrollState = rememberScrollState(),
                            mode = mode,
                            onSelectMode = { mode = it },
                            shipyard = { Text("shipyard-under-test") },
                            fleets = { Text("fleets-under-test") },
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onNodeWithTag(ShellTestTags.shipsMode(ShipsMode.FLEETS)).performClick()
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/ships_screen_recomposed.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
