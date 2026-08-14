package dev.fardavide.oltre.client.shipyard.ui

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ShipType

// **Numbers by hand, and since 0.9.1 that is the whole file rather than three quarters of it.** Every
// state below states its own figures rather than deriving them from a `GameState`, so a baseline
// moves when the *screen* moves and never when a balance constant does.
//
// **The fourth used to be derived and the change was not a change of mind.** `toShipyardUiState`
// moved to `:client:shipyard:presentation` when the layer split landed, and a ui module is a leaf
// that cannot see one — Davide's call, 2026-08-13. What it cost is written over `buildingUiState`,
// which is the frame the argument was about; what it did not cost is a re-record, because the values
// there are the mapper's own, copied.
//
// What none of these do is *invent copy*. A fixture that wrote its own sentences would pin text the
// app never produces, which is the mistake the galaxy module made and paid for. Every string here
// was produced by the mapper first.

// Declared ahead of the four states that carry it, because a top-level `val` in Kotlin is
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
//
// **It was derived from a real `GameState` until 0.9.1 and is stated here now**, because
// `toShipyardUiState` moved to `:client:shipyard:presentation` and a ui module is a leaf that cannot
// see one. Davide's call, 2026-08-13, taking the layer split as written. Every value below is the
// one the mapper produced, to the character, so the two baselines this frame feeds did not move.
//
// **The thing that argument was protecting is worth restating, because it is now this comment's job
// rather than the compiler's.** The four yard fields are not four facts — they are one job's two
// instants rendered four ways, and the first draft of this fixture typed them independently and
// pinned a state the app cannot produce: 31% through a job with 2h 11m left is not the job
// "done 14:05" describes. So they are written together, from one pair of instants, and the arithmetic
// that ties them is spelled out beside each one:
//
//   laid down 11:00Z, due 14:05Z, read at 12:04Z — a 3h 05m job, 1h 04m elapsed, 2h 01m left
//   countdown  02:01:00  the remainder, ceiled to the second
//   progress   34%       64 minutes of 185, floored
//   doneAt     14:05     the completion in the frame's own zone, which is UTC
//   queued     2 queued  the two behind the one on the slipway
//
// `ShipyardUiStateTest` next door asserts that same arithmetic against the real mapper, which is
// what still fails if any of it drifts.
internal val buildingUiState: ShipyardUiState = ShipyardUiState(
    // Two owned and three on the slipway: the heading counts the fleet that exists, and the pool
    // line is the only place the order is visible.
    fleet = "2 hulls",
    hulls = listOf(
        HullUiState(
            type = ShipType.SKIFF,
            name = "Skiff",
            pool = "2 owned · 2 idle · 3 building",
            purpose = "One berth of hold · 10m + 1m per 10 units, one way",
            // The sixth rung, because `buildShips` prices against everything *committed* — two owned
            // plus three on the slipway. Deep enough that the card offers the verb: this frame is
            // about the slipway, and a reddened chip would make it about affordability instead.
            costs = listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = "6,075", short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "1,518", short = false),
            ),
            action = BuildActionUiState.Build,
            yard = YardUiState(
                countdown = "02:01:00",
                progressPercent = 34,
                doneAt = "done 14:05",
                queued = "2 queued",
            ),
        ),
    ),
    comingHulls = listOf(HAULER),
)
