package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.perMillion
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.design.format.toCountdown
import dev.fardavide.oltre.client.design.format.toWaitLabel
import dev.fardavide.oltre.core.DepositBalance
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.World
import dev.fardavide.oltre.core.worldAt
import kotlin.time.Duration
import kotlin.time.Instant

// **What the player has touched, and nothing else.** Every field but the slot is null until they
// change it, and the mapper below fills the blanks — which is why opening the sheet needs no read of
// the game state at all, and why a default that moves (the richer resource, the whole idle pool) is
// stated once in the mapper rather than copied into the screen that opens the sheet.
//
// The slot rather than a whole coordinate: the page *is* a system, exactly as it is for the probe.
data class DispatchSelection(
    val slot: Int,
    val gathering: ResourceKind?,
    val ships: Int?,
    val window: Duration?,
)

// Raised from a world row. Three controls, one figure — and **no cost line and no affordability
// state**, which is Design's fourth call and a subtraction rather than an omission: `FleetBalance`
// charges nothing per run, so the sheet has nothing to be short of. The hull was the price and it is
// paid at the Shipyard, where "cannot afford" is drawn in the idiom the probe and the facility rows
// already spend.
sealed interface DispatchUiState {

    val coordinate: String

    // "metal 1.24 · crystal 0.74 · no hazards", richer resource first — the head is the world in one
    // line, so the sheet answers "which world is this" before it answers anything else.
    val head: String

    // 320dp drops the lesser resource rather than ellipsising the pair. A width decision, not a
    // change of voice: what goes is the number you were not going to pick.
    val compactHead: String

    data class Offer(
        override val coordinate: String,
        override val head: String,
        override val compactHead: String,
        // **The three subjects of the run, resolved.** These are what `startRun` is actually called
        // with, and they are on the ui-state rather than left in `DispatchSelection` for one reason:
        // the mapper is what filled in the three defaults and what clamped the hull count to the
        // idle pool, so the selection and the offer can differ — and the offer is what the player
        // was shown. Dispatching anything else would send a run the sheet never described.
        val slot: Int,
        val window: Duration,
        // Bring back, send, home in — in that order, because it is the order of decreasing
        // permanence. What your colony is short of changes over days, how many hulls you have
        // changes over hours, and how long you will be away changes every check-in.
        val gathering: ResourceKind,
        val metalRichness: String,
        val crystalRichness: String,
        // What each chip says about the world, under its currency: "richness 1.24 · deposit full",
        // "richness 0.74 · deposit 620/1,798", "richness 0.74 · deposit empty". **Richness lives here
        // now rather than on the row** — Design moved it when the stocks took the row's headline, and
        // the chip is where there is prose room for both.
        val metalDeposit: String,
        val crystalDeposit: String,
        val ships: String,
        val shipCount: Int,
        val atFewest: Boolean,
        val atMost: Boolean,
        val pool: String,
        val windows: List<WindowRungUiState>,
        // Present only when the ladder has narrowed. The rung that vanished is the copy — this
        // sentence exists so a player who never saw the full ladder still learns why.
        val ladderNote: String?,
        // "The 12h window brings the same." — the shortest rung that still takes everything there is,
        // named only when a shorter one exists and the chosen one is wasting hours. **Earned rather
        // than standing**: on a rung that is already the shortest that empties the vein there is
        // nothing to say, and a note that appeared on every dispatch would be furniture.
        //
        // No new control and no new state on the rungs. A rung whose extra hours bring nothing is not
        // locked and not disabled — inventing a state for *not better* would be the first greyed thing
        // in the app, and the ladder narrows by absence everywhere else.
        val rungNote: String?,
        // "3 skiffs empty it. The 4th brings nothing." Present only when the clamp bites *and* there
        // is a remedy: at `atFewest` there is no smaller fleet to send, so the sheet shows the figure
        // and stops. Under the cliff the marginal hull is worth exactly zero, so this is arithmetic
        // stated before the tap rather than a scold.
        val clampNote: String?,
        // The only thing on the sheet that moves when a control is touched, which is why it sits
        // under a rule and above the verb.
        val figure: String,
        // "449 each" on an unclamped run, "the whole deposit" when the vein is what stopped it —
        // one token in a slot that already exists, and the only marker the clamped state needs.
        // **The figure is never restated**: when the clamp bites the headline number already *is* the
        // deposit, and printing it twice is the defect the null-on-a-single-hull rule below exists to
        // prevent. Null on a single unclamped hull, because "132 each" beside "132 metal" is the same
        // number twice.
        val perShip: String?,
        val legs: String,
        val compactLegs: String,
        val danger: String,
        val compactDanger: String,
    ) : DispatchUiState

