package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.galaxy.ui.GalaxyUiState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.startSurvey
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.days

// The system card's footer in the two states that are a *job* rather than an offer, and a system
// holding one of each verdict. All three carried baselines before 0.11 and keep them.
//
// **0.12 moves all three, and the move is the slice rather than a regression.** Nothing here maps
// differently — the footer's two job states and the six verdicts are untouched by the redesign — but
// the orbit page they are photographed on lost the reach strip, so the layout around them shifts.
// What the re-record has to show is the footer and the verdicts arriving intact on the shorter page;
// a word or a mark that changed in either is exactly what these three still exist to catch.

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

// **The footer with the fleet short rather than the bank**, and a state every colony meets on day
// one: genesis grants no hull, a probe flies a `SCOUT`, so this is what the tab says before the first
// one is bought. Deep stores throughout — the metal chip must not redden, because the metal is not
// what is missing, and that distinction is the whole of what this frame is for.
//
// It names the Shipyard rather than a wait, which is the one place the footer's *"available in"*
// treatment carries something that is not a countdown: every other unaffordable state in the game is
// answered by standing still and this one is not.
internal val probeNeedsScoutUiState: GalaxyUiState = frame(
    state = wealthy.copy(ships = Ships.NONE),
    view = GalaxyView.SYSTEM,
    at = SystemSelection(probeTarget.galaxy, probeTarget.system),
)

// The other half of the same refusal: a scout **is** coming back, so a countdown is the honest
// answer and the Shipyard is not the advice. One scout, sent, so the pool is empty and the probe
// that emptied it is the thing being waited on.
internal val probeScoutComingHomeUiState: GalaxyUiState = wealthy.let { state ->
    val elsewhere = state.galaxy.home.let { SystemAddress(galaxy = it.galaxy, system = it.system + 40) }
    val out = assertIs<StartSurveyResult.Started>(startSurvey(state, elsewhere, at = FIXTURE_NOW)).state
    frame(
        state = out,
        view = GalaxyView.SYSTEM,
        at = SystemSelection(probeTarget.galaxy, probeTarget.system),
    )
}

// The home system, which genesis surveys whole — so it holds the player's own world beside blocked,
// barren and settleable ones, and is the frame that would catch one of the six being drawn like
// another.
internal val everyVerdictUiState: GalaxyUiState = frame(view = GalaxyView.SYSTEM)
