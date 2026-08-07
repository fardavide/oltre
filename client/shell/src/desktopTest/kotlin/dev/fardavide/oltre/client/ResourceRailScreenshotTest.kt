package dev.fardavide.oltre.client

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ResourceRailScreenshotTest {

    @Test
    fun `resource rail with metal stock and rate`() {
        capture(name = "resource_rail", throttled = false)
    }

    // The rail is the component that misled the player: it stated the throttled rate in the
    // production green with nothing to say the figure was being held down. Throttled, the same
    // strings take the amber and the mark, at no cost in width.
    @Test
    fun `resource rail while a power shortage throttles the rates`() {
        capture(name = "resource_rail_throttled", throttled = true)
    }

    private fun capture(name: String, throttled: Boolean) {
        runDesktopComposeUiTest {
            setContent {
                OltreTheme {
                    ResourceRail(
                        uiState = ResourceRailUiState(
                            metal = "482,910",
                            metalRatePerHour = "+12,400/h",
                            crystal = "198,340",
                            crystalRatePerHour = "+6,180/h",
                            deuterium = "74,120",
                            deuteriumRatePerHour = "+900/h",
                            throttled = throttled,
                        ),
                    )
                }
            }
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
