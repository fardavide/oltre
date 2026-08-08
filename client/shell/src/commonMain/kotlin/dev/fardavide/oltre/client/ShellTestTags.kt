package dev.fardavide.oltre.client

// Stable handles for the navigation tests: a tab is identified by its destination rather than by
// its label, so renaming what a tab is called cannot silently retarget an assertion.
internal object ShellTestTags {

    // The rail's cells sit on the same centred column as every screen's content; the rule is
    // asserted on bounds, which needs a stable handle on the node that carries it.
    const val RESOURCE_RAIL_CONTENT = "resource-rail-content"

    fun tab(tab: OltreTab): String = "tab-${tab.name.lowercase()}"

    // The rate shares a line with the stock rather than sitting under it, so the pair — not the
    // rate alone — is what has to fit the cell. When it does not, the rate has to wrap under the
    // stock instead of being squeezed out sideways, and asserting that needs a handle on both the
    // cell and the rate inside it.
    fun resourceCell(name: String): String = "resource-cell-${name.lowercase()}"

    fun resourceRate(name: String): String = "resource-rate-${name.lowercase()}"
}
