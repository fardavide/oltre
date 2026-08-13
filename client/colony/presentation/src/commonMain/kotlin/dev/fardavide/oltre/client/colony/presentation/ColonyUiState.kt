package dev.fardavide.oltre.client.colony.presentation

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
import dev.fardavide.oltre.client.design.format.pad2
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.design.format.toCountdown
import dev.fardavide.oltre.client.design.format.toPaybackLabel
import dev.fardavide.oltre.client.design.format.watchedAtLabel
import dev.fardavide.oltre.core.BuildJob
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Buildings
import dev.fardavide.oltre.core.DeepBuildRelief
import dev.fardavide.oltre.core.EnergyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.Gate
import dev.fardavide.oltre.core.GateSubject
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.LevelPurpose
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.Research
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.FleetRun
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.WatchTarget
import dev.fardavide.oltre.core.deepBuildRelief
import dev.fardavide.oltre.core.gatesOf
import dev.fardavide.oltre.core.purposeOfNextLevel
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
    // "watching Metal Mine" over the facility list, and null when nothing is watched. It names the
    // watched row **even when that row is on another screen**, which is the whole answer to a slot
    // shared with research and adaptation: one watch exists in the game, and this is where you read
    // which. Handed in rather than derived, because what a technology is called is not the colony's
    // to know — see the same field on `ResearchUiState`.
    val watching: String?,
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
    // What the row is called when the window is a Slide Over pane. One name differs — "Robotics
    // Factory" becomes "Robotics", which is what the game already calls it in "Requires Robotics 10"
    // — and it differs because it is the one name the square's 29dp would otherwise clip mid-word,
    // which this app never does. The other five fit at both widths and are the same string twice.
    val compactName: String,
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
    // The square beside the ghost time, and null on every row that has no instant to book: an
    // affordable row is not waiting for anything, a building one is already the thing happening, a
    // locked one has no price yet, and a row whose binding resource has no net income never reaches
    // its price at all. See `WatchUiState`.
    val watch: WatchUiState?,
    // What one more level is worth to *this* colony now, in the slot the adaptation ladder has spent
    // on its shortlist since that line shipped. Null in the two states where nobody is choosing: a
    // row in flight, where the decision was made when the player tapped the action and the slot
    // belongs to the countdown, and a row at its ceiling, where there is no level left to price.
    val verdict: VerdictUiState?,
    // Everything the sheet says that the row does not already say. The rest of `RowSheetUiState` is
    // assembled from this row — see `toRowSheetUiState` — which is what keeps the sheet a second
    // rendering of a state the screen already holds rather than a second state.
    val detail: FacilityDetailUiState,
    // True on at most one row, and only for the first couple of seconds after a launch: this is the
    // upgrade that landed while the app was closed. The row answers it with a band of light crossing
    // the card once and a level badge that changes behind the band.
    //
    // It is a fact about *this launch* rather than about the colony, which is why it lives on the
    // row rather than on the action: the same colony rendered a minute later has the same levels and
    // nothing to announce.
    val finishedWhileAway: Boolean,
)

