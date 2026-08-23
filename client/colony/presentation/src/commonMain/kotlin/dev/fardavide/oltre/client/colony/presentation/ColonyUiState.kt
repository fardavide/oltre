package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.client.colony.ui.ColonyUiState
import dev.fardavide.oltre.client.colony.ui.EnergyUiState
import dev.fardavide.oltre.client.colony.ui.FacilityActionUiState
import dev.fardavide.oltre.client.colony.ui.FacilityDetailUiState
import dev.fardavide.oltre.client.colony.ui.FacilityPowerUiState
import dev.fardavide.oltre.client.colony.ui.FacilityRowUiState
import dev.fardavide.oltre.client.colony.ui.ReturningFleetUiState
import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.component.SheetLadderStep
import dev.fardavide.oltre.client.design.component.SheetLine
import dev.fardavide.oltre.client.design.component.SheetPointer
import dev.fardavide.oltre.client.design.component.VerdictUiState
import dev.fardavide.oltre.client.design.component.WatchUiState
import dev.fardavide.oltre.client.design.component.figure
import dev.fardavide.oltre.client.design.component.sheetLine
import dev.fardavide.oltre.client.design.component.words
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.design.format.toCountdown
import dev.fardavide.oltre.client.design.format.toPaybackLabel
import dev.fardavide.oltre.client.design.format.watchedAtLabel
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.asksOnRow
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

// **Everything on the Colony tab that decides rather than draws.** The types it produces live in
// `:client:colony:ui`, which knows nothing about `GameState` — this is the one file that reads the
// colony and writes the screen's sentences, and it is what a mapper's unit test can hold on to
// without a Compose runtime anywhere near it.

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
    watching: TextRes? = null,
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
    returningFleet = runs.toStrip(home = galaxy.home, now = now, research = research),
    watching = watching,
)

private fun Buildings.toEnergyUiState(research: Research): EnergyUiState {
    val balance = PlaceholderBalance.energyBalance(this, research)
    val span = maxOf(balance.produced, balance.consumed)
    val covered = if (balance.isDeficit) balance.produced else balance.consumed
    return EnergyUiState(
        verdict = balance.verdict(headroomLevels = PlaceholderBalance.energyHeadroomLevels(this, research)),
        terms = Strings.clauses(
            listOf(
                Strings.energyProduced(balance.produced.groupedByThousands()),
                Strings.energyDrawn(balance.consumed.groupedByThousands()),
                abs(balance.surplus).groupedByThousands().let {
                    if (balance.isDeficit) Strings.energyShort(it) else Strings.energySpare(it)
                },
            ),
        ),
        // A razed colony spans nothing; every other case has a term to divide by.
        coveredFraction = if (span == 0L) 0f else covered.toFloat() / span.toFloat(),
        deficit = balance.isDeficit,
    )
}

