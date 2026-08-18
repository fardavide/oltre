package dev.fardavide.oltre.client.design.component

import androidx.compose.ui.graphics.Color
import dev.fardavide.oltre.client.design.core.OltreColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// **Six hex literals that a screenshot can only check to within 8% of a frame**, which is the reason
// this is a unit test rather than another baseline. The card's three fills are the handoff's stated
// values and `OltreCard`'s own comment records that their published derivation does not hold — they
// are composited over `OltreColors.background` where the app actually puts them over
// `OltreColors.surface`, leaving them five to ten units per channel darker than the alphas they
// replace. They are kept as specified because the handoff calls them final, and that makes them
// exactly the kind of number that gets "corrected" by somebody reading the comment and not the
// decision. Pinned here, that edit fails a test instead of shipping.
//
// What is asserted is the design's *claims* rather than the literals restated — that the three fills
// are ordered, that RUNNING is the only accent state, that the edge is lit from above — plus the
// literals themselves, because a claim about ordering survives all six of them drifting together.
class OltreCardTest {

    @Test
    fun `every state has its own fill`() {
        val fills = OltreCardState.entries.map { it.fill() }
        assertEquals(fills.size, fills.toSet().size)
    }

    // The depth pass's whole argument in one assertion: six identical cards become a foreground and a
    // background at no cost in ink, so a row with everything in hand has to be brighter than one that
    // is waiting. Compared on the blue channel because these are three neutral greys of one hue and
    // any channel orders them alike.
    @Test
    fun `an actionable card is brighter than a waiting one`() {
        assertEquals(true, OltreCardState.ACTIONABLE.fill().blue > OltreCardState.WAITING.fill().blue)
    }

    // Accent means *in flight* and nothing else. A second accent state would make the one lit thing
    // on the screen stop answering "why can nothing else start".
    @Test
    fun `only a running card is lit in accent`() {
        assertEquals(OltreColors.accent.red, OltreCardState.RUNNING.bevelTop().red)
        assertEquals(OltreColors.accent.red, OltreCardState.RUNNING.bevelFoot().red)
        assertNotEquals(OltreColors.accent.red, OltreCardState.ACTIONABLE.bevelTop().red)
        assertNotEquals(OltreColors.accent.red, OltreCardState.WAITING.bevelTop().red)
    }

    // **The card is lit from above** — the top edge takes one step more than the rest, which is what
    // reads as an edge catching a light rather than as a gradient running down the card. True of every
    // state, which is what makes it the rule rather than a property of one of them.
    @Test
    fun `the top of the bevel is brighter than its foot in every state`() {
        OltreCardState.entries.forEach { state ->
            assertEquals(
                true,
                state.bevelTop().alpha > state.bevelFoot().alpha,
                "$state should be lit from above",
            )
        }
    }

    // A waiting card is dimmer than an actionable one and wears the same edge: the two are told apart
    // by fill alone, deliberately, because a border change would read as a state rather than as a
    // recession.
    @Test
    fun `waiting and actionable share an edge and differ only in fill`() {
        assertEquals(OltreCardState.ACTIONABLE.bevelTop(), OltreCardState.WAITING.bevelTop())
        assertEquals(OltreCardState.ACTIONABLE.bevelFoot(), OltreCardState.WAITING.bevelFoot())
        assertNotEquals(OltreCardState.ACTIONABLE.fill(), OltreCardState.WAITING.fill())
    }

    // The literals, stated once. Everything above is a property and would survive all six moving
    // together; this is what catches one of them being retyped.
    @Test
    fun `the fills are the values the handoff specified`() {
        assertEquals(Color(0xFF14161C), OltreCardState.ACTIONABLE.fill())
        assertEquals(Color(0xFF0C0E14), OltreCardState.WAITING.fill())
        assertEquals(Color(0xFF090F1C), OltreCardState.RUNNING.fill())
    }

    @Test
    fun `the bevels are the values the depth pass specified`() {
        assertEquals(Color.White.copy(alpha = 0.17f), OltreCardState.ACTIONABLE.bevelTop())
        assertEquals(Color.White.copy(alpha = 0.09f), OltreCardState.ACTIONABLE.bevelFoot())
        assertEquals(OltreColors.accent.copy(alpha = 0.62f), OltreCardState.RUNNING.bevelTop())
        assertEquals(OltreColors.accent.copy(alpha = 0.45f), OltreCardState.RUNNING.bevelFoot())
    }
}
