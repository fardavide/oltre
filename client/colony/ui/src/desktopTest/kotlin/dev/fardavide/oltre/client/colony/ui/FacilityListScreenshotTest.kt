package dev.fardavide.oltre.client.colony.ui

import dev.fardavide.oltre.client.design.text.TextRes
import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.component.VerdictUiState
import dev.fardavide.oltre.client.design.component.WatchUiState
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
        runDesktopComposeUiTest(width = 393, height = FOUR_ROWS) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        FacilityList(
                            facilities = listOf(
                                FacilityRowUiState(
                                    building = BuildingType.METAL_MINE,
                                    name = TextRes("Metal Mine"),
                                    compactName = TextRes("Metal Mine"),
                                    level = BuildingLevel(12),
                                    costs = listOf(
                                        CostChipUiState(kind = ResourceKind.METAL, amount = TextRes("7,749"), short = false),
                                        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = TextRes("1,851"), short = false),
                                    ),
                                    duration = TextRes("2h 10m"),
                                    action = FacilityActionUiState.Upgrading(
                                        toLevel = BuildingLevel(13),
                                        countdown = TextRes("01:42:19"),
                                        progressPercent = 68,
                                        doneAt = TextRes("done 11:23"),
                                    ),
                                    power = null,
                                    fix = null,
                                    watch = WatchUiState.Offered,
                                    // The only row in the frame with no verdict, and the frame is
                                    // where that reads: three cards carry a second line and the one
                                    // already building does not.
                                    verdict = null,
                                    detail = EMPTY_DETAIL,
                                    finishedWhileAway = false,
                                ),
                                FacilityRowUiState(
                                    building = BuildingType.ROBOTICS_FACTORY,
                                    name = TextRes("Robotics Factory"),
                                    compactName = TextRes("Robotics Factory"),
                                    level = BuildingLevel(0),
                                    costs = listOf(
                                        CostChipUiState(kind = ResourceKind.METAL, amount = TextRes("400"), short = false),
                                        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = TextRes("120"), short = false),
                                        CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = TextRes("200"), short = false),
                                    ),
                                    duration = TextRes("30m"),
                                    action = FacilityActionUiState.Upgrade,
                                    power = null,
                                    fix = null,
                                    watch = null,
                                    verdict = VerdictUiState(
                                        label = TextRes("−12m per build · LV 1 → research"),
                                        compactLabel = TextRes("−12m per build"),
                                    ),
                                    detail = EMPTY_DETAIL,
                                    finishedWhileAway = false,
                                ),
                                FacilityRowUiState(
                                    building = BuildingType.DEUTERIUM_SYNTHESIZER,
                                    name = TextRes("Deuterium Synth."),
                                    compactName = TextRes("Deuterium Synth."),
                                    level = BuildingLevel(16),
                                    costs = listOf(
                                        CostChipUiState(kind = ResourceKind.METAL, amount = TextRes("147,169"), short = true),
                                        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = TextRes("48,997"), short = false),
                                    ),
                                    duration = TextRes("5h 40m"),
                                    action = FacilityActionUiState.AffordableIn(TextRes("in 3h 12m")),
                                    power = null,
                                    fix = null,
                                    watch = WatchUiState.Offered,
                                    verdict = VerdictUiState(
                                        label = TextRes("+41/h deuterium · back in 61h"),
                                        compactLabel = TextRes("+41/h deuterium"),
                                    ),
                                    detail = EMPTY_DETAIL,
                                    finishedWhileAway = false,
                                ),
                                FacilityRowUiState(
                                    building = BuildingType.NANITE_FACTORY,
                                    name = TextRes("Nanite Factory"),
                                    compactName = TextRes("Nanite Factory"),
                                    level = BuildingLevel(0),
                                    costs = listOf(
                                        CostChipUiState(kind = ResourceKind.METAL, amount = TextRes("20,000"), short = false),
                                        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = TextRes("10,000"), short = false),
                                        CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = TextRes("4,000"), short = false),
                                    ),
                                    duration = TextRes("2h 00m"),
                                    action = FacilityActionUiState.Locked(TextRes("Requires Robotics 10")),
                                    power = null,
                                    fix = null,
                                    watch = null,
                                    // Under the requirement rather than over it, and at the same
                                    // 42% as the rest of the card — which is the case the whole
                                    // "state the payoff on day one" argument has to survive.
                                    verdict = VerdictUiState(
                                        label = TextRes("A 298h build takes 26h at LV 6"),
                                        compactLabel = TextRes("298h builds take 26h at LV 6"),
                                    ),
                                    detail = EMPTY_DETAIL,
                                    finishedWhileAway = false,
                                ),
                            ),
                            onUpgrade = {},
                            compact = false,
                            onToggleWatch = {},
                            onOpenDetail = {},
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
        runDesktopComposeUiTest(width = 393, height = ONE_ROW) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        FacilityList(
                            facilities = listOf(
                                FacilityRowUiState(
                                    building = BuildingType.SOLAR_PLANT,
                                    name = TextRes("Solar Plant"),
                                    compactName = TextRes("Solar Plant"),
                                    level = BuildingLevel(9),
                                    costs = listOf(
                                        CostChipUiState(kind = ResourceKind.METAL, amount = TextRes("2,868"), short = false),
                                        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = TextRes("1,135"), short = false),
                                    ),
                                    duration = TextRes("1h 48m"),
                                    action = FacilityActionUiState.Upgrade,
                                    power = null,
                                    fix = null,
                                    watch = null,
                                    verdict = VerdictUiState(
                                        label = TextRes("+50 supply · draw already covered"),
                                        compactLabel = TextRes("+50 supply"),
                                    ),
                                    detail = EMPTY_DETAIL,
                                    finishedWhileAway = true,
                                ),
                            ),
                            onUpgrade = {},
                            compact = false,
                            onToggleWatch = {},
                            onOpenDetail = {},
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
        runDesktopComposeUiTest(width = 393, height = FOUR_ROWS) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        FacilityList(
                            facilities = listOf(
                                // Supply, and the one line that says what ends the shortage. The
                                // verdict sits above it: what the level is worth, then what it fixes.
                                FacilityRowUiState(
                                    building = BuildingType.SOLAR_PLANT,
                                    name = TextRes("Solar Plant"),
                                    compactName = TextRes("Solar Plant"),
                                    level = BuildingLevel(1),
                                    costs = listOf(
                                        CostChipUiState(kind = ResourceKind.METAL, amount = TextRes("112"), short = true),
                                        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = TextRes("45"), short = false),
                                    ),
                                    duration = TextRes("16m"),
                                    action = FacilityActionUiState.AffordableIn(TextRes("in 15m")),
                                    power = FacilityPowerUiState(label = TextRes("+50"), supply = true),
                                    fix = TextRes("→ LV 2 covers all 90 drawn"),
                                    watch = WatchUiState.Offered,
                                    verdict = VerdictUiState(
                                        label = TextRes("+63/h metal · back in 19m"),
                                        compactLabel = TextRes("+63/h metal"),
                                    ),
                                    detail = EMPTY_DETAIL,
                                    finishedWhileAway = false,
                                ),
                                // A draw on a row that is affordable: taking it deepens the
                                // throttle, and the screen says so without arguing about it.
                                FacilityRowUiState(
                                    building = BuildingType.METAL_MINE,
                                    name = TextRes("Metal Mine"),
                                    compactName = TextRes("Metal Mine"),
                                    level = BuildingLevel(3),
                                    costs = listOf(
                                        CostChipUiState(kind = ResourceKind.METAL, amount = TextRes("202"), short = false),
                                        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = TextRes("50"), short = false),
                                    ),
                                    duration = TextRes("40m"),
                                    action = FacilityActionUiState.Upgrade,
                                    power = FacilityPowerUiState(label = TextRes("−30"), supply = false),
                                    fix = null,
                                    watch = null,
                                    verdict = VerdictUiState(
                                        label = TextRes("throttles every mine · Solar Plant 2 covers it"),
                                        compactLabel = TextRes("throttles every mine"),
                                    ),
                                    detail = EMPTY_DETAIL,
                                    finishedWhileAway = false,
                                ),
                                FacilityRowUiState(
                                    building = BuildingType.CRYSTAL_MINE,
                                    name = TextRes("Crystal Mine"),
                                    compactName = TextRes("Crystal Mine"),
                                    level = BuildingLevel(2),
                                    costs = emptyList(),
                                    duration = TextRes("36m"),
                                    action = FacilityActionUiState.Upgrading(
                                        toLevel = BuildingLevel(3),
                                        countdown = TextRes("00:07:12"),
                                        progressPercent = 62,
                                        doneAt = TextRes("done 09:41"),
                                    ),
                                    power = FacilityPowerUiState(label = TextRes("−20"), supply = false),
                                    fix = null,
                                    watch = WatchUiState.Offered,
                                    verdict = null,
                                    detail = EMPTY_DETAIL,
                                    finishedWhileAway = false,
                                ),
                                // Not built, so it draws nothing and carries no mark — there is
                                // nothing to attribute and nothing to fight the dim.
                                FacilityRowUiState(
                                    building = BuildingType.NANITE_FACTORY,
                                    name = TextRes("Nanite Factory"),
                                    compactName = TextRes("Nanite Factory"),
                                    level = BuildingLevel(0),
                                    costs = listOf(
                                        CostChipUiState(kind = ResourceKind.METAL, amount = TextRes("20,000"), short = false),
                                        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = TextRes("10,000"), short = false),
                                        CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = TextRes("4,000"), short = false),
                                    ),
                                    duration = TextRes("2h 00m"),
                                    action = FacilityActionUiState.Locked(TextRes("Requires Robotics 10")),
                                    power = null,
                                    fix = null,
                                    watch = null,
                                    verdict = VerdictUiState(
                                        label = TextRes("A 271h build takes 23h 49m at LV 6"),
                                        compactLabel = TextRes("271h builds take 23h 49m at LV 6"),
                                    ),
                                    detail = EMPTY_DETAIL,
                                    finishedWhileAway = false,
                                ),
                            ),
                            onUpgrade = {},
                            compact = false,
                            onToggleWatch = {},
                            onOpenDetail = {},
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

        // **Every row grew by a verdict**, which is one 10.5sp line over a 15sp leading plus the
        // card's own 4dp between lines — 19dp, and the running row is the only one that does not
        // take it. So four rows are 500 + 3 × 19 = 557 and one row is 120 + 19 = 139, both rounded
        // up to the next ten: a window a few pixels short clips the last card into the baseline and
        // says nothing about it.
        const val FOUR_ROWS: Int = 560
        const val ONE_ROW: Int = 140

        // A row's `detail` is what the *sheet* draws, and these frames are about the card. Empty
        // rather than plausible: a fixture nothing renders is a fixture nothing can be wrong about.
        // `FacilitySheetScreenshotTest` is where it is filled in.
        val EMPTY_DETAIL = FacilityDetailUiState(lines = emptyList(), ladder = emptyList(), pointer = null)
    }
}
