package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class FleetStripScreenshotTest {

    @Test
    fun `fleet strip with origin, composition and countdown`() {
        runDesktopComposeUiTest(width = 393, height = 80) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        FleetStrip(
                            uiState = ReturningFleetUiState(
                                title = "Fleet returning",
                                subtitle = "from [2:117:9] · 14 cargo · 1 cruiser",
                                countdown = "04:11:52",
                            ),
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/fleet_strip.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
