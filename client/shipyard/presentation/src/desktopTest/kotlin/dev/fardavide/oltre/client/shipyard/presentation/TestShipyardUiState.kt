package dev.fardavide.oltre.client.shipyard.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ShipType

// **Numbers by hand, strings from nowhere but here.** The states below state their own figures
// rather than deriving them from a `GameState`, so a baseline moves when the *screen* moves and
// never when a balance constant does — which is the rule every screenshot fixture in this
// repository follows. What they do not do is re-word anything: a fixture that invented copy would
// pin sentences the app never produces, which is the mistake the galaxy module made and paid for.

// Declared ahead of the three states that carry it, because a top-level `val` in Kotlin is
// initialised in file order and a forward reference reads as null.
private val HAULER = ComingHullUiState(
    type = ShipType.HAULER,
    name = "Hauler",
    purpose = "Four berths of hold, at half a skiff's speed.",
)

// The first sitting: one granted skiff, idle, and 500 metal in the store — which buys the second at
// 120 metal and 30 crystal. The frame Design drew for this slice, at one hull.
internal val oneHullUiState = ShipyardUiState(
    fleet = "1 hull",
    hulls = listOf(
        HullUiState(
            type = ShipType.SKIFF,
            name = "Skiff",
            pool = "1 owned · 1 idle",
            purpose = "One berth of hold · 10m + 1m per 10 units, one way",
            costs = listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = "120", short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "30", short = false),
            ),
            action = BuildActionUiState.Build,
            yard = null,
        ),
    ),
    comingHulls = listOf(HAULER),
)

// A fleet at depth, five of it away, and the seventh hull priced where the curve has got to. This is
// the frame Design published — `6 owned · 1 idle · 5 away`, 910 metal and 225 crystal.
internal val sixHullsUiState = ShipyardUiState(
    fleet = "6 hulls",
    hulls = listOf(
        HullUiState(
            type = ShipType.SKIFF,
            name = "Skiff",
            pool = "6 owned · 1 idle · 5 away",
            purpose = "One berth of hold · 10m + 1m per 10 units, one way",
            costs = listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = "910", short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "225", short = false),
            ),
            action = BuildActionUiState.Build,
            yard = null,
        ),
    ),
    comingHulls = listOf(HAULER),
)

// **The state this tab owns.** The dispatch sheet has no affordability state because a run is free;
// this is where "cannot afford" is drawn, in the shipped idiom — the metal chip reddens and the verb
// becomes a ghost carrying the wait.
internal val cannotAffordUiState = ShipyardUiState(
    fleet = "6 hulls",
    hulls = listOf(
        HullUiState(
            type = ShipType.SKIFF,
            name = "Skiff",
            pool = "6 owned · 1 idle · 5 away",
            purpose = "One berth of hold · 10m + 1m per 10 units, one way",
            costs = listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = "910", short = true),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "225", short = false),
            ),
            action = BuildActionUiState.AvailableIn("in 1h 06m"),
            yard = null,
        ),
    ),
    comingHulls = listOf(HAULER),
)

// **The state 0.9.0 added, and the only one on this tab where something is happening.** A hull on
// the slipway with two behind it: the card takes `OltreCardState.RUNNING`, the footer is the probe's
// in-flight drawing, and the verb above it stays live — which is the one thing here that is not the
// Colony row's treatment, because a serial yard can always be given another hull.
internal val buildingUiState = ShipyardUiState(
    fleet = "2 hulls",
    hulls = listOf(
        HullUiState(
            type = ShipType.SKIFF,
            name = "Skiff",
            pool = "2 owned · 2 idle · 3 building",
            purpose = "One berth of hold · 10m + 1m per 10 units, one way",
            costs = listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = "6,075", short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "1,518", short = false),
            ),
            action = BuildActionUiState.Build,
            yard = YardUiState(
                countdown = "02:11:47",
                progressPercent = 31,
                doneAt = "done 14:05",
                queued = "2 queued",
            ),
        ),
    ),
    comingHulls = listOf(HAULER),
)
