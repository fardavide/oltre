package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.pad2
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.design.format.toCountdown
import dev.fardavide.oltre.client.galaxy.ui.ProbeActionUiState
import dev.fardavide.oltre.client.galaxy.ui.ProbeFindKind
import dev.fardavide.oltre.client.galaxy.ui.ProbeOfferUiState
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
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
            note = "${GalaxyBalance.SLOTS_PER_SYSTEM} empty slots · nothing to survey",
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
            lands = "lands ${lands.hour.pad2()}:${lands.minute.pad2()}",
            progressPercent = (elapsed * 100 / total).toInt(),
        )
    }

    if (galaxy.hasSurveyed(target)) {
        val landing = eventLog.filterIsInstance<Event.SurveyCompleted>().lastOrNull { it.target == target }
            ?: return ProbeActionUiState.Charted("Surveyed at genesis")
        val landedAt = landing.at.toLocalDateTime(timeZone)
        val find = findIn(worlds)
        return ProbeActionUiState.Landed(
            landedAt = "Probe landed ${landedAt.hour.pad2()}:${landedAt.minute.pad2()}",
            summary = "${landing.worldsFound} ${if (landing.worldsFound == 1) "world" else "worlds"} surveyed",
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
        costWord = "metal",
        flight = "flight ${flight.toChipLabel()}",
        compactFlight = flight.toChipLabel(),
    )
    if (resources.covers(cost)) {
        // "Dispatch probe" at 393dp and "Dispatch" at 320dp. Nothing else on the card says the word
        // probe, so at full width the verb carries its object.
        return ProbeActionUiState.Dispatch(offer = offer, label = "Dispatch probe", compactLabel = "Dispatch")
    }
    val wait = timeUntilAffordable(resources, cost, buildings, research)
    return ProbeActionUiState.Unaffordable(
        offer = offer,
        // A resource with no production at all never arrives, and "in 2,000,000h" would be a worse
        // lie than saying nothing. The same rule the Research and Colony ghosts follow.
        availableIn = if (wait.isFinite()) "in ${wait.toChipLabel()}" else "—",
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
    if (settleable > 0) return ProbeFind("$settleable settleable", ProbeFindKind.SETTLEABLE)
    val nearMisses = verdicts.count { (it as? WorldVerdict.Blocked)?.failures?.size == 1 }
    if (nearMisses > 0) return ProbeFind("$nearMisses blocked at one axis", ProbeFindKind.NEAR_MISS)
    return ProbeFind("none settleable", ProbeFindKind.NONE)
}

private data class ProbeFind(val label: String, val kind: ProbeFindKind)

private const val MILLIS_PER_SECOND: Long = 1_000
