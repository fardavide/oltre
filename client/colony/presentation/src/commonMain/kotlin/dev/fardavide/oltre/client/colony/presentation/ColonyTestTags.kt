package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.core.BuildingType

// Layout rules are asserted on bounds, which needs a stable handle on the node that carries them
// — see ColonyScreenLayoutBehaviourTest.
//
// **Public rather than internal since 0.5.0**, and only just: the composition root has one behaviour
// test that has to tap a square — what a tap costs is a save and a whole re-derived alert schedule,
// and neither is visible from inside this module. Every other control the shell's robots reach, they
// reach by the words on it; the square has none, so the tag is the only handle there is.
object ColonyTestTags {

    const val CONTENT = "colony-content"

    // What the sheet is opened from, and the whole card rather than a control on it: everything on a
    // row that is not the action or the square opens the arithmetic behind the row.
    const val SHEET = "colony-sheet"

    // The action inside the sheet, which says the same word as the button on the row it came from
    // and therefore cannot be reached by that word.
    const val SHEET_ACTION = "colony-sheet-action"

    // The watch square carries no text, so it is the one control on this screen a Robot cannot
    // find by what it says. Keyed by facility rather than by position, so reordering the list
    // cannot silently retarget an assertion — the same rule the Research tags follow.
    fun watch(building: BuildingType): String = "colony-watch-${building.name.lowercase()}"

    // Keyed by facility for the same reason the square is: a card is tapped by the row it belongs
    // to, never by where in the list it happens to sit.
    fun card(building: BuildingType): String = "colony-card-${building.name.lowercase()}"
}
