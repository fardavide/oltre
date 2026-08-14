package dev.fardavide.oltre.client.galaxy.ui

import dev.fardavide.oltre.core.StarClass

// **The stepper was a lens one system wide.** Widen it until it holds the galaxy and the browsing
// surface falls out — which is the answer to the question that actually threatened the 5–10 minute
// rule: ±1 is 249 taps to cross a galaxy and 999 to cross the map.
//
// The tap count was only the symptom. The disease was that the *charted* tier — free, galaxy-wide,
// known from the first launch — had no surface at all. A player short of deuterium has a real
// reason to prefer a dim star, and before this the only way to act on it was to step one system at
// a time reading the header. A jump-to-coordinate box would have fixed the taps and left that
// exactly as it was; it also presumes you already know the number, which is the one thing the
// charted tier does not give you.
//
// **The axis is hours, not coordinates, and that is the whole design.** A dispatch costs 150 metal
// wherever it goes, so the only thing the player is choosing is a duration — "what can I reach in
// the nine hours I am about to be asleep" is the only question the screen is asked, and a ruler is
// the only control that answers it.
data class ReachBandUiState(
    // All 250, never a window on them: the strip is the price list and its order is the price.
    val ticks: List<ReachTickUiState>,
    val marks: List<ReachMarkUiState>,
    val lens: ReachLensUiState,
    // Five cells rather than seven. The cell size holds and the count drops, so a target is never
    // under 44dp — the same rule that drops a word rather than shrinking a figure.
    val compactLens: ReachLensUiState,
)

data class ReachTickUiState(val system: Int, val mark: ReachTick)

// Star class in height and alpha, and the two things that are yours in colour. Nothing else on the
// strip is coloured, so your star and your probe are findable in a field of 250 without a legend.
enum class ReachTick {
    DIM,
    STANDARD,
    BRIGHT,

    // Your star.
    ORIGIN,

    // The index your flights are measured from while you are looking at another galaxy. It is not
    // your star, so it does not take the accent — a hop is priced at a whole galaxy plus the
    // distance from your own index, which is why the mark is at that index and not at your home.
    FOREIGN_ORIGIN,

    // A probe in flight, on its target. Amber rather than accent: inside its own system it is a job
    // running and the card is doing something; out here it is a thing of yours crossing space,
    // which is what the fleet strip's amber has always meant. This is the first object in the app
    // that is both at once — see `decisions.md`.
    PROBE,
}

// An hour of flight, at the system where a flight first costs it. Symmetric about the origin
// because distance is; the *galaxy* is not symmetric about you, and the ruler says so without a
// word of copy — from system 165 the left edge is 3h 14m out and the right edge 1h 55m.
data class ReachMarkUiState(val system: Int, val label: String)

data class ReachLensUiState(val firstSystem: Int, val cells: List<ReachCellUiState>)

// One system: its number, and a dot for what it holds. **No time.** Seven neighbours differ by one
// minute each, and printing "1h 21m" through "1h 27m" across the lens is seven near-identical
// figures where the ruler has already answered the question and the footer answers it exactly.
data class ReachCellUiState(
    val system: Int,
    val label: String,
    val dot: ReachDotUiState,
    val selected: Boolean,
)

sealed interface ReachDotUiState {

    data object Home : ReachDotUiState

    // A star with nothing around it. Drawn as the smallest mark there is rather than as no mark,
    // because "nothing there" is a fact the strip already knows for free and is worth one pixel.
    data object Empty : ReachDotUiState

    // Sized by the count and lit by the class. This is the one number on the band that costs
    // anything to know — fifteen world generations a system — which is why the lens counts seven
    // and the strip counts none. A whole-galaxy count would be 3,750 generations for a second
    // channel on a 1.4dp mark, encoding a figure the player cannot act on until after they have
    // paid for the survey.
    data class Worlds(val count: Int, val starClass: StarClass) : ReachDotUiState
}
