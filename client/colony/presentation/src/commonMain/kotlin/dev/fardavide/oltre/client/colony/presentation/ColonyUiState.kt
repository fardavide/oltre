package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.pad2
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.design.format.toCountdown
import dev.fardavide.oltre.core.BuildJob
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Buildings
import dev.fardavide.oltre.core.EnergyBalance
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.Research
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ReturningFleet
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.shortfallOf
import dev.fardavide.oltre.core.timeUntilAffordable
import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// The stocks and their rates are deliberately absent: the resource rail is the shell's chrome
// now, because it frames every destination rather than only this one.
data class ColonyUiState(
    val energy: EnergyUiState,
    val facilities: List<FacilityRowUiState>,
    val returningFleet: ReturningFleetUiState?,
)

// Energy is not shown as a fourth resource, because it is not one: it never accumulates, so a
// stock and a per-hour rate would both be lies. It reads as a verdict — the consequence, which
// is the only reason a player needs the number at all — over a track, over the two terms.
//
// A deficit is the normal state and it arrives almost immediately, so this is present in both
// states rather than appearing when things go wrong: the healthy reading is what teaches the
// mechanic, long before the player is confused by it.
data class EnergyUiState(
    val verdict: String,
    val terms: String,
    // The green length of the track, which spans the larger of the two terms. Healthy, the fill
    // is the draw and the empty tail is the headroom. In deficit the fill is what the plant
    // actually supplies, so the boundary is the plant's ceiling and the amber tail is how far
    // past it the colony has been built.
    val coveredFraction: Float,
    val deficit: Boolean,
)

// This facility's own contribution to the balance, shown only while the colony is in deficit.
// It carries the facility's draw rather than the headline percentage: each mine floors
// independently, so three cards reading "55%" would each be slightly wrong, where the draw is
// exactly true per card and is the number that changes when the player acts.
data class FacilityPowerUiState(
    val label: String,
    val supply: Boolean,
)

data class ReturningFleetUiState(
    val title: String,
    val subtitle: String,
    val countdown: String,
)

data class FacilityRowUiState(
    val building: BuildingType,
    val name: String,
    val level: BuildingLevel,
    val costs: List<CostChipUiState>,
    val duration: String,
    val action: FacilityActionUiState,
    // Null while the colony is healthy, and on anything that neither draws nor supplies — an
    // unbuilt facility draws nothing, so it has nothing to attribute and nothing to fight the
    // locked row's dim.
    val power: FacilityPowerUiState?,
    // Only the Solar Plant, only in a deficit, and only when one more level would end it. It
    // sits in the slot a card already uses to say what its next level is, which is why it is a
    // specification rather than a nag.
    val fix: String?,
    // True on at most one row, and only for the first couple of seconds after a launch: this is the
    // upgrade that landed while the app was closed. The row answers it with a band of light crossing
    // the card once and a level badge that changes behind the band.
    //
    // It is a fact about *this launch* rather than about the colony, which is why it lives on the
    // row rather than on the action: the same colony rendered a minute later has the same levels and
    // nothing to announce.
    val finishedWhileAway: Boolean,
)

sealed interface FacilityActionUiState {
    data object Upgrade : FacilityActionUiState
    data class AffordableIn(val label: String) : FacilityActionUiState
    data class Locked(val reason: String) : FacilityActionUiState

    // Builds run in parallel, so progress belongs to the facility that is building rather than
    // to a single card at the top of the screen.
    data class Upgrading(
        val toLevel: BuildingLevel,
        val countdown: String,
        val progressPercent: Int,
        val doneAt: String,
    ) : FacilityActionUiState
}

