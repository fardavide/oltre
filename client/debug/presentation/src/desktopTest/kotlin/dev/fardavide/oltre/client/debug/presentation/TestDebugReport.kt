package dev.fardavide.oltre.client.debug.presentation

import dev.fardavide.oltre.client.debug.domain.DebugReport
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.FutureEvent
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
