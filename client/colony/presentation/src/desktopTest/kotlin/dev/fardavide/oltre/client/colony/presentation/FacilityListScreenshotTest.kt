package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import dev.fardavide.oltre.client.design.OltreTheme
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.ResourceKind
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class FacilityListScreenshotTest {

    @Test
    fun `facility list with affordable, unaffordable and locked rows`() {
        runDesktopComposeUiTest(width = 393, height = 420) {
            setContent {
                OltreTheme {
                    Surface {
                        FacilityList(
                            facilities = listOf(
                                FacilityRowUiState(
                                    building = BuildingType.ROBOTICS_FACTORY,
                                    name = "Robotics Factory",
                                    level = BuildingLevel(0),
                                    costs = listOf(
                                        CostChipUiState(kind = ResourceKind.METAL, amount = "400", short = false),
                                        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "120", short = false),
                                        CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = "200", short = false),
                                    ),
                                    duration = "30m",
                                    action = FacilityActionUiState.Upgrade,
                                ),
                                FacilityRowUiState(
                                    building = BuildingType.DEUTERIUM_SYNTHESIZER,
                                    name = "Deuterium Synth.",
                                    level = BuildingLevel(16),
                                    costs = listOf(
                                        CostChipUiState(kind = ResourceKind.METAL, amount = "604,900", short = true),
                                        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "201,600", short = false),
                                    ),
                                    duration = "2h 51m",
                                    action = FacilityActionUiState.AffordableIn("in 3h 12m"),
                                ),
                                FacilityRowUiState(
                                    building = BuildingType.NANITE_FACTORY,
                                    name = "Nanite Factory",
                                    level = BuildingLevel(0),
                                    costs = listOf(
                                        CostChipUiState(kind = ResourceKind.METAL, amount = "1,000,000", short = false),
                                        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "500,000", short = false),
                                        CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = "100,000", short = false),
                                    ),
                                    duration = "4h 00m",
                                    action = FacilityActionUiState.Locked("Requires Robotics 10"),
                                ),
                            ),
                            onUpgrade = {},
                        )
                    }
                }
            }
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/facility_list.png",
                roborazziOptions = RoborazziOptions(
                    compareOptions = RoborazziOptions.CompareOptions(
                        resultValidator = ThresholdValidator(0.01f),
                    ),
                ),
            )
        }
    }
}
