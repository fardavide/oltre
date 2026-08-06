package dev.fardavide.oltre.client

// Stable handles for the navigation tests: a tab is identified by its destination rather than by
// its label, so renaming what a tab is called cannot silently retarget an assertion.
internal object ShellTestTags {

    fun tab(tab: OltreTab): String = "tab-${tab.name.lowercase()}"
}
