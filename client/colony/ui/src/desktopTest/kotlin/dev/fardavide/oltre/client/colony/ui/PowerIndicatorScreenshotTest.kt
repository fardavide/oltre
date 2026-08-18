package dev.fardavide.oltre.client.colony.ui

import dev.fardavide.oltre.client.design.text.TextRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
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
                verdict = TextRes("room for 1 mine level"),
                terms = TextRes("50 produced · 40 drawn · 10 spare"),
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
                verdict = TextRes("every mine at 55%"),
                terms = TextRes("50 produced · 90 drawn · 40 short"),
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
                verdict = TextRes("every mine stopped"),
                terms = TextRes("0 produced · 90 drawn · 90 short"),
                coveredFraction = 0f,
                deficit = true,
            ),
        )
    }

    // The narrowest supported window against the longest terms string the balance can reach.
    // At 320dp the card's interior is 266dp and the line holds 38 characters; a deep colony —
    // solar 20 against metal 25, crystal 31 and deuterium 27 — produces 40. This is the case
    // that clipped its last term silently before the line was allowed to wrap, so the baseline
    // exists to keep it wrapping.
    @Test
    fun `power indicator at Slide Over width with four-digit terms`() {
        // Tall enough for the wrapped second line. On the real screen the card sits in a
        // vertical scroll and has no height ceiling at all; only this harness imposes one.
        runDesktopComposeUiTest(width = 320, height = 150) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        PowerIndicator(
                            uiState = EnergyUiState(
                                verdict = TextRes("every mine at 90%"),
                                terms = TextRes("1,000 produced · 1,100 drawn · 100 short"),
                                coveredFraction = 1000f / 1100f,
                                deficit = true,
                            ),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/power_indicator_slide_over.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    private fun capture(name: String, uiState: EnergyUiState) {
        // Roomier than the card needs. The card wraps its content, and a window sized to the
        // nominal 72dp leaves the terms line nothing to measure into once real font metrics are
        // applied — it disappears from the baseline rather than failing the test.
        runDesktopComposeUiTest(width = 393, height = 120) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        PowerIndicator(uiState = uiState, modifier = Modifier.padding(16.dp))
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
