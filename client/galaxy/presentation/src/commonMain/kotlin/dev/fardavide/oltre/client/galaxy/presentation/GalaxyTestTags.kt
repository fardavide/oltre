package dev.fardavide.oltre.client.galaxy.presentation

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
}
