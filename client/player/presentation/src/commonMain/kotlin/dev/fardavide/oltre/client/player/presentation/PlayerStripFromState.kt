package dev.fardavide.oltre.client.player.presentation

import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.player.ui.PlayerStripUiState
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.playerProgress
import dev.fardavide.oltre.protocol.PlayerProfile

// Who is playing, read off two different things — which is the whole of what this file gained. Four
// readings and no verbs: two come off the colony and two off the account above it.
//
// **The level and the gauge come off `GameState.experience`, which is a stored running total** — so
// this is a lookup rather than a fold, however long the colony has been played. Davide, 2026-08-23:
// *"the more the player progresses, the more it will be intensive to infer the level."*
//
// A colony carried forward from 0.16 still opens on the level it had already earned — Davide,
// 2026-08-22: *"make it so next time I start the game it gives me experience for everything I did
// before"* — because the 15 → 16 hop folds its log into that total once, on the way in. See `core`'s
// `Experience.kt`.
//
// **The name and the mark stopped being constants, and the line that said so named this day.** It
// read *"the day the player can rename themselves, this is the line that reads the chosen one"* —
// this is that line. What `player-strip-sheet.md` §3 settled is untouched and is the reason the
// *default* is still not a function of the seed: a name derived from a number nobody picked asserts
// an identity the save cannot back. A name somebody typed is the opposite of that.
//
// **The receiver is the colony and the account is the argument**, which is the way round every other
// mapper in this repository reads — `toColonyUiState`, `toResearchUiState`, `toAlertSheetUiState` —
// and the two identity faces are deliberately the other way round, because they answer about the
// account and no colony reaches them at all. See `IdentityFromProfile`.
// **Null is an account this device has not read yet**, and the strip draws it exactly as it draws an
// account that has chosen nothing — which is the whole reason it can be nullable here without a third
// state on screen. What must not treat the two alike is the *write* path, and that is the shell's:
// see `IdentityFromProfile`'s note and `App`'s `profile`.
fun GameState.toPlayerStripUiState(profile: PlayerProfile?): PlayerStripUiState {
    val progress = playerProgress()
    return PlayerStripUiState(
        // **A name a player typed is untranslatable by construction**, so it is raw text where the
        // default is a catalogue entry — and neither is drawn differently from the other, which is
        // what makes `Dead Reckoning` a name rather than a placeholder. `spokenName` is the one place
        // that substitution happens, because the settings sheet draws it too.
        name = profile.spokenName(),
        // **What an account that has chosen nothing wears**, and the substitution belongs here for
        // the reason `PlayerProfile` states: a default is a mark rather than an absence, and it is
        // drawn no differently from a chosen one. Two commanders may already share a name, so
        // marking a default would be a claim the server cannot make. `worn` is the one place it
        // happens, so the strip and the grid on the face it opens cannot disagree about it.
        mark = profile.worn(),
        level = Strings.levelBadge(progress.level.value),
        // The share of the level being served rather than of the game, so a player who has just
        // levelled sees an empty track. `PlayerProgress.percent` cannot reach 100 — reaching the span
        // *is* the next level — so the strip's own coercion never has anything to do.
        experiencePercent = progress.percent,
    )
}
