package dev.fardavide.oltre.client.research.ui

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

    // The one sheet a row can open, and the button inside it. Neither is keyed by anything: one
    // sheet is open at a time, and a tag that named the row it came from would be a second way of
    // saying what the sheet's own heading already says.
    const val SHEET = "research-sheet"
    const val SHEET_ACTION = "research-sheet-action"

    // The card is what gets pressed and the row is what gets read, and they are two tags because a
    // clickable card merges its descendants' semantics: the target and the text it holds stop being
    // one node the moment the body opens a sheet.
    fun card(technology: Technology): String = "research-card-${technology.name.lowercase()}"

    fun card(technology: AdaptationTechnology): String = "research-card-${technology.name.lowercase()}"

    fun row(technology: Technology): String = "research-row-${technology.name.lowercase()}"

    fun action(technology: Technology): String = "research-action-${technology.name.lowercase()}"

    fun row(technology: AdaptationTechnology): String = "research-row-${technology.name.lowercase()}"

    fun action(technology: AdaptationTechnology): String = "research-action-${technology.name.lowercase()}"

    // The watch square carries no text, so it is the one control on this screen a Robot cannot find
    // by what it says.
    fun watch(technology: Technology): String = "research-watch-${technology.name.lowercase()}"

    fun watch(technology: AdaptationTechnology): String = "research-watch-${technology.name.lowercase()}"
}
