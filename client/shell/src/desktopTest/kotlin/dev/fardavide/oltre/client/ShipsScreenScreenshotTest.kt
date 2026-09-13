package dev.fardavide.oltre.client

import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
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
}