    // **A mode rather than a refusal, and that distinction is Design's.** A dry world keeps its whole
    // sheet — chips, stepper, ladder — and loses only the figure, which becomes a countdown to the
    // hold *this* offer would lift. That is what makes the state worth entering rather than backing
    // out of: **the wait is a function of the ask**, so the remedy is in the player's hands.
    //
    // It has to be, because of what Design measured: the vein and the rate carry one multiplier, so a
    // full fleet's lift is about the size of a vein — "four skiffs at 6h" is 18d 13h away, which reads
    // exactly like the "full again" this sheet ruled out. Shrink the ask to one skiff at 3h and the
    // same world is worth visiting in 2d 04h. The countdown is only honest because the controls above
    // it still move.
    data class Waiting(
        override val coordinate: String,
        override val head: String,
        override val compactHead: String,
        val slot: Int,
        val window: Duration,
        val gathering: ResourceKind,
        val metalRichness: String,
        val crystalRichness: String,
        val metalDeposit: String,
        val crystalDeposit: String,
        val ships: String,
        val shipCount: Int,
        val atFewest: Boolean,
        val atMost: Boolean,
        val pool: String,
        val windows: List<WindowRungUiState>,
        val ladderNote: String?,
        val title: String,
        val note: String,
        // "in 18d 13h", or null when no amount of waiting covers this ask and only a smaller one will.
        val wait: String?,
        val legs: String,
        val compactLegs: String,
        val danger: String,
        val compactDanger: String,
    ) : DispatchUiState

    // The sheet refuses the sale and says why, in the words of the thing that refused it — the same
    // shape `ProbeActionUiState.NothingToSurvey` already has. Both refusals are reachable on a first
    // check-in, and neither is an error state.
    data class Refuse(
        override val coordinate: String,
        override val head: String,
        override val compactHead: String,
        val title: String,
        val note: String,
        val action: RefuseActionUiState?,
    ) : DispatchUiState
}

// A rung of the ladder. Keyed by its own duration rather than by its index, because the ladder
// **narrows** on a distant target rather than greying rungs out — so the rung at index 0 is a
// different window depending on how far away the world is.
data class WindowRungUiState(val label: String, val window: Duration, val selected: Boolean)

sealed interface RefuseActionUiState {

    // The one refusal in the app that hands back a verb. It also chains the two: a probe used to buy
    // a verdict and stop, and now it buys the right to send a ship.
    data class Probe(val label: String) : RefuseActionUiState

    // A reading, not a control — the idiom the unaffordable probe already spends. There is nothing
    // to send, so there is no button to grey out.
    data class Waiting(val label: String) : RefuseActionUiState
}

