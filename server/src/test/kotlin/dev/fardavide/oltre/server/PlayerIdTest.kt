package dev.fardavide.oltre.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlayerIdTest {

    @Test
    fun `a player id carries the subject it was built from`() {
        assertEquals("000123.abc.4567", PlayerId("000123.abc.4567").value)
    }

    @Test
    fun `an empty subject is not a player`() {
        assertFailsWith<IllegalArgumentException> { PlayerId("") }
    }

    @Test
    fun `a subject of nothing but spaces is not a player either`() {
        // The route strips a blank header before it gets here, so this guard fires for nobody today.
        // It is what makes the type safe for `#110`, where the value stops being a header somebody
        // typed and becomes a claim read out of a token — and a claim can be present and empty.
        assertFailsWith<IllegalArgumentException> { PlayerId("   ") }
    }
}
