package dev.fardavide.oltre.client.research.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.milli
import dev.fardavide.oltre.client.design.format.milliTrimmed
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
import dev.fardavide.oltre.core.LadderShortlist
import dev.fardavide.oltre.core.ResearchBalance
import dev.fardavide.oltre.core.ResearchRequirement
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.adaptationShortlist
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
// When a project is in flight, five rows read the same wait and the sixth counts it down, and the
// two numbers verify each other with nothing added to carry the explanation. That is what one
// screen buys and what neither a segmented control nor a sixth tab can buy at any price.
//
// **They very nearly fit and do not quite.** The 0.3 design put six rows at ~610dp against ~708dp
// of content on a 393x852 phone; it measured the row at 74dp and the real Compose row is 106dp, so
// the true figure is ~788dp and the screen scrolls by about 105dp. The argument survives — five of
// the six rows and the countdown are on screen together, which is the part that has to be true —
// but it is now an argument about a screen that scrolls a little, and the design sheet should know.
// See `decisions.md`.
data class ResearchUiState(
    val technologies: List<TechnologyRowUiState>,
    val adaptation: List<AdaptationRowUiState>,
)

// Which project landed between the instant the save was written and the instant the app came back.
// One or the other, never both: the two branches share one slot, so at most one thing can have been
// in flight while the app was closed.
sealed interface FinishedWhileAway {
    data class Project(val technology: Technology) : FinishedWhileAway
    data class Ladder(val technology: AdaptationTechnology) : FinishedWhileAway
}

data class TechnologyRowUiState(
    val technology: Technology,
    val name: String,
    val level: TechLevel,
    val effect: EffectUiState,
    val costs: List<CostChipUiState>,
    val duration: String,
    val action: ResearchActionUiState,
    // True on at most one row in the whole app, and only for the first couple of seconds after a
    // launch. See the same field on the colony's facility rows — it is a fact about this launch
    // rather than about the empire.
    val finishedWhileAway: Boolean,
) {
    // Always absent, and it is a field rather than nothing at all so that `ProjectRow` can draw
    // both branches from one set of parts. An applied technology multiplies a rate; it cannot make
    // a world habitable, so a line about worlds here would be the screen inventing a consequence.
    val shortlist: ShortlistUiState? get() = null
}

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
    // The one line the applied branch has no equivalent for, and the reason the two row types
    // stayed separate. Never null on this branch: a ladder that would unlock nothing reports zero
    // rather than going quiet, because "Thermal 1 unlocks nothing" is the sentence that makes the
    // other two mean something.
    val shortlist: ShortlistUiState,
    val finishedWhileAway: Boolean,
)

// What the next level of this ladder would buy, counted over the worlds the player has **already
// surveyed** — and therefore the line that makes surveying a decision rather than a bookmark.
//
// Without it the optimal play is to wait: `verdictFor` re-derives against current levels and
// `GalaxyState.surveyed` is monotone, so surveying later returns strictly better-labelled rows for
// the same price. This inverts the order — survey, read the shortlist, then commit the one shared
// slot — so the information has to arrive before the purchase rather than after it.
//
// It also answers, for the first time, a question the three ladders could never pose. Priced at
// 1 : 2 : 3 they cost identically, so with four home worlds surveyed the choice between Thermal,
// Gravitic and Atmospheric is arbitrary. With fifty worlds surveyed it is arithmetic.
data class ShortlistUiState(
    val unlocks: Int,
    // The honest half. Most worlds a ladder unlocks are still not worth taking, by construction —
    // a line that sold the count without this would be selling the ladder on a number the player
    // cannot spend.
    val worthTaking: Int,
    val label: String,
    // 320dp drops the adjective, never a figure. Both counts are what the player is comparing
    // across the three rows, and the effect line above it abbreviates by the same rule.
    val compactLabel: String,
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

// `finishedWhileAway` defaults to nothing, so the existing calls in the tests still say what they
// meant and every render after the arrival window is a plain one.
fun GameState.toResearchUiState(
    now: Instant,
    timeZone: TimeZone,
    finishedWhileAway: FinishedWhileAway? = null,
): ResearchUiState {
    // Derived once for all three rows rather than per row: `adaptationShortlist` regenerates every
    // surveyed world from the seed, and asking it three times would do that work three times over
    // for one answer it already computes in full.
    val shortlists = adaptationShortlist(this).associateBy { it.technology }
    val finishedProject = (finishedWhileAway as? FinishedWhileAway.Project)?.technology
    val finishedLadder = (finishedWhileAway as? FinishedWhileAway.Ladder)?.technology
    return ResearchUiState(
        technologies = Technology.entries.map {
            toTechnologyRow(it, now = now, timeZone = timeZone, finishedWhileAway = it == finishedProject)
        },
        adaptation = AdaptationTechnology.entries.map {
            toAdaptationRow(
                it,
                shortlist = shortlists.getValue(it),
                now = now,
                timeZone = timeZone,
                finishedWhileAway = it == finishedLadder,
            )
        },
    )
}

private fun GameState.toTechnologyRow(
    technology: Technology,
    now: Instant,
    timeZone: TimeZone,
    finishedWhileAway: Boolean,
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
        finishedWhileAway = finishedWhileAway,
    )
}

