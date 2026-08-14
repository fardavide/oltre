package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.galaxy.ui.GalaxyUiState
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.worldAt
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

// The system card's footer in the two states that are a *job* rather than an offer, and a system
// holding one of each verdict. All three carried baselines before 0.11 and keep them: the footer and
// the verdicts are unchanged by this slice, so a moved pixel in either is a regression.

private val probeTarget: SystemAddress = frameState.neighbourSelection()
    .let { SystemAddress(galaxy = it.galaxy, system = it.system) }

// Enough metal to buy the flight, so the frame is about the countdown rather than about affording it.
private val wealthy = frameState.copy(resources = Resources.of(metal = 40_000, crystal = 9_000))

internal val probeInFlightUiState: GalaxyUiState = wealthy.let { state ->
    val dispatched = assertIs<StartSurveyResult.Started>(
        startSurvey(state, probeTarget, at = FIXTURE_NOW),
    ).state
    frame(
        state = dispatched,
        view = GalaxyView.SYSTEM,
        at = SystemSelection(probeTarget.galaxy, probeTarget.system),
    )
}

internal val probeLandedUiState: GalaxyUiState = wealthy.let { state ->
    val landed = advance(
        assertIs<StartSurveyResult.Started>(startSurvey(state, probeTarget, at = FIXTURE_NOW)).state,
        from = FIXTURE_NOW,
        to = FIXTURE_NOW + 2.days,
    )
    frame(
        state = landed,
        view = GalaxyView.SYSTEM,
        at = SystemSelection(probeTarget.galaxy, probeTarget.system),
    )
}

// The home system, which genesis surveys whole — so it holds the player's own world beside blocked,
// barren and settleable ones, and is the frame that would catch one of the six being drawn like
// another.
internal val everyVerdictUiState: GalaxyUiState = frame(view = GalaxyView.SYSTEM)
