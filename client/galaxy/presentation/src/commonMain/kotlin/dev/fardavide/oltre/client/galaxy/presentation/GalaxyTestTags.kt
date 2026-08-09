package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.core.AdaptationTechnology

// Keyed by the slot number rather than by a label, for the reason `ResearchTestTags` is keyed by the
// technology: renaming what a world reads cannot then silently retarget an assertion.
internal object GalaxyTestTags {

    const val CONTENT = "galaxy-content"
    const val MAP = "galaxy-map"
    const val COORDINATE = "galaxy-coordinate"
    const val HOME = "galaxy-home"

    // The ±1 steppers went with 0.2.0: the reach band's lens holds the neighbouring system as the
    // cell beside the lit one, which is still one tap and tells you what you are stepping onto
    // before you step.
    const val REACH_STRIP = "galaxy-reach-strip"

    // The whole footer of the system card, whichever of the six states it is in — so a test can
    // assert *what the card says* without first knowing which state produced it.
    const val PROBE_FOOTER = "galaxy-probe-footer"

    // Only the two states that offer a flight have this, which is the assertion: a screen that
    // never offers a dispatch it would refuse is one where this tag is absent exactly when the
    // model would say no.
    const val DISPATCH = "galaxy-dispatch"

    fun reachCell(system: Int): String = "galaxy-reach-cell-$system"

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
