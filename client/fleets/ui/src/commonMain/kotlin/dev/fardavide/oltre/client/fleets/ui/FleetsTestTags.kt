package dev.fardavide.oltre.client.fleets.ui

import dev.fardavide.oltre.core.GalaxyCoordinate

// Stable handles for the Robot. A run has no enum to be keyed by — several may target one world, and
// two dispatched in the same check-in are the ordinary case — so the cards are keyed by position in
// the list the screen was handed. That is honest rather than convenient: the list is sorted on an
// intrinsic key, so position is reproducible for a given state.
object FleetsTestTags {

    const val CONTENT = "fleets-content"

    fun card(index: Int): String = "fleets-card-$index"

    // **Keyed by the world rather than by position**, which is the fold stated as a tag: the section
    // is a list of worlds now, and a world is the thing a test wants to tap. `landing(index)` went
    // with the per-run ledger — an index there named whichever run happened to be fifth.
    fun world(at: GalaxyCoordinate): String = "fleets-world-${at.galaxy}-${at.system}-${at.slot}"

    // The line with no disc. Tagged so a test can assert both halves of Design's sixth call at once:
    // that it is present, and that tapping it opens nothing.
    const val UNRECORDED = "fleets-unrecorded"
}