// Converting the surplus into the unit the player spends is the teaching move: it says the
// mechanic exists, what it is denominated in, and that it will run out — all while nothing is
// wrong. When it does run out the same slot becomes the deficit sentence, already familiar.
private fun EnergyBalance.verdict(headroomLevels: Long): TextRes = when {
    isDeficit && produced == 0L -> Strings.energyEveryMineStopped()
    isDeficit -> Strings.energyEveryMineAt(outputPercent)
    headroomLevels == 0L -> Strings.energyBreakEven()
    else -> Strings.energyRoomForMineLevels(headroomLevels)
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
private fun List<FleetRun>.toStrip(
    home: GalaxyCoordinate,
    now: Instant,
    research: Research,
): ReturningFleetUiState? {
    val next = minByOrNull { it.nextEventAt(home, now, research) } ?: return null
    val at = next.nextEventAt(home, now, research)
    val remainingMs = (at.toEpochMilliseconds() - now.toEpochMilliseconds()).coerceAtLeast(0)
    val composition = ShipType.entries
        .mapNotNull { type -> next.ships.counts[type]?.let { count -> Strings.shipsOfType(count, type) } }
    val others = size - 1
    val trailing = Strings.moreAway(others).takeIf { others > 0 }
    val target = Strings.coordinate(next.target.galaxy, next.target.system, next.target.slot)
    val outbound = at < next.returnsAt
    return ReturningFleetUiState(
        title = if (outbound) Strings.onStationAt(target) else Strings.fleetReturning(),
        subtitle = Strings.clauses(
            listOfNotNull(Strings.fromTarget(target).takeIf { !outbound }) + composition +
                listOfNotNull(trailing),
        ),
        countdown = ((remainingMs + 999) / 1000).toCountdown(),
    )
}

// Whichever of a run's two moments has not happened yet. Derived from the one instant `core` stores
// per end rather than from a third field — see `FleetRun.flightEndsAt`, which exists for exactly this
// and is read by nothing in `advance`, because a run has one transition and it is the return.
private fun FleetRun.nextEventAt(home: GalaxyCoordinate, now: Instant, research: Research): Instant {
    val onStation = flightEndsAt(home, research)
    return if (now < onStation) onStation else returnsAt
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
        //
        // **A fourth answer since 0.18, and it is above all three**: under `BY_CATEGORY` the question
        // has been answered one level up, so the square goes — not disabled, absent, which is what a
        // missing square has always meant on these rows. The price watch survives because it names a
        // row rather than a kind. See `AlertSettings.asksOnRow`.
        watch = when {
            job != null -> when {
                !alerts.asksOnRow(AlertCategory.FACILITIES) -> null
                WatchTarget.Facility(building) in subscribed -> WatchUiState.Subscribed
                else -> WatchUiState.Offered
            }
            waiting -> untilAffordable?.takeIf { alerts.asksOnRow(AlertCategory.PRICE_REACHED) }?.let { wait ->
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
                Strings.requiresRobotics(PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT),
            )
            short.isEmpty() -> FacilityActionUiState.Upgrade
            else -> FacilityActionUiState.AffordableIn(
                untilAffordable?.let { Strings.availableIn(it.toChipLabel()) } ?: Strings.availableNever(),
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
        label = Strings.clauses(listOf(gain(), Strings.backIn(payback.toPaybackLabel()))),
        compactLabel = gain(),
    )
    // Only ever the plant on this screen: every mine level raises the draw, so a mine that buys
    // nothing is throttled rather than inert.
    is LevelPurpose.Inert -> VerdictUiState(
        label = Strings.clauses(
            listOf(Strings.suppliesMore(suppliesMore.groupedByThousands()), Strings.drawAlreadyCovered()),
        ),
        compactLabel = Strings.suppliesMore(suppliesMore.groupedByThousands()),
    )
    // The delta is not small, it is negative — so the first clause is what the level costs the rest
    // of the colony and the second is the plant level that would carry it.
    is LevelPurpose.Throttled -> VerdictUiState(
        label = Strings.clauses(
            listOf(Strings.throttlesEveryMine(), Strings.solarPlantCovers(coveredAtPlantLevel)),
        ),
        compactLabel = Strings.throttlesEveryMine(),
    )
    is LevelPurpose.Sooner -> {
        val saved = Strings.savedPerBuild((before - after).toChipLabel())
        VerdictUiState(
            label = gateClause(building, level)?.let { Strings.clauses(listOf(saved, it)) } ?: saved,
            compactLabel = saved,
        )
    }
    // Three the colony screen cannot reach, kept as branches rather than folded into an `else` so an
    // eighth purpose still has to answer here. `Haul` and `Reach` are technologies' answers — what a
    // hull lifts and how far it flies — and this screen prices facilities; `Unmeasured` is a ceiling,
    // where the row has no upgrade to offer either.
    is LevelPurpose.Haul,
    is LevelPurpose.Reach,
    LevelPurpose.Unmeasured,
    -> null
}

private fun LevelPurpose.Output.gain(): TextRes =
    Strings.outputGain(perHour = perHour.groupedByThousands(), kind = kind)

// Deliberately *not* about the next level of anything: this has to read while the building is
// twelve days out and 42% dim, so it states the payoff instead of the saving.
private fun DeepBuildRelief.toVerdict(): VerdictUiState = VerdictUiState(
    label = Strings.naniteReliefLong(
        unaided = unaided.toPaybackLabel(),
        helped = helped.toPaybackLabel(),
        level = naniteLevel,
    ),
    compactLabel = Strings.naniteReliefShort(
        unaided = unaided.toPaybackLabel(),
        helped = helped.toPaybackLabel(),
        level = naniteLevel,
    ),
)

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
    is LevelPurpose.Reach,
    LevelPurpose.Unmeasured,
    -> emptyList()
}

private fun LevelPurpose.Output.mineLines(level: BuildingLevel): List<SheetLine> = listOf(
    sheetLine(
        words(Strings.sheetMineMakes()),
        figure(Strings.perHour(from.groupedByThousands())),
        words(Strings.sheetMineAtLevel(kind = kind, level = level.value + 1)),
        figure(Strings.perHour(to.groupedByThousands())),
        words(Strings.sheetFullStop()),
    ),
    paybackLine(),
)

private fun LevelPurpose.Output.plantOutputLines(energy: EnergyBalance): List<SheetLine> = listOf(
    sheetLine(
        words(Strings.sheetPlantsSupply()),
        figure(energy.produced.groupedByThousands()),
        words(Strings.sheetColonyDraws()),
        figure(energy.consumed.groupedByThousands()),
        words(Strings.sheetSoEveryMineAt()),
        figure(Strings.percent(energy.outputPercent)),
        words(Strings.sheetFullStop()),
    ),
    sheetLine(
        words(Strings.sheetThisLevelLifts()),
        figure(Strings.plusPerHour(perHour.groupedByThousands())),
        words(Strings.sheetRatherThanEnergy(kind)),
    ),
    paybackLine(),
)

// Against everything the level costs rather than against the resource it hands back, because a mine
// level is paid for in a basket of three and repaid in one.
private fun LevelPurpose.Output.paybackLine(): SheetLine = sheetLine(
    words(Strings.sheetPaybackPrefix()),
    figure(payback.toPaybackLabel()),
    words(Strings.sheetFullStop()),
)

private fun LevelPurpose.Inert.plantInertLines(energy: EnergyBalance): List<SheetLine> = listOf(
    sheetLine(
        words(Strings.sheetPlantsSupply()),
        figure(energy.produced.groupedByThousands()),
        words(Strings.sheetColonyDraws()),
        figure(energy.consumed.groupedByThousands()),
        words(Strings.sheetFullStop()),
    ),
    sheetLine(
        words(Strings.sheetSupplyNotLimiting()),
        figure(Strings.plusAmount(suppliesMore.groupedByThousands())),
        words(Strings.sheetChangesNoRate()),
    ),
    crossingLine(),
)

// When the level stops buying nothing, counted in the unit the power indicator already reports
// headroom in. Spelled at one and dropped at none, because "1 more mine levels away" is not a
// sentence and "0 more mine levels away" is a worse one.
private fun LevelPurpose.Inert.crossingLine(): SheetLine = when (mineLevelsSpare) {
    0L -> sheetLine(words(Strings.sheetPaysNextMineLevel()))
    1L -> sheetLine(
        words(Strings.sheetPaysWhenDrawPasses()),
        figure(Strings.sheetOneSpelled()),
        words(Strings.sheetMoreMineLevelAway()),
    )
    else -> sheetLine(
        words(Strings.sheetPaysWhenDrawPasses()),
        figure(mineLevelsSpare.groupedByThousands()),
        words(Strings.sheetMoreMineLevelsAway()),
    )
}

private fun LevelPurpose.Throttled.throttledLines(): List<SheetLine> = listOf(
    sheetLine(words(Strings.sheetCannotPowerLevel())),
    sheetLine(
        words(Strings.sheetPlantCarriesPrefix()),
        figure(Strings.plainNumber(coveredAtPlantLevel)),
        words(Strings.sheetPlantCarriesSuffix()),
    ),
)

private fun LevelPurpose.Sooner.factoryLines(
    building: BuildingType,
    level: BuildingLevel,
): List<SheetLine> = listOf(
    sheetLine(words(building.shortensWhat())),
    sheetLine(
        words(Strings.sheetNextBuildTakes(on.displayName())),
        figure(before.toChipLabel()),
        words(Strings.sheetAtBuildingLevelTakes(building.displayName(), level.value + 1)),
        figure(after.toChipLabel()),
        words(Strings.sheetFullStop()),
    ),
)

// The two rows that raise no rate at all, and they are worth different kinds of thing: one shortens
// everything a little, and the other is the only answer the game has to a wait measured in days.
private fun BuildingType.shortensWhat(): TextRes = when (this) {
    BuildingType.NANITE_FACTORY -> Strings.sheetShortensDeepBuild()
    BuildingType.METAL_MINE,
    BuildingType.CRYSTAL_MINE,
    BuildingType.DEUTERIUM_SYNTHESIZER,
    BuildingType.SOLAR_PLANT,
    BuildingType.ROBOTICS_FACTORY,
    -> Strings.sheetShortensEveryBuild()
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
            words(Strings.sheetNaniteMineTakes(relief.level)),
            figure(relief.unaided.toPaybackLabel()),
            words(Strings.sheetNaniteUnaidedAt(relief.naniteLevel)),
            figure(relief.helped.toPaybackLabel()),
            words(Strings.sheetFullStop()),
        ),
        sheetLine(
            words(Strings.sheetRoboticsIsAt()),
            figure(Strings.plainNumber(robotics)),
            words(Strings.sheetLevelsToGo(toGo)),
            figure(
                PlaceholderBalance.upgradeCost(BuildingType.NANITE_FACTORY, BuildingLevel(1))
                    .metal.groupedByThousands(),
            ),
            words(Strings.sheetMetalSuffix()),
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
private data class GateSummary(val short: TextRes, val long: TextRes)

private fun List<GateSubject>.summarised(): GateSummary {
    val facility = singleOrNull() as? GateSubject.Facility
    return when {
        facility != null -> GateSummary(
            // "Nanite" rather than the row's own name, on the same measurement that shortens
            // "Robotics Factory": a clause has room for one word here and the row has already
            // spent the rest of its width.
            short = if (facility.building == BuildingType.NANITE_FACTORY) {
                Strings.gateSummaryNanite()
            } else {
                facility.building.displayName()
            },
            // The price is what makes a facility on a ladder different from a technology on one: a
            // level that opens a 2,000-metal building has opened something you still have to buy.
            long = Strings.gateFacilityLong(
                name = facility.building.displayName(),
                metal = PlaceholderBalance
                    .upgradeCost(facility.building, BuildingLevel(1))
                    .metal.groupedByThousands(),
            ),
        )
        all { it is GateSubject.Ladder } -> GateSummary(
            short = Strings.gateSummaryAdaptationShort(),
            long = Strings.gateSummaryAdaptationLong(),
        )
        else -> GateSummary(
            short = Strings.gateSummaryResearchShort(),
            long = Strings.gateSummaryResearchLong(),
        )
    }
}

// The lowest gate the colony has not passed yet, which is the only one a verdict has room for.
private fun gateClause(building: BuildingType, level: BuildingLevel): TextRes? {
    val ahead = gatesOf(building).filter { it.level > level.value }
    val next = ahead.minOfOrNull { it.level } ?: return null
    return Strings.gateClause(
        level = next,
        opens = ahead.filter { it.level == next }.map { it.opens }.summarised().short,
    )
}

// Every gate the row has, including the ones already passed — greyed, and said out loud, because it
// is how you learn that gating is a thing this row does at all.
private fun List<Gate>.toLadder(level: BuildingLevel): List<SheetLadderStep> = groupBy { it.level }
    .map { (gateLevel, gates) ->
        val held = gateLevel <= level.value
        val opens = gates.map { it.opens }.summarised().long
        SheetLadderStep(
            level = Strings.levelBadge(gateLevel),
            opens = if (held) Strings.ladderStepHeld(opens) else opens,
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
        detail = Strings.pointerLevelStep(from = level.value, to = toLevel.value, wait = wait.toChipLabel()),
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
            detail = Strings.pointerBestBuy(
                level = buildings.levelOf(building).value + 1,
                payback = purpose.payback.toPaybackLabel(),
            ),
        )
    }

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
        supplied > 0 -> FacilityPowerUiState(label = Strings.powerSupply(supplied.groupedByThousands()), supply = true)
        drawn > 0 -> FacilityPowerUiState(label = Strings.powerDraw(drawn.groupedByThousands()), supply = false)
        else -> null
    }
}