// `finishedWhileAway` is what the launch found and nothing else knows: which upgrade completed
// between the instant the save was written and the instant the app came back. Defaulted to nothing,
// so the fifteen existing calls in the tests still say what they meant — and so that every render
// after the arrival window has passed is a plain render with no announcement in it.
fun GameState.toColonyUiState(
    now: Instant,
    timeZone: TimeZone,
    finishedWhileAway: BuildingType? = null,
): ColonyUiState = ColonyUiState(
    energy = buildings.toEnergyUiState(research),
    facilities = BuildingType.entries.map {
        toFacilityRow(
            building = it,
            energy = PlaceholderBalance.energyBalance(buildings, research),
            now = now,
            timeZone = timeZone,
            finishedWhileAway = it == finishedWhileAway,
        )
    },
    returningFleet = returningFleet?.toStrip(now),
)

private fun Buildings.toEnergyUiState(research: Research): EnergyUiState {
    val balance = PlaceholderBalance.energyBalance(this, research)
    val span = maxOf(balance.produced, balance.consumed)
    val covered = if (balance.isDeficit) balance.produced else balance.consumed
    return EnergyUiState(
        verdict = balance.verdict(headroomLevels = PlaceholderBalance.energyHeadroomLevels(this, research)),
        terms = "${balance.produced.groupedByThousands()} produced · " +
            "${balance.consumed.groupedByThousands()} drawn · " +
            "${abs(balance.surplus).groupedByThousands()} ${if (balance.isDeficit) "short" else "spare"}",
        // A razed colony spans nothing; every other case has a term to divide by.
        coveredFraction = if (span == 0L) 0f else covered.toFloat() / span.toFloat(),
        deficit = balance.isDeficit,
    )
}

// Converting the surplus into the unit the player spends is the teaching move: it says the
// mechanic exists, what it is denominated in, and that it will run out — all while nothing is
// wrong. When it does run out the same slot becomes the deficit sentence, already familiar.
private fun EnergyBalance.verdict(headroomLevels: Long): String = when {
    isDeficit && produced == 0L -> "every mine stopped"
    isDeficit -> "every mine at $outputPercent%"
    headroomLevels == 0L -> "break even"
    headroomLevels == 1L -> "room for 1 mine level"
    else -> "room for $headroomLevels mine levels"
}

private fun ReturningFleet.toStrip(now: Instant): ReturningFleetUiState {
    val remainingMs = (arrivesAt.toEpochMilliseconds() - now.toEpochMilliseconds()).coerceAtLeast(0)
    val composition = ShipType.entries
        .mapNotNull { type -> ships[type]?.let { count -> "$count ${type.displayName()}" } }
        .joinToString(" · ")
    return ReturningFleetUiState(
        title = "Fleet returning",
        subtitle = "from [${origin.galaxy}:${origin.system}:${origin.position}] · $composition",
        countdown = ((remainingMs + 999) / 1000).toCountdown(),
    )
}

private fun ShipType.displayName(): String = when (this) {
    ShipType.CARGO -> "cargo"
    ShipType.FIGHTER -> "fighter"
    ShipType.CRUISER -> "cruiser"
    ShipType.COLONY_SHIP -> "colony ship"
}

private fun GameState.toFacilityRow(
    building: BuildingType,
    energy: EnergyBalance,
    now: Instant,
    timeZone: TimeZone,
    finishedWhileAway: Boolean,
): FacilityRowUiState {
    val level = buildings.levelOf(building)
    val toLevel = BuildingLevel(level.value + 1)
    val cost = PlaceholderBalance.upgradeCost(building, toLevel)
    val short = resources.shortfallOf(cost)
    val locked = building == BuildingType.NANITE_FACTORY &&
        buildings.roboticsFactory.value < PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT
    val job = builds[building]
    return FacilityRowUiState(
        building = building,
        name = building.displayName(),
        level = level,
        costs = listOfNotNull(
            cost.metal.toCostChip(ResourceKind.METAL, short),
            cost.crystal.toCostChip(ResourceKind.CRYSTAL, short),
            cost.deuterium.toCostChip(ResourceKind.DEUTERIUM, short),
        ),
        duration = PlaceholderBalance.upgradeDuration(building, toLevel, buildings.roboticsFactory).toChipLabel(),
        power = if (energy.isDeficit) building.powerAt(level, research) else null,
        fix = energy.fixOn(building, solarPlant = buildings.solarPlant, research = research),
        finishedWhileAway = finishedWhileAway,
        action = when {
            job != null -> job.toUpgradingAction(now = now, timeZone = timeZone)
            locked -> FacilityActionUiState.Locked(
                "Requires Robotics ${PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT}",
            )
            short.isEmpty() -> FacilityActionUiState.Upgrade
            else -> FacilityActionUiState.AffordableIn(
                timeUntilAffordable(resources, cost, buildings, research)
                    .takeIf { it.isFinite() }
                    ?.let { "in ${it.toChipLabel()}" }
                    ?: "—",
            )
        },
    )
}

