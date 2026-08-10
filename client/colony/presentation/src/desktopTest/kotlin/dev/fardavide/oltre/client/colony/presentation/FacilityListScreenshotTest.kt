package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
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
            mainClock.autoAdvance = false
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
                                    power = null,
                                    fix = null,
                                    finishedWhileAway = false,
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
                                    power = null,
                                    fix = null,
                                    finishedWhileAway = false,
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
                                    power = null,
                                    fix = null,
                                    finishedWhileAway = false,
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
                                    power = null,
                                    fix = null,
                                    finishedWhileAway = false,
                                ),
                            ),
                            onUpgrade = {},
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/facility_list.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    // The one frame in the repo that is deliberately captured *mid*-transition, and the only way to
    // see the completion sweep at all: it is 750ms of light crossing a card once, so a settled
    // baseline of the same row is a baseline of a row with nothing on it.
    //
    // 795ms is 420ms of delay plus half of the 750ms crossing, so the band is at the middle of the
    // card and at its brightest. It is deterministic for the same reason every other baseline here
    // is — the clock is stopped and wound by hand, so this is not a race with a real frame time.
    //
    // The badge still reads LV 8 rather than LV 9: the swap is at 930ms, so at 795 the band has not
    // reached it yet. That is the assertion, not a detail — the level changes behind the light.
    @Test
    fun `the row that finished while the app was closed mid-sweep`() {
        runDesktopComposeUiTest(width = 393, height = 120) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        FacilityList(
                            facilities = listOf(
                                FacilityRowUiState(
                                    building = BuildingType.SOLAR_PLANT,
                                    name = "Solar Plant",
                                    level = BuildingLevel(9),
                                    costs = listOf(
                                        CostChipUiState(kind = ResourceKind.METAL, amount = "2,868", short = false),
                                        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "1,135", short = false),
                                    ),
                                    duration = "1h 48m",
                                    action = FacilityActionUiState.Upgrade,
                                    power = null,
                                    fix = null,
                                    finishedWhileAway = true,
                                ),
                            ),
                            onUpgrade = {},
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(MID_SWEEP_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/facility_list_finished_while_away.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    // The mark in every state it can occur in, plus the two things it has to share a line with:
    // the red chip of a resource you are short of — a different channel, so they do not conflict
    // — and the accent line of a row that is building.
    @Test
    fun `facility list while a power shortage throttles it`() {
        runDesktopComposeUiTest(width = 393, height = 500) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        FacilityList(
                            facilities = listOf(
                                // Supply, and the one line that says what ends the shortage.
                                FacilityRowUiState(
                                    building = BuildingType.SOLAR_PLANT,
                                    name = "Solar Plant",
                                    level = BuildingLevel(1),
                                    costs = listOf(
                                        CostChipUiState(kind = ResourceKind.METAL, amount = "112", short = true),
                                        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "45", short = false),
                                    ),
                                    duration = "16m",
                                    action = FacilityActionUiState.AffordableIn("in 15m"),
                                    power = FacilityPowerUiState(label = "+50", supply = true),
                                    fix = "→ LV 2 covers all 90 drawn",
                                    finishedWhileAway = false,
                                ),
                                // A draw on a row that is affordable: taking it deepens the
                                // throttle, and the screen says so without arguing about it.
                                FacilityRowUiState(
                                    building = BuildingType.METAL_MINE,
                                    name = "Metal Mine",
                                    level = BuildingLevel(3),
                                    costs = listOf(
                                        CostChipUiState(kind = ResourceKind.METAL, amount = "202", short = false),
                                        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "50", short = false),
                                    ),
                                    duration = "40m",
                                    action = FacilityActionUiState.Upgrade,
                                    power = FacilityPowerUiState(label = "−30", supply = false),
                                    fix = null,
                                    finishedWhileAway = false,
                                ),
                                FacilityRowUiState(
                                    building = BuildingType.CRYSTAL_MINE,
                                    name = "Crystal Mine",
                                    level = BuildingLevel(2),
                                    costs = emptyList(),
                                    duration = "36m",
                                    action = FacilityActionUiState.Upgrading(
                                        toLevel = BuildingLevel(3),
                                        countdown = "00:07:12",
                                        progressPercent = 62,
                                        doneAt = "done 09:41",
                                    ),
                                    power = FacilityPowerUiState(label = "−20", supply = false),
                                    fix = null,
                                    finishedWhileAway = false,
                                ),
                                // Not built, so it draws nothing and carries no mark — there is
                                // nothing to attribute and nothing to fight the dim.
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
                                    power = null,
                                    fix = null,
                                    finishedWhileAway = false,
                                ),
                            ),
                            onUpgrade = {},
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/facility_list_throttled.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    private companion object {
        // 420ms of delay plus half of the 750ms crossing: the band is at the middle of the card,
        // and 135ms short of the level swap at 930ms.
        const val MID_SWEEP_MILLIS: Long = 795
    }
}
