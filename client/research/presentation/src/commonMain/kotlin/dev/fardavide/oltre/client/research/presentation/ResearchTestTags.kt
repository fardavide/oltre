package dev.fardavide.oltre.client.research.presentation

import dev.fardavide.oltre.core.Technology

// Stable handles for the Robot: a row is identified by its technology rather than by its label, so
// renaming what a technology is called cannot silently retarget an assertion.
internal object ResearchTestTags {

    const val CONTENT = "research-content"

    fun row(technology: Technology): String = "research-row-${technology.name.lowercase()}"

    fun action(technology: Technology): String = "research-action-${technology.name.lowercase()}"
}
