package dev.fardavide.oltre.client.world.ui

import dev.fardavide.oltre.core.Gravity
import dev.fardavide.oltre.core.Hazard
import dev.fardavide.oltre.core.Pressure
import dev.fardavide.oltre.core.Temperature

// What a world looks like. **Four channels and five marks, every one of them a trait `core` already
// generates** — fill is temperature, diameter is gravity, banding is pressure, and the marks are the
// hazards. Nothing here is rolled except the ring.
//
// **The unsurveyed case is a state rather than a set of nulls**, and that is the load-bearing part
// of this file. A disc is drawn exactly where an epithet is drawn — they are the same permission —
// so a sealed pair makes it impossible to render a trait the player has not paid for. Claude Design,
// 2026-08-14: *"an empty socket next to a filled one"*, which is also what let the word `Unsurveyed`
// leave the row entirely.
//
// The traits keep their typed wrappers rather than arriving as `Int`s: the portrait is the one place
// in the app that reads all three axes at once, and it is the place a swapped pair would be hardest
// to see.
sealed interface WorldPortraitUiState {

    // 98% of every list, and drawn at ONE size — see `WorldPortrait`, where the diameter deliberately
    // ignores gravity so that the outline cannot leak the first trait.
    data object Unsurveyed : WorldPortraitUiState

    data class Surveyed(
        val temperature: Temperature,
        val gravity: Gravity,
        val pressure: Pressure,
        val hazards: Set<Hazard>,
        // Means nothing, and that is the point: one world in a few hundred is memorable for no
        // reason at all, and a player who says "the one with the ring" has built exactly the
        // knowledge this slice is for. Drawn from its own generation stream, so it costs the save
        // nothing.
        val hasRing: Boolean,
    ) : WorldPortraitUiState
}
