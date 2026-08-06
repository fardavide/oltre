package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.ResourceKind

// A mid-game colony: a build running, a fleet on its way home, and facility rows in all three
// action states. Shared by the layout assertions and the wide-window baseline.
internal val testColonyUiState = ColonyUiState(
    metal = "482,910",
    metalRatePerHour = "+12,400/h",
    crystal = "198,340",
    crystalRatePerHour = "+6,180/h",
    deuterium = "74,120",
    deuteriumRatePerHour = "+900/h",
    facilities = listOf(
        FacilityRowUiState(
            building = BuildingType.METAL_MINE,
            name = "Metal Mine",
            level = BuildingLevel(18),
            costs = listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = "120,400", short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "30,100", short = false),
            ),
            duration = "1h 04m",
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
    inProgress = InProgressUiState(
        title = "Crystal Mine → 9",
        countdown = "00:41:12",
        progressPercent = 62,
        doneAt = "done 21:14",
    ),
    returningFleet = ReturningFleetUiState(
        title = "Fleet returning",
        subtitle = "from [1:42:7] · 12 cargo",
        countdown = "02:11:40",
    ),
)