// Signed, because the sign is what makes the top of the list the supply side and the rest of it
// the draw side — which is what makes the indicator's two terms attributable by eye.
private fun BuildingType.powerAt(level: BuildingLevel, research: Research): FacilityPowerUiState? {
    val supplied = PlaceholderBalance.energySupply(this, level, research)
    val drawn = PlaceholderBalance.energyConsumption(this, level)
    return when {
        supplied > 0 -> FacilityPowerUiState(label = "+${supplied.groupedByThousands()}", supply = true)
        drawn > 0 -> FacilityPowerUiState(label = "−${drawn.groupedByThousands()}", supply = false)
        else -> null
    }
}

// The arrow already means "becomes" on a row that is building, so the fix needs no new element:
// in a deficit, what the plant's next level *is* happens to be the end of the deficit.
private fun EnergyBalance.fixOn(
    building: BuildingType,
    solarPlant: BuildingLevel,
    research: Research,
): String? {
    if (building != BuildingType.SOLAR_PLANT || !isDeficit) return null
    val nextLevel = BuildingLevel(solarPlant.value + 1)
    if (PlaceholderBalance.energySupply(BuildingType.SOLAR_PLANT, nextLevel, research) < consumed) return null
    return "→ LV ${nextLevel.value} covers all ${consumed.groupedByThousands()} drawn"
}

private fun BuildJob.toUpgradingAction(now: Instant, timeZone: TimeZone): FacilityActionUiState.Upgrading {
    val totalMs = (completesAt.toEpochMilliseconds() - startedAt.toEpochMilliseconds()).coerceAtLeast(1)
    val elapsedMs = (now.toEpochMilliseconds() - startedAt.toEpochMilliseconds()).coerceIn(0, totalMs)
    val remainingMs = (completesAt.toEpochMilliseconds() - now.toEpochMilliseconds()).coerceAtLeast(0)
    val completesLocal = completesAt.toLocalDateTime(timeZone)
    return FacilityActionUiState.Upgrading(
        toLevel = toLevel,
        // Ceil the remainder so a countdown only reads 00:00:00 once the build is actually done.
        countdown = ((remainingMs + 999) / 1000).toCountdown(),
        progressPercent = (elapsedMs * 100 / totalMs).toInt(),
        doneAt = "done ${completesLocal.hour.pad2()}:${completesLocal.minute.pad2()}",
    )
}

private fun Long.toCostChip(kind: ResourceKind, short: Set<ResourceKind>): CostChipUiState? =
    takeIf { it > 0 }?.let { CostChipUiState(kind = kind, amount = it.groupedByThousands(), short = kind in short) }

internal fun BuildingType.displayName(): String = when (this) {
    BuildingType.METAL_MINE -> "Metal Mine"
    BuildingType.CRYSTAL_MINE -> "Crystal Mine"
    BuildingType.DEUTERIUM_SYNTHESIZER -> "Deuterium Synth."
    BuildingType.SOLAR_PLANT -> "Solar Plant"
    BuildingType.ROBOTICS_FACTORY -> "Robotics Factory"
    BuildingType.NANITE_FACTORY -> "Nanite Factory"
}
