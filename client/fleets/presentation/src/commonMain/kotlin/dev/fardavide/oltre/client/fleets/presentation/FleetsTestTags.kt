package dev.fardavide.oltre.client.fleets.presentation

// Stable handles for the Robot. A run has no enum to be keyed by — several may target one world, and
// two dispatched in the same check-in are the ordinary case — so the cards are keyed by position in
// the list the screen was handed. That is honest rather than convenient: the list is sorted on an
// intrinsic key, so position is reproducible for a given state.
internal object FleetsTestTags {

    const val CONTENT = "fleets-content"

    fun card(index: Int): String = "fleets-card-$index"

    fun landing(index: Int): String = "fleets-landing-$index"
}
