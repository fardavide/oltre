package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.OltreTheme
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ResourceRailScreenshotTest {

    @Test
    fun `resource rail with metal stock and rate`() {
        capture(name = "resource_rail", deficit = false)
    }

    // The rail is the component that misled the player: it stated the throttled rate in the
    // production green with nothing to say the figure was being held down. Throttled, the same
    // strings take the amber and the mark, at no cost in width.
    @Test
    fun `resource rail while a power shortage throttles the rates`() {
        capture(name = "resource_rail_throttled", deficit = true)
    }

    private fun capture(name: String, deficit: Boolean) {
        runDesktopComposeUiTest {
            setContent {
                OltreTheme {
                    ResourceRail(
                        uiState = ColonyUiState(
                            metal = "482,910",
                            metalRatePerHour = "+12,400/h",
                            crystal = "198,340",
                            crystalRatePerHour = "+6,180/h",
                            deuterium = "74,120",
                            deuteriumRatePerHour = "+900/h",
                            energy = EnergyUiState(
                                verdict = if (deficit) "every mine at 70%" else "room for 7 mine levels",
                                terms = if (deficit) {
                                    "450 produced · 640 drawn · 190 short"
                                } else {
                                    "450 produced · 380 drawn · 70 spare"
                                },
                                coveredFraction = if (deficit) 450f / 640f else 380f / 450f,
                                deficit = deficit,
                            ),
                            facilities = emptyList(),
                            returningFleet = null,
                        ),
                    )
                }
            }
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
