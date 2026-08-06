package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.core.BuildJob
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Buildings
import dev.fardavide.oltre.core.EnergyBalance
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ReturningFleet
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.shortfallOf
import dev.fardavide.oltre.core.timeUntilAffordable
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class ColonyUiState(
    val metal: String,
    val metalRatePerHour: String,
    val crystal: String,
    val crystalRatePerHour: String,
    val deuterium: String,
    val deuteriumRatePerHour: String,
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
)

data class CostChipUiState(
    val kind: ResourceKind,
    val amount: String,
    val short: Boolean,
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

fun GameState.toColonyUiState(now: Instant, timeZone: TimeZone): ColonyUiState = ColonyUiState(
    metal = resources.metal.groupedByThousands(),
    metalRatePerHour = "+${PlaceholderBalance.effectiveMetalProductionPerHour(buildings).groupedByThousands()}/h",
    crystal = resources.crystal.groupedByThousands(),
    crystalRatePerHour = "+${PlaceholderBalance.effectiveCrystalProductionPerHour(buildings).groupedByThousands()}/h",
    deuterium = resources.deuterium.groupedByThousands(),
    deuteriumRatePerHour = "+${PlaceholderBalance.effectiveDeuteriumProductionPerHour(buildings).groupedByThousands()}/h",
    energy = buildings.toEnergyUiState(),
    facilities = BuildingType.entries.map {
        toFacilityRow(
            building = it,
            energy = PlaceholderBalance.energyBalance(buildings),
            now = now,
            timeZone = timeZone,
        )
    },
    returningFleet = returningFleet?.toStrip(now),
)

private fun Buildings.toEnergyUiState(): EnergyUiState {
    val balance = PlaceholderBalance.energyBalance(this)
    val span = maxOf(balance.produced, balance.consumed)
    val covered = if (balance.isDeficit) balance.produced else balance.consumed
    return EnergyUiState(
        verdict = balance.verdict(headroomLevels = PlaceholderBalance.energyHeadroomLevels(this)),
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

private fun Long.toCountdown(): String {
    val hours = this / 3600
    val minutes = this % 3600 / 60
    val seconds = this % 60
    return "${hours.pad2()}:${minutes.pad2()}:${seconds.pad2()}"
}

private fun Long.pad2(): String = toString().padStart(2, '0')

private fun Int.pad2(): String = toString().padStart(2, '0')

private fun GameState.toFacilityRow(
    building: BuildingType,
    energy: EnergyBalance,
    now: Instant,
    timeZone: TimeZone,
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
        power = if (energy.isDeficit) building.powerAt(level) else null,
        fix = energy.fixOn(building, solarPlant = buildings.solarPlant),
        action = when {
            job != null -> job.toUpgradingAction(now = now, timeZone = timeZone)
            locked -> FacilityActionUiState.Locked(
                "Requires Robotics ${PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT}",
            )
            short.isEmpty() -> FacilityActionUiState.Upgrade
            else -> FacilityActionUiState.AffordableIn(
                timeUntilAffordable(resources, cost, buildings)
                    .takeIf { it.isFinite() }
                    ?.let { "in ${it.toChipLabel()}" }
                    ?: "—",
            )
        },
    )
}

// Signed, because the sign is what makes the top of the list the supply side and the rest of it
// the draw side — which is what makes the indicator's two terms attributable by eye.
private fun BuildingType.powerAt(level: BuildingLevel): FacilityPowerUiState? {
    val supplied = PlaceholderBalance.energySupply(this, level)
    val drawn = PlaceholderBalance.energyConsumption(this, level)
    return when {
        supplied > 0 -> FacilityPowerUiState(label = "+${supplied.groupedByThousands()}", supply = true)
        drawn > 0 -> FacilityPowerUiState(label = "−${drawn.groupedByThousands()}", supply = false)
        else -> null
    }
}

// The arrow already means "becomes" on a row that is building, so the fix needs no new element:
// in a deficit, what the plant's next level *is* happens to be the end of the deficit.
private fun EnergyBalance.fixOn(building: BuildingType, solarPlant: BuildingLevel): String? {
    if (building != BuildingType.SOLAR_PLANT || !isDeficit) return null
    val nextLevel = BuildingLevel(solarPlant.value + 1)
    if (PlaceholderBalance.energySupply(BuildingType.SOLAR_PLANT, nextLevel) < consumed) return null
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

// Mockup style: "1h 04m" / "42m"; sub-minute durations round up so a chip never reads 0m.
private fun Duration.toChipLabel(): String {
    val totalMinutes = (inWholeSeconds + 59) / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes.toString().padStart(2, '0')}m" else "${minutes}m"
}

internal fun BuildingType.displayName(): String = when (this) {
    BuildingType.METAL_MINE -> "Metal Mine"
    BuildingType.CRYSTAL_MINE -> "Crystal Mine"
    BuildingType.DEUTERIUM_SYNTHESIZER -> "Deuterium Synth."
    BuildingType.SOLAR_PLANT -> "Solar Plant"
    BuildingType.ROBOTICS_FACTORY -> "Robotics Factory"
    BuildingType.NANITE_FACTORY -> "Nanite Factory"
}

private fun Long.groupedByThousands(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()
