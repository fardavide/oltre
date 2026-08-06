package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.ResourceKind

// A mid-game colony: two builds running in parallel, a fleet on its way home, and rows in every
// action state. Shared by the layout assertions and the wide-window baselines.
//
// The energy figures are the ones these four rows actually add up to — solar 8 supplies 400
// against metal 12 and deuterium 16 drawing 120 and 320 — so the marks on the cards, the terms
// under the track and the fix line can all be checked against each other by eye in a baseline.
internal val testColonyUiState = ColonyUiState(
    // Short of power, so the wide baselines carry the whole vocabulary at once: the amber tail on
    // the indicator, the throttled rates on the rail, the marks that attribute the cut, and the
    // one line that says what ends it.
    energy = EnergyUiState(
        verdict = "every mine at 90%",
        terms = "400 produced · 440 drawn · 40 short",
        coveredFraction = 400f / 440f,
        deficit = true,
    ),
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
            power = FacilityPowerUiState(label = "−120", supply = false),
            fix = null,
        ),
        FacilityRowUiState(
            building = BuildingType.SOLAR_PLANT,
            name = "Solar Plant",
            level = BuildingLevel(8),
            costs = listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = "1,912", short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "757", short = false),
            ),
            duration = "1h 12m",
            action = FacilityActionUiState.Upgrade,
            power = FacilityPowerUiState(label = "+400", supply = true),
            fix = "→ LV 9 covers all 440 drawn",
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
            power = FacilityPowerUiState(label = "−320", supply = false),
            fix = null,
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
        ),
    ),
    returningFleet = ReturningFleetUiState(
        title = "Fleet returning",
        subtitle = "from [1:42:7] · 12 cargo",
        countdown = "02:11:40",
    ),
)
