package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.player.presentation.toPlayerStripUiState
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState

// The strip a new colony wears, built by the mapper the app itself uses rather than assembled here.
// The scaffold's tests are about the frame rather than about who is in it, so they share one — the
// same reason `testResourceRailUiState` exists.
//
// **Through the real mapper on purpose.** A hand-written `PlayerStripUiState` would keep passing on
// the day the mapping broke, and the frame's own tests are the only place in the shell that draws
// this strip at all.
internal val testPlayerStripUiState = GameState.initial(GalaxySeed(20_260_807)).toPlayerStripUiState()
