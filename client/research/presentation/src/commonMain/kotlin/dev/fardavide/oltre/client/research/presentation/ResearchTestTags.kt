package dev.fardavide.oltre.client.research.presentation

import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.Technology

// Stable handles for the Robot: a row is identified by its technology rather than by its label, so
// renaming what a technology is called cannot silently retarget an assertion.
//
// Overloaded rather than widened to a String, so a caller cannot ask for the row of a technology
// that does not exist and the two branches cannot collide: the six names are distinct across both
// enums, and the compiler is what says so rather than a naming convention.
internal object ResearchTestTags {

    const val CONTENT = "research-content"

    fun row(technology: Technology): String = "research-row-${technology.name.lowercase()}"

    fun action(technology: Technology): String = "research-action-${technology.name.lowercase()}"

    fun row(technology: AdaptationTechnology): String = "research-row-${technology.name.lowercase()}"

    fun action(technology: AdaptationTechnology): String = "research-action-${technology.name.lowercase()}"

    // The watch square carries no text, so it is the one control on this screen a Robot cannot find
    // by what it says.
    fun watch(technology: Technology): String = "research-watch-${technology.name.lowercase()}"

    fun watch(technology: AdaptationTechnology): String = "research-watch-${technology.name.lowercase()}"
}
