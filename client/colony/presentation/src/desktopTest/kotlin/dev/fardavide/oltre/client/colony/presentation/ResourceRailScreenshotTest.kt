package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import dev.fardavide.oltre.client.design.OltreTheme
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ResourceRailScreenshotTest {

    @Test
    fun `resource rail with metal stock and rate`() {
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
                            facilities = emptyList(),
                        ),
                    )
                }
            }
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/resource_rail.png",
                // Fonts are bundled, so glyphs match across OSes; 1% absorbs anti-aliasing
                // drift between the macOS recording environment and the Linux CI runner.
                roborazziOptions = RoborazziOptions(
                    compareOptions = RoborazziOptions.CompareOptions(
                        resultValidator = ThresholdValidator(0.01f),
                    ),
                ),
            )
        }
    }
}
