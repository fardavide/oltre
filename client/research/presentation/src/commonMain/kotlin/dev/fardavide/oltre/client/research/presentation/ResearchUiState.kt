package dev.fardavide.oltre.client.research.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.milli
import dev.fardavide.oltre.client.design.format.pad2
import dev.fardavide.oltre.client.design.format.signed
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.design.format.toCountdown
import dev.fardavide.oltre.core.AdaptationBalance
import dev.fardavide.oltre.core.AdaptationLevels
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.HostilityAxis
import dev.fardavide.oltre.core.ResearchBalance
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

// Two branches, six rows, one screen — and the flat list *is* the tech tree, which is still the
// strongest argument against drawing one. Two lists rather than one of six, because an applied
// level multiplies a per-hour rate and an adaptation level widens a band in °C, g or atm: sorting
// them into one column would make two kinds of thing look like one.
//
// They fit. Six rows, two section labels and the 22dp seam between them measure ~610dp against
// ~708dp of content on a 393x852 phone, so there is no scroll — which is what makes the shared slot
// legible without a word of explanation. When a project is in flight, five rows read the same wait
// and the sixth counts it down, on the same screen, and the two numbers verify each other. The day
// a branch grows past what a phone holds, that stops being true and the segmented control the 0.3
// design rejected is back on the table.
data class ResearchUiState(
    val technologies: List<TechnologyRowUiState>,
    val adaptation: List<AdaptationRowUiState>,
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

// The same seven fields as an applied row, and deliberately its own type rather than a widened one:
// the two branches carry different technology enums, and a sum type in the identity field would
// make every reader answer for a branch it does not render. What it is *not* is a different row —
// the screen draws both through one composable, because from three rows away a running ladder has
// to look exactly like a running technology.
data class AdaptationRowUiState(
    val technology: AdaptationTechnology,
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
    // Absent at level 0 on the applied branch: there is no "now" to compare against yet. Never
    // absent on an adaptation row — a tolerance band exists at level 0 where a production bonus
    // does not, which is why Enrichment 0 reads "→ +14%" and Gravitic 0 reads
    // "0.65 … 1.40 → 0.60 … 1.52 g".
    val current: String?,
    val next: String,
    val subject: String,
    // A 320dp Slide Over pane drops the trailing noun. The percentages and the resource names are
    // load-bearing; the word "output" is not. An adaptation row sets this equal to `subject`: the
    // band line is digits, units and relations, and there is nothing in it that could be cut.
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
    adaptation = AdaptationTechnology.entries.map { toAdaptationRow(it, now = now, timeZone = timeZone) },
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
            running != null -> runningAction(
                toLevel = running.toLevel,
                startedAt = running.startedAt,
                completesAt = running.completesAt,
                now = now,
                timeZone = timeZone,
            )
            !requirement.isMetBy(this) -> ResearchActionUiState.Locked(requirement.label())
            else -> startOrWait(cost = cost, now = now)
        },
    )
}

