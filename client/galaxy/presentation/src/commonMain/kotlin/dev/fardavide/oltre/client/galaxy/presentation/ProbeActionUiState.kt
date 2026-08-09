package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.pad2
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.design.format.toCountdown
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

// **The verb lives in the footer of the system card, under the orbits.** The map is the only
// star-scoped object on the screen and a probe targets a star, so the star-scoped verb goes in it —
// the same rule that already puts a build's countdown inside its facility row rather than in a hero
// panel above the list. Everything the probe ever says, from the price to the receipt, lands in the
// card that owns the thing it describes.
//
// Not the header: that is navigation, and a purchase there would be the only buyable thing in the
// app sitting in a nav bar. Not a world row: a row-level button multiplies by 4.75 and asks the
// player to pick a slot for a flight that surveys all fifteen.
sealed interface ProbeActionUiState {

    // Cost then time, left to right, because the cost never moves and the time is the whole
    // purchase. The eye learns where the changing number is and stops reading the other one.
    data class Dispatch(val offer: ProbeOfferUiState, val label: String, val compactLabel: String) :
        ProbeActionUiState

    // The committed idiom, unchanged: the chip reddens for the one resource you are short of and
    // the verb becomes a ghost carrying the wait. Two durations then share a row — "flight 39m" and
    // "in 1h 06m" — told apart by side, by colour and by the preposition. It is the tightest
    // reading on the screen.
    data class Unaffordable(val offer: ProbeOfferUiState, val availableIn: String) : ProbeActionUiState

    // The three parts a running build already draws, in that order. No cancel: nothing in the app
    // cancels, and the state after the tap is its own receipt.
    data class InFlight(val countdown: String, val lands: String, val progressPercent: Int) : ProbeActionUiState

    // A receipt for a flight that was paid for. `find` is the honest half and is usually "none
    // settleable" — saying it in the same breath as the count is what keeps a run of these reading
    // as calibration rather than as bad luck, which is the job the Barren row's threshold already
    // does.
    data class Landed(
        val landedAt: String,
        val summary: String,
        val find: String,
        val findKind: ProbeFindKind,
    ) : ProbeActionUiState

    // Home, which was never flown to. One tertiary line, no receipt, no verb.
    data class Charted(val note: String) : ProbeActionUiState

    // The card refuses the sale and says why, in the words of the thing above it: fifteen ticks, no
    // dots. **It never says "already surveyed", because nothing was** — and `startSurvey` really
    // does refuse, because `hasSurveyed` is vacuously true where there is nothing to survey.
    data class NothingToSurvey(val note: String) : ProbeActionUiState
}

// The price and the flight. `costWord` is its own field rather than part of a sentence because it
// is what 320dp drops — along with the word "flight" and the word "probe" on the button. Two words
// and no figures: 150 stays, 1h 22m stays, and the red chip still reddens. The colour does the work
// the word did.
data class ProbeOfferUiState(
    val cost: CostChipUiState,
    val costWord: String,
    val flight: String,
    val compactFlight: String,
)

// Green once, and only on the count. `NEAR_MISS` is the middle tier — a world blocked on a single
// axis, about one dispatch in fourteen — and it is neither green nor red: a fact that points at
// Research, in body weight, because it is worth reading and not worth acting on today.
enum class ProbeFindKind { NONE, SETTLEABLE, NEAR_MISS }

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
