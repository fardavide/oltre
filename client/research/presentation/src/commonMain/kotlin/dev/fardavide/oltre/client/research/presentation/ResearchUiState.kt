package dev.fardavide.oltre.client.research.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.pad2
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.design.format.toCountdown
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResearchBalance
import dev.fardavide.oltre.core.ResearchJob
import dev.fardavide.oltre.core.ResearchRequirement
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.shortfallOf
import dev.fardavide.oltre.core.timeUntilAffordable
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Three rows and nothing else — the flat list *is* the tech tree, which is the strongest argument
// against drawing one: a tree needs a picture, and a picture of three nodes is not worth a screen.
data class ResearchUiState(
    val technologies: List<TechnologyRowUiState>,
)

data class TechnologyRowUiState(
    val technology: Technology,
    val name: String,
    val level: TechLevel,
    val effect: EffectUiState,
    val costs: List<CostChipUiState>,
    val duration: String,
    val action: ResearchActionUiState,
)

// The one line research adds to the facility idiom, and it earns it by carrying both halves of the
// question: what the technology does now, and what the next level would make it. Showing only the
// next level's value answers a question nobody asked — level 8 is only meaningful against level 7.
data class EffectUiState(
    // Absent at level 0: there is no "now" to compare against yet.
    val current: String?,
    val next: String,
    val subject: String,
    // A 320dp Slide Over pane drops the trailing noun. The percentages and the resource names are
    // load-bearing; the word "output" is not.
    val compactSubject: String,
)

sealed interface ResearchActionUiState {
    data object Start : ResearchActionUiState

    // One number with one meaning: when you can start this. It is the later of "when can I pay
    // for it" and "when does the slot free", and the player never has to know which it was.
    data class AvailableIn(val label: String) : ResearchActionUiState
    data class Locked(val reason: String) : ResearchActionUiState

    data class Running(
        val toLevel: TechLevel,
        val countdown: String,
        val progressPercent: Int,
        val doneAt: String,
    ) : ResearchActionUiState
}

fun GameState.toResearchUiState(now: Instant, timeZone: TimeZone): ResearchUiState = ResearchUiState(
    technologies = Technology.entries.map { toTechnologyRow(it, now = now, timeZone = timeZone) },
)

private fun GameState.toTechnologyRow(
    technology: Technology,
    now: Instant,
    timeZone: TimeZone,
): TechnologyRowUiState {
    val level = research.levelOf(technology)
    val toLevel = TechLevel(level.value + 1)
    val cost = ResearchBalance.researchCost(technology, toLevel)
    val short = resources.shortfallOf(cost)
    val requirement = ResearchBalance.requirementFor(technology)
    val running = activeResearch?.takeIf { it.technology == technology }
    return TechnologyRowUiState(
        technology = technology,
        name = technology.displayName(),
        level = level,
        effect = EffectUiState(
            current = level.takeIf { it.value > 0 }?.let { ResearchBalance.effectPercent(technology, it).toPercent() },
            next = ResearchBalance.effectPercent(technology, toLevel).toPercent(),
            subject = technology.subject(),
            compactSubject = technology.compactSubject(),
        ),
        costs = listOfNotNull(
            cost.metal.toCostChip(ResourceKind.METAL, short),
            cost.crystal.toCostChip(ResourceKind.CRYSTAL, short),
            cost.deuterium.toCostChip(ResourceKind.DEUTERIUM, short),
        ),
        // Already divided by Robotics, because that is the duration the player would actually wait.
        duration = ResearchBalance.researchDuration(technology, toLevel, buildings.roboticsFactory).toChipLabel(),
        action = when {
            running != null -> running.toRunningAction(now = now, timeZone = timeZone)
            !requirement.isMetBy(this) -> ResearchActionUiState.Locked(requirement.label())
            else -> startOrWait(cost = cost, now = now)
        },
    )
}

