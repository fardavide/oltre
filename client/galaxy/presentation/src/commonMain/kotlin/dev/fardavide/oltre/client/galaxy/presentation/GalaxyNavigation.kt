package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.galaxy.ui.LedgerFilter
import dev.fardavide.oltre.client.galaxy.ui.LedgerSort
import kotlin.time.Instant

// **Everything the Galaxy tab remembers, and none of it reaches the save.** Claude Design's rule,
// 2026-08-14: *"a filter that outlives the check-in that set it is a screen lying about what it
// holds."* So this lives beside `at` in `GalaxyScreen` and dies with the composition — the only
// thing the slice writes to disk is `GalaxyState.pinned`.
internal data class GalaxyNavigation(
    val view: GalaxyView,
    val at: SystemSelection,
    val query: String,
    val filters: Set<LedgerFilter>,
    val sort: LedgerSort,
    // The boundary the discovery section is measured from: worlds surveyed after this instant are
    // the ones the player has not seen yet. Moves forward when the ledger is looked at, which is
    // what makes a discovery card impossible to meet twice.
    val seenAt: Instant,
    // Rebuilt per render rather than stored, because one of the five names the region the player is
    // standing in — so it moves when they move.
    val availableFilters: List<LedgerFilter>,
)

// Three views, one head. The switch in the head moves between the first two; the third is reached by
// tapping the region name in the system header and is a **chooser rather than a level you pass
// through** — the strip still goes anywhere directly.
enum class GalaxyView { LEDGER, SYSTEM, REGIONS }
