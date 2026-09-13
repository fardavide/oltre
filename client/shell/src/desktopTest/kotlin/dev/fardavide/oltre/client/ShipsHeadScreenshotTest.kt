package dev.fardavide.oltre.client

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// The merged Ships destination's own navigation: the galaxy's `worlds · map` switch, the same
// component and the same text-pill style, with this feature's own two words. A baseline is the
// only thing that says the lit pill's fill and ink actually turn together, since nothing here is
// asserted by a behaviour test's semantics tree — `MainScaffoldBehaviourTest` covers which content
// shows, not what the switch itself looks like.
@OptIn(ExperimentalTestApi::class)
class ShipsHeadScreenshotTest {

    @Test
    fun `the head with Shipyard selected`() {
        captureHead(mode = ShipsMode.SHIPYARD, name = "ships_head_shipyard")
    }

    @Test
    fun `the head with Fleets selected`() {
        captureHead(mode = ShipsMode.FLEETS, name = "ships_head_fleets")
    }

    // Wide enough for "SHIPYARD" and "FLEETS" side by side with room either side — content-sized
    // text pills, unlike the fixed-size chips this replaced, need the frame to fit the longer word
    // rather than the other way round.
    private fun captureHead(mode: ShipsMode, name: String) {
        runDesktopComposeUiTest(width = 220, height = 60) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        ShipsHead(mode = mode, onSelectMode = {})
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
