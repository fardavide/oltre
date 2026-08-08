package dev.fardavide.oltre.client

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
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

    @Test
    fun `the frame and the field behind it in a phone-sized window`() {
        runDesktopComposeUiTest(width = 393, height = 852) {
            setContent {
                OltreTheme {
                    Surface {
                        MainScaffold(
                            resources = testResourceRailUiState,
                            colony = { Text("colony-under-test") },
                            research = { Text("research-under-test") },
                            galaxy = { Text("galaxy-under-test") },
                        )
                    }
                }
            }
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/main_scaffold.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
