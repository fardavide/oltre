package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.galaxy.ui.BlockedAxisUiState
import dev.fardavide.oltre.client.galaxy.ui.DepositItemUiState
import dev.fardavide.oltre.client.galaxy.ui.DepositReadingUiState
import dev.fardavide.oltre.client.galaxy.ui.DepositTone
import dev.fardavide.oltre.client.galaxy.ui.GalaxyRowUiState
import dev.fardavide.oltre.client.galaxy.ui.WorldPortraitUiState
import dev.fardavide.oltre.client.galaxy.ui.WorldVerdictUiState
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.milli
import dev.fardavide.oltre.client.design.format.perMillion
import dev.fardavide.oltre.client.design.format.signed
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxyState
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.HostilityAxis
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ToleranceFailure
import dev.fardavide.oltre.core.World
import dev.fardavide.oltre.core.WorldTraits
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.epithetFor
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldNameAt
import kotlin.time.Instant

// **One mapper for one row, used by both screens that draw one.** The system view and the ledger
// differ only in whether a round trip travels with the row — in a system every row shares the header's
// astronomy line, and in the ledger the rows come from everywhere and nothing above them can say it.
internal fun GameState.toWorldRow(
    world: World,
    now: Instant,
    withTrailing: Boolean,
): GalaxyRowUiState.World {
    val verdict = verdictFor(world, this)
    val surveyed = world.at in galaxy.surveyed
    val traits = world.traits

    return GalaxyRowUiState.World(
        at = world.at,
        name = worldNameAt(galaxy.seed, world.at),
        coordinate = world.at.label(),
        // **The one gate, and it is the survey set rather than the verdict.** The disc and the
        // epithet are the same permission — both are trait readouts — so deriving them from one
        // condition is what makes it impossible for the picture and the word to disagree about what
        // the player has paid for.
        portrait = world.toPortrait(surveyed),
        epithet = if (surveyed) epithetFor(traits).toString() else null,
        verdict = verdict.toUiState(),
        trailing = if (withTrailing) FleetBalance.roundTrip(from = galaxy.home, to = world.at).toChipLabel() else null,
        deposits = if (verdict.pricesAHold()) toDepositReading(world.at, now) else null,
        requirements = (verdict as? WorldVerdict.Blocked)?.failures.orEmpty().map { it.toUiState() },
        note = verdict.toNote(traits),
    )
}

private fun World.toPortrait(surveyed: Boolean): WorldPortraitUiState = when {
    !surveyed -> WorldPortraitUiState.Unsurveyed
    else -> WorldPortraitUiState.Surveyed(
        temperature = traits.temperature,
        gravity = traits.gravity,
        pressure = traits.pressure,
        hazards = traits.hazards,
        hasRing = hasRing,
    )
}

private fun WorldVerdict.toUiState(): WorldVerdictUiState = when (this) {
    WorldVerdict.Home -> WorldVerdictUiState.HOME
    is WorldVerdict.Occupied -> WorldVerdictUiState.OCCUPIED
    WorldVerdict.Unsurveyed -> WorldVerdictUiState.UNSURVEYED
    is WorldVerdict.Blocked -> WorldVerdictUiState.BLOCKED
    WorldVerdict.Barren -> WorldVerdictUiState.BARREN
    is WorldVerdict.Settleable -> WorldVerdictUiState.SETTLEABLE
}

// The last line, and only where there is something to say that the disc and the epithet have not
// already said. **`Blocked` has none**: its requirement lines are the sentence, and a note above them
// would be the row saying the same thing twice in two voices.
private fun WorldVerdict.toNote(traits: WorldTraits): String? = when (this) {
    WorldVerdict.Home -> "Your colony."
    is WorldVerdict.Occupied -> "Held by ${holder.value}."
    is WorldVerdict.Settleable -> "Nothing here blocks a colony."
    // Barren fails no band at all — it fails the *bar* — so its one line is the yield against the
    // threshold. Naming the threshold is what makes a run of Barren answers read as calibration
    // rather than as bad luck, and Barren is designed to be a common answer.
    WorldVerdict.Barren -> "Yield ${traits.yieldLabel()}, $WORTH_IT_AT"
    WorldVerdict.Unsurveyed, is WorldVerdict.Blocked -> null
}

