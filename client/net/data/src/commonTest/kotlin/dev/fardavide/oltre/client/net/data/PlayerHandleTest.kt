package dev.fardavide.oltre.client.net.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlayerHandleTest {

    @Test
    fun `a handle carries the value the header will hold`() {
        assertEquals("davide", PlayerHandle("davide").value)
    }

    // **The guard is here and not on the wire**, which is `IdempotencyKey`'s asymmetry in the other
    // direction: an absent player has a first-class answer at the far end — `ApiError
    // .Unauthenticated`, and the sign-in screen — but a *blank* one is not that. It is a header this
    // build sent with nothing in it, which the server reads as nobody asking, and it would arrive
    // back as *"sign in again"* to a player who already had.
    @Test
    fun `a handle with nothing in it is refused rather than sent`() {
        assertFailsWith<IllegalArgumentException> { PlayerHandle("  ") }
    }
}
