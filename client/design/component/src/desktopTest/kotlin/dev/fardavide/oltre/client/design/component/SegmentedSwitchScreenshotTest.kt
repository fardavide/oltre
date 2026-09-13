package dev.fardavide.oltre.client.design.component

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.core.settlingColor
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// **The one control every "which of these am I looking at" surface in the app now shares** — text
// only, on both call sites, per Davide's call on 2026-09-13: the galaxy's `worlds · map` and the
// Ships destination's `shipyard · fleets` are the same component with different words, not two
// components that happen to agree. Two words stand in for each real pair here, since this module
// cannot see either feature's own labels.
@OptIn(ExperimentalTestApi::class)
class SegmentedSwitchScreenshotTest {

    @Test
    fun `the switch with the first option selected`() {
        capture(name = "segmented_switch_first", selectedIndex = 0)
    }

    @Test
    fun `the switch with the second option selected`() {
        capture(name = "segmented_switch_second", selectedIndex = 1)
    }

    // **A real selection change, not two independent captures of two independent states.** The two
    // tests above each compose the switch exactly once, which never asks Compose to recompose a
    // segment that already existed — the one thing a static capture structurally cannot do, whatever
    // state it is handed. A player tapping the other segment recomposes it for real, and this is
    // that tap, settled before the shutter.
    @Test
    fun `tapping the other segment recomposes it rather than replacing it`() {
        runDesktopComposeUiTest(width = 140, height = 60) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        var selected by remember { mutableStateOf(OPTIONS[0]) }
                        SegmentedSwitch(
                            options = OPTIONS,
                            selected = selected,
                            onSelect = { selected = it },
                            testTag = { "segment-$it" },
                        ) { option, on ->
                            Text(
                                text = option.uppercase(),
                                color = settlingColor(if (on) OltreColors.accent else OltreColors.textTertiary),
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 9.5.sp,
                            )
                        }
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onNodeWithTag("segment-${OPTIONS[1]}").performClick()
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/segmented_switch_recomposed.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    private fun capture(name: String, selectedIndex: Int) {
        runDesktopComposeUiTest(width = 140, height = 60) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        var selected by remember { mutableStateOf(OPTIONS[selectedIndex]) }
                        SegmentedSwitch(
                            options = OPTIONS,
                            selected = selected,
                            onSelect = { selected = it },
                            testTag = { "segment-$it" },
                        ) { option, on ->
                            Text(
                                text = option.uppercase(),
                                // The ink transition every real caller applies — `ModeLabel` and
                                // `ShipsModeLabel` both settle to this exact pair, and a preview
                                // that left it at the default content colour would show white text
                                // no caller's does.
                                color = settlingColor(if (on) OltreColors.accent else OltreColors.textTertiary),
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 9.5.sp,
                            )
                        }
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
        val OPTIONS = listOf("worlds", "map")
    }
}
