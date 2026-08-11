package dev.fardavide.oltre.client.research.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.component.RowSheetUiState
import dev.fardavide.oltre.client.design.component.SheetAction
import dev.fardavide.oltre.client.design.component.SheetFooter
import dev.fardavide.oltre.client.design.component.SheetLadderStep
import dev.fardavide.oltre.client.design.component.SheetLine
import dev.fardavide.oltre.client.design.component.SheetPointer
import dev.fardavide.oltre.client.design.component.VerdictUiState
import dev.fardavide.oltre.client.design.component.WatchUiState
import dev.fardavide.oltre.client.design.component.figure
import dev.fardavide.oltre.client.design.component.sheetLine
import dev.fardavide.oltre.client.design.component.words
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.milli
import dev.fardavide.oltre.client.design.format.milliTrimmed
import dev.fardavide.oltre.client.design.format.pad2
import dev.fardavide.oltre.client.design.format.signed
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.design.format.toCountdown
import dev.fardavide.oltre.client.design.format.toPaybackLabel
import dev.fardavide.oltre.client.design.format.watchedAtLabel
import dev.fardavide.oltre.core.AdaptationBalance
import dev.fardavide.oltre.core.AdaptationLevels
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Gate
import dev.fardavide.oltre.core.GateSubject
import dev.fardavide.oltre.core.HostilityAxis
import dev.fardavide.oltre.core.LadderShortlist
import dev.fardavide.oltre.core.LevelPurpose
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.ResearchBalance
import dev.fardavide.oltre.core.ResearchRequirement
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.WatchTarget
import dev.fardavide.oltre.core.adaptationShortlist
import dev.fardavide.oltre.core.gatesOf
import dev.fardavide.oltre.core.purposeOfNextLevel
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
// of content on a 393x852 phone; it measured the row at 74dp and the real Compose row was 106dp, so
// the true figure was ~788dp and the screen scrolled by about 105dp. The argument survives — five of
// the six rows and the countdown are on screen together, which is the part that has to be true —
// but it is now an argument about a screen that scrolls a little, and the design sheet should know.
// See `decisions.md`.
//
// **The gap halved when the verdict arrived.** Every row is 97dp now, measured, because one line of
// consequence replaced the applied row's effect line and the ladder's effect *and* shortlist lines —
// so the tallest frame is 734dp and the screen scrolls by about 50dp. The two branches are also the
// same height for the first time, which is what "a running ladder looks exactly like a running
// technology" was always supposed to mean.
data class ResearchUiState(
    val technologies: List<TechnologyRowUiState>,
    val adaptation: List<AdaptationRowUiState>,
    // "watching Metal Mine" beside the TECHNOLOGIES heading, and null when nothing is watched. The
    // watch is one slot shared with the colony, so this names a facility as readily as a technology
    // — and it is handed in for the same reason the Colony screen's copy is: what a facility is
    // called belongs to the screen that draws facilities.
    val watching: String?,
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
    // No longer drawn on the card — the verdict took that line, because two lines of numbers about
    // the same level is where a dense row becomes an unreadable one. It is still carried, because
    // the sheet states both halves of it and the sheet is derived from here.
    val effect: EffectUiState,
    // What the next level is worth to this empire now. Null only while the row is in flight, where
    // the decision has already been made and the slot belongs to the finish line, and at a ceiling
    // there is no next level to price.
    val verdict: VerdictUiState?,
    val costs: List<CostChipUiState>,
    val duration: String,
    val action: ResearchActionUiState,
    // What the card body opens: the arithmetic behind the verdict, the ladder of what the level
    // gates, and the numbers the verdict displaced. Derived here rather than in the screen because
    // an inert row's pointer names the best buy on the *screen*, which no single row can know.
    val sheet: RowSheetUiState,
    // The square beside the ghost time. Null unless this row is one the empire cannot *pay* for —
    // a row held up only by a busy slot has nothing to be told about, because the resources are
    // already there. See `WatchUiState`.
    val watch: WatchUiState?,
    // True on at most one row in the whole app, and only for the first couple of seconds after a
    // launch. See the same field on the colony's facility rows — it is a fact about this launch
    // rather than about the empire.
    val finishedWhileAway: Boolean,
)

