package dev.fardavide.oltre.client.dispatch.presentation

import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.perMillion
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.design.format.toCountdown
import dev.fardavide.oltre.client.design.format.toWaitLabel
import dev.fardavide.oltre.client.dispatch.ui.DispatchUiState
import dev.fardavide.oltre.client.dispatch.ui.RefuseActionUiState
import dev.fardavide.oltre.client.dispatch.ui.WindowRungUiState
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
import dev.fardavide.oltre.core.worldNameAt
import kotlin.time.Duration
import kotlin.time.Instant

// What the dispatch sheet offers, refuses and prices — everything on it that is a function of a
// real world, a real fleet and a real deposit. The shapes it fills in are `:client:dispatch:ui`'s.


// **What the player has touched, and nothing else.** Every field but the target is null until they
// change it, and the mapper below fills the blanks — which is why opening the sheet needs no read of
// the game state at all, and why a default that moves (the richer resource, the whole idle pool) is
// stated once in the mapper rather than copied into the screen that opens the sheet.
//
// **The whole coordinate rather than a slot**, and the correction is the whole of this file's bug
// history: the page is a system only in the *map* view, and the ledger — the view the tab opens on —
// lists rows from everywhere. A selection carrying a slot alone was completed from the map's own
// system, so tapping a ledger row priced the same slot of wherever the map was parked, and a row
// reading `crystal full` raised a sheet reading `deposit empty` about a different world.
data class DispatchSelection(
    val at: GalaxyCoordinate,
    val gathering: ResourceKind?,
    val ships: Int?,
    val window: Duration?,
)

// **The two controls that change what a fleet would lift put the manifest back to null**, which is
// how the sheet re-derives it — Davide, 2026-08-17, having asked for a suggested count that follows
// the window. A rung is not a change of schedule: a longer stay means a smaller fleet takes the same
// vein, so a count chosen against the 3h rung is arithmetic about a run that no longer exists. The
// currency is the same claim about the other axis, since the two deposits are different sizes.
//
// **They live here rather than in the screens for one reason**: Galaxy and Fleets both raise this
// sheet and rule 5 stops either seeing the other, so a `copy` written twice is two places for the
// two doors to start disagreeing about what a tap means. The stepper has no counterpart, because a
// count the player typed in with their thumb is the one thing on the sheet nothing should overrule.
fun DispatchSelection.homingIn(window: Duration): DispatchSelection = copy(window = window, ships = null)

fun DispatchSelection.bringingBack(gathering: ResourceKind): DispatchSelection =
    copy(gathering = gathering, ships = null)

// **The probe the unsurveyed refusal may hand back, priced by whoever knows how.** Three strings and
// no verb: this module cannot price a survey and must not learn to, because the map card's footer
// already decides whether a flight would be honoured — it is in flight, it is unaffordable, it has
// landed — and a second copy of that decision here is a second place for the two to disagree about
// one flight.
//
// So the caller passes what its own footer resolved, or null. **Null is the ordinary case from a
// landing**: a world a fleet has already been sent to was surveyed in order to be dispatched to, and
// `surveyed` is never removed — so the refusal this feeds is unreachable from the Fleets ledger.
data class DispatchProbeOffer(val label: String, val cost: String, val flight: String)

