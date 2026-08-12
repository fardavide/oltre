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

// The tab glyphs are drawn from paths rather than imported, so a baseline is the only thing that
// says they still look like a ringed world, a lab ring, a rocket, a galaxy and a fleet wedge.
//
// Nothing here performs a gesture. The first recording captured the frame after tapping Galaxy
// and baked the tab's hover highlight into the baseline — an interaction state, asserted forever.
// A screenshot renders a state; the selected tab is passed in.
@OptIn(ExperimentalTestApi::class)
class TabBarScreenshotTest {

    @Test
    fun `tab bar at phone width`() {
        captureBar(width = 393, selected = OltreTab.COLONY, name = "tab_bar_phone")
    }

    // Narrower than any phone the game ships on, and reachable since the app became a real iPad
    // app: five destinations still have to fit.
    @Test
    fun `tab bar in a Slide Over window`() {
        captureBar(width = 320, selected = OltreTab.COLONY, name = "tab_bar_slide_over")
    }

    // A destination other than the first, so the accent tint is covered on a tab that has to sit
    // between neighbours rather than at the edge.
    @Test
    fun `tab bar with a later destination selected`() {
        captureBar(width = 393, selected = OltreTab.GALAXY, name = "tab_bar_galaxy_selected")
    }

    // **`an unbuilt tab` was here and it is retired.** Galaxy carried the baseline until 0.0.15 gave
    // it a real screen, Shipyard inherited it, and at 0.8.0 there is no unbuilt tab left to
    // photograph: `UnbuiltTabScreen` lost its last caller and went with it, and
    // `unbuilt_tab_shipyard.png` is deleted rather than left as a picture of a screen that no longer
    // exists. What replaced its coverage is the two new tabs' own baselines, which is the right
    // trade — the empty state was one drawing shared by two destinations, and each of them has a
    // drawing of its own now.

    // Tall enough for the whole bar and little else. The first recording used 60px, which clipped
    // the labels off the bottom — the bar needs ~68dp — and produced a baseline of five icons
    // over empty space that would have passed forever.
    private fun captureBar(width: Int, selected: OltreTab, name: String) {
        runDesktopComposeUiTest(width = width, height = 84) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        OltreTabBar(selected = selected, onSelect = {})
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
