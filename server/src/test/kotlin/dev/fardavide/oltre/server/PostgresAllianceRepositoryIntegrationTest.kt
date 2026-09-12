package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceSeats
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ApiError
import io.zonky.test.db.postgres.junit.SingleInstancePostgresRule
import kotlinx.coroutines.test.runTest
import org.junit.ClassRule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PostgresAllianceRepositoryIntegrationTest {

    private val database = postgres.embeddedPostgres.postgresDatabase
    private val repository = PostgresAllianceRepository(database)
    private val founder = PlayerId("founder")

    @BeforeTest
    fun anEmptyAllianceStore() {
        database.applySchema()
        database.emptyEveryTable()
        database.givenPlayer(founder)
    }

    @Test
    fun `founding persists an alliance with its founder seat`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))

        val enlisted = assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW))

        assertEquals(made.alliance, enlisted.alliance)
        assertEquals(AllianceRole.FOUNDER, enlisted.seat.role)
        assertEquals(AllianceSeats(1, 20), enlisted.alliance.alliance.seats)
        assertEquals(1, database.rowsIn("alliances"))
        assertEquals(1, database.rowsIn("alliance_members"))
    }

    @Test
    fun `retrying founding reads the same alliance instead of violating uniqueness`() = runTest {
        val name = AllianceName("Vanguard")
        val tag = AllianceTag("VNG")
        val first = assertIs<Founded.Made>(repository.found(founder, name, tag, TEST_NOW))

        val again = assertIs<Founded.AlreadyFounded>(repository.found(founder, name, tag, TEST_NOW))

        assertEquals(first.alliance, again.alliance)
        assertEquals(1, database.rowsIn("alliances"))
        assertEquals(1, database.rowsIn("alliance_members"))
    }

    @Test
    fun `normalised name collisions are a refusal instead of a database error`() = runTest {
        val rival = PlayerId("rival")
        database.givenPlayer(rival)
        repository.found(founder, AllianceName("Straße  Fleet"), AllianceTag("VNG"), TEST_NOW)

        val refused = assertIs<Founded.Refused>(repository.found(rival, AllianceName("STRASSE\tFLEET"), AllianceTag("RIV"), TEST_NOW))

        assertEquals(ApiError.AllianceNameTaken, refused.error)
        assertEquals(Affiliation.Unaffiliated, repository.allianceOf(rival, TEST_NOW))
        assertEquals(1, database.rowsIn("alliances"))
    }

    @Test
    fun `tag collisions are a refusal instead of a database error`() = runTest {
        val rival = PlayerId("rival")
        database.givenPlayer(rival)
        repository.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW)

        val refused = assertIs<Founded.Refused>(repository.found(rival, AllianceName("Rival"), AllianceTag("VNG"), TEST_NOW))

        assertEquals(ApiError.AllianceTagTaken, refused.error)
        assertEquals(1, database.rowsIn("alliances"))
    }

    private companion object {

        @get:ClassRule
        @JvmStatic
        val postgres: SingleInstancePostgresRule = embeddedPostgres()
    }
}
