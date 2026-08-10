package dev.fardavide.oltre.client

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.tilt.domain.Tilt
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// The frame as a whole, and the only baseline that sees the starfield at all: it is drawn inside
// the destination box, so every per-screen baseline in the repo renders the screen without the
// scaffold around it and never captures a single star.
//
// That gap is worth closing rather than noting. The reason the field is twenty-six hard-coded
// positions instead of a seeded generator is precisely that a screenshot test has to be able to
// hold it still — which is an argument for a baseline that would notice, and there was none.
//
// The destination is deliberately a bare marker rather than a real screen. What this baseline is
// for is the chrome and the layer behind it: the rail above, the tab bar below, and the field in
// between with nothing occluding it. A real colony here would cover the stars with cards and
// re-assert what the colony's own baselines already say.
@OptIn(ExperimentalTestApi::class)
class MainScaffoldScreenshotTest {

    // The parallax's only witness. The field behind a destination is a function of that
    // destination's scroll offset and of nothing else, so the way to photograph it is to hand it an
    // offset — no gesture, no clock, no scrollable content to measure against.
    //
    // 620 is past a phone's own height, which is the case the un-wrapped first draft got wrong: the
    // near plane keeps 58% of the list's speed, so at this offset an unwrapped field would have
    // carried its stars 360dp up and left the bottom of the frame empty. What this baseline holds is
    // that the sky still reaches both edges.
    @Test
    fun `the field behind a destination that has been scrolled`() {
        runDesktopComposeUiTest(width = 393, height = 600) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        Starfield(scrollOffset = { 620f })
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/starfield_scrolled.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    @Test
    fun `the frame and the field behind it in a phone-sized window`() {
        runDesktopComposeUiTest(width = 393, height = 852) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        MainScaffold(
                            // Desktop has no motion sensor, so this is also what the app itself passes here.
                            tilt = { Tilt.NONE },
                            resources = testResourceRailUiState,
                            colony = { Text("colony-under-test") },
                            research = { Text("research-under-test") },
                            galaxy = { _, _ -> Text("galaxy-under-test") },
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/main_scaffold.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
