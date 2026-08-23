package dev.fardavide.oltre.client.fleets.presentation

import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.design.format.toCountdown
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.dispatch.presentation.DispatchSelection
import dev.fardavide.oltre.client.dispatch.presentation.toDispatchUiState
import dev.fardavide.oltre.client.fleets.ui.FleetsUiState
import dev.fardavide.oltre.client.fleets.ui.RunBarUiState
import dev.fardavide.oltre.client.fleets.ui.RunCardUiState
import dev.fardavide.oltre.client.fleets.ui.RunPhase
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.FleetRun
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.NotificationSettings
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Research
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.ownedShips
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// **Everything the Fleets tab decides.** The types it produces live in `:client:fleets:ui`, which
// knows nothing about `GameState` — this is the one file that reads the fleet and the event log and
// writes what the cards say.

fun GameState.toFleetsUiState(
    now: Instant,
    // The instant this launch advanced *from*. It is what the landing clock is measured against: a
    // world that came home while the app was closed says so, and one that came home before that has
    // nothing new to report. Defaults to `now` — an empty span, so nothing is new — which is what a
    // preview or a test that does not care should get.
    since: Instant = now,
    timeZone: TimeZone,
    // What the player has touched on the sheet a row raised, or null when no sheet is up. The
    // mapping is `:client:dispatch:presentation`'s; this only knows which world is open.
    dispatch: DispatchSelection? = null,
    // What the player said on the settings screen, which the sheet reads to decide whether it draws
    // a bell beside Dispatch. Nothing else on this tab consults it.
    alerts: NotificationSettings = NotificationSettings.DEFAULT,
): FleetsUiState {
    val owned = ownedShips().total
    val away = owned - ships.total
    return FleetsUiState(
        away = Strings.fleetsAwayOf(away = away, owned = owned),
        // Sorted by the moment the card is counting down to rather than by the list's order — `runs`
        // is unordered on `GameState` for the reason `advance` sorts its arrivals on an intrinsic
        // key, and a list whose order depended on the sequence of taps that produced it is one a
        // reloaded save reproduces only by accident.
        runs = runs
            .sortedWith(
                compareBy({ it.nextEventAt(galaxy.home, now, research) }, { it.dispatchedAt }, { it.target.slot }),
            )
            .map { it.toCard(home = galaxy.home, now = now, timeZone = timeZone, research = research) },
        worked = toWorkedListUiState(now = now, since = since, timeZone = timeZone),
        // **No probe offer, and null is the honest answer rather than a shortcut.** A world a fleet
        // has already been sent to was surveyed in order to be dispatched to, and `surveyed` is
        // never removed — so the refusal that offer feeds cannot be reached from this list at all.
        dispatch = dispatch?.let { toDispatchUiState(selection = it, probe = null, now = now, alerts = alerts) },
    )
}

private fun FleetRun.toCard(
    home: GalaxyCoordinate,
    now: Instant,
    timeZone: TimeZone,
    research: Research,
): RunCardUiState {
    // **Read off the run's own clock rather than recomputed beside it**, so the legs and the ticks
    // cannot disagree: `flightEndsAt` clamps a leg to half the stored span, and a `station` derived
    // from an unclamped flight went negative for every run that was in the air when 0.15 halved the
    // base speed — `on station -19m`, rendered through a truncating division that made it -19 out of
    // -380.
    val onStationAt = flightEndsAt(home, research)
    val inboundAt = inboundBeginsAt(home, research)
    val flight = onStationAt - dispatchedAt
    val station = inboundAt - onStationAt
    val at = nextEventAt(home, now, research)
    val remainingMs = (at.toEpochMilliseconds() - now.toEpochMilliseconds()).coerceAtLeast(0)
    val landsLocal = returnsAt.toLocalDateTime(timeZone)
    val composition = ShipType.entries
        .mapNotNull { type -> ships.counts[type]?.let { count -> Strings.ships(count, type) } }
    return RunCardUiState(
        coordinate = target.label(),
        manifest = Strings.clauses(
            composition + Strings.amountOfResource(cargo.of(gathering).groupedByThousands(), gathering),
        ),
        // Ceiled, so a countdown only reads 00:00:00 once the moment it names has actually passed —
        // the same rule every other countdown in the app follows.
        countdown = ((remainingMs + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toCountdown(),
        lands = Strings.homeAt(hour = landsLocal.hour, minute = landsLocal.minute),
        legs = Strings.clauses(
            listOf(
                Strings.legOut(flight.toChipLabel()),
                Strings.legOnStation(station.toChipLabel()),
                Strings.legHome(flight.toChipLabel()),
            ),
        ),
        // The compact form drops the words and keeps the three figures in the same order, which is
        // the same call the dispatch sheet's own legs line makes one width down.
        compactLegs = Strings.clauses(
            listOf(flight.toChipLabel(), station.toChipLabel(), flight.toChipLabel()),
        ),
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
private fun FleetRun.nextEventAt(home: GalaxyCoordinate, now: Instant, research: Research): Instant {
    val onStation = flightEndsAt(home, research)
    return if (now < onStation) onStation else returnsAt
}

private fun GalaxyCoordinate.label(): TextRes = Strings.coordinate(galaxy, system, slot)

private fun Resources.of(kind: ResourceKind): Long = when (kind) {
    ResourceKind.METAL -> metal
    ResourceKind.CRYSTAL -> crystal
    ResourceKind.DEUTERIUM -> deuterium
}

private const val MILLIS_PER_SECOND: Long = 1_000