// The arrow already means "becomes" on a row that is building, so the fix needs no new element:
// in a deficit, what the plant's next level *is* happens to be the end of the deficit.
private fun EnergyBalance.fixOn(
    building: BuildingType,
    solarPlant: BuildingLevel,
    research: Research,
): TextRes? {
    if (building != BuildingType.SOLAR_PLANT || !isDeficit) return null
    val nextLevel = BuildingLevel(solarPlant.value + 1)
    if (PlaceholderBalance.energySupply(BuildingType.SOLAR_PLANT, nextLevel, research) < consumed) return null
    return Strings.solarFix(level = nextLevel.value, drawn = consumed.groupedByThousands())
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
        doneAt = Strings.doneAt(hour = completesLocal.hour, minute = completesLocal.minute),
    )
}

private fun Long.toCostChip(kind: ResourceKind, short: Set<ResourceKind>): CostChipUiState? =
    takeIf { it > 0 }?.let { CostChipUiState(kind = kind, amount = it.groupedByThousands(), short = kind in short) }

// Public rather than internal since the watch: the shell writes "watching Deuterium Synth." over
// both lists, and what a facility is called is this module's to say. One name, read by whoever
// needs it, beats a second table in the composition root that would drift the first time one of
// these is renamed.
//
// **Here rather than in `ui`, and that is the rule doing its job.** What a `BuildingType` is called
// is a mapping from a `core` enum into words — exactly the kind of decision a presentation module
// holds — and no composable in `:client:colony:ui` reads it: every name a row prints arrives on the
// row, already chosen.
fun BuildingType.displayName(): TextRes = Strings.buildingName(this)

// **One name shortens at 320dp and the other five do not**, which is a measurement rather than a
// style: with the square stacked under the ghost the name column is back to the width it had before
// the watch existed, and at that width only "Robotics Factory" runs past it. The short form is not
// invented here either — it is what the Research screen already prints in "Requires Robotics 10".
//
// Public for the same reason `displayName` is: the section label over both lists names the watched
// row, and at this width it has to call it what the row calls it.
fun BuildingType.compactName(): TextRes = Strings.buildingCompactName(this)
