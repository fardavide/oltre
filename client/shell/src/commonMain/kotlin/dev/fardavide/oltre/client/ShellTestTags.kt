package dev.fardavide.oltre.client

// Stable handles for the navigation tests: a tab is identified by its destination rather than by
// its label, so renaming what a tab is called cannot silently retarget an assertion.
internal object ShellTestTags {

    // The rail's cells sit on the same centred column as every screen's content; the rule is
    // asserted on bounds, which needs a stable handle on the node that carries it.
    const val RESOURCE_RAIL_CONTENT = "resource-rail-content"

    fun tab(tab: OltreTab): String = "tab-${tab.name.lowercase()}"
}