// The applied mapper with three things swapped: `AdaptationBalance` for `ResearchBalance`, the
// other half of the shared slot for `activeResearch`, and a band line for a percentage line. The
// order of the checks is the same because the two branches differ in what they buy, not in how
// they are bought — and the ghost's contract in `startOrWait` is shared outright.
private fun GameState.toAdaptationRow(
    technology: AdaptationTechnology,
    now: Instant,
    timeZone: TimeZone,
): AdaptationRowUiState {
    val level = research.levelOf(technology)
    val toLevel = TechLevel(level.value + 1)
    val cost = AdaptationBalance.adaptationCost(technology, toLevel)
    val short = resources.shortfallOf(cost)
    val requirement = AdaptationBalance.requirementFor(technology)
    val running = activeAdaptation?.takeIf { it.technology == technology }
    return AdaptationRowUiState(
        technology = technology,
        name = technology.displayName(),
        level = level,
        effect = EffectUiState(
            current = technology.bandLabel(level),
            next = technology.bandLabel(toLevel),
            subject = technology.unit(),
            // The same string at both widths, deliberately. See `EffectUiState`.
            compactSubject = technology.unit(),
        ),
        costs = listOfNotNull(
            cost.metal.toCostChip(ResourceKind.METAL, short),
            cost.crystal.toCostChip(ResourceKind.CRYSTAL, short),
            cost.deuterium.toCostChip(ResourceKind.DEUTERIUM, short),
        ),
        duration = AdaptationBalance.adaptationDuration(technology, toLevel, buildings.roboticsFactory).toChipLabel(),
        action = when {
            running != null -> runningAction(
                toLevel = running.toLevel,
                startedAt = running.startedAt,
                completesAt = running.completesAt,
                now = now,
                timeZone = timeZone,
            )
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
    // `researchSlotFreesAt`, not `activeResearch.completesAt`: the slot is empire-wide and shared
    // with the adaptation branch, so a ladder holds it exactly as hard as a technology does.
    // Reading one field would offer a Research button the model then refuses as `SlotBusy`.
    val untilSlotFrees = researchSlotFreesAt
        ?.let { it - now }
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

// Takes the three instants rather than a job, because the two branches have two job types holding
// the same three fields and a running ladder must render identically to a running technology —
// which is only guaranteed if there is one implementation for both to call.
private fun runningAction(
    toLevel: TechLevel,
    startedAt: Instant,
    completesAt: Instant,
    now: Instant,
    timeZone: TimeZone,
): ResearchActionUiState.Running {
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

// One word each, like the applied three, and no trailing "Adaptation": all three would end in the
// same word, which carries nothing and costs eleven characters the row does not have. The Galaxy
// screen's blocked rows already say "Gravitic 9" for the same reason, so the two screens name the
// same object the same way.
private fun AdaptationTechnology.displayName(): String = when (this) {
    AdaptationTechnology.THERMAL -> "Thermal"
    AdaptationTechnology.GRAVITIC -> "Gravitic"
    AdaptationTechnology.ATMOSPHERIC -> "Atmospheric"
}

private fun Technology.subject(): String = when (this) {
    Technology.PHOTOVOLTAICS -> "Solar Plant output"
    Technology.EXTRACTION -> "metal · crystal output"
    Technology.ENRICHMENT -> "deuterium output"
}

// No subject noun, unlike the applied rows: Thermal already says temperature, Gravitic gravity and
// Atmospheric pressure, so "°C tolerance" would be the third time one row said the same thing. Both
// bands carry the same unit, so it is stated once at the end — exactly where the applied row's
// trailing noun sits, which is what keeps the two row types structurally identical.
private fun AdaptationTechnology.unit(): String = when (this) {
    AdaptationTechnology.THERMAL -> "°C"
    AdaptationTechnology.GRAVITIC -> "g"
    AdaptationTechnology.ATMOSPHERIC -> "atm"
}

// "−30 … +45" — the band this ladder tolerates at `level`, in the axis's own unit and written the
// way the Galaxy screen writes a world's reading, because that is the number it is read against.
//
// The widening is `GalaxyBalance`'s rather than `AdaptationBalance`'s: what a level *costs* is the
// research branch's business and what it *buys* is the map's, and this line is the one place the
// two meet. Asking for a tolerance with only this axis raised is exact — each level widens exactly
// its own axis, which is the mechanic that makes an empire that pushed Thermal and one that pushed
// Gravitic look at two different maps.
//
// "…" is the one new glyph in the product and it appears in this line alone. An en dash is
// unreadable against a leading minus — "−30–+45" — and the word "to" puts English inside a line of
// numbers. It leaves exactly one glyph per relation: … means through, → means becomes.
private fun AdaptationTechnology.bandLabel(level: TechLevel): String {
    val band = GalaxyBalance.tolerance(levelsWithOnly(level)).bandOf(axis())
    return when (this) {
        AdaptationTechnology.THERMAL -> "${band.min.signed()} … ${band.max.signed()}"
        AdaptationTechnology.GRAVITIC,
        AdaptationTechnology.ATMOSPHERIC,
        -> "${band.min.milli()} … ${band.max.milli()}"
    }
}

private fun AdaptationTechnology.axis(): HostilityAxis = HostilityAxis.entries.first { it.adaptation == this }

private fun AdaptationTechnology.levelsWithOnly(level: TechLevel): AdaptationLevels = AdaptationLevels(
    thermal = if (this == AdaptationTechnology.THERMAL) level.value else 0,
    gravitic = if (this == AdaptationTechnology.GRAVITIC) level.value else 0,
    atmospheric = if (this == AdaptationTechnology.ATMOSPHERIC) level.value else 0,
)

private fun Technology.compactSubject(): String = when (this) {
    Technology.PHOTOVOLTAICS -> "Solar Plant"
    Technology.EXTRACTION -> "metal · crystal"
    Technology.ENRICHMENT -> "deuterium"
}

private fun Int.toPercent(): String = "+$this%"

private fun Long.toCostChip(kind: ResourceKind, short: Set<ResourceKind>): CostChipUiState? =
    takeIf { it > 0 }?.let { CostChipUiState(kind = kind, amount = it.groupedByThousands(), short = kind in short) }