// The applied mapper with three things swapped: `AdaptationBalance` for `ResearchBalance`, the
// other half of the shared slot for `activeResearch`, and a band line for a percentage line. The
// order of the checks is the same because the two branches differ in what they buy, not in how
// they are bought — and the ghost's contract in `startOrWait` is shared outright.
private fun GameState.toAdaptationRow(
    technology: AdaptationTechnology,
    shortlist: LadderShortlist,
    now: Instant,
    timeZone: TimeZone,
    finishedWhileAway: Boolean,
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
        shortlist = shortlist.toUiState(),
        finishedWhileAway = finishedWhileAway,
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

// PLACEHOLDER copy, like the notification bodies and for the same reason: what a screen says to a
// player is content, and content is Davide's.
//
// The shape is not placeholder. It states both counts and it states the second one even when it is
// zero, because the pair is the honest reading — round 9 measured 0.35% of worlds galaxy-wide over
// the worth-it bar, so a line that reported only "unlocks 5" would read as a payoff on a row where
// it usually is not one. It names neither the technology nor the level, both of which are already
// on the row two lines above: the name in the title, the level it becomes on the right of the band
// line. Repeating them would be the third time one card said the same thing.
// Internal rather than private so the screenshot fixtures can state their *numbers* by hand — which
// is this module's rule, so a baseline moves only when the screen does — and still get their
// *strings* from here. The galaxy module learned the other way round: hand-written strings drifted
// from the mapper's own formatting within an hour, and the baselines quietly asserted sentences the
// app would never produce.
internal fun LadderShortlist.toUiState(): ShortlistUiState = ShortlistUiState(
    unlocks = unlocks,
    worthTaking = worthTaking,
    label = describe(verb = true),
    compactLabel = describe(verb = false),
)

// The verb is what 320dp drops, and nothing else — a word is recoverable from the row it sits on
// and a figure is not, which is the same rule the effect line above abbreviates by.
//
// **Both forms are measured rather than guessed.** The text column beside the action button is
// ~254dp at 393dp and ~181dp at 320dp, which at 10.5sp JetBrains Mono is about 40 and 28
// characters. The first draft said "Unlocks 5 surveyed worlds, 1 worth taking" — 41 characters,
// so it wrapped to two lines on a *phone*, and on a screen already scrolling by ~105dp three
// two-line rows are 45dp more of it. "surveyed" went; the counts stayed.
//
// The zero case keeps "you have surveyed" at both widths, and the asymmetry is deliberate: it is
// the one reading where the useful information is not the number but what would change it. "Go
// survey more" is the whole reason this line exists.
private fun LadderShortlist.describe(verb: Boolean): String {
    if (unlocks == 0) return if (verb) "Unlocks nothing you have surveyed" else "Nothing you have surveyed"
    val worlds = if (unlocks == 1) "1 world" else "$unlocks worlds"
    // U+00A0 between the count and its qualifier, exactly as the Galaxy screen binds a value to its
    // unit. If a longer count does push this to two lines, it has to break at the comma — "1 worth"
    // stranded above "taking" reads as a defect where a clause break reads as a wrap.
    val worth = if (worthTaking == 0) "none${NBSP}worth${NBSP}taking" else "$worthTaking${NBSP}worth${NBSP}taking"
    return if (verb) "Unlocks $worlds, $worth" else "$worlds, $worth"
}

// Between a count and the words that qualify it, so a line that has to wrap never leaves "taking"
// alone on one.
private const val NBSP = ' '

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
// Each axis at the precision the design specifies for it, which is not one rule because the three
// axes do not have one width. Temperature is whole degrees. Gravity keeps both decimals, so its two
// bands read as a column. Pressure drops the zeros it does not need — its band spans an order of
// magnitude more than gravity's, so it carries a leading digit more, and padded it is the one line
// in the app that does not fit at 320dp: the unit gets ellipsised to "a…". See `milliTrimmed`.
private fun AdaptationTechnology.bandLabel(level: TechLevel): String {
    val band = GalaxyBalance.tolerance(levelsWithOnly(level)).bandOf(axis())
    return when (this) {
        AdaptationTechnology.THERMAL -> "${band.min.signed()} … ${band.max.signed()}"
        AdaptationTechnology.GRAVITIC -> "${band.min.milli()} … ${band.max.milli()}"
        AdaptationTechnology.ATMOSPHERIC -> "${band.min.milliTrimmed()} … ${band.max.milliTrimmed()}"
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
