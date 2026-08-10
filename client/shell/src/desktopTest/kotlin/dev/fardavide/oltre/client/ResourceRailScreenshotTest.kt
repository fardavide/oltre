package dev.fardavide.oltre.client

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
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
        runDesktopComposeUiTest(width = RAIL_WIDTH, height = RAIL_HEIGHT) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    // Filling the window is what actually pins the capture. `captureRoboImage` on
                    // `onRoot()` photographs the root *node's* measured bounds, not the window — so
                    // stating a window size did nothing on its own, and the image was still the
                    // rail's own text-driven height. Every other screenshot in the repo renders
                    // something that fills its window, which is why this was the only one that could
                    // come out 67 pixels tall on Linux against 68 on macOS.
                    Box(modifier = Modifier.fillMaxSize()) {
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
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    private companion object {

        // **The one screenshot test in the repo that used to state no window size**, and the only
        // one that could fail on a mismatch of *dimensions* rather than of pixels. A one-pixel
        // difference in height is not something a tolerance can absorb: Roborazzi compares sizes
        // before it compares anything else, and fails outright.
        //
        // 1024 is the width it was already rendering at, so the composition is unchanged: the rail
        // is full-bleed and its cells stay on the 560dp centred column, which is what these
        // baselines are about. 68 clears the taller of the two measurements, so neither platform
        // clips and the pixel or two below the bar is window background.
        const val RAIL_WIDTH = 1024
        const val RAIL_HEIGHT = 68
    }
}
