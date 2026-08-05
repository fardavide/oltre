package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import dev.fardavide.oltre.client.design.OltreTheme
import dev.fardavide.oltre.core.BuildingType
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class FacilityListScreenshotTest {

    @Test
    fun `facility list with affordable, unaffordable and locked rows`() {
        runDesktopComposeUiTest(width = 393, height = 420) {
            setContent {
                OltreTheme {
                    androidx.compose.material3.Surface {
                        FacilityList(
                        facilities = listOf(
                            FacilityRowUiState(
                                building = BuildingType.CRYSTAL_MINE,
                                name = "Crystal Mine",
                                level = 19,
                                metalCost = "212,480",
                                crystalCost = "106,240",
                                deuteriumCost = "0",
                                affordable = true,
                                locked = false,
                                lockedReason = null,
                            ),
                            FacilityRowUiState(
                                building = BuildingType.DEUTERIUM_SYNTHESIZER,
                                name = "Deuterium Synth.",
                                level = 16,
                                metalCost = "604,900",
                                crystalCost = "201,600",
                                deuteriumCost = "0",
                                affordable = false,
                                locked = false,
                                lockedReason = null,
                            ),
                            FacilityRowUiState(
                                building = BuildingType.NANITE_FACTORY,
                                name = "Nanite Factory",
                                level = 0,
                                metalCost = "1,000,000",
                                crystalCost = "500,000",
                                deuteriumCost = "100,000",
                                affordable = true,
                                locked = true,
                                lockedReason = "Requires Robotics 10",
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
