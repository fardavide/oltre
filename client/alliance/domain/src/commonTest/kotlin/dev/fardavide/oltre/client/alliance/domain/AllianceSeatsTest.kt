package dev.fardavide.oltre.client.alliance.domain

import dev.fardavide.oltre.protocol.AllianceSeats
import kotlin.test.Test
import kotlin.test.assertEquals

class AllianceSeatsTest {

    @Test
    fun `free seats exclude occupied seats`() {
        assertEquals(13, AllianceSeats(taken = 7, cap = 20).free)
    }
}