// The three things a row cannot say in one clause: the arithmetic behind a verdict that reads
// "nothing", the ladder of what the level gates, and the row worth reading instead.
//
// Held as design-system types rather than as prose, for the reason `SheetLine` states next door — a
// mapper's test can assert the figures without parsing anything, and what "picked out" looks like
// stays the component's business.
data class FacilityDetailUiState(
    val lines: List<SheetLine>,
    val ladder: List<SheetLadderStep>,
    val pointer: SheetPointer?,
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
//
// `watching` is the other thing this screen is told rather than derives, and for a different reason:
// the watch is empire-wide, so the row it points at may be a technology, and what a technology is
// called belongs to the screen that draws technologies. Which of *these* rows holds it is read off
// the state below, where it can be.
fun GameState.toColonyUiState(
    now: Instant,
    timeZone: TimeZone,
    finishedWhileAway: BuildingType? = null,
    watching: String? = null,
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
    returningFleet = runs.toStrip(home = galaxy.home, now = now),
    watching = watching,
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

// The strip stays exactly what it was drawn as at 0.0.6 — coordinate, manifest, countdown, in one
// 48dp row — and it finally has something real to say. Claude Design's call, 2026-08-10: **it names
// the next *event*, not the next return**, and with several runs out it gains one trailing clause and
// nothing else. A strip that grew a row per run would push a facility row off a 393×852 phone at the
// measured 106dp, and the full list already has a tab; the count is a door to Fleets, not a summary
// of it.
//
// The change of scope is the part worth stating: a run has two moments a player is waiting on, and
// for the first half of every run the nearer one is the *arrival*. So an outbound skiff reads "On
// station at [3:185:4]" and only becomes "Fleet returning" once it has turned for home. The strip has
// always been amber for in transit and a run is in transit in both directions, so nothing about its
// colour changes.
//
// Runs are unordered on `GameState`, so the soonest is picked here rather than assumed — the same
// reason `advance` sorts its arrivals on an intrinsic key instead of on list order. And it is sorted
// by *event* rather than by return, which is not the same ordering: a run dispatched far away can
// land on station after a nearer one has already started home.
private fun List<FleetRun>.toStrip(home: GalaxyCoordinate, now: Instant): ReturningFleetUiState? {
    val next = minByOrNull { it.nextEventAt(home, now) } ?: return null
    val at = next.nextEventAt(home, now)
    val remainingMs = (at.toEpochMilliseconds() - now.toEpochMilliseconds()).coerceAtLeast(0)
    val composition = ShipType.entries
        .mapNotNull { type -> next.ships.counts[type]?.let { count -> "$count ${type.displayName()}" } }
        .joinToString(" · ")
    val others = size - 1
    val trailing = if (others > 0) " · $others more away" else ""
    val target = "[${next.target.galaxy}:${next.target.system}:${next.target.slot}]"
    val outbound = at < next.returnsAt
    return ReturningFleetUiState(
        title = if (outbound) "On station at $target" else "Fleet returning",
        subtitle = if (outbound) "$composition$trailing" else "from $target · $composition$trailing",
        countdown = ((remainingMs + 999) / 1000).toCountdown(),
    )
}

// Whichever of a run's two moments has not happened yet. Derived from the one instant `core` stores
// per end rather than from a third field — see `FleetRun.flightEndsAt`, which exists for exactly this
// and is read by nothing in `advance`, because a run has one transition and it is the return.
private fun FleetRun.nextEventAt(home: GalaxyCoordinate, now: Instant): Instant {
    val onStation = flightEndsAt(home)
    return if (now < onStation) onStation else returnsAt
}

private fun ShipType.displayName(): String = when (this) {
    ShipType.SKIFF -> "skiff"
    ShipType.HAULER -> "hauler"
    ShipType.ESCORT -> "escort"
    ShipType.SETTLER -> "settler"
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
    // Null when the wait never ends, which is the one reading both the ghost and the square have to
    // agree about: the button prints "—" because it has no time to print, and the square is absent
    // because there is no instant to book. Computed once so the two cannot disagree.
    val untilAffordable = timeUntilAffordable(resources, cost, buildings, research).takeIf { it.isFinite() }
    val waiting = job == null && !locked && short.isNotEmpty()
    val purpose = purposeOfNextLevel(building)
    return FacilityRowUiState(
        building = building,
        name = building.displayName(),
        compactName = building.compactName(),
        level = level,
        costs = listOfNotNull(
            cost.metal.toCostChip(ResourceKind.METAL, short),
            cost.crystal.toCostChip(ResourceKind.CRYSTAL, short),
            cost.deuterium.toCostChip(ResourceKind.DEUTERIUM, short),
        ),
        duration = PlaceholderBalance.upgradeDuration(
            building,
            toLevel,
            buildings.roboticsFactory,
            buildings.naniteFactory,
        ).toChipLabel(),
        power = if (energy.isDeficit) building.powerAt(level, research) else null,
        fix = energy.fixOn(building, solarPlant = buildings.solarPlant, research = research),
        // The locked row is the one that does not price its next level: the Nanite Factory's whole
        // argument has to read while the building is still twelve days out, so it is about the shape
        // of the curve rather than about the level after this one.
        verdict = when {
            job != null -> null
            locked -> deepBuildRelief().toVerdict()
            else -> purpose.toVerdict(building = building, level = level)
        },
        detail = FacilityDetailUiState(
            lines = sheetLines(
                building = building,
                purpose = purpose,
                energy = energy,
                level = level,
                locked = locked,
                running = job != null,
            ),
            ladder = gatesOf(building).toLadder(level),
            // The answer to "then what", and only the two rows that have not answered it themselves
            // need one: a locked row points at what moves its gate, and a row worth nothing points
            // at the row worth the most.
            pointer = when {
                job != null -> null
                locked -> gatePointer()
                purpose is LevelPurpose.Inert -> bestBuyPointer(excluding = building)
                else -> null
            },
        ),
        finishedWhileAway = finishedWhileAway,
        // Three cases, and the row's own state picks between them. A job in flight can be asked
        // about its completion, a row waiting on its stores about its price, and everything else —
        // affordable, locked, or waiting on a resource that will never arrive — about nothing.
        watch = when {
            job != null -> if (WatchTarget.Facility(building) in subscribed) {
                WatchUiState.Subscribed
            } else {
                WatchUiState.Offered
            }
            waiting -> untilAffordable?.let { wait ->
                watchState(
                    watched = watching == WatchTarget.Facility(building),
                    at = now + wait,
                    timeZone = timeZone,
                )
            }
            else -> null
        },
        action = when {
            job != null -> job.toUpgradingAction(now = now, timeZone = timeZone)
            locked -> FacilityActionUiState.Locked(
                "Requires Robotics ${PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT}",
            )
            short.isEmpty() -> FacilityActionUiState.Upgrade
            else -> FacilityActionUiState.AffordableIn(
                untilAffordable?.let { "in ${it.toChipLabel()}" } ?: "—",
            )
        },
    )
}

// ── What a level is worth, and the sheet that shows the working ──────────────────────────────
//
// `core` computes the verdict and this file writes the sentence, exactly as the power indicator
// already works: every member of `LevelPurpose` is a number or a duration, and which words go round
// it is the screen's business — the same fact reads differently on a plant and on a mine.
//
// Colony and Research speak one dialect here on purpose. Where a string below looks like it could be
// worded better, the answer is that the other screen says it too, and one sentence read twice is
// what makes three rows comparable at a glance.

// The full label is two clauses and the compact one is the first alone — dropped rather than
// ellipsised, which is the same call the app already makes when it writes "Deuterium Synth.".
// Nothing a dropped clause said is lost: the sheet repeats it.
private fun LevelPurpose.toVerdict(
    building: BuildingType,
    level: BuildingLevel,
): VerdictUiState? = when (this) {
    is LevelPurpose.Output -> VerdictUiState(
        label = "${gain()} · back in ${payback.toPaybackLabel()}",
        compactLabel = gain(),
    )
    // Only ever the plant on this screen: every mine level raises the draw, so a mine that buys
    // nothing is throttled rather than inert.
    is LevelPurpose.Inert -> VerdictUiState(
        label = "+${suppliesMore.groupedByThousands()} supply · draw already covered",
        compactLabel = "+${suppliesMore.groupedByThousands()} supply",
    )
    // The delta is not small, it is negative — so the first clause is what the level costs the rest
    // of the colony and the second is the plant level that would carry it.
    is LevelPurpose.Throttled -> VerdictUiState(
        label = "throttles every mine · Solar Plant $coveredAtPlantLevel covers it",
        compactLabel = "throttles every mine",
    )
    is LevelPurpose.Sooner -> {
        val saved = "−${(before - after).toChipLabel()} per build"
        VerdictUiState(
            label = gateClause(building, level)?.let { "$saved · $it" } ?: saved,
            compactLabel = saved,
        )
    }
    // Two the colony screen cannot reach, kept as branches rather than folded into an `else` so a
    // seventh purpose still has to answer here. `Haul` is a technology's answer and this screen
    // prices facilities; `Unmeasured` is a ceiling, where the row has no upgrade to offer either.
    is LevelPurpose.Haul,
    LevelPurpose.Unmeasured,
    -> null
}

private fun LevelPurpose.Output.gain(): String = "+${perHour.groupedByThousands()}/h ${kind.word()}"

// Deliberately *not* about the next level of anything: this has to read while the building is
// twelve days out and 42% dim, so it states the payoff instead of the saving.
private fun DeepBuildRelief.toVerdict(): VerdictUiState = VerdictUiState(
    label = "A ${unaided.toPaybackLabel()} build takes ${helped.toPaybackLabel()} at LV $naniteLevel",
    compactLabel = "${unaided.toPaybackLabel()} builds take ${helped.toPaybackLabel()} at LV $naniteLevel",
)

// Lower case, because it is a word inside a sentence rather than a label on a chip.
private fun ResourceKind.word(): String = when (this) {
    ResourceKind.METAL -> "metal"
    ResourceKind.CRYSTAL -> "crystal"
    ResourceKind.DEUTERIUM -> "deuterium"
}

private fun GameState.sheetLines(
    building: BuildingType,
    purpose: LevelPurpose,
    energy: EnergyBalance,
    level: BuildingLevel,
    locked: Boolean,
    running: Boolean,
): List<SheetLine> {
    val lines = if (locked) lockedNaniteLines() else purpose.toLines(building, energy, level)
    // A row in flight has already been decided about, so the sheet keeps the sentence that says what
    // the level *is* and drops the arithmetic that was there to argue for it.
    return if (running) lines.take(1) else lines
}

private fun LevelPurpose.toLines(
    building: BuildingType,
    energy: EnergyBalance,
    level: BuildingLevel,
): List<SheetLine> = when (this) {
    // The plant is the one row where a gain is not what it looks like: what it raises is energy, and
    // the rate it reads as is the deficit lifting off every mine.
    is LevelPurpose.Output ->
        if (building == BuildingType.SOLAR_PLANT) plantOutputLines(energy) else mineLines(level)
    is LevelPurpose.Inert -> plantInertLines(energy)
    is LevelPurpose.Throttled -> throttledLines()
    is LevelPurpose.Sooner -> factoryLines(building, level)
    is LevelPurpose.Haul,
    LevelPurpose.Unmeasured,
    -> emptyList()
}

private fun LevelPurpose.Output.mineLines(level: BuildingLevel): List<SheetLine> = listOf(
    sheetLine(
        words("Your colony makes "),
        figure("${from.groupedByThousands()}/h"),
        words(" ${kind.word()}. At LV ${level.value + 1} it makes "),
        figure("${to.groupedByThousands()}/h"),
        words("."),
    ),
    paybackLine(),
)

private fun LevelPurpose.Output.plantOutputLines(energy: EnergyBalance): List<SheetLine> = listOf(
    sheetLine(
        words("Your plants supply "),
        figure(energy.produced.groupedByThousands()),
        words(" energy. The colony draws "),
        figure(energy.consumed.groupedByThousands()),
        words(", so every mine is running at "),
        figure("${energy.outputPercent}%"),
        words("."),
    ),
    sheetLine(
        words("This level lifts that, which is why it reads as "),
        figure("+${perHour.groupedByThousands()}/h"),
        words(" ${kind.word()} rather than as energy."),
    ),
    paybackLine(),
)

// Against everything the level costs rather than against the resource it hands back, because a mine
// level is paid for in a basket of three and repaid in one.
private fun LevelPurpose.Output.paybackLine(): SheetLine = sheetLine(
    words("Counted against everything the level costs, you are even after "),
    figure(payback.toPaybackLabel()),
    words("."),
)

private fun LevelPurpose.Inert.plantInertLines(energy: EnergyBalance): List<SheetLine> = listOf(
    sheetLine(
        words("Your plants supply "),
        figure(energy.produced.groupedByThousands()),
        words(" energy. The colony draws "),
        figure(energy.consumed.groupedByThousands()),
        words("."),
    ),
    sheetLine(
        words("Supply is not what is limiting you, so a level that adds "),
        figure("+${suppliesMore.groupedByThousands()}"),
        words(" changes no rate."),
    ),
    crossingLine(),
)

// When the level stops buying nothing, counted in the unit the power indicator already reports
// headroom in. Spelled at one and dropped at none, because "1 more mine levels away" is not a
// sentence and "0 more mine levels away" is a worse one.
private fun LevelPurpose.Inert.crossingLine(): SheetLine = when (mineLevelsSpare) {
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

private fun LevelPurpose.Throttled.throttledLines(): List<SheetLine> = listOf(
    sheetLine(
        words(
            "The colony cannot power this level. Taking it would throttle every mine you have " +
                "rather than raise anything.",
        ),
    ),
    sheetLine(
        words("A Solar Plant at LV "),
        figure("$coveredAtPlantLevel"),
        words(" carries the new draw. Build that first and this level becomes what it looks like."),
    ),
)

private fun LevelPurpose.Sooner.factoryLines(
    building: BuildingType,
    level: BuildingLevel,
): List<SheetLine> = listOf(
    sheetLine(words(building.shortensWhat())),
    sheetLine(
        words("Your next ${on.displayName()} takes "),
        figure(before.toChipLabel()),
        words(". At ${building.displayName()} ${level.value + 1} it takes "),
        figure(after.toChipLabel()),
        words("."),
    ),
)

// The two rows that raise no rate at all, and they are worth different kinds of thing: one shortens
// everything a little, and the other is the only answer the game has to a wait measured in days.
private fun BuildingType.shortensWhat(): String = when (this) {
    BuildingType.NANITE_FACTORY ->
        "Takes the late game's waits apart. It is the only thing in the game that shortens a deep build."
    BuildingType.METAL_MINE,
    BuildingType.CRYSTAL_MINE,
    BuildingType.DEUTERIUM_SYNTHESIZER,
    BuildingType.SOLAR_PLANT,
    BuildingType.ROBOTICS_FACTORY,
    ->
        "Shortens every build on this colony and every research in the empire. " +
            "It raises no output of its own."
}

// The one sheet in the game that is about a building the player cannot start, and the reason the row
// is tappable while it is still dim: the third sentence is what turns the promise into a distance.
private fun GameState.lockedNaniteLines(): List<SheetLine> {
    val relief = deepBuildRelief()
    val robotics = buildings.roboticsFactory.value
    val toGo = PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT - robotics
    return listOf(
        sheetLine(words(BuildingType.NANITE_FACTORY.shortensWhat())),
        sheetLine(
            words("A level-${relief.level} Metal Mine takes "),
            figure(relief.unaided.toPaybackLabel()),
            words(" unaided. At ${relief.naniteLevel} Nanite levels it takes "),
            figure(relief.helped.toPaybackLabel()),
            words("."),
        ),
        sheetLine(
            words("Your Robotics Factory is at "),
            figure("$robotics"),
            words(
                ". ${if (toGo == 1) "One level" else "$toGo levels"} to go, " +
                    "and the first Nanite level costs ",
            ),
            figure(
                PlaceholderBalance.upgradeCost(BuildingType.NANITE_FACTORY, BuildingLevel(1))
                    .metal.groupedByThousands(),
            ),
            words(" metal."),
        ),
    )
}

// ── What a level opens ───────────────────────────────────────────────────────────────────────
//
// The Robotics Factory is the only row on this screen that gates anything, and what it opens at one
// level is never one thing: two technologies at 1, three ladders at 2, the Nanite Factory at 10. So
// a group is described by what it holds rather than by naming its members — "LV 2 → adaptation" is a
// clause a player can act on where three names in a row is a table.
//
// That is also why **no technology is named here**. What a technology is called belongs to the
// screen that draws technologies, exactly as `watching` does, and no gate on this screen is a lone
// project or ladder for it to have to name.
private data class GateSummary(val short: String, val long: String)

private fun List<GateSubject>.summarised(): GateSummary {
    val facility = singleOrNull() as? GateSubject.Facility
    return when {
        facility != null -> GateSummary(
            // "Nanite" rather than the row's own name, on the same measurement that shortens
            // "Robotics Factory": a clause has room for one word here and the row has already
            // spent the rest of its width.
            short = if (facility.building == BuildingType.NANITE_FACTORY) {
                "Nanite"
            } else {
                facility.building.displayName()
            },
            // The price is what makes a facility on a ladder different from a technology on one: a
            // level that opens a 2,000-metal building has opened something you still have to buy.
            long = "${facility.building.displayName()} · " + PlaceholderBalance
                .upgradeCost(facility.building, BuildingLevel(1))
                .metal.groupedByThousands() + " metal",
        )
        all { it is GateSubject.Ladder } ->
            GateSummary(short = "adaptation", long = "the three adaptation ladders")
        else -> GateSummary(short = "research", long = "applied research")
    }
}

// The lowest gate the colony has not passed yet, which is the only one a verdict has room for.
private fun gateClause(building: BuildingType, level: BuildingLevel): String? {
    val ahead = gatesOf(building).filter { it.level > level.value }
    val next = ahead.minOfOrNull { it.level } ?: return null
    return "LV $next → ${ahead.filter { it.level == next }.map { it.opens }.summarised().short}"
}

// Every gate the row has, including the ones already passed — greyed, and said out loud, because it
// is how you learn that gating is a thing this row does at all.
private fun List<Gate>.toLadder(level: BuildingLevel): List<SheetLadderStep> = groupBy { it.level }
    .map { (gateLevel, gates) ->
        val held = gateLevel <= level.value
        val opens = gates.map { it.opens }.summarised().long
        SheetLadderStep(
            level = "LV $gateLevel",
            opens = if (held) "$opens · you have this" else opens,
            held = held,
        )
    }

// ── The row to look at instead ───────────────────────────────────────────────────────────────

// What moves the gate under a locked row. One gate is locked on this screen and one row moves it,
// which is the same pair the row's own "Requires Robotics 10" already names.
private fun GameState.gatePointer(): SheetPointer {
    val level = buildings.roboticsFactory
    val toLevel = BuildingLevel(level.value + 1)
    val wait = PlaceholderBalance.upgradeDuration(
        BuildingType.ROBOTICS_FACTORY,
        toLevel,
        buildings.roboticsFactory,
        buildings.naniteFactory,
    )
    return SheetPointer(
        name = BuildingType.ROBOTICS_FACTORY.displayName(),
        detail = "LV ${level.value} → ${toLevel.value} · ${wait.toChipLabel()}",
    )
}

// The shortest payback on this screen, the row's own excluded — the comparison a player would make
// by reading down the list, made for them on the one row that has nothing of its own to offer.
private fun GameState.bestBuyPointer(excluding: BuildingType): SheetPointer? = BuildingType.entries
    .filter { it != excluding }
    .mapNotNull { building -> (purposeOfNextLevel(building) as? LevelPurpose.Output)?.let { building to it } }
    .minByOrNull { (_, purpose) -> purpose.payback }
    ?.let { (building, purpose) ->
        SheetPointer(
            name = building.displayName(),
            detail = "LV ${buildings.levelOf(building).value + 1} · " +
                "back in ${purpose.payback.toPaybackLabel()}",
        )
    }

// ── The sheet, assembled from the row it came from ───────────────────────────────────────────
//
// Nothing new crosses the module boundary for this: the screen holds the row already, so the sheet
// is derived where it is opened and `App` keeps the parameter list it has.
fun FacilityRowUiState.toRowSheetUiState(): RowSheetUiState = RowSheetUiState(
    // The full name, never the compact one — the sheet is the full width of the window.
    name = name,
    level = level.value,
    verdict = sheetHeading(),
    lines = detail.lines,
    ladder = detail.ladder,
    pointer = detail.pointer,
    footer = when (val current = action) {
        FacilityActionUiState.Upgrade -> SheetFooter(
            costs = costs,
            duration = duration,
            action = SheetAction.Live("Upgrade"),
        )
        is FacilityActionUiState.AffordableIn -> SheetFooter(
            costs = costs,
            duration = duration,
            action = SheetAction.Ghost(current.label),
        )
        // A locked row has no price yet and a running one has already been paid for. Both end on
        // what to do about it instead.
        is FacilityActionUiState.Locked,
        is FacilityActionUiState.Upgrading,
        -> null
    },
)

// The sentence the player has just read on the row, repeated where the sheet answers a question they
// still have in mind. It is the **compact** verdict when the sheet carries a ladder, because the
// clause a narrow row drops is the ladder and the sheet is about to say it in full.
private fun FacilityRowUiState.sheetHeading(): String = when (val current = action) {
    is FacilityActionUiState.Locked -> current.reason
    is FacilityActionUiState.Upgrading -> current.becomes()
    FacilityActionUiState.Upgrade,
    is FacilityActionUiState.AffordableIn,
    -> verdict?.let { if (detail.ladder.isEmpty()) it.label else it.compactLabel }.orEmpty()
}

// The accent line a running row already carries, authored once and read twice: the card draws it in
// the "→ becomes" slot and the sheet repeats it where the verdict would have been. A row that said
// one thing and a sheet that said another would be the worst failure this pass has available.
internal fun FacilityActionUiState.Upgrading.becomes(): String = "→ LV ${toLevel.value} · $doneAt"

// The one line the watch adds to a card, and it is built like the line a running row already
// carries — an arrow, a fact, in accent. **Relative on the right, absolute here**: the ghost says
// "in 8h 13m" because that is what you weigh against your evening, and this says "19:51" because
// that is what the alert on the lock screen will be stamped with.
private fun watchState(watched: Boolean, at: Instant, timeZone: TimeZone): WatchUiState {
    if (!watched) return WatchUiState.Offered
    val local = at.toLocalDateTime(timeZone)
    return WatchUiState.Booked(watchedAtLabel(hour = local.hour, minute = local.minute))
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

// Public rather than internal since the watch: the shell writes "watching Deuterium Synth." over
// both lists, and what a facility is called is this module's to say. One name, read by whoever
// needs it, beats a second table in the composition root that would drift the first time one of
// these is renamed.
fun BuildingType.displayName(): String = when (this) {
    BuildingType.METAL_MINE -> "Metal Mine"
    BuildingType.CRYSTAL_MINE -> "Crystal Mine"
    BuildingType.DEUTERIUM_SYNTHESIZER -> "Deuterium Synth."
    BuildingType.SOLAR_PLANT -> "Solar Plant"
    BuildingType.ROBOTICS_FACTORY -> "Robotics Factory"
    BuildingType.NANITE_FACTORY -> "Nanite Factory"
}

// **One name shortens at 320dp and the other five do not**, which is a measurement rather than a
// style: with the square stacked under the ghost the name column is back to the width it had before
// the watch existed, and at that width only "Robotics Factory" runs past it. The short form is not
// invented here either — it is what the Research screen already prints in "Requires Robotics 10".
//
// Public for the same reason `displayName` is: the section label over both lists names the watched
// row, and at this width it has to call it what the row calls it.
fun BuildingType.compactName(): String = when (this) {
    BuildingType.ROBOTICS_FACTORY -> "Robotics"
    BuildingType.METAL_MINE,
    BuildingType.CRYSTAL_MINE,
    BuildingType.DEUTERIUM_SYNTHESIZER,
    BuildingType.SOLAR_PLANT,
    BuildingType.NANITE_FACTORY,
    -> displayName()
}
