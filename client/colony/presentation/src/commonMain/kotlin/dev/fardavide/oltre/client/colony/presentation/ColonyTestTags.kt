package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.core.BuildingType

// Layout rules are asserted on bounds, which needs a stable handle on the node that carries them
// — see ColonyScreenLayoutBehaviourTest.
internal object ColonyTestTags {

    const val CONTENT = "colony-content"

    // The watch square carries no text, so it is the one control on this screen a Robot cannot
    // find by what it says. Keyed by facility rather than by position, so reordering the list
    // cannot silently retarget an assertion — the same rule the Research tags follow.
    fun watch(building: BuildingType): String = "colony-watch-${building.name.lowercase()}"
}