// Null when the target is not a target at all, which is the screen agreeing with `startRun` rather
// than finding out afterwards: your own world, a world somebody holds, an empty slot and a relay all
// refuse outright, so the row never offers a sheet the verb would throw away.
fun GameState.toDispatchUiState(
    selection: DispatchSelection,
    probe: DispatchProbeOffer?,
    now: Instant,
): DispatchUiState? {
    val target = selection.at
    if (target == galaxy.home) return null
    if (galaxy.holderOf(target) != null) return null
    val world = worldAt(galaxy.seed, target) ?: return null

    // **The name in the title and the address in the head**, on both doors — see `DispatchUiState`.
    // `worldNameAt` is a pure function of the seed, so it names an unsurveyed world too: a name is
    // astronomy, and it is the traits a survey buys rather than the label.
    val name = worldNameAt(galaxy.seed, target)
    val address = target.label()
    if (target !in galaxy.surveyed) {
        return DispatchUiState.Refuse(
            name = name,
            head = "$address$SEPARATOR$UNSURVEYED_HEAD",
            compactHead = "$address$SEPARATOR$UNSURVEYED_HEAD",
            title = "A hold cannot be priced from a world nobody has looked at.",
            note = unsurveyedNote(at = target, probe = probe),
            // Only when the caller's own footer would honour it — see `DispatchProbeOffer`.
            action = probe?.let { RefuseActionUiState.Probe(it.label) },
        )
    }

    // **The richness left the head when the address arrived**, which is Claude Design's own frame
    // and a subtraction rather than a trade: the two gather cards below already print `richness 1.15`
    // and `richness 1.47`, so the head was saying it twice. Measured rather than argued — the first
    // cut kept both and clipped the hazard clause off the end of a 393dp sheet.
    val head = "$address$SEPARATOR${world.hazardClause()}"
    val compactHead = head
    val idle = ships.countOf(ShipType.SKIFF)
    if (idle <= 0) {
        return DispatchUiState.Refuse(
            name = name,
            // **A refusal has no gather cards, so the richness has nowhere else to be said** — and
            // it is the reading a player is being refused *for*. The compact form at both widths:
            // the address costs the room the lesser resource had.
            head = "$address$SEPARATOR${world.headLine(compact = true)}",
            compactHead = "$address$SEPARATOR${world.headLine(compact = true)}",
            title = "Every skiff is away.",
            note = awayNote(),
            action = nextReturn(now)?.let { RefuseActionUiState.Waiting("in ${it.toCountdown()}") },
        )
    }

    val home = galaxy.home
    val offered = FleetBalance.windowsFor(from = home, to = target)
    val window = selection.window?.takeIf { it in offered } ?: offered.defaultRung()
    val gathering = selection.gathering ?: world.richerOf()
    val flight = FleetBalance.flight(from = home, to = target)
    val station = FleetBalance.stationFor(from = home, to = target, window = window)
    val danger = FleetBalance.danger(from = home, world = world)
    // What is actually in the ground, and the cap behind it. Both are read once and shared by the
    // chips, the figure and the countdown, so the sheet cannot contradict itself about one world.
    val inTheGround = galaxy.remaining(target, gathering, now)
    // **The fleet that empties the vein, not every hull you own** — Davide, 2026-08-17, having
    // counted the taps on a 55-hull pool a world could absorb three of. A hull past the cliff is
    // locked away for the whole window and brings back exactly zero, and until now the sheet said so
    // in a note and then made the player walk the stepper down anyway.
    //
    // **It is a suggestion rather than a cap.** The pool is still stated in full beside the label,
    // the `+` still reaches every idle hull, and a deep vein still opens on the whole fleet — which
    // is the same rule, since there nothing is wasted. Null is a window with no surface time, which
    // the ladder never offers; the pool is the honest fallback because no fleet size would empty it.
    val suggested = FleetBalance.hullsToLift(
        world = world,
        gathering = gathering,
        remaining = inTheGround,
        station = station,
        danger = danger,
        research = research,
    ) ?: idle
    val hulls = (selection.ships ?: suggested).coerceIn(1, idle)
    val sent = Ships.of(ShipType.SKIFF, hulls)
    val cargo = FleetBalance.cargo(
        world = world,
        gathering = gathering,
        ships = sent,
        station = station,
        danger = danger,
        research = research,
    )
    val lift = cargo.of(gathering)
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
            name = name,
            head = head,
            compactHead = compactHead,
            at = target,
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
        name = name,
        head = head,
        compactHead = compactHead,
        at = target,
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
//
// **Only the away refusal calls this since 0.13.** An offer's head is the address and the hazards,
// because its two chips carry the richness — see the head above.
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

// Reuses the probe footer's own price rather than recomputing it: the two sentences describe one
// flight, and a system that holds five worlds must not be described as holding four here.
//
// **The target's own system, never the page's.** A survey covers all fifteen slots of the star the
// world orbits, and the sheet is raised from rows belonging to six systems at once.
private fun GameState.unsurveyedNote(at: GalaxyCoordinate, probe: DispatchProbeOffer?): String {
    val worlds = (1..GalaxyBalance.SLOTS_PER_SYSTEM).count { slot ->
        worldAt(galaxy.seed, GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = slot)) != null
    }
    val plural = if (worlds == 1) "world" else "worlds"
    val offer = probe?.let { " ${it.cost} metal · ${it.flight}." } ?: ""
    return "Richness and hazards need a survey. A probe surveys all " +
        "${GalaxyBalance.SLOTS_PER_SYSTEM} slots at once, and this system holds $worlds $plural.$offer"
}

// "1h", "24h" — the rung is a window rather than a duration to be read, so it is written the short
// way the ladder is spoken in.
private fun Duration.rungLabel(): String = "${inWholeHours}h"

// **A second copy of the one in `:client:galaxy:presentation`, and it has to be.** The sheet heads
// itself with the address the row that raised it prints, and rule 5 stops two presentation modules
// seeing each other — so what a shared home would cost is a feature module every feature depends on
// for one line of string building. The two are pinned together by the frames rather than by an
// import: a sheet raised from a row asserts the row's own coordinate.
private fun GalaxyCoordinate.label(): String = "[$galaxy:$system:$slot]"

// Between the facts on a line, everywhere in the app. Duplicated for the reason above and cheap for
// the same one.
private const val SEPARATOR = " · "

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
