package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.OltreTheme
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class FleetStripScreenshotTest {

    @Test
    fun `fleet strip with origin, composition and countdown`() {
        runDesktopComposeUiTest(width = 393, height = 80) {
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
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/fleet_strip.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
