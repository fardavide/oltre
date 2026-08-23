package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.dispatch.presentation.DispatchSelection
import dev.fardavide.oltre.client.galaxy.ui.GalaxyUiState
import dev.fardavide.oltre.client.galaxy.ui.ProbeActionUiState
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.NotificationSettings
import dev.fardavide.oltre.core.World
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// **The real mapper refuses a missing `alerts`**, so that `GalaxyScreen` — its one production caller
// — cannot forget to hand the player's choice down. Here the same default costs nothing: a test that
// is not about the settings should not have to name them, and the ones that *are* about them pass an
// argument and say so in their name.
//
// An overload in the test source set rather than a default value on the real declaration, so the
// requirement holds everywhere except the one file that opts out of it.
internal fun GameState.toGalaxyUiState(
    nav: GalaxyNavigation,
    now: Instant,
    timeZone: TimeZone,
    dispatch: DispatchSelection? = null,
): GalaxyUiState = toGalaxyUiState(
    nav = nav,
    now = now,
    timeZone = timeZone,
    alerts = NotificationSettings.DEFAULT,
    dispatch = dispatch,
)

internal fun GameState.toProbeActionUiState(
    at: SystemSelection,
    worlds: List<World>,
    now: Instant,
    timeZone: TimeZone,
): ProbeActionUiState = toProbeActionUiState(
    at = at,
    worlds = worlds,
    now = now,
    timeZone = timeZone,
    alerts = NotificationSettings.DEFAULT,
)