// The same fields as an applied row, and deliberately its own type rather than a widened one:
// the two branches carry different technology enums, and a sum type in the identity field would
// make every reader answer for a branch it does not render. What it is *not* is a different row —
// the screen draws both through one composable, because from three rows away a running ladder has
// to look exactly like a running technology.
data class AdaptationRowUiState(
    val technology: AdaptationTechnology,
    val name: String,
    val level: TechLevel,
    val effect: EffectUiState,
    // The shortlist wearing the name every row now uses for the same slot. It is derived from the
    // field below rather than authored twice — the shortlist *was* the verdict, and this design is
    // that sentence repeated on the other twelve rows.
    val verdict: VerdictUiState?,
    val costs: List<CostChipUiState>,
    val duration: String,
    val action: ResearchActionUiState,
    // The counts behind the verdict, which the sheet states as a sentence. Never null on this
    // branch: a ladder that would unlock nothing reports zero rather than going quiet, because
    // "Thermal 1 unlocks nothing" is the sentence that makes the other two mean something.
    val shortlist: ShortlistUiState,
    val sheet: RowSheetUiState,
    // The applied branch's field, unchanged: the two rows share one composable, so a watched ladder
    // has to be drawn by the same parts as a watched technology.
    val watch: WatchUiState?,
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

// What the technology does now and what the next level would make it — both halves, because
// level 8 is only meaningful against level 7.
//
// **It was the row's second line until 0.6.0 and it is the sheet's first sentence now.** The verdict
// took the slot, and the two of them could not share it: two lines of numbers about the same level
// is where a dense row becomes an unreadable one. That move is also why this no longer carries a
// short form — the shortening existed because 320dp could not fit "metal · crystal output" beside a
// watch square, and the sheet is a full-width surface with no square on it.
data class EffectUiState(
    // Absent at level 0 on the applied branch: there is no "now" to compare against yet. Never
    // absent on an adaptation row — a tolerance band exists at level 0 where a production bonus
    // does not, which is why Enrichment 0 reads "→ +14%" and Gravitic 0 reads
    // "0.65 … 1.40 → 0.60 … 1.52 g".
    val current: String?,
    val next: String,
    val subject: String,
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
    watching: String? = null,
): ResearchUiState {
    // Derived once for all three rows rather than per row: `adaptationShortlist` regenerates every
    // surveyed world from the seed, and asking it three times would do that work three times over
    // for one answer it already computes in full.
    val shortlists = adaptationShortlist(this).associateBy { it.technology }
    // Every applied row's purpose, derived once for the same reason: an inert row's sheet points at
    // the best buy *on this screen*, which is a fact about the other rows rather than about itself.
    val purposes = Technology.entries.associateWith { purposeOfNextLevel(it) }
    val finishedProject = (finishedWhileAway as? FinishedWhileAway.Project)?.technology
    val finishedLadder = (finishedWhileAway as? FinishedWhileAway.Ladder)?.technology
    return ResearchUiState(
        technologies = Technology.entries.map {
            toTechnologyRow(
                it,
                purpose = purposes.getValue(it),
                bestBuy = bestBuyBesides(it, purposes),
                now = now,
                timeZone = timeZone,
                finishedWhileAway = it == finishedProject,
            )
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
        watching = watching,
    )
}

private fun GameState.toTechnologyRow(
    technology: Technology,
    purpose: LevelPurpose,
    bestBuy: SheetPointer?,
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
    val effect = EffectUiState(
        current = level.takeIf { it.value > 0 }?.let { ResearchBalance.effectPercent(technology, it).toPercent() },
        next = ResearchBalance.effectPercent(technology, toLevel).toPercent(),
        subject = technology.subject(),
    )
    val costs = listOfNotNull(
        cost.metal.toCostChip(ResourceKind.METAL, short),
        cost.crystal.toCostChip(ResourceKind.CRYSTAL, short),
        cost.deuterium.toCostChip(ResourceKind.DEUTERIUM, short),
    )
    // Already divided by Robotics, because that is the duration the player would actually wait.
    val duration = ResearchBalance.researchDuration(technology, toLevel, buildings.roboticsFactory).toChipLabel()
    val action = when {
        running != null -> runningAction(
            toLevel = running.toLevel,
            startedAt = running.startedAt,
            completesAt = running.completesAt,
            now = now,
            timeZone = timeZone,
        )
        !requirement.isMetBy(this) -> ResearchActionUiState.Locked(requirement.label())
        else -> startOrWait(cost = cost, now = now)
    }
    val verdict = purpose.toVerdictUiState(throttled = throttled())
    return TechnologyRowUiState(
        technology = technology,
        name = technology.displayName(),
        level = level,
        effect = effect,
        verdict = verdict.takeIf { running == null },
        costs = costs,
        duration = duration,
        action = action,
        sheet = rowSheet(
            name = technology.displayName(),
            level = level,
            verdict = verdict,
            action = action,
            requirement = requirement,
            lines = projectLines(purpose = purpose, effect = effect, name = technology.displayName()),
            ladder = technology.sheetLadder(level),
            // Only an inert row carries one: a row with a rate to state has its own argument, and
            // an arrow to a better buy would be the screen arguing against the row it is on.
            bestBuy = bestBuy.takeIf { purpose is LevelPurpose.Inert },
            costs = costs,
            duration = duration,
        ),
        watch = watchOn(
            target = WatchTarget.Project(technology),
            cost = cost,
            running = running != null,
            requirementMet = requirement.isMetBy(this),
            now = now,
            timeZone = timeZone,
        ),
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
    val band = technology.bandLabel(level)
    val widened = technology.bandLabel(toLevel)
    val effect = EffectUiState(
        current = band,
        next = widened,
        subject = technology.unit(),
        // The same string at both widths, deliberately. See `EffectUiState`.
    )
    val costs = listOfNotNull(
        cost.metal.toCostChip(ResourceKind.METAL, short),
        cost.crystal.toCostChip(ResourceKind.CRYSTAL, short),
        cost.deuterium.toCostChip(ResourceKind.DEUTERIUM, short),
    )
    val duration = AdaptationBalance
        .adaptationDuration(technology, toLevel, buildings.roboticsFactory)
        .toChipLabel()
    val action = when {
        running != null -> runningAction(
            toLevel = running.toLevel,
            startedAt = running.startedAt,
            completesAt = running.completesAt,
            now = now,
            timeZone = timeZone,
        )
        !requirement.isMetBy(this) -> ResearchActionUiState.Locked(requirement.label())
        else -> startOrWait(cost = cost, now = now)
    }
    val ladderShortlist = shortlist.toUiState()
    return AdaptationRowUiState(
        technology = technology,
        name = technology.displayName(),
        level = level,
        effect = effect,
        verdict = ladderShortlist.toVerdictUiState().takeIf { running == null },
        costs = costs,
        duration = duration,
        action = action,
        shortlist = ladderShortlist,
        sheet = rowSheet(
            name = technology.displayName(),
            level = level,
            verdict = ladderShortlist.toVerdictUiState(),
            action = action,
            requirement = requirement,
            lines = listOf(
                bandSentence(unit = technology.unit(), current = band, next = widened),
                reachSentence(ladderShortlist),
            ),
            // Nothing gates a ladder onward: the three are the end of their own branch, and a
            // ladder of one empty step would be a component asserting that.
            ladder = emptyList(),
            // No pointer either. A ladder is never inert — every level widens a band, whether or
            // not a surveyed world happens to sit inside the widening.
            bestBuy = null,
            costs = costs,
            duration = duration,
        ),
        watch = watchOn(
            target = WatchTarget.Ladder(technology),
            cost = cost,
            running = running != null,
            requirementMet = requirement.isMetBy(this),
            now = now,
            timeZone = timeZone,
        ),
        finishedWhileAway = finishedWhileAway,
    )
}

// **The square answers a narrower question than the ghost beside it.** The ghost's time is the later
// of two waits — the price and the slot — but a watch is only ever about the price, so a row the
// empire can already pay for offers none: there is nothing to be told, the resources are in the
// stores and the lab is simply busy. That is not a special case for this screen, it is core's own
// rule: `futureEvents` projects no instant for a purchase the stocks already cover.
//
// Absent too when the row cannot be bought at all — a requirement it has not met has no price yet,
// and a binding resource with no net income never reaches the price it does have.
private fun GameState.watchOn(
    target: WatchTarget,
    cost: Resources,
    running: Boolean,
    requirementMet: Boolean,
    now: Instant,
    timeZone: TimeZone,
): WatchUiState? {
    // A project in flight is asked about its completion, not its price — the price is paid. This is
    // the same square and a different question, and which one it is is a fact about the row.
    if (running) return if (target in subscribed) WatchUiState.Subscribed else WatchUiState.Offered
    if (!requirementMet || resources.covers(cost)) return null
    val wait = timeUntilAffordable(resources, cost, buildings, research).takeIf { it.isFinite() } ?: return null
    if (watching != target) return WatchUiState.Offered
    val local = (now + wait).toLocalDateTime(timeZone)
    return WatchUiState.Booked(watchedAtLabel(hour = local.hour, minute = local.minute))
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

// ── What the level is worth ──────────────────────────────────────────────────────────────────
//
// One sentence per row, in the slot the effect line had, and the same question answered the same
// way on every row of both screens — which is what makes three technologies comparable at a glance.
// The figures are `core`'s and the words are this screen's, exactly as the Galaxy row already works.

// Internal for the reason `LadderShortlist.toUiState` is: the screenshot fixtures state their
// *numbers* by hand and get their *strings* from here, so a baseline moves when the screen does and
// never when the mapper's formatting drifts.
internal fun LevelPurpose.toVerdictUiState(throttled: Boolean): VerdictUiState? = when (this) {
    is LevelPurpose.Output -> VerdictUiState(
        label = "+${perHour.groupedByThousands()}/h ${kind.word()} · back in ${payback.toPaybackLabel()}",
        // The clause a 320dp pane drops, and it is dropped rather than ellipsised: a payback is
        // recoverable from the sheet the row opens and half a number is not.
        compactLabel = "+${perHour.groupedByThousands()}/h ${kind.word()}",
    )
    // **Two ways a project can be worth nothing, and the colony's power tells them apart** — not the
    // technology, which was the first reading and was wrong. Photovoltaics multiplies a supply
    // nothing is limited by while the colony is in surplus; and in a deficit *any* of the three can
    // land here, because `scaleByEnergy` floors `rate × produced / consumed` and a bad enough ratio
    // swallows a whole multiplier step. A colony at 40% can buy an Enrichment level whose entire
    // gain rounds away, and the row has to say so rather than say the opposite.
    is LevelPurpose.Inert -> if (throttled) {
        VerdictUiState(
            label = "nothing while your mines are throttled",
            compactLabel = "nothing while throttled",
        )
    } else {
        VerdictUiState(label = "nothing while you are in surplus", compactLabel = "nothing while in surplus")
    }
    // None of the three reaches a project. A technology draws no power and shortens no build, so
    // `purposeOfNextLevel` can only answer with a rate that moved or one that did not; and the level
    // above the ceiling is refused by `TechLevel` before a purpose is ever asked for. Nothing rather
    // than words this screen has no design for.
    is LevelPurpose.Throttled,
    is LevelPurpose.Sooner,
    LevelPurpose.Unmeasured,
    -> null
}

// The shortlist wearing the name the rest of the app now uses for the same slot. Nothing about the
// copy changes: the shortlist *was* the verdict, and this design is that sentence repeated on the
// twelve rows that did not have one.
internal fun ShortlistUiState.toVerdictUiState(): VerdictUiState =
    VerdictUiState(label = label, compactLabel = compactLabel)

// Lower case, because these are nouns inside a sentence rather than labels on a chip.
private fun ResourceKind.word(): String = when (this) {
    ResourceKind.METAL -> "metal"
    ResourceKind.CRYSTAL -> "crystal"
    ResourceKind.DEUTERIUM -> "deuterium"
}

// ── What the card body opens ─────────────────────────────────────────────────────────────────
//
// One builder for both branches, for the reason `ProjectRow` takes a row's parts rather than either
// row type: a ladder's sheet and a technology's sheet have to be the same instrument, and the only
// way that is a fact rather than two promises is for there to be one of them.
//
// Three states share it and each drops something. A **locked** row has no price yet, so it ends on
// what it requires and points at the row that would move that. A **running** row has been paid for,
// so it keeps its first sentence and nothing else — mid-project the question is when, not what.
// Everything else is the whole sheet.
private fun GameState.rowSheet(
    name: String,
    level: TechLevel,
    verdict: VerdictUiState?,
    action: ResearchActionUiState,
    requirement: ResearchRequirement,
    lines: List<SheetLine>,
    ladder: List<SheetLadderStep>,
    bestBuy: SheetPointer?,
    costs: List<CostChipUiState>,
    duration: String,
): RowSheetUiState = RowSheetUiState(
    name = name,
    level = level.value,
    // The sentence the player has just read on the row, repeated — which on a locked row is its
    // requirement and on a running one its finish line, because those are what the row says in that
    // slot instead. Where the ladder below already spells the second clause out, the compact form
    // is used so the sheet does not say it twice. Empty only at a ceiling, which is a level
    // `TechLevel` refuses to construct — what a sheet would show there rather than a state it has.
    verdict = when (action) {
        is ResearchActionUiState.Locked -> action.reason
        is ResearchActionUiState.Running -> "→ LV ${action.toLevel.value} · ${action.doneAt}"
        ResearchActionUiState.Start,
        is ResearchActionUiState.AvailableIn,
        -> verdict?.let { if (ladder.isEmpty()) it.label else it.compactLabel }.orEmpty()
    },
    lines = when (action) {
        is ResearchActionUiState.Locked -> lines.take(1) + requirementSentence(requirement)
        is ResearchActionUiState.Running -> lines.take(1)
        ResearchActionUiState.Start,
        is ResearchActionUiState.AvailableIn,
        -> lines
    },
    ladder = ladder,
    pointer = when (action) {
        is ResearchActionUiState.Locked -> gatePointer(requirement)
        is ResearchActionUiState.Running -> null
        ResearchActionUiState.Start,
        is ResearchActionUiState.AvailableIn,
        -> bestBuy
    },
    footer = when (action) {
        // Null on a row with nothing to offer: a locked one has no price yet, a running one has
        // already been paid for.
        is ResearchActionUiState.Locked,
        is ResearchActionUiState.Running,
        -> null
        ResearchActionUiState.Start ->
            SheetFooter(costs = costs, duration = duration, action = SheetAction.Live("Research"))
        // The row's own ghost, carried whole: no disabled state here either, because a player who
        // wants the level they cannot afford yet is told when rather than told no.
        is ResearchActionUiState.AvailableIn ->
            SheetFooter(costs = costs, duration = duration, action = SheetAction.Ghost(action.label))
    },
)

// The three sentences an applied row has, in the order they are read: what the level does to the
// number on the row, what that number is in units an hour, and how long it takes to pay for itself.
private fun GameState.projectLines(purpose: LevelPurpose, effect: EffectUiState, name: String): List<SheetLine> =
    when (purpose) {
        is LevelPurpose.Output -> listOf(
            effectSentence(effect),
            sheetLine(
                words("Your colony makes "),
                figure("${purpose.from.groupedByThousands()}/h"),
                words(" ${purpose.kind.word()} and would make "),
                figure("${purpose.to.groupedByThousands()}/h"),
                words("."),
            ),
            sheetLine(
                words("Counted against everything the level costs, you are even after "),
                figure(purpose.payback.toPaybackLabel()),
                words("."),
            ),
        )
        // The row names itself rather than naming Photovoltaics, because in a deficit any of the
        // three lands here — see `toVerdictUiState`. The ledger sentence carries the percentage in
        // that case, since the percentage is the whole reason the level's gain disappeared.
        is LevelPurpose.Inert -> if (throttled()) {
            listOf(
                throttledSupplySentence(),
                sheetLine(
                    words("At that ratio $name's "),
                    figure(effect.next),
                    words(" rounds away before it reaches your stores."),
                ),
                sheetLine(words("It starts to pay when your plants carry the draw again.")),
            )
        } else {
            listOf(
                supplySentence(),
                sheetLine(
                    words("$name multiplies supply, and supply is not what is limiting you. At "),
                    figure(effect.next),
                    words(" your output does not move."),
                ),
                crossingSentence(purpose.mineLevelsSpare),
            )
        }
        // Unreachable from a project, for the reasons `toVerdictUiState` gives — and a sheet with
        // nothing in it is what a row with no verdict has to say.
        is LevelPurpose.Throttled,
        is LevelPurpose.Sooner,
        LevelPurpose.Unmeasured,
        -> emptyList()
    }

// "metal · crystal output: +36% → +47%." — the line the verdict displaced, moved to where it has
// the width to state both halves. At level 0 there is no "now" to compare against, so the sentence
// names the level the figure belongs to instead of leaving one side of the arrow empty.
private fun effectSentence(effect: EffectUiState): SheetLine = effect.current
    ?.let { current ->
        sheetLine(words("${effect.subject}: "), figure(current), words(" → "), figure(effect.next), words("."))
    }
    ?: sheetLine(words("${effect.subject}: "), figure(effect.next), words(" at LV 1."))

private fun bandSentence(unit: String, current: String, next: String): SheetLine =
    sheetLine(words("$unit tolerance: "), figure(current), words(" → "), figure(next), words("."))

// The counts the row states as a verdict, spelled back out as a sentence. The zero case says what
// would change it rather than counting nothing, which is the same asymmetry the row's own line has:
// there the useful information is not the number but that surveying is what moves it.
private fun reachSentence(shortlist: ShortlistUiState): SheetLine = if (shortlist.unlocks == 0) {
    sheetLine(
        words("Nothing you have surveyed is blocked by this band alone, so this level reaches no new world."),
    )
} else {
    sheetLine(
        words("Of the worlds you have surveyed this level reaches "),
        figure("${shortlist.unlocks}"),
        words(", and "),
        figure("${shortlist.worthTaking}"),
        words(" of those are worth taking."),
    )
}

private fun GameState.supplySentence(): SheetLine {
    val energy = PlaceholderBalance.energyBalance(buildings, research)
    return sheetLine(
        words("Your plants supply "),
        figure(energy.produced.groupedByThousands()),
        words(" energy. The colony draws "),
        figure(energy.consumed.groupedByThousands()),
        words("."),
    )
}

// The same ledger with the consequence on the end of it, for the sheet that has to explain a gain
// that vanished rather than one that was never there.
private fun GameState.throttledSupplySentence(): SheetLine {
    val energy = PlaceholderBalance.energyBalance(buildings, research)
    return sheetLine(
        words("Your plants supply "),
        figure(energy.produced.groupedByThousands()),
        words(" energy and the colony draws "),
        figure(energy.consumed.groupedByThousands()),
        words(", so every mine is running at "),
        figure("${energy.outputPercent}%"),
        words("."),
    )
}

private fun GameState.throttled(): Boolean = PlaceholderBalance.energyBalance(buildings, research).isDeficit

// When supply stops being the thing nothing is limited by, in the unit the power indicator already
// reports headroom in. One level away is written as a word rather than as a digit — "about 1 more
// mine level" reads as arithmetic where the sentence wants a consequence — and none at all drops
// the count entirely, because there the answer is the next thing you buy.
private fun crossingSentence(mineLevelsSpare: Long): SheetLine = when (mineLevelsSpare) {
    0L -> sheetLine(words("It starts to pay with the next mine level you take."))
    1L -> sheetLine(
        words("It starts to pay when draw passes supply — about "),
        figure("one"),
        words(" more mine level away."),
    )
    else -> sheetLine(
        words("It starts to pay when draw passes supply — about "),
        figure(mineLevelsSpare.groupedByThousands()),
        words(" more mine levels away."),
    )
}

// The requirement as a figure, because it is the thing the player has to go and get.
private fun requirementSentence(requirement: ResearchRequirement): SheetLine =
    sheetLine(words("Requires "), figure(requirement.subject()), words("."))

// The row that moves the gate, named the way the screen it lives on names it. A technology gate
// points at nothing: the row that moves that one is three rows up this same screen, and an arrow to
// somewhere the thumb is already resting is noise.
private fun GameState.gatePointer(requirement: ResearchRequirement): SheetPointer? = when (requirement) {
    is ResearchRequirement.Facility -> {
        val held = buildings.levelOf(requirement.building)
        val next = BuildingLevel(held.value + 1)
        SheetPointer(
            name = requirement.building.facilityName(),
            detail = "LV ${held.value} → ${next.value} · " + PlaceholderBalance.upgradeDuration(
                building = requirement.building,
                toLevel = next,
                roboticsFactory = buildings.roboticsFactory,
                naniteFactory = buildings.naniteFactory,
            ).toChipLabel(),
        )
    }
    is ResearchRequirement.Tech -> null
}

// The best buy on this screen, which is the argument an inert row has instead of one of its own.
// Excludes the row asking, because a row that answered "what is this worth" by naming itself would
// not have answered.
private fun GameState.bestBuyBesides(
    technology: Technology,
    purposes: Map<Technology, LevelPurpose>,
): SheetPointer? = purposes
    .filterKeys { it != technology }
    .mapNotNull { (other, purpose) -> if (purpose is LevelPurpose.Output) other to purpose else null }
    .minByOrNull { (_, output) -> output.payback }
    ?.let { (other, output) ->
        SheetPointer(
            name = other.displayName(),
            detail = "LV ${research.levelOf(other).value + 1} · back in ${output.payback.toPaybackLabel()}",
        )
    }

// What each level of this technology opens, against the level the player holds. One step per level
// rather than per gate, because two things opening at once is one rung of the ladder.
private fun Technology.sheetLadder(level: TechLevel): List<SheetLadderStep> = gatesOf(this)
    .groupBy { it.level }
    .map { (gateLevel, gates) ->
        val held = gateLevel <= level.value
        SheetLadderStep(
            level = "LV $gateLevel",
            opens = if (held) "${gates.opensLong()} · you have this" else gates.opensLong(),
            held = held,
        )
    }

// `gatesOf` on a technology yields nothing but projects — the requirement table runs from a facility
// or a technology *to* a technology, never back — so a level that opens more than one thing opens
// more than one project, and the branch is named rather than listed.
private fun List<Gate>.opensLong(): String =
    if (size > 1) "applied research" else single().opens.opensLong()

private fun GateSubject.opensLong(): String = when (this) {
    // A facility names its price beside itself: on the screen where that gate does appear, the first
    // level's metal is the number a reader wants next.
    is GateSubject.Facility -> "${building.facilityName()} · " +
        "${PlaceholderBalance.upgradeCost(building, BuildingLevel(1)).metal.groupedByThousands()} metal"
    is GateSubject.Project -> technology.displayName()
    is GateSubject.Ladder -> technology.displayName()
}

// The full name rather than the requirement line's short one, because a pointer names a row on the
// Colony screen and has to call it what that screen calls it. A second table rather than a
// dependency: two `presentation` modules cannot see each other, which is rule 5.
private fun BuildingType.facilityName(): String = when (this) {
    BuildingType.METAL_MINE -> "Metal Mine"
    BuildingType.CRYSTAL_MINE -> "Crystal Mine"
    BuildingType.DEUTERIUM_SYNTHESIZER -> "Deuterium Synth."
    BuildingType.SOLAR_PLANT -> "Solar Plant"
    BuildingType.ROBOTICS_FACTORY -> "Robotics Factory"
    BuildingType.NANITE_FACTORY -> "Nanite Factory"
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
private fun ResearchRequirement.label(): String = "Requires ${subject()}"

// The thing wanted, without the verb in front of it: the row states the whole line, and the sheet
// picks the requirement out as a figure inside a sentence of its own.
private fun ResearchRequirement.subject(): String = when (this) {
    is ResearchRequirement.Facility -> "${building.shortName()} ${level.value}"
    is ResearchRequirement.Tech -> "${technology.displayName()} ${level.value}"
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
// Public rather than internal since the watch — see the note on the colony's own `displayName`.
fun Technology.displayName(): String = when (this) {
    Technology.PHOTOVOLTAICS -> "Photovoltaics"
    Technology.EXTRACTION -> "Extraction"
    Technology.ENRICHMENT -> "Enrichment"
}

// One word each, like the applied three, and no trailing "Adaptation": all three would end in the
// same word, which carries nothing and costs eleven characters the row does not have. The Galaxy
// screen's blocked rows already say "Gravitic 9" for the same reason, so the two screens name the
// same object the same way.
fun AdaptationTechnology.displayName(): String = when (this) {
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

private fun Int.toPercent(): String = "+$this%"

private fun Long.toCostChip(kind: ResourceKind, short: Set<ResourceKind>): CostChipUiState? =
    takeIf { it > 0 }?.let { CostChipUiState(kind = kind, amount = it.groupedByThousands(), short = kind in short) }