// Null when the slot is not a target at all, which is the screen agreeing with `startRun` rather
// than finding out afterwards: your own world, a world somebody holds, an empty slot and a relay all
// refuse outright, so the row never offers a sheet the verb would throw away.
internal fun GameState.toDispatchUiState(
    at: SystemSelection,
    selection: DispatchSelection,
    probe: ProbeActionUiState,
    now: Instant,
): DispatchUiState? {
    val target = GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = selection.slot)
    if (target == galaxy.home) return null
    if (galaxy.holderOf(target) != null) return null
    val world = worldAt(galaxy.seed, target) ?: return null

    val coordinate = target.label()
    if (target !in galaxy.surveyed) {
        return DispatchUiState.Refuse(
            coordinate = coordinate,
            head = UNSURVEYED_HEAD,
            compactHead = UNSURVEYED_HEAD,
            title = "A hold cannot be priced from a world nobody has looked at.",
            note = unsurveyedNote(at = at, probe = probe),
            // Only when the card above would honour it. The footer of the map card already decides
            // whether a probe can be sent — it is in flight, it is unaffordable, it has landed — and
            // a second copy of that decision here is a second place for the two to disagree.
            action = (probe as? ProbeActionUiState.Dispatch)?.let { RefuseActionUiState.Probe(it.label) },
        )
    }

    val head = world.headLine(compact = false)
    val compactHead = world.headLine(compact = true)
    val idle = ships.countOf(ShipType.SKIFF)
    if (idle <= 0) {
        return DispatchUiState.Refuse(
            coordinate = coordinate,
            head = head,
            compactHead = compactHead,
            title = "Every skiff is away.",
            note = awayNote(),
            action = nextReturn(now)?.let { RefuseActionUiState.Waiting("in ${it.toCountdown()}") },
        )
    }

    val home = galaxy.home
    val offered = FleetBalance.windowsFor(from = home, to = target)
    val window = selection.window?.takeIf { it in offered } ?: offered.defaultRung()
    val gathering = selection.gathering ?: world.richerOf()
    val hulls = (selection.ships ?: idle).coerceIn(1, idle)
    val sent = Ships.of(ShipType.SKIFF, hulls)
    val flight = FleetBalance.flight(from = home, to = target)
    val station = FleetBalance.stationFor(from = home, to = target, window = window)
    val danger = FleetBalance.danger(from = home, world = world)
    val cargo = FleetBalance.cargo(
        world = world,
        gathering = gathering,
        ships = sent,
        station = station,
        danger = danger,
        research = research,
    )
    val lift = cargo.of(gathering)
    // What is actually in the ground, and the cap behind it. Both are read once and shared by the
    // chips, the figure and the countdown, so the sheet cannot contradict itself about one world.
    val inTheGround = galaxy.remaining(target, gathering, now)
    val haul = minOf(lift, inTheGround)
    val clamped = lift > inTheGround
    val working = DepositBalance.workingTime(
        world = world,
        gathering = gathering,
        ships = sent,
        danger = danger,
        remaining = haul,
        research = research,
    )
    val chips = DepositChips(
        metal = depositChip(target, ResourceKind.METAL, now),
        crystal = depositChip(target, ResourceKind.CRYSTAL, now),
    )
    val stepper = SteppedFleet(
        ships = "$hulls ${if (hulls == 1) "skiff" else "skiffs"}",
        shipCount = hulls,
        atFewest = hulls <= 1,
        atMost = hulls >= idle,
        pool = "of $idle idle",
    )
    val rungs = offered.map { WindowRungUiState(label = it.rungLabel(), window = it, selected = it == window) }
    val ladderNote = ladderNoteFor(offered = offered, roundTrip = flight * 2)

    if (inTheGround <= 0) {
        val wait = galaxy.timeUntil(target, gathering, wanted = lift, now = now)
        return DispatchUiState.Waiting(
            coordinate = coordinate,
            head = head,
            compactHead = compactHead,
            slot = selection.slot,
            window = window,
            gathering = gathering,
            metalRichness = world.traits.metalRichness.perMillion.perMillion(),
            crystalRichness = world.traits.crystalRichness.perMillion.perMillion(),
            metalDeposit = chips.metal,
            crystalDeposit = chips.crystal,
            ships = stepper.ships,
            shipCount = stepper.shipCount,
            atFewest = stepper.atFewest,
            atMost = stepper.atMost,
            pool = stepper.pool,
            windows = rungs,
            ladderNote = ladderNote,
            title = waitingTitle(target, now),
            note = waitingNote(ships = stepper.ships, window = window, lift = lift, gathering = gathering, wait = wait),
            wait = wait?.let { "in ${it.toWaitLabel()}" },
            legs = legsLine(flight = flight, station = station, working = Duration.ZERO, compact = false),
            compactLegs = legsLine(flight = flight, station = station, working = Duration.ZERO, compact = true),
            danger = dangerLine(world = world, danger = danger, compact = false),
            compactDanger = dangerLine(world = world, danger = danger, compact = true),
        )
    }

    return DispatchUiState.Offer(
        coordinate = coordinate,
        head = head,
        compactHead = compactHead,
        slot = selection.slot,
        window = window,
        gathering = gathering,
        metalRichness = world.traits.metalRichness.perMillion.perMillion(),
        crystalRichness = world.traits.crystalRichness.perMillion.perMillion(),
        metalDeposit = chips.metal,
        crystalDeposit = chips.crystal,
        ships = stepper.ships,
        shipCount = stepper.shipCount,
        atFewest = stepper.atFewest,
        atMost = stepper.atMost,
        pool = stepper.pool,
        windows = rungs,
        ladderNote = ladderNote,
        rungNote = rungNoteFor(offered = offered, chosen = window, roundTrip = flight * 2, working = working),
        clampNote = clampNoteFor(clamped = clamped, hulls = hulls, perShip = lift / hulls, inTheGround = inTheGround),
        figure = "${haul.groupedByThousands()} ${gathering.label()}",
        perShip = when {
            clamped -> "the whole deposit"
            hulls > 1 -> "${(haul / hulls).groupedByThousands()} each"
            else -> null
        },
        legs = legsLine(flight = flight, station = station, working = working, compact = false),
        compactLegs = legsLine(flight = flight, station = station, working = working, compact = true),
        danger = dangerLine(world = world, danger = danger, compact = false),
        compactDanger = dangerLine(world = world, danger = danger, compact = true),
    )
}

