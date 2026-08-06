package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.OltreTheme
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// Both states get a baseline: the shortage is the one that matters, but the healthy line is what
// the player sees most of the time and is what makes the shortage legible when it arrives.
@OptIn(ExperimentalTestApi::class)
class PowerStripScreenshotTest {

    @Test
    fun `power strip while the colony is short of energy`() {
        runDesktopComposeUiTest(width = 393, height = 40) {
            setContent {
                OltreTheme {
                    PowerStrip(
                        uiState = EnergyUiState(
                            reading = "50 / 90",
                            consequence = "every mine at 55%",
                            deficit = true,
                        ),
                    )
                }
            }
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/power_strip_deficit.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    @Test
    fun `power strip while the colony has headroom`() {
        runDesktopComposeUiTest(width = 393, height = 40) {
            setContent {
                OltreTheme {
                    PowerStrip(
                        uiState = EnergyUiState(
                            reading = "50 / 40",
                            consequence = "+10 spare",
                            deficit = false,
                        ),
                    )
                }
            }
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/power_strip_headroom.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
