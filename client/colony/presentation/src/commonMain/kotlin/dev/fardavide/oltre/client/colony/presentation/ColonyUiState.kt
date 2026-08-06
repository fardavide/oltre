package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.core.BuildJob
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ReturningFleet
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.shortfallOf
import dev.fardavide.oltre.core.timeUntilAffordable
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// The stocks and their rates are deliberately absent: the resource rail is the shell's chrome
// now, because it frames every destination rather than only this one.
data class ColonyUiState(
    val facilities: List<FacilityRowUiState>,
    val returningFleet: ReturningFleetUiState?,
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
    facilities = BuildingType.entries.map { toFacilityRow(it, now = now, timeZone = timeZone) },
    returningFleet = returningFleet?.toStrip(now),
)

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