private class DepositChips(val metal: String, val crystal: String)

private class SteppedFleet(
    val ships: String,
    val shipCount: Int,
    val atFewest: Boolean,
    val atMost: Boolean,
    val pool: String,
)

// "deposit full", "deposit 620/1,798", "deposit empty" — the second line of a chip whose first is
// the richness the card already prints. **The two are separate strings because the card owns the
// word "richness"**, and the first cut of this returned both in one and rendered "richness richness
// 1.15" with the stock clipped off the end. A screenshot caught it; no test could have, because a
// node query reads the whole string whatever is painted.
private fun GameState.depositChip(target: GalaxyCoordinate, kind: ResourceKind, now: Instant): String {
    val cap = galaxy.depositCap(target, kind)
    val remaining = galaxy.remaining(target, kind, now)
    val stock = when {
        cap == null || remaining <= 0 -> "empty"
        remaining >= cap -> "full"
        else -> "${remaining.groupedByThousands()}/${cap.groupedByThousands()}"
    }
    return "deposit $stock"
}

// "out 10m · on station 11h 40m · working 6h 03m · home 10m". **The fourth segment is the invariant
// made visible with no copy at all** — because the vein and the rate carry one multiplier, `working`
// reads the same on the doorstep as in the next galaxy, so the rule teaches itself off this line
// rather than out of a tooltip. Absent when there is nothing to work.
private fun legsLine(flight: Duration, station: Duration, working: Duration, compact: Boolean): String {
    val stationWord = if (compact) "station" else "on station"
    val parts = listOfNotNull(
        "out ${flight.toChipLabel()}",
        "$stationWord ${station.toChipLabel()}",
        "working ${working.toChipLabel()}".takeIf { working > Duration.ZERO },
        "home ${flight.toChipLabel()}",
    )
    return parts.joinToString(SEPARATOR)
}

// "3 skiffs empty it. The 4th brings nothing." Under the cliff the marginal hull contributes exactly
// zero and is locked away for the whole window, so this is deterministic arithmetic stated before the
// tap — the app's own voice — rather than a scold.
//
// **Earned rather than standing.** Null at one hull, where there is no smaller fleet to send and the
// shorter rung is the only remedy left; null when nothing is clamped. A note that appeared on every
// dispatch would be furniture, and furniture is what stops the other two being read as instructions.
private fun clampNoteFor(clamped: Boolean, hulls: Int, perShip: Long, inTheGround: Long): String? {
    if (!clamped || hulls <= 1 || perShip <= 0) return null
    val enough = ((inTheGround + perShip - 1) / perShip).toInt().coerceIn(1, hulls)
    if (enough >= hulls) return null
    val idle = hulls - enough
    val subject = if (enough == 1) "1 skiff empties it" else "$enough skiffs empty it"
    val rest = if (idle == 1) "The ${ordinal(hulls)} brings nothing." else "The other $idle bring nothing."
    return "$subject. $rest"
}

private fun ordinal(n: Int): String = when (n % 10) {
    1 -> if (n % 100 == 11) "${n}th" else "${n}st"
    2 -> if (n % 100 == 12) "${n}th" else "${n}nd"
    3 -> if (n % 100 == 13) "${n}th" else "${n}rd"
    else -> "${n}th"
}

