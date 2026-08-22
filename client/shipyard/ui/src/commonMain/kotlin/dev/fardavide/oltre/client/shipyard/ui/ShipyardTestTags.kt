package dev.fardavide.oltre.client.shipyard.ui

import dev.fardavide.oltre.core.ShipType

// Stable handles for the Robot, keyed by the hull rather than by its label — renaming what a hull is
// called on screen must not silently retarget an assertion.
//
// **Public rather than internal, on `ColonyTestTags`' precedent and for the same reason**: the
// composition root has a behaviour test that has to tap Build, and what that tap costs — a charged
// price, a hull in the pool, a save written and the whole alert schedule re-derived — is visible
// from nowhere inside this module. "Build" is also a word the app will use on a second surface one
// day, so the tag is the handle that does not go ambiguous.
object ShipyardTestTags {

    const val CONTENT = "shipyard-content"

    fun card(type: ShipType): String = "shipyard-card-${type.name.lowercase()}"

    fun action(type: ShipType): String = "shipyard-action-${type.name.lowercase()}"

    // The slipway footer, which exists only while something is on it — so this is also the handle a
    // Robot asks "is the yard busy" with, rather than reading a countdown string.
    fun yard(type: ShipType): String = "shipyard-yard-${type.name.lowercase()}"

    // The square that asks to be told about this hull. Like the footer it exists only while the yard
    // holds one, so its absence is an assertion a Robot can make rather than a query that has to
    // find nothing.
    fun alert(type: ShipType): String = "shipyard-alert-${type.name.lowercase()}"
}
