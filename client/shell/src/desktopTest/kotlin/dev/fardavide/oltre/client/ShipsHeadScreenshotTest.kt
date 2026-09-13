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

// The merged Ships destination's own navigation, `alliance-sheet.md` §7: two glyph chips at the tap
// minimum, raised from the galaxy's 22dp filter row. A baseline is the only thing that says the
// lit chip's fill and ink actually turn together, since nothing here is asserted by a behaviour
// test's semantics tree — `MainScaffoldBehaviourTest` covers which content shows, not what the
// switch itself looks like.
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

    private fun captureHead(mode: ShipsMode, name: String) {
        runDesktopComposeUiTest(width = 120, height = 60) {
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