// The ghost carries a time, never a dead button. The wait is the later of the two reasons a
// project cannot start, so a row that is both unaffordable and blocked by the slot shows the one
// that actually governs.
private fun GameState.startOrWait(cost: Resources, now: Instant): ResearchActionUiState {
    val untilAffordable = timeUntilAffordable(resources, cost, buildings, research)
    val untilSlotFrees = activeResearch
        ?.let { it.completesAt - now }
        ?.coerceAtLeast(Duration.ZERO)
        ?: Duration.ZERO
    val wait = maxOf(untilAffordable, untilSlotFrees)
    return when {
        wait == Duration.ZERO -> ResearchActionUiState.Start
        // A resource with no production at all never arrives; saying "in 2,000,000h" would be a
        // worse lie than saying nothing.
        wait.isFinite() -> ResearchActionUiState.AvailableIn("in ${wait.toChipLabel()}")
        else -> ResearchActionUiState.AvailableIn("—")
    }
}

private fun ResearchJob.toRunningAction(now: Instant, timeZone: TimeZone): ResearchActionUiState.Running {
    val totalMs = (completesAt.toEpochMilliseconds() - startedAt.toEpochMilliseconds()).coerceAtLeast(1)
    val elapsedMs = (now.toEpochMilliseconds() - startedAt.toEpochMilliseconds()).coerceIn(0, totalMs)
    val remainingMs = (completesAt.toEpochMilliseconds() - now.toEpochMilliseconds()).coerceAtLeast(0)
    val completesLocal = completesAt.toLocalDateTime(timeZone)
    return ResearchActionUiState.Running(
        toLevel = toLevel,
        // Ceil the remainder so a countdown only reads 00:00:00 once the project is actually done.
        countdown = ((remainingMs + 999) / 1000).toCountdown(),
        progressPercent = (elapsedMs * 100 / totalMs).toInt(),
        doneAt = "done ${completesLocal.hour.pad2()}:${completesLocal.minute.pad2()}",
    )
}

// "Requires Robotics 1" / "Requires Extraction 3" — short forms, because the requirement line is
// read at a glance and the full facility name buys nothing here.
private fun ResearchRequirement.label(): String = when (this) {
    is ResearchRequirement.Facility -> "Requires ${building.shortName()} ${level.value}"
    is ResearchRequirement.Tech -> "Requires ${technology.displayName()} ${level.value}"
}

private fun BuildingType.shortName(): String = when (this) {
    BuildingType.METAL_MINE -> "Metal Mine"
    BuildingType.CRYSTAL_MINE -> "Crystal Mine"
    BuildingType.DEUTERIUM_SYNTHESIZER -> "Deuterium"
    BuildingType.SOLAR_PLANT -> "Solar Plant"
    BuildingType.ROBOTICS_FACTORY -> "Robotics"
    BuildingType.NANITE_FACTORY -> "Nanite"
}

// One word each, so no row ever needs a 320dp abbreviation, and the set reads as a set next to the
// two-word facility names: facilities are physical things, technologies are disciplines.
internal fun Technology.displayName(): String = when (this) {
    Technology.PHOTOVOLTAICS -> "Photovoltaics"
    Technology.EXTRACTION -> "Extraction"
    Technology.ENRICHMENT -> "Enrichment"
}

private fun Technology.subject(): String = when (this) {
    Technology.PHOTOVOLTAICS -> "Solar Plant output"
    Technology.EXTRACTION -> "metal · crystal output"
    Technology.ENRICHMENT -> "deuterium output"
}

private fun Technology.compactSubject(): String = when (this) {
    Technology.PHOTOVOLTAICS -> "Solar Plant"
    Technology.EXTRACTION -> "metal · crystal"
    Technology.ENRICHMENT -> "deuterium"
}

private fun Int.toPercent(): String = "+$this%"

private fun Long.toCostChip(kind: ResourceKind, short: Set<ResourceKind>): CostChipUiState? =
    takeIf { it > 0 }?.let { CostChipUiState(kind = kind, amount = it.groupedByThousands(), short = kind in short) }