// "The 12h window brings the same." The shortest rung that still takes everything there is — named
// only when the chosen rung is longer than it needs to be, because a rung that is already the
// shortest that empties the vein has nothing to say.
private fun rungNoteFor(
    offered: List<Duration>,
    chosen: Duration,
    roundTrip: Duration,
    working: Duration,
): String? {
    val shortest = offered.firstOrNull { it >= roundTrip + working } ?: return null
    if (shortest >= chosen) return null
    return "The ${shortest.rungLabel()} window brings the same."
}

private fun GameState.waitingTitle(target: GalaxyCoordinate, now: Instant): String {
    val metal = galaxy.remaining(target, ResourceKind.METAL, now)
    val crystal = galaxy.remaining(target, ResourceKind.CRYSTAL, now)
    return if (metal <= 0 && crystal <= 0) "Both deposits are empty." else "This deposit is empty."
}

// The ask, what it would lift, and when the world holds that much again — then the remedy, which is
// that the ask can shrink. Design's finding is the reason the last sentence is there at all: a full
// fleet's lift is about the size of a vein, so the honest answer to "when?" is often "not soon, and
// you can ask for less."
private fun waitingNote(
    ships: String,
    window: Duration,
    lift: Long,
    gathering: ResourceKind,
    wait: Duration?,
): String {
    val ask = "$ships at ${window.rungLabel()} would lift ${lift.groupedByThousands()} ${gathering.label()}."
    val when_ = wait?.let { "The world holds that much again in ${it.toWaitLabel()}." }
        ?: "No world this size ever holds that much."
    return "$ask $when_ Fewer skiffs, or a shorter window, is sooner."
}

// 3h is the rhythm the measured cadence names — *"ogni 2/3 ore"* — so it is where the ladder opens
// when it is offered, and the shortest surviving rung when it is not. A default of the *longest*
// would send the first skiff of a new colony away for a day on a tap the player had not thought
// about yet; a default of the shortest would make the frontier look worthless, because the frontier
// only pays at the long end.
private fun List<Duration>.defaultRung(): Duration = firstOrNull { it >= PREFERRED_WINDOW } ?: last()

// "18h 20m out and back. No shorter window leaves 20 minutes on the surface." Present only when
// something has actually been dropped: on a neighbour every rung is offered and the sentence would
// be explaining an absence that is not there.
private fun ladderNoteFor(offered: List<Duration>, roundTrip: Duration): String? {
    if (offered.size == FleetBalance.WINDOWS.size) return null
    return "${roundTrip.toChipLabel()} out and back. No shorter window leaves " +
        "${FleetBalance.MINIMUM_STATION.inWholeMinutes} minutes on the surface."
}

// The world in one line, richer resource first, because the first thing the eye lands on should be
// the one the sheet is about to default to. At 320dp the lesser one goes rather than being
// ellipsised — it is the number you were not going to pick.
private fun World.headLine(compact: Boolean): String {
    val metal = "metal ${traits.metalRichness.perMillion.perMillion()}"
    val crystal = "crystal ${traits.crystalRichness.perMillion.perMillion()}"
    val richer = if (traits.metalRichness.perMillion >= traits.crystalRichness.perMillion) metal else crystal
    val lesser = if (richer == metal) crystal else metal
    return listOfNotNull(richer, lesser.takeIf { !compact }, hazardClause()).joinToString(SEPARATOR)
}

// Which resource this world is better at, and therefore what the sheet opens on. Compared in the
// generator's own units rather than in the priced basket, because the player is choosing between
// two columns of the same number and the richer column is the one they can see.
private fun World.richerOf(): ResourceKind =
    if (traits.metalRichness.perMillion >= traits.crystalRichness.perMillion) {
        ResourceKind.METAL
    } else {
        ResourceKind.CRYSTAL
    }

// "danger 2 · one hazard, 195 units out · +70% of the hold". The sum, and both the things it came
// from — this is the only place either is stated, because it is the only place the number is spent.
// A row cannot say it: the distance band is astronomy and belongs to the system, and on an
// unsurveyed world a hazard count would be claiming knowledge the player has not paid for.
//
// **The third clause changed direction at round 21 and the sentence had to change with it.** It used
// to read "20% of the hold" and mean *taken*; danger now pays, so it reads "+70%" and means *added*.
// The sign is carried by the `+` and by the word the zero case uses, because a bare percentage next
// to the word "danger" would be read as a cost by anyone who has played anything.
//
// **Deterministic, and stated before the tap.** Nothing in this mechanic is rolled, so the sentence
// is a specification rather than a warning.
private fun GameState.dangerLine(world: World, danger: Int, compact: Boolean): String {
    val take = when (danger) {
        0 -> "nothing added"
        else -> "+${danger * DANGER_BONUS_PERCENT}% of the hold"
    }
    if (compact) return "danger $danger · $take"
    val band = FleetBalance.distanceBand(from = galaxy.home, to = world.at)
    val distance = when {
        band == 0 -> "your own system"
        world.at.galaxy != galaxy.home.galaxy -> "another galaxy"
        else -> "${FleetBalance.distanceUnits(from = galaxy.home, to = world.at)} units out"
    }
    return "danger $danger · ${world.hazardClause()}, $distance · $take"
}

