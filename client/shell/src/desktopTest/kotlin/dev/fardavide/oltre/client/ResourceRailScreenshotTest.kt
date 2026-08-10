package dev.fardavide.oltre.client

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
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
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    ResourceRail(
                        uiState = ResourceRailUiState(
                            // Settled: what the player last saw is what the colony holds, so the
                            // roll has nowhere to travel and the bar draws its final figures on the
                            // first frame. These baselines are about the cells, not the arrival.
                            metal = ResourceStockUiState(
                                stock = 482_910,
                                lastSeenStock = 482_910,
                                ratePerHour = "+12,400/h",
                            ),
                            crystal = ResourceStockUiState(
                                stock = 198_340,
                                lastSeenStock = 198_340,
                                ratePerHour = "+6,180/h",
                            ),
                            deuterium = ResourceStockUiState(
                                stock = 74_120,
                                lastSeenStock = 74_120,
                                ratePerHour = "+900/h",
                            ),
                            throttled = throttled,
                        ),
                    )
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
