package dev.fardavide.oltre.client

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.OltreTheme
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// The tab glyphs are drawn from paths rather than imported, so a baseline is the only thing that
// says they still look like a ringed world, a lab ring, a rocket, a galaxy and a fleet wedge.
// Phone width first — five destinations in 393dp is the tightest the bar ever gets on a phone —
// then Slide Over, which is narrower than any phone the game ships on.
@OptIn(ExperimentalTestApi::class)
class TabBarScreenshotTest {

    @Test
    fun `tab bar at phone width`() {
        captureBar(width = 393, name = "tab_bar_phone")
    }

    @Test
    fun `tab bar in a Slide Over window`() {
        captureBar(width = 320, name = "tab_bar_slide_over")
    }

    // The empty state carries the whole screen, so it is captured whole: an unbuilt tab is the
    // first thing a player who taps past the colony sees.
    @Test
    fun `an unbuilt tab`() {
        runDesktopComposeUiTest(width = 393, height = 852) {
            setContent {
                OltreTheme {
                    Surface {
                        MainScaffold { }
                    }
                }
            }
            onNodeWithTag(ShellTestTags.tab(OltreTab.GALAXY)).performClick()
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/unbuilt_tab_galaxy.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    private fun captureBar(width: Int, name: String) {
        // Tall enough for the bar and nothing else: the bar sizes itself, and a taller window
        // would bake the empty space above it into the baseline.
        runDesktopComposeUiTest(width = width, height = 60) {
            setContent {
                OltreTheme {
                    Surface {
                        OltreTabBar(selected = OltreTab.COLONY, onSelect = {})
                    }
                }
            }
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
