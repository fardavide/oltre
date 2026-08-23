package dev.fardavide.oltre.client.player.presentation

import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.player.ui.PlayerStripUiState
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.playerProgress

// Who is playing, read off the save. Three readings and no verbs, which is why this module holds one
// function.
//
// **The level and the gauge are derived rather than stored**, and that is the slice: `playerProgress`
// folds `GameState.eventLog`, which has recorded every completed build, project, ladder, hull, survey
// and run since the format's first version. So a colony carried forward from 0.16 opens on the level
// it had already earned — Davide, 2026-08-22: *"make it so next time I start the game it gives me
// experience for everything I did before."* Nothing migrated and `GameSave.SCHEMA_VERSION` did not
// move; see `core`'s `Experience.kt` for why a stored field could not have answered this.
//
// **The name is still a constant, and still deliberately not a function of the seed.**
// `player-strip-sheet.md` §3 settled that and nothing here reopens it: a name derived from a number
// nobody picked asserts an identity the save cannot back. It lives in this module rather than in
// `ui` because a state assembled in two places is a state whose shape nobody owns — the day the
// player can rename themselves, this is the line that reads the chosen one.
fun GameState.toPlayerStripUiState(): PlayerStripUiState {
    val progress = playerProgress()
    return PlayerStripUiState(
        name = Strings.playerDefaultName(),
        level = Strings.levelBadge(progress.level.value),
        // The share of the level being served rather than of the game, so a player who has just
        // levelled sees an empty track. `PlayerProgress.percent` cannot reach 100 — reaching the span
        // *is* the next level — so the strip's own coercion never has anything to do.
        experiencePercent = progress.percent,
    )
}
