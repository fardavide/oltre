package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.core.BuildingType

// Layout rules are asserted on bounds, which needs a stable handle on the node that carries them
// — see ColonyScreenLayoutBehaviourTest.
//
// **Public rather than internal since 0.6.0**, and only just: the composition root has one behaviour
// test that has to tap a square — what a tap costs is a save and a whole re-derived alert schedule,
// and neither is visible from inside this module. Every other control the shell's robots reach, they
// reach by the words on it; the square has none, so the tag is the only handle there is.
object ColonyTestTags {

    const val CONTENT = "colony-content"

    // The watch square carries no text, so it is the one control on this screen a Robot cannot
    // find by what it says. Keyed by facility rather than by position, so reordering the list
    // cannot silently retarget an assertion — the same rule the Research tags follow.
    fun watch(building: BuildingType): String = "colony-watch-${building.name.lowercase()}"
}
