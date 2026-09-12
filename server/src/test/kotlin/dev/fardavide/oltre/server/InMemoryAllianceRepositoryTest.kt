package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceSeats
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ApiError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class InMemoryAllianceRepositoryTest {

    private val colonies = InMemoryColonyRepository()
    private val repository = InMemoryAllianceRepository(colonies)
    private val founder = PlayerId("founder")

    @Test
    fun `a player without a seat reads as unaffiliated`() = runTest {
        assertEquals(Affiliation.Unaffiliated, repository.allianceOf(founder, TEST_NOW))
    }

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

    @Test
    fun `retrying founding returns the alliance already owned by the caller`() = runTest {
        val first = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))

        val again = assertIs<Founded.AlreadyFounded>(repository.found(founder, NAME, TAG, TEST_NOW))

        assertEquals(first.alliance, again.alliance)
        assertEquals(first.alliance, assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW)).alliance)
    }

    @Test
    fun `founding refuses a name differing only by Unicode case and whitespace`() = runTest {
        repository.found(founder, AllianceName("Straße  Fleet"), TAG, TEST_NOW)

        val refused = assertIs<Founded.Refused>(
            repository.found(PlayerId("rival"), AllianceName("STRASSE\tFLEET"), AllianceTag("RIV"), TEST_NOW),
        )

        assertEquals(ApiError.AllianceNameTaken, refused.error)
        assertEquals(Affiliation.Unaffiliated, repository.allianceOf(PlayerId("rival"), TEST_NOW))
    }

    private companion object {

        val NAME = AllianceName("Vanguard")
        val TAG = AllianceTag("VNG")
    }
}
