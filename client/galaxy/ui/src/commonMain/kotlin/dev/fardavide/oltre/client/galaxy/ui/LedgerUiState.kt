package dev.fardavide.oltre.client.galaxy.ui

import dev.fardavide.oltre.core.AdaptationTechnology

// **The Galaxy tab opens on what you know.** Davide's call, 2026-08-14, on Claude Design's
// recommendation — option (c) of three, and the direct answer to *"finding a planet feels like
// searching a phone number on pagine gialle in the 90s"*.
//
// The argument for the default, in one line: **the map is where you spend probes and the ledger is
// where you spend ships.** Runs go out several times a day and probes once or twice, so before this
// the rarer errand was sitting in the commoner one's chair — and reaching a world you already had a
// reading on cost four taps of paging.
//
// The map is never more than one tap away, no tab was added, and nothing else on the tab moved.
data class LedgerHeadUiState(
    val mode: LedgerMode,
    // **Always visible, never a mode.** Names are unique inside a galaxy, so a full name returns one
    // row and a system name returns its worlds — the only place in the app where typing beats
    // tapping, and the literal answer to "two pages before".
    val query: String,
    // Unselected by default, so the tab opens with no filter to undo. Empty in `MAP` mode.
    val chips: List<LedgerChipUiState>,
    // Null in `MAP` mode: it is the count of what the query and the chips left, and the map is not a
    // query. It gates the sort control with it, because a sort of nothing is not a control.
    val count: String?,
    val sort: LedgerSort,
)

enum class LedgerMode { WORLDS, MAP }

// One accent string at the right of the count line — a target, so the accent is legitimate. No
// glyph: the only two non-letter marks the design system permits anywhere are `→` and `·`, and a
// chevron is neither.
enum class LedgerSort(val label: String) {
    NEAREST("nearest first"),
    RICHEST("richest"),
    MOST_LEFT("most left"),
    NEWEST("newest"),
}

data class LedgerChipUiState(val filter: LedgerFilter, val label: String, val on: Boolean)

// **Typed at the boundary, never a string the row has to parse.** The tap is keyed by the filter
// rather than by the label it prints, so a copy change cannot silently stop a chip working.
sealed interface LedgerFilter {

    data class ReachableWithin(val hours: Int) : LedgerFilter

    data class Verdict(val verdict: WorldVerdictUiState) : LedgerFilter

    // "one level away" — the chip that turns the research tab into a shopping list from the other
    // direction.
    data class OneLevelAway(val technology: AdaptationTechnology) : LedgerFilter

    data object StillHolding : LedgerFilter

    data class Region(val region: Int, val name: String) : LedgerFilter
}

// **Nothing here persists except pins**, and pins live on `GalaxyState` rather than in this model.
// Claude Design's rule, 2026-08-14: *"a filter that outlives the check-in that set it is a screen
// lying about what it holds."* So the mode, the query, the chip set and the sort are navigation —
// they sit beside `at` in `GalaxyScreen` and die with the check-in.
data class LedgerBodyUiState(
    // Worlds surveyed inside the span the app just advanced through. **Derived, not stored**: the
    // event log already carries `SurveyCompleted` with an instant, and the worlds of a surveyed
    // system are regenerable from the seed — so "new" costs the save nothing and cannot fire twice.
    val discoveries: List<DiscoveryCardUiState>,
    // Sorted to the top under their own heading. A pin that changes *where a thing is* is what a pin
    // means — which is why no row grows a pin glyph and pinning is a control on the dispatch sheet.
    val pinned: List<GalaxyRowUiState.World>,
    val rows: List<GalaxyRowUiState.World>,
    // What to say when the filters exclude everything: which filter is doing the excluding and what
    // dropping it would return. The `time-until-affordable` pattern applied to a query — never a
    // dead end, always the next number.
    val emptiness: LedgerEmptinessUiState?,
)

data class LedgerEmptinessUiState(val headline: String, val detail: String)

// The survey moment. **A section of the ledger rather than a layer over it** — a card you must
// dismiss is a tax on the one thing the app promises, and three of them is that tax three times.
// Scrolling past is the dismissal.
//
// One discovery gets the large portrait and the labelled axis column; **two or more degrade to the
// compact card**, because at three the thing to protect is the scroll rather than the ceremony.
data class DiscoveryCardUiState(
    val world: String,
    val coordinate: String,
    val epithet: String,
    val portrait: WorldPortraitUiState.Surveyed,
    val temperature: String,
    val gravity: String,
    val pressure: String,
    val note: String,
    // "found 5 days ago", and **not** "found day 9": nothing in `GameState` carries a genesis
    // instant, so a day number is not derivable — where elapsed-since is, straight off the
    // `SurveyCompleted` event that put the world in the set.
    val found: String,
)
