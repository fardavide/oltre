package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.design.text.English
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
            English.resolve(WatchTarget.Facility(BuildingType.METAL_MINE).watchingLabel(compact = false)),
        )
    }

    // The heading's short form, not the lock screen's "Deuterium Synthesizer": this sits at the end
    // of a section label that has to survive a 320dp Slide Over pane.
    @Test
    fun `a long facility name keeps the row's abbreviation`() {
        assertEquals(
            "watching Deuterium Synth.",
            English.resolve(WatchTarget.Facility(BuildingType.DEUTERIUM_SYNTHESIZER).watchingLabel(compact = false)),
        )
    }

    // The one name that shortens at a Slide Over's width. The row calls it "Robotics" there, so a
    // heading still saying "Robotics Factory" would be naming a row by a name nowhere on the screen.
    @Test
    fun `the one facility whose row shortens is named the short way in a narrow window`() {
        assertEquals(
            "watching Robotics",
            English.resolve(WatchTarget.Facility(BuildingType.ROBOTICS_FACTORY).watchingLabel(compact = true)),
        )
        assertEquals(
            "watching Robotics Factory",
            English.resolve(WatchTarget.Facility(BuildingType.ROBOTICS_FACTORY).watchingLabel(compact = false)),
        )
    }

    // Every other facility is the same string at both widths, so nothing else moves under the flag.
    @Test
    fun `a name that fits at both widths is the same string twice`() {
        assertEquals(
            "watching Deuterium Synth.",
            English.resolve(WatchTarget.Facility(BuildingType.DEUTERIUM_SYNTHESIZER).watchingLabel(compact = true)),
        )
    }

    @Test
    fun `a watched technology is named from the screen that draws technologies`() {
        assertEquals(
            "watching Extraction",
            English.resolve(WatchTarget.Project(Technology.EXTRACTION).watchingLabel(compact = false)),
        )
    }

    // "Gravitic", not "Gravitic Adaptation" — all three ladders would end in the same word, which
    // carries nothing and costs eleven characters the heading does not have.
    @Test
    fun `a watched ladder drops the trailing noun its rows drop`() {
        assertEquals(
            "watching Gravitic",
            English.resolve(WatchTarget.Ladder(AdaptationTechnology.GRAVITIC).watchingLabel(compact = false)),
        )
    }
}
