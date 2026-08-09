package dev.fardavide.oltre.client.debug.presentation

// Stable handles for the Robot, so renaming a label cannot silently retarget an assertion.
//
// A row carries three tags rather than one — the row itself is what gets tapped, and its two lines
// are what get read. Tagging the leaves rather than asserting over the row's descendants is what
// lets the tests assert *exact* strings: a query scoped to a container has to say `substring` and
// then quietly passes on a label that grew a word.
internal object DebugTestTags {

    const val SHEET = "debug-sheet"
    const val SCRIM = "debug-scrim"
    const val SKIP = "debug-skip"
    const val RESET = "debug-reset"
    const val CLOSE = "debug-close"

    fun label(row: String): String = "$row-label"

    fun detail(row: String): String = "$row-detail"

    // The bar that fills as the row is held. Tagged so a test can say the row shows its progress
    // without asserting a width, which would be asserting the animation rather than the design.
    fun fill(row: String): String = "$row-fill"

    fun reading(name: String): String = "debug-reading-${name.lowercase().replace(' ', '-')}"
}
