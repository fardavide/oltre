package dev.fardavide.oltre.client.design.component

import dev.fardavide.oltre.client.design.text.TextRes
import kotlin.test.Test
import kotlin.test.assertEquals

// **The rule five mappers spend, held in one place.**
//
// `asSquare` used to invert when the square was held, on the design's own sentence — *"a held row
// asks for the opposite of what the server is on, so the request is what the square draws"*. The
// sentence is right and the reading of it was not: it describes the **server's** colony, and the one
// the mapper is handed is the local one, which every tap has already transitioned optimistically
// before anything is drawn. So the inversion produced the state the player was leaving.
//
// **Nothing else could have caught it.** A screenshot photographs a lit square identically whichever
// fact it means; the count, the amber and the withdraw were all correct; and the mappers agreed with
// each other because they all read the same wrong rule. It took tapping a dark bell in a running app
// and reading the sentence back — `HeldAppBehaviourTest`'s four direction tests are that, and this
// file is the property underneath them.
class WatchSquareUiStateTest {

    // **Held changes the colour and not the face**, which is the whole of the corrected rule.
    @Test
    fun `should draw the same face held or not`() {
        for (state in listOf(WatchUiState.Offered, WatchUiState.Subscribed, WatchUiState.Booked(TextRes.Raw("11:23")))) {
            assertEquals(
                state.asSquare(held = false).asked,
                state.asSquare(held = true).asked,
                "$state changes face when it is held",
            )
        }
    }

    // Two of the three members mean booked and one does not, and the square is the only part of a
    // row that cares which — so this is the one thing `asSquare` actually decides.
    @Test
    fun `should light for the two states that mean booked and for no other`() {
        assertEquals(WatchAsk.NONE, WatchUiState.Offered.asSquare(held = false).asked)
        assertEquals(WatchAsk.ONE, WatchUiState.Subscribed.asSquare(held = false).asked)
        assertEquals(WatchAsk.ONE, WatchUiState.Booked(TextRes.Raw("11:23")).asSquare(held = false).asked)
    }

    @Test
    fun `should carry the held flag through untouched`() {
        assertEquals(false, WatchUiState.Offered.asSquare(held = false).held)
        assertEquals(true, WatchUiState.Offered.asSquare(held = true).held)
    }
}