// The world's own half of the danger, in words carrying their own arithmetic — the row prints this
// too, which is what lets the two agree without either quoting a total.
private fun World.hazardClause(): String = when (traits.hazards.size) {
    0 -> "no hazards"
    1 -> "one hazard"
    else -> "two hazards"
}

// What is out, and what is coming — the honest half of a refusal that has nothing to offer. Named
// off the soonest return rather than the list's order, for the reason `advance` sorts its arrivals
// on an intrinsic key: `runs` is unordered.
private fun GameState.awayNote(): String {
    // Unreachable through the sheet — the branch above only asks when the idle pool is empty, and a
    // colony with no idle hull and no run in flight owns no hulls at all, which genesis forbids. It
    // is a sentence rather than an `error` because a refusal is the wrong place to crash a screen.
    val soonest = runs.minByOrNull { it.returnsAt } ?: return "Nothing is idle and nothing is out."
    val plural = if (runs.size == 1) "run is" else "runs are"
    val kind = soonest.gathering
    val rest = if (runs.size > 1) " ${runs.size - 1} more behind it." else ""
    return "${runs.size} $plural out. ${soonest.target.label()} is inbound with " +
        "${soonest.cargo.of(kind).groupedByThousands()} ${kind.label()}.$rest" +
        " A hull is idle only once it is home."
}

private fun GameState.nextReturn(now: Instant): Long? {
    val soonest = runs.minByOrNull { it.returnsAt } ?: return null
    val remainingMs = (soonest.returnsAt - now).inWholeMilliseconds.coerceAtLeast(0)
    // Ceiled, so a countdown only reads 00:00:00 once a hull has actually landed.
    return (remainingMs + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
}

// Reuses the probe footer's own count rather than recomputing it: the two sentences describe one
// flight, and a system that holds five worlds must not be described as holding four here.
private fun GameState.unsurveyedNote(at: SystemSelection, probe: ProbeActionUiState): String {
    val worlds = (1..GalaxyBalance.SLOTS_PER_SYSTEM).count { slot ->
        worldAt(galaxy.seed, GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = slot)) != null
    }
    val plural = if (worlds == 1) "world" else "worlds"
    val offer = (probe as? ProbeActionUiState.Dispatch)?.let { dispatch ->
        " ${dispatch.offer.cost.amount} metal · ${dispatch.offer.flight}."
    } ?: ""
    return "Richness and hazards need a survey. A probe surveys all " +
        "${GalaxyBalance.SLOTS_PER_SYSTEM} slots at once, and this system holds $worlds $plural.$offer"
}

// "1h", "24h" — the rung is a window rather than a duration to be read, so it is written the short
// way the ladder is spoken in.
private fun Duration.rungLabel(): String = "${inWholeHours}h"

private fun ResourceKind.label(): String = when (this) {
    ResourceKind.METAL -> "metal"
    ResourceKind.CRYSTAL -> "crystal"
    ResourceKind.DEUTERIUM -> "deuterium"
}

private fun Resources.of(kind: ResourceKind): Long = when (kind) {
    ResourceKind.METAL -> metal
    ResourceKind.CRYSTAL -> crystal
    ResourceKind.DEUTERIUM -> deuterium
}

private val PREFERRED_WINDOW: Duration = FleetBalance.WINDOWS[1]

private const val DANGER_BONUS_PERCENT: Int = 35

private const val MILLIS_PER_SECOND: Long = 1_000

// The head of a world nobody has looked at. It says both halves at once: the *system* is charted, so
// the player knows a world is there — and the world is not, so nothing about it can be priced.
private const val UNSURVEYED_HEAD = "charted · unsurveyed"
