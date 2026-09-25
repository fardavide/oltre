package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.design.text.TextRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreMotion
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.advance
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalTestApi::class)
class ResourceRailScreenshotTest {

    @Test
    fun `resource rail with metal stock and rate`() {
        capture(name = "resource_rail", uiState = settledRail(throttled = false))
    }

    // The rail is the component that misled the player: it stated the throttled rate in the
    // production green with nothing to say the figure was being held down. Throttled, the same
    // strings take the amber and the mark, at no cost in width.
    @Test
    fun `resource rail while a power shortage throttles the rates`() {
        capture(name = "resource_rail_throttled", uiState = settledRail(throttled = true))
    }

    // A Slide Over pane, where the stock and its rate stop fitting one line. Left to the measurement
    // only two of the three cells would wrap and the bar would go ragged; below the compact width
    // every cell stacks, so the three stay a set. Taller than the wide capture by exactly the line
    // it gains.
    @Test
    fun `resource rail in a Slide Over window`() {
        capture(name = "resource_rail_slide_over", uiState = settledRail(throttled = false), width = SLIDE_OVER_WIDTH, height = SLIDE_OVER_HEIGHT)
    }

    // **Halfway through the arrival**, which is the one thing the rail does that the three frames
    // above cannot show: the count from the figure the player last saw to the one the colony has
    // accrued to, over 900ms, once. A settled frame of either end says nothing about it — and a
    // roll nothing photographs is a roll that can quietly stop happening, or start from the wrong
    // figure, which is the defect `App` is careful about when it sets `lastSeen` before the session.
    //
    // Built the way the app builds it rather than from a pair of numbers: a colony saved at noon and
    // resumed four hours later, the arrival read off the two states by `arrivalOf`, the rail by
    // `toResourceRailUiState`. So the figures rolling are the ones the game computes, and the frame
    // breaks on the day either mapping does.
    @Test
    fun `resource rail halfway through the arrival roll`() {
        val saved = GameState.initial(GalaxySeed(SEED))
        val resumed = advance(saved, from = TEST_NOW, to = TEST_NOW + 4.hours)
        val arrival = arrivalOf(saved = saved, resumed = resumed)
        capture(
            name = "resource_rail_arriving",
            uiState = resumed.toResourceRailUiState(lastSeen = arrival?.lastSeen),
            settleMillis = OltreMotion.FILL_MILLIS / 2L,
        )
    }

    private fun capture(
        name: String,
        uiState: ResourceRailUiState,
        width: Int = RAIL_WIDTH,
        height: Int = RAIL_HEIGHT,
        // How far the clock is wound before the shutter: past the roll for a settled frame, and to
        // the middle of it for the one frame that is about the roll.
        settleMillis: Long = SETTLED_MILLIS,
    ) {
        runDesktopComposeUiTest(width = width, height = height) {
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
                        ResourceRail(uiState = uiState)
                    }
                }
            }
            mainClock.advanceTimeBy(settleMillis)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    // Settled: what the player last saw is what the colony holds, so the roll has nowhere to travel
    // and the bar draws its final figures on the first frame. These baselines are about the cells,
    // not the arrival.
    private fun settledRail(throttled: Boolean) = ResourceRailUiState(
        metal = ResourceStockUiState(
            stock = 482_910,
            lastSeenStock = 482_910,
            ratePerHour = TextRes("+12,400/h"),
        ),
        crystal = ResourceStockUiState(
            stock = 198_340,
            lastSeenStock = 198_340,
            ratePerHour = TextRes("+6,180/h"),
        ),
        deuterium = ResourceStockUiState(
            stock = 74_120,
            lastSeenStock = 74_120,
            ratePerHour = TextRes("+900/h"),
        ),
        throttled = throttled,
    )

    private companion object {

        const val SEED = 20_260_807L

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

        // The narrowest window the app has to survive.
        const val SLIDE_OVER_WIDTH = 320

        // Taller than the stacked bar by a clear band of background. Erring tall costs a strip of
        // window; erring short silently clips the rate out of the baseline and asserts the
        // truncation forever — the failure the wide capture's own note was written about.
        const val SLIDE_OVER_HEIGHT = 100
    }
}
