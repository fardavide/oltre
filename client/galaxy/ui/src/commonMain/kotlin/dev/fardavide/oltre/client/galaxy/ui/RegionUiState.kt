package dev.fardavide.oltre.client.galaxy.ui

import dev.fardavide.oltre.core.StarClass

// **Ten rows against a thousand pages**, and the cheapest answer to the phone-book complaint in the
// whole slice.
//
// It is not a level you pass through — the strip still goes anywhere directly — it is where you
// decide **where to probe next**, which is the one decision the map exists for and the one it has
// never helped with. Reached by tapping the region name in the system header, which is the only
// accent string there and therefore reads as a target.
//
// Sorted nearest first rather than by coordinate: the index is a chooser, and coordinate order is
// what the strip is for.
data class RegionRowUiState(
    val region: Int,
    val name: String,
    val range: String,
    // Exactly 25, one per system. **The bias is already visible here and needs no copy** — tick
    // height and alpha are star class, so a Deep is a visibly shorter, darker run.
    val histogram: List<RegionTickUiState>,
    val bias: String,
    // The thing a player can act on **before surveying anything**: `settle close in · deuterium
    // good` is the Deep's whole strategy in five words, and it is true from the first launch because
    // star class is charted and charted is free.
    val fact: String,
    val known: String,
    val nearest: String,
    // Drives the active border and the accent tick both.
    val isHome: Boolean,
)

data class RegionTickUiState(val system: Int, val starClass: StarClass, val isHome: Boolean)

// **The stepper was a lens one system wide; the strip is the whole galaxy at once.** Unchanged since
// 0.2 in what it is for — the axis is hours, not coordinates, because a dispatch costs the same
// wherever it goes and the only thing being chosen is a duration.
//
// What 0.11 adds is **the boundary**. Texture with no edges is not ten places: the bias was already
// drawn — 25 short dark ticks in a row *is* a Deep — but nothing said where one region stopped and
// the next began. Nine hairline breaks are the whole intervention, and the header names the region
// you are in.
//
// Ten labels were drawn and measured and rejected: a region is 33dp of strip at 393dp and its name
// is 68–90dp of type at the 9.5 floor, so ten of them overlap by a factor of two and a half before
// one is legible.
data class RegionStripUiState(
    val ticks: List<ReachTickUiState>,
    val marks: List<ReachMarkUiState>,
    val lens: ReachLensUiState,
    val compactLens: ReachLensUiState,
)

data class ReachTickUiState(val system: Int, val mark: ReachTick)

// Star class in height and alpha, and the three things that are *yours* in colour — so your star,
// your probe and anything you pinned are findable in a field of 250 without a legend.
enum class ReachTick {
    DIM,
    STANDARD,
    BRIGHT,

    // Your star.
    ORIGIN,

    // The index your flights are measured from while you are looking at another galaxy. Not your
    // star, so it does not take the accent.
    FOREIGN_ORIGIN,

    // A probe in flight, on its target. Amber, because out here it is a thing of yours crossing
    // space and that is what the fleet strip's amber has always meant.
    PROBE,

    // Somewhere you said mattered. White at 90% — the only mark that is neither a star class nor a
    // job in progress.
    PIN,
}

// An hour of flight, at the system where a flight first costs it. Symmetric about the origin because
// distance is; the *galaxy* is not symmetric about you, and the ruler says so without a word of copy.
data class ReachMarkUiState(val system: Int, val label: String)

data class ReachLensUiState(val firstSystem: Int, val cells: List<ReachCellUiState>)

// **Five named cells rather than seven numbered ones.** 65dp a cell fits a nine-character name at
// the 9.5 floor and 46dp did not — so the picker stopped being a row of coordinates and started
// being a row of places, which is the slice in miniature.
data class ReachCellUiState(
    val system: Int,
    val name: String,
    val label: String,
    val dot: ReachDotUiState,
    val selected: Boolean,
)

sealed interface ReachDotUiState {

    data object Home : ReachDotUiState

    // A star with nothing around it. The smallest mark there is rather than no mark, because
    // "nothing there" is a fact the strip already knows for free.
    data object Empty : ReachDotUiState

    // Sized by the count and lit by the class. The one number on the band that costs anything to
    // know — fifteen world generations a system — which is why the lens counts five and the strip
    // counts none.
    data class Worlds(val count: Int, val starClass: StarClass) : ReachDotUiState
}
