package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceSeats
import dev.fardavide.oltre.protocol.AllianceTag
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class InMemoryAllianceRepositoryTest {

    private val colonies = InMemoryColonyRepository()
    private val repository = InMemoryAllianceRepository(colonies)
    private val founder = PlayerId("founder")

    @Test
    fun `founding stores an alliance and its founder seat together`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))

        val enlisted = assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW))
        assertEquals(made.alliance, enlisted.alliance)
        assertEquals(AllianceRole.FOUNDER, enlisted.seat.role)
        assertEquals(AllianceSeats(1, 20), enlisted.alliance.alliance.seats)
        assertEquals(NAME, enlisted.alliance.alliance.name)
        assertEquals(TAG, enlisted.alliance.alliance.tag)
    }

    private companion object {

        val NAME = AllianceName("Vanguard")
        val TAG = AllianceTag("VNG")
    }
}
