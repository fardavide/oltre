package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.OltreTheme
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// All three states get a baseline. The healthy one is not the afterthought: it is what the player
// sees most weeks, and it is what makes the deficit legible when it arrives.
@OptIn(ExperimentalTestApi::class)
class PowerIndicatorScreenshotTest {

    @Test
    fun `power indicator while the colony has headroom`() {
        capture(
            name = "power_indicator_headroom",
            uiState = EnergyUiState(
                verdict = "room for 1 mine level",
                terms = "50 produced · 40 drawn · 10 spare",
                coveredFraction = 40f / 50f,
                deficit = false,
            ),
        )
    }

    @Test
    fun `power indicator while the colony is short of energy`() {
        capture(
            name = "power_indicator_deficit",
            uiState = EnergyUiState(
                verdict = "every mine at 55%",
                terms = "50 produced · 90 drawn · 40 short",
                coveredFraction = 50f / 90f,
                deficit = true,
            ),
        )
    }

    // The only total case: no green in the track at all, which is why it needs no new colour.
    @Test
    fun `power indicator with no plant at all`() {
        capture(
            name = "power_indicator_stopped",
            uiState = EnergyUiState(
                verdict = "every mine stopped",
                terms = "0 produced · 90 drawn · 90 short",
                coveredFraction = 0f,
                deficit = true,
            ),
        )
    }

    private fun capture(name: String, uiState: EnergyUiState) {
        // Roomier than the card needs. The card wraps its content, and a window sized to the
        // nominal 72dp leaves the terms line nothing to measure into once real font metrics are
        // applied — it disappears from the baseline rather than failing the test.
        runDesktopComposeUiTest(width = 393, height = 120) {
            setContent {
                OltreTheme {
                    Surface {
                        PowerIndicator(uiState = uiState, modifier = Modifier.padding(16.dp))
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
