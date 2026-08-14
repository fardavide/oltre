package dev.fardavide.oltre.client.fleets.presentation

import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.pad2
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.design.format.toCountdown
import dev.fardavide.oltre.client.fleets.ui.FleetsUiState
import dev.fardavide.oltre.client.fleets.ui.LandingUiState
import dev.fardavide.oltre.client.fleets.ui.RunBarUiState
import dev.fardavide.oltre.client.fleets.ui.RunCardUiState
import dev.fardavide.oltre.client.fleets.ui.RunPhase
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.FleetRun
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.ownedShips
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// **Everything the Fleets tab decides.** The types it produces live in `:client:fleets:ui`, which
// knows nothing about `GameState` — this is the one file that reads the fleet and the event log and
// writes what the cards say.

fun GameState.toFleetsUiState(now: Instant, timeZone: TimeZone): FleetsUiState {
    val owned = ownedShips().total
    val away = owned - ships.total
    return FleetsUiState(
        away = "$away of $owned away",
        // Sorted by the moment the card is counting down to rather than by the list's order — `runs`
        // is unordered on `GameState` for the reason `advance` sorts its arrivals on an intrinsic
        // key, and a list whose order depended on the sequence of taps that produced it is one a
        // reloaded save reproduces only by accident.
        runs = runs
            .sortedWith(compareBy({ it.nextEventAt(galaxy.home, now) }, { it.dispatchedAt }, { it.target.slot }))
            .map { it.toCard(home = galaxy.home, now = now, timeZone = timeZone) },
        landed = eventLog
            .filterIsInstance<Event.FleetReturned>()
            .takeLast(LANDINGS_SHOWN)
            .reversed()
            .map { it.toLanding(now = now, timeZone = timeZone) },
    )
}

private fun FleetRun.toCard(home: GalaxyCoordinate, now: Instant, timeZone: TimeZone): RunCardUiState {
    val flight = FleetBalance.flight(from = home, to = target)
    val station = returnsAt - dispatchedAt - flight * 2
    val onStationAt = flightEndsAt(home)
    val inboundAt = inboundBeginsAt(home)
    val at = nextEventAt(home, now)
    val remainingMs = (at.toEpochMilliseconds() - now.toEpochMilliseconds()).coerceAtLeast(0)
    val landsLocal = returnsAt.toLocalDateTime(timeZone)
    val composition = ShipType.entries
        .mapNotNull { type -> ships.counts[type]?.let { count -> "$count ${type.displayName(count)}" } }
        .joinToString(SEPARATOR)
    return RunCardUiState(
        coordinate = target.label(),
        manifest = "$composition$SEPARATOR${cargo.of(gathering).groupedByThousands()} ${gathering.label()}",
        // Ceiled, so a countdown only reads 00:00:00 once the moment it names has actually passed —
        // the same rule every other countdown in the app follows.
        countdown = ((remainingMs + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toCountdown(),
        lands = "home ${landsLocal.hour.pad2()}:${landsLocal.minute.pad2()}",
        legs = "out ${flight.toChipLabel()}$SEPARATOR" +
            "on station ${station.toChipLabel()}$SEPARATOR" +
            "home ${flight.toChipLabel()}",
        compactLegs = "${flight.toChipLabel()}$SEPARATOR${station.toChipLabel()}$SEPARATOR${flight.toChipLabel()}",
        phase = when {
            now < onStationAt -> RunPhase.OUTBOUND
            now < inboundAt -> RunPhase.ON_STATION
            else -> RunPhase.INBOUND
        },
        bar = RunBarUiState(
            progress = fractionOf(dispatchedAt, returnsAt, now),
            outboundEndsAt = fractionOf(dispatchedAt, returnsAt, onStationAt),
            inboundBeginsAt = fractionOf(dispatchedAt, returnsAt, inboundAt),
        ),
    )
}

// The window is at least `roundTrip + MINIMUM_STATION` by `startRun`'s own refusal, so the divisor
// is never zero — but it is floored at one millisecond anyway, because a screen may not depend on a
// verb's guard holding for it. A save hand-edited to a zero-length run would divide rather than
// crash, and would read as complete.
private fun fractionOf(from: Instant, to: Instant, at: Instant): Float {
    val total = (to.toEpochMilliseconds() - from.toEpochMilliseconds()).coerceAtLeast(1)
    val elapsed = (at.toEpochMilliseconds() - from.toEpochMilliseconds()).coerceIn(0, total)
    return elapsed.toFloat() / total.toFloat()
}

// Whichever of a run's two moments has not happened yet — the same derivation the Colony strip
// makes, and it is duplicated rather than shared for the reason two `presentation` modules cannot
// see each other. That is rule 5, and what it costs here is six lines.
private fun FleetRun.nextEventAt(home: GalaxyCoordinate, now: Instant): Instant {
    val onStation = flightEndsAt(home)
    return if (now < onStation) onStation else returnsAt
}

// "11:04" today and "yest." before it, which is what the ledger needs and the whole of what it
// needs: a landing older than yesterday is not on the list at all, so no third form can occur.
private fun Event.FleetReturned.toLanding(now: Instant, timeZone: TimeZone): LandingUiState {
    val local = at.toLocalDateTime(timeZone)
    val landed = local.date
    val today = now.toLocalDateTime(timeZone).date
    // **Read off the cargo rather than assumed from the two kinds a run may gather.** A run's
    // `gathering` is guarded to metal or crystal, so the obvious version — crystal if there is any,
    // metal otherwise — is right for every run this game can dispatch and prints `+0 metal` for
    // anything else. The event is the wider type: nothing stops a `FleetReturned` carrying
    // deuterium, and a ledger that answered "0 metal" about a landing that brought something would
    // be lying about the one thing it exists to report.
    val kind = ResourceKind.entries.firstOrNull { cargo.of(it) > 0 } ?: ResourceKind.METAL
    return LandingUiState(
        stamp = if (landed == today) "${local.hour.pad2()}:${local.minute.pad2()}" else "yest.",
        // Null only on a fleet the schema-8 migration folded forward, which came from a coordinate
        // no old event ever recorded. "—" is the truthful answer and the one the migration wrote.
        coordinate = from?.label() ?: "—",
        amount = "+${cargo.of(kind).groupedByThousands()} ${kind.label()}",
        kind = kind,
    )
}

// **The last five, and the cap is a layout decision rather than a data one.** The log is append-only
// and unbounded, so a ledger that folded all of it would grow a screen without limit — and what the
// section is for is *what came back since I last looked*, which is a handful. The whole history is
// still in the log for the day something wants it.
private const val LANDINGS_SHOWN = 5

private fun GalaxyCoordinate.label(): String = "[$galaxy:$system:$slot]"

private fun ShipType.displayName(count: Int): String = when (this) {
    ShipType.SKIFF -> if (count == 1) "skiff" else "skiffs"
    ShipType.HAULER -> if (count == 1) "hauler" else "haulers"
    ShipType.ESCORT -> if (count == 1) "escort" else "escorts"
    ShipType.SETTLER -> if (count == 1) "settler" else "settlers"
}

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

private const val SEPARATOR = " · "

private const val MILLIS_PER_SECOND: Long = 1_000
