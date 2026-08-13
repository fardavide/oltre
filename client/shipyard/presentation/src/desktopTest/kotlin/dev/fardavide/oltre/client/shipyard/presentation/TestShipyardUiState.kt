package dev.fardavide.oltre.client.shipyard.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.YardJob
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import dev.fardavide.oltre.core.ShipType

// **Numbers by hand, strings from nowhere but here.** The three price-list states below state their
// own figures rather than deriving them from a `GameState`, so a baseline moves when the *screen*
// moves and never when a balance constant does. What they do not do is re-word anything: a fixture
// that invented copy would pin sentences the app never produces, which is the mistake the galaxy
// module made and paid for.
//
// **The rule used to say "every fixture in this repository" and that was never true.**
// `TestGalaxyUiState` derives its dispatch sheet from a real `GameState` and argues for it in as many
// words — the sheet states `FleetBalance.cargo` to the unit, *"and a hand-typed one would drift"*. So
// the repository has both practices, each with its reason written down, and the honest form of this
// paragraph is a choice per fixture rather than a rule with an undisclosed exception in it.
//
// **`buildingUiState` below is derived, and it is the case that shows why the choice is per fixture.**
// Its four yard fields are not four facts — they are one job's two instants rendered four ways, and
// the first draft typed them independently and pinned a state the app cannot produce: 31% through a
// job with 2h 11m left is not the job "done 14:05" describes. That is exactly the drift the galaxy
// module cited. So the fixture states the two instants, which nothing in the balance can move, and
// lets the mapper compute the rest — which is also what makes the pool line, the queue count and the
// countdown provably the ones the screen is given rather than four numbers that happen to look right.

// The seed every client test in the repository uses, so a state built here and a state built on the
// Galaxy tab are the same map. Declared first for the same reason `HAULER` is.
private val SEED = GalaxySeed(20_260_807L)

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
//
// **Derived, unlike the three above, and only as far as it has to be.** What is stated is the pair of
// instants — laid down at 11:00, due at 14:05, read at 12:04 — and those are the fixture's own: no
// balance constant can move them, so `MINUTES_PER_ROOT_COST`, the Robotics divisor and the hull's
// duration curve are all out of this baseline. What is computed is everything downstream of them, so
// the countdown, the bar and the wall-clock label cannot disagree with each other or with the mapper.
//
// **What it does still couple to is the price**: the chips come from `FleetBalance.shipCost`, so a
// round that moves the hull base moves this baseline and it has to be re-recorded. That is the cost,
// it is one command, and it buys a picture the app can actually produce. The three price lists above
// keep the hand-typed treatment precisely so a price round does not churn all seven.
internal val buildingUiState: ShipyardUiState = run {
    val laidDown = Instant.parse("2026-08-13T11:00:00Z")
    val due = Instant.parse("2026-08-13T14:05:00Z")
    GameState.initial(SEED)
        .copy(
            // Deep enough that the card offers the verb: this frame is about the slipway, and a
            // reddened chip would make it about affordability instead.
            resources = Resources.of(metal = 1_000_000, crystal = 1_000_000),
            ships = Ships.of(ShipType.SKIFF, 2),
            // Chained, because `GameState.init` refuses anything else — which is the invariant doing
            // the fixture's checking for it.
            yard = listOf(
                YardJob(ship = ShipType.SKIFF, startedAt = laidDown, completesAt = due),
                YardJob(ship = ShipType.SKIFF, startedAt = due, completesAt = due + 3.hours),
                YardJob(ship = ShipType.SKIFF, startedAt = due + 3.hours, completesAt = due + 7.hours),
            ),
        )
        .toShipyardUiState(now = Instant.parse("2026-08-13T12:04:00Z"), timeZone = TimeZone.UTC)
}
