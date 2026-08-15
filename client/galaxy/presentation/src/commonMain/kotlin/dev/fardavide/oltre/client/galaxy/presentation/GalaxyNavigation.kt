package dev.fardavide.oltre.client.galaxy.presentation

import kotlin.time.Instant

// **What the Galaxy tab remembers, and one line of it reaches the save.**
//
// Claude Design's rule, 2026-08-14 — *"a filter that outlives the check-in that set it is a screen
// lying about what it holds"* — still governs everything here: the view, the selection, the query and
// the discovery boundary all sit beside `at` in `GalaxyScreen` and die with the composition. The one
// thing that does not is **which of the two lists the tab lands on**, and that is Davide's call of
// 2026-08-15 overruling the rule for one field: opening on the map is right the first time and wrong
// on the hundredth check-in if the map is not where you go. It lives in a preferences file beside the
// save rather than in it — see `GalaxyLanding`.
internal data class GalaxyNavigation(
    val view: GalaxyView,
    val at: SystemSelection,
    val query: String,
    // The boundary the discovery section is measured from: worlds surveyed after this instant are
    // the ones the player has not seen yet. Moves forward when the worlds list is looked at, which is
    // what makes a discovery card impossible to meet twice.
    val seenAt: Instant,
)

// Four views and two heads. **The universe is not a level you pass through and the system is** — the
// first is a second state of the map's own surface, reached by a chip that swaps it in place, and the
// second is the tab's one real push, because a system is a different kind of object and it is where
// you act.
enum class GalaxyView { MAP, UNIVERSE, SYSTEM, WORLDS }

// The only thing this tab writes to disk, and it is a preference rather than game state. Two values
// rather than four: the universe is a state of the map and the orbit page is somewhere you go from
// it, so neither is a place a tab can sensibly *land*.
enum class GalaxyLanding { MAP, WORLDS }