// **Present exactly where a run is legal**, which is not a coincidence: absent on `Unsurveyed`
// because a hold cannot be priced from a world nobody has looked at, and absent on `Home` and
// `Occupied` because a run there is refused outright.
private fun WorldVerdict.pricesAHold(): Boolean = when (this) {
    WorldVerdict.Home, is WorldVerdict.Occupied, WorldVerdict.Unsurveyed -> false
    is WorldVerdict.Blocked, WorldVerdict.Barren, is WorldVerdict.Settleable -> true
}

// `metal full`, `metal 174/819`, `metal empty` — a word at each end because neither end poses any
// arithmetic, and a fraction between because 120 of 600 and 120 of 2,400 are the same number and not
// the same target.
//
// **Never the words this design refused**: no *left*, no *deposit*, no rate of refill. With no noun
// the row asserts nothing about who took what, which is what lets `full` be the honest reading of the
// ~98% of worlds nobody has ever worked.
//
// Deuterium is never here: a run cannot lift it, so a figure for it would be an offer the verb
// refuses.
private fun GameState.toDepositReading(at: GalaxyCoordinate, now: Instant): DepositReadingUiState =
    DepositReadingUiState(
        metal = galaxy.depositItem(at, ResourceKind.METAL, now),
        crystal = galaxy.depositItem(at, ResourceKind.CRYSTAL, now),
    )

private fun GalaxyState.depositItem(
    at: GalaxyCoordinate,
    gathering: ResourceKind,
    now: Instant,
): DepositItemUiState {
    val cap = depositCap(at, gathering)
    val remaining = if (cap == null) 0 else remaining(at, gathering, now)
    return when {
        cap == null || remaining <= 0 -> DepositItemUiState(gathering, "empty", DepositTone.EMPTY)
        remaining >= cap -> DepositItemUiState(gathering, "full", DepositTone.FULL)
        else -> DepositItemUiState(
            resource = gathering,
            reading = "${remaining.groupedByThousands()}/${cap.groupedByThousands()}",
            tone = DepositTone.PARTIAL,
        )
    }
}

internal fun ToleranceFailure.toUiState(): BlockedAxisUiState = BlockedAxisUiState(
    axis = axis.name.lowercase(),
    reading = axis.reading(worldValue),
    tolerated = axis.tolerated(toleratedBound),
    technology = axis.adaptation,
    // "Gravitic 9", not "Gravitic Adaptation 9". All three technologies end in the same word, so it
    // carries nothing and costs eleven characters the row does not have.
    label = "${axis.adaptation.name.lowercase().replaceFirstChar { it.uppercase() }} $closedAtLevel",
)

private fun HostilityAxis.reading(value: Int): String = when (this) {
    HostilityAxis.TEMPERATURE -> value.signed()
    HostilityAxis.GRAVITY, HostilityAxis.PRESSURE -> value.milli()
}

// **The space before each unit below is U+00A0, not U+0020** — invisible in a diff, so it is said
// here. What it must not do is break between a number and its unit, which leaves "atm" alone on a
// line and reads as a defect rather than as a wrap.
private fun HostilityAxis.tolerated(value: Int): String = when (this) {
    HostilityAxis.TEMPERATURE -> "${value.signed()} °C"
    HostilityAxis.GRAVITY -> "${value.milli()} g"
    HostilityAxis.PRESSURE -> "${value.milli()} atm"
}

internal fun WorldTraits.yieldLabel(): String = GalaxyBalance.yieldScore(this).perMillion.perMillion()

internal val WORTH_IT_AT: String =
    "worth it at ${GalaxyBalance.WORTH_IT_THRESHOLD.perMillion.perMillion()}"
