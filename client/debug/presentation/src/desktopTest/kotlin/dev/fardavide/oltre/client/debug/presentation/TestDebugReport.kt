package dev.fardavide.oltre.client.debug.presentation

import dev.fardavide.oltre.client.debug.domain.DebugReport
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.FutureEvent
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Hand-written reports rather than ones derived from a colony. The sheet is a rendering of this
// data class and of nothing else, so building the states that would produce each shape would test
// `debugReport` a second time — which `DebugReportTest` already does, against the real thing.
internal val EPOCH: Instant = Instant.fromEpochSeconds(0)

internal val idleReport = DebugReport(
    gameTime = EPOCH,
    wallTime = EPOCH,
    skippedBy = Duration.ZERO,
    debugUsed = false,
    schemaVersion = 7,
    galaxySeed = 20_260_807,
    eventLogSize = 0,
    buildsInFlight = 0,
    surveysInFlight = 0,
    researchSlotBusy = false,
    fleetInbound = false,
    nextEvent = null,
)

internal val buildingReport = idleReport.copy(
    eventLogSize = 1,
    buildsInFlight = 1,
    nextEvent = FutureEvent.BuildCompletes(
        building = BuildingType.METAL_MINE,
        toLevel = BuildingLevel(2),
        at = EPOCH + 1.hours + 4.minutes,
    ),
)

internal val skippedReport = idleReport.copy(
    gameTime = EPOCH + 4.hours,
    skippedBy = 4.hours,
    debugUsed = true,
)

// One report per kind of thing that can happen next. The sheet writes each with a `when` over the
// sealed `FutureEvent`, so without all five the compiler is the only thing that has ever looked at
// four of those branches — and the day core adds a sixth, a test that only ever saw a build would
// still pass while the panel said the wrong thing about a probe.
internal val nextEventReports: List<Pair<FutureEvent, String>> = listOf(
    FutureEvent.BuildCompletes(
        building = BuildingType.NANITE_FACTORY,
        toLevel = BuildingLevel(3),
        at = EPOCH + 1.hours,
    ) to "NANITE_FACTORY → 3",
    FutureEvent.ResearchCompletes(
        technology = Technology.ENRICHMENT,
        toLevel = TechLevel(2),
        at = EPOCH + 1.hours,
    ) to "ENRICHMENT → 2",
    FutureEvent.AdaptationCompletes(
        technology = AdaptationTechnology.GRAVITIC,
        toLevel = TechLevel(4),
        at = EPOCH + 1.hours,
    ) to "GRAVITIC → 4",
    FutureEvent.SurveyLands(
        target = SystemAddress(galaxy = 3, system = 165),
        worldsFound = 5,
        settleable = 0,
        at = EPOCH + 1.hours,
    ) to "PROBE → 3:165",
    FutureEvent.FleetReturns(
        // A run names where it *went*, so the coordinate is the target rather than the origin the
        // old arrival carried — and the sheet still says only that a fleet is coming back, because
        // a developer tool's next-event line is about the kind of thing, not about its manifest.
        target = GalaxyCoordinate(galaxy = 2, system = 117, slot = 9),
        ships = Ships.of(ShipType.SKIFF, 14),
        cargo = Resources.of(metal = 500),
        dispatchedAt = EPOCH,
        at = EPOCH + 1.hours,
    ) to "FLEET RETURNS",
)
