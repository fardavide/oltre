package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.design.format.toCountdown
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.galaxy.ui.ProbeActionUiState
import dev.fardavide.oltre.client.galaxy.ui.ProbeFindKind
import dev.fardavide.oltre.client.galaxy.ui.ProbeOfferUiState
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.SurveyBalance
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.World
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.shortfallOf
import dev.fardavide.oltre.core.timeUntilAffordable
import dev.fardavide.oltre.core.verdictFor
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Which of the probe footer's six states the system card is in, and what each one says.


internal fun GameState.toProbeActionUiState(
    at: SystemSelection,
    worlds: List<World>,
    now: Instant,
    timeZone: TimeZone,
): ProbeActionUiState {
    val target = SystemAddress(galaxy = at.galaxy, system = at.system)

    // Asked first, and it has to be: a system with no worlds is *vacuously* surveyed — `hasSurveyed`
    // asks whether every occupied slot is known and there are none — so every branch below would
    // otherwise claim it had been charted.
    if (worlds.isEmpty()) {
        return ProbeActionUiState.NothingToSurvey(
            note = Strings.nothingToSurvey(GalaxyBalance.SLOTS_PER_SYSTEM),
        )
    }

    surveys.firstOrNull { it.target == target }?.let { job ->
        val total = (job.completesAt - job.startedAt).inWholeMilliseconds.coerceAtLeast(1)
        val elapsed = (now - job.startedAt).inWholeMilliseconds.coerceIn(0, total)
        val remaining = (job.completesAt - now).inWholeMilliseconds.coerceAtLeast(0)
        val lands = job.completesAt.toLocalDateTime(timeZone)
        return ProbeActionUiState.InFlight(
            // Ceiled, so a countdown only reads 00:00:00 once the probe has actually landed.
            countdown = ((remaining + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toCountdown(),
            lands = Strings.landsAt(hour = lands.hour, minute = lands.minute),
            progressPercent = (elapsed * 100 / total).toInt(),
        )
    }

    if (galaxy.hasSurveyed(target)) {
        val landing = eventLog.filterIsInstance<Event.SurveyCompleted>().lastOrNull { it.target == target }
            ?: return ProbeActionUiState.Charted(Strings.surveyedAtGenesis())
        val landedAt = landing.at.toLocalDateTime(timeZone)
        val find = findIn(worlds)
        return ProbeActionUiState.Landed(
            landedAt = Strings.probeLandedAt(hour = landedAt.hour, minute = landedAt.minute),
            summary = Strings.worldsSurveyedCount(landing.worldsFound),
            find = find.label,
            findKind = find.kind,
        )
    }

    val cost = SurveyBalance.cost()
    val flight = SurveyBalance.duration(from = SystemAddress.of(galaxy.home), to = target)
    val offer = ProbeOfferUiState(
        cost = CostChipUiState(
            kind = ResourceKind.METAL,
            amount = cost.metal.groupedByThousands(),
            short = ResourceKind.METAL in resources.shortfallOf(cost),
        ),
        costWord = Strings.resourceName(ResourceKind.METAL),
        flight = Strings.probeFlightLabel(flight.toChipLabel()),
        compactFlight = flight.toChipLabel(),
    )
    // **The hull is asked about before the metal, because that is the order the verb checks in** —
    // and the footer's whole job is that the button is absent wherever `startSurvey` would refuse.
    // A probe flies a `SCOUT` since 0.15, so a colony with a full bank and an empty pool is refused,
    // and this is where it finds that out rather than by tapping.
    //
    // **It reuses `Unaffordable` rather than earning a state of its own.** That treatment is already
    // "here is the offer, here is why you cannot take it, and there is no verb" — which is exactly
    // this — and a seventh state would be a new drawing for a sentence the sixth already frames. The
    // note is what differs, and it differs in the way that matters: every other unaffordable state in
    // the game is answered by *standing still*, and this one is answered at the Shipyard. So when a
    // scout is genuinely on its way home the countdown is the honest answer, and when none is coming
    // a countdown would be a lie however well it rendered.
    if (!ships.covers(SurveyBalance.SHIPS)) {
        val soonest = surveys.minOfOrNull { it.completesAt }
            ?: yard.firstOrNull { it.ship == ShipType.SCOUT }?.completesAt
        return ProbeActionUiState.Unaffordable(
            offer = offer,
            availableIn = soonest
                ?.let { Strings.availableIn((it - now).coerceAtLeast(Duration.ZERO).toChipLabel()) }
                ?: Strings.probeNeedsScout(),
        )
    }
    if (resources.covers(cost)) {
        // "Dispatch probe" at 393dp and "Dispatch" at 320dp. Nothing else on the card says the word
        // probe, so at full width the verb carries its object.
        return ProbeActionUiState.Dispatch(
            offer = offer,
            label = Strings.dispatchProbe(),
            compactLabel = Strings.dispatchProbeCompact(),
        )
    }
    val wait = timeUntilAffordable(resources, cost, buildings, research)
    return ProbeActionUiState.Unaffordable(
        offer = offer,
        // A resource with no production at all never arrives, and "in 2,000,000h" would be a worse
        // lie than saying nothing. The same rule the Research and Colony ghosts follow.
        availableIn = if (wait.isFinite()) Strings.availableIn(wait.toChipLabel()) else Strings.availableNever(),
    )
}

// Precedence, best first: a settleable world is what the whole verb exists for, a single blocked
// axis is the middle tier the brief names, and "none settleable" is the honest common answer.
//
// Read off the *current* verdicts rather than off the landing event, and deliberately: `verdictFor`
// re-derives against the adaptation levels the empire holds now, so a system that was all-blocked
// when the probe landed reads as settleable the day the ladder that closes it is bought. The
// receipt says what was found; this line says what it is worth today.
private fun GameState.findIn(worlds: List<World>): ProbeFind {
    val verdicts = worlds.map { verdictFor(it, this) }
    val settleable = verdicts.count { it is WorldVerdict.Settleable }
    if (settleable > 0) return ProbeFind(Strings.findSettleable(settleable), ProbeFindKind.SETTLEABLE)
    val nearMisses = verdicts.count { (it as? WorldVerdict.Blocked)?.failures?.size == 1 }
    if (nearMisses > 0) return ProbeFind(Strings.findNearMiss(nearMisses), ProbeFindKind.NEAR_MISS)
    return ProbeFind(Strings.findNone(), ProbeFindKind.NONE)
}

private data class ProbeFind(val label: TextRes, val kind: ProbeFindKind)

private const val MILLIS_PER_SECOND: Long = 1_000
