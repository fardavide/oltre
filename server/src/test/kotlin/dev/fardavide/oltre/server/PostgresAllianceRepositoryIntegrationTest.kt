package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceSeats
import dev.fardavide.oltre.protocol.AllianceTag
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

    private companion object {

        @get:ClassRule
        @JvmStatic
        val postgres: SingleInstancePostgresRule = embeddedPostgres()
    }
}
