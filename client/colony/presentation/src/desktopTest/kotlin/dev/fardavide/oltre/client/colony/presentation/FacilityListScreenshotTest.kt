package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.OltreTheme
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.ResourceKind
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class FacilityListScreenshotTest {

    @Test
    fun `facility list with building, affordable, unaffordable and locked rows`() {
        runDesktopComposeUiTest(width = 393, height = 500) {
            setContent {
                OltreTheme {
                    Surface {
                        FacilityList(
                            facilities = listOf(
                                FacilityRowUiState(
                                    building = BuildingType.METAL_MINE,
                                    name = "Metal Mine",
                                    level = BuildingLevel(12),
                                    costs = listOf(
                                        CostChipUiState(kind = ResourceKind.METAL, amount = "7,749", short = false),
                                        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "1,851", short = false),
                                    ),
                                    duration = "2h 10m",
                                    action = FacilityActionUiState.Upgrading(
                                        toLevel = BuildingLevel(13),
                                        countdown = "01:42:19",
                                        progressPercent = 68,
                                        doneAt = "done 11:23",
                                    ),
                                ),
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
                                        CostChipUiState(kind = ResourceKind.METAL, amount = "147,169", short = true),
                                        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "48,997", short = false),
                                    ),
                                    duration = "5h 40m",
                                    action = FacilityActionUiState.AffordableIn("in 3h 12m"),
                                ),
                                FacilityRowUiState(
                                    building = BuildingType.NANITE_FACTORY,
                                    name = "Nanite Factory",
                                    level = BuildingLevel(0),
                                    costs = listOf(
                                        CostChipUiState(kind = ResourceKind.METAL, amount = "20,000", short = false),
                                        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "10,000", short = false),
                                        CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = "4,000", short = false),
                                    ),
                                    duration = "2h 00m",
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
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
