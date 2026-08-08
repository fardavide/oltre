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

    // Keyed by the slot *and* the ladder rather than by the string it renders. The enum rather than
    // the label because "Gravitic 9" is a level away from "Gravitic 8", and a tag that moved with
    // the level would retarget itself every time the empire climbed. The slot because a system
    // routinely holds several worlds wanting the same ladder — the seed's own home system holds
    // three — so the ladder alone would name three targets rather than one.
    fun adaptation(slot: Int, technology: AdaptationTechnology): String =
        "galaxy-adaptation-$slot-${technology.name.lowercase()}"
}
