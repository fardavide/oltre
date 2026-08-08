package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.core.AdaptationTechnology

// Keyed by the slot number rather than by a label, for the reason `ResearchTestTags` is keyed by the
// technology: renaming what a world reads cannot then silently retarget an assertion.
internal object GalaxyTestTags {

    const val CONTENT = "galaxy-content"
    const val MAP = "galaxy-map"
    const val COORDINATE = "galaxy-coordinate"
    const val STEP_BACK = "galaxy-step-back"
    const val STEP_FORWARD = "galaxy-step-forward"
    const val HOME = "galaxy-home"

    fun row(slot: Int): String = "galaxy-row-$slot"

    fun galaxy(galaxy: Int): String = "galaxy-tab-$galaxy"

    // Keyed by the ladder rather than by the string it renders, which is why the row carries the
    // enum as well as its label: "Gravitic 9" is a level away from "Gravitic 8" and a tag that
    // moved with the level would retarget itself every time the empire climbed.
    fun adaptation(technology: AdaptationTechnology): String = "galaxy-adaptation-${technology.name.lowercase()}"
}
