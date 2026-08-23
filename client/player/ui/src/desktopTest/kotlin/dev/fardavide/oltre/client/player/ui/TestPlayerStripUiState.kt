package dev.fardavide.oltre.client.player.ui

import dev.fardavide.oltre.client.design.text.Strings

// What a colony that has done nothing reads as — the state every baseline in this module except
// `player_strip_levelled` was recorded at.
//
// **A test fixture rather than a factory in `commonMain`**, and that is where 0.16's
// `playerStripUiState()` went. This module cannot see a `GameState` and must not: the mapping from a
// save into these three readings is `:client:player:presentation`'s, and a `ui` module that also
// knew how to build one would be a second opinion about the same screen. What is left here is the
// value the drawings are drawn against.
internal val newColonyPlayerStrip = PlayerStripUiState(
    name = Strings.playerDefaultName(),
    level = Strings.levelBadge(0),
    experiencePercent = 0,
)
