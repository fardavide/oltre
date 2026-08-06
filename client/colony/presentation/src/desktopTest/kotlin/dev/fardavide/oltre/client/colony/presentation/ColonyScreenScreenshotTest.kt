package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.OltreTheme
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// The whole screen at iPad-landscape size (1194×834pt, 11"): what the centred column actually
// looks like when the window is far wider than a phone. The component baselines cover phone
// width; this one is here so a regression in the wide layout is visible, not just asserted.
@OptIn(ExperimentalTestApi::class)
class ColonyScreenScreenshotTest {

    @Test
    fun `colony screen in an iPad-sized window`() {
        runDesktopComposeUiTest(width = 1194, height = 834) {
            setContent {
                OltreTheme {
                    Surface {
                        ColonyScreen(uiState = testColonyUiState, onUpgrade = {})
                    }
                }
            }
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/colony_screen_wide.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
