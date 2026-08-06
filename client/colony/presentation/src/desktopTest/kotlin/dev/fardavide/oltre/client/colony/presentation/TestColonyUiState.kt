package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.ResourceKind

// A mid-game colony: two builds running in parallel, a fleet on its way home, and rows in every
// action state. Shared by the layout assertions and the wide-window baseline.
internal val testColonyUiState = ColonyUiState(
    metal = "8,420",
    metalRatePerHour = "+310/h",
    crystal = "3,180",
    crystalRatePerHour = "+140/h",
    deuterium = "960",
    deuteriumRatePerHour = "+45/h",
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
            building = BuildingType.SOLAR_PLANT,
            name = "Solar Plant",
            level = BuildingLevel(9),
            costs = listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = "2,306", short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "922", short = false),
            ),
            duration = "48m",
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
    returningFleet = ReturningFleetUiState(
        title = "Fleet returning",
        subtitle = "from [1:42:7] · 12 cargo",
        countdown = "02:11:40",
    ),
)
