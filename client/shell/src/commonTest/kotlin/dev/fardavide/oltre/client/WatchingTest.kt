package dev.fardavide.oltre.client

import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.WatchTarget
import kotlin.test.Test
import kotlin.test.assertEquals

// The one string the composition root writes, and the reason it writes it: a watch set on Research
// has to be readable on Colony and the other way round, so the clause has to be able to name a row
// from either feature. Both screens print the same one.
class WatchingTest {

    @Test
    fun `a watched facility is named the way its own row names it`() {
        assertEquals(
            "watching Metal Mine",
            WatchTarget.Facility(BuildingType.METAL_MINE).watchingLabel(),
        )
    }

    // The heading's short form, not the lock screen's "Deuterium Synthesizer": this sits at the end
    // of a section label that has to survive a 320dp Slide Over pane.
    @Test
    fun `a long facility name keeps the row's abbreviation`() {
        assertEquals(
            "watching Deuterium Synth.",
            WatchTarget.Facility(BuildingType.DEUTERIUM_SYNTHESIZER).watchingLabel(),
        )
    }

    @Test
    fun `a watched technology is named from the screen that draws technologies`() {
        assertEquals(
            "watching Extraction",
            WatchTarget.Project(Technology.EXTRACTION).watchingLabel(),
        )
    }

    // "Gravitic", not "Gravitic Adaptation" — all three ladders would end in the same word, which
    // carries nothing and costs eleven characters the heading does not have.
    @Test
    fun `a watched ladder drops the trailing noun its rows drop`() {
        assertEquals(
            "watching Gravitic",
            WatchTarget.Ladder(AdaptationTechnology.GRAVITIC).watchingLabel(),
        )
    }
}
