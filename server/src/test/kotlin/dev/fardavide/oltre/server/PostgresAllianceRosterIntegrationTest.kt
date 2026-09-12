package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.Experience
import dev.fardavide.oltre.protocol.AllianceMember
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceRosterResponse
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.CommanderName
import dev.fardavide.oltre.protocol.ExperienceReading
import dev.fardavide.oltre.protocol.JoinRequest
import dev.fardavide.oltre.protocol.JoinDecision
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.PlayerMark
import dev.fardavide.oltre.protocol.PlayerProfile
import dev.fardavide.oltre.protocol.Protocol
import io.zonky.test.db.postgres.junit.SingleInstancePostgresRule
import kotlinx.coroutines.test.runTest
import org.junit.ClassRule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.time.Duration.Companion.minutes

class PostgresAllianceRosterIntegrationTest {

    private val database = postgres.embeddedPostgres.postgresDatabase
    private val clock = MovableClock(TEST_NOW)
    private val alliances = PostgresAllianceRepository(database)
    private val colonies = PostgresColonyRepository(database, clock)
    private val players = PostgresPlayerRepository(database, clock)
    private val founder = PlayerId("private-founder-account")

    @BeforeTest
    fun anEmptyAllianceStore() {
        database.applySchema()
        database.emptyEveryTable()
        database.givenPlayer(founder)
    }

    @Test
    fun `a founder roster reads the profile earned experience and last sync without the account id`() = runTest {
        val profile = PlayerProfile(
            name = CommanderName("Ada di Notte"),
            mark = PlayerMark.Preset(MarkPreset.SEXTANT),
        )
        val lastUpdatedAt = Instant.parse("2026-08-25T11:17:23.123456Z")
        val fresh = freshColony(at = lastUpdatedAt)
        val snapshot = fresh.copy(state = fresh.state.copy(experience = Experience(1_200)))
        assertTrue(players.setProfile(founder, profile))
        colonies.found(founder, snapshot)
        assertIs<Founded.Made>(alliances.found(founder, AllianceName("Fleet"), AllianceTag("FLT"), TEST_NOW))
        val affiliation = assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, TEST_NOW))

        val roster = assertIs<RosterRead.Present>(alliances.rosterOf(founder, TEST_NOW))

        assertEquals(
            listOf(
                AllianceMember(
                    id = affiliation.seat.id,
                    profile = profile,
                    role = AllianceRole.FOUNDER,
                    experience = ExperienceReading.Known(Experience(1_200)),
                    lastSyncedAt = lastUpdatedAt,
                ),
            ),
            roster.members,
        )
        assertEquals(emptyList(), roster.pending)
        assertNotEquals(founder.value, roster.members.single().id.value)
        val response = AllianceRosterResponse(ApiVersion.CURRENT, roster.members, roster.pending)
        val json = Protocol.json.encodeToString(AllianceRosterResponse.serializer(), response)
        assertFalse(json.contains(founder.value))
    }

    @Test
    fun `a legacy colony reads unknown experience until a write stores its earned points`() = runTest {
        val fresh = freshColony()
        val snapshot = fresh.copy(state = fresh.state.copy(experience = Experience(1_200)))
        val stored = colonies.found(founder, snapshot).colony
        assertIs<Founded.Made>(alliances.found(founder, AllianceName("Fleet"), AllianceTag("FLT"), TEST_NOW))
        database.connection.use { connection ->
            connection.prepareStatement("UPDATE colonies SET experience = NULL WHERE player_id = ?").use { statement ->
                statement.setString(1, founder.value)
                assertEquals(1, statement.executeUpdate())
            }
        }

        val beforeWrite = assertIs<RosterRead.Present>(alliances.rosterOf(founder, TEST_NOW))

        assertEquals(ExperienceReading.Unknown, beforeWrite.members.single().experience)

        assertEquals(
            WriteResult.WRITTEN,
            colonies.write(founder, snapshot, applied = emptySet(), expected = stored.version),
        )
        val afterWrite = assertIs<RosterRead.Present>(alliances.rosterOf(founder, TEST_NOW))

        assertEquals(ExperienceReading.Known(Experience(1_200)), afterWrite.members.single().experience)
    }

    @Test
    fun `a founder roster reads the pending request details without the applicant account id`() = runTest {
        val applicant = PlayerId("private-applicant-account")
        database.givenPlayer(applicant)
        val profile = PlayerProfile(
            name = CommanderName("Lin di Mare"),
            mark = PlayerMark.Preset(MarkPreset.WAKE),
        )
        val fresh = freshColony()
        colonies.found(founder, fresh)
        colonies.found(applicant, fresh.copy(state = fresh.state.copy(experience = Experience(340))))
        assertTrue(players.setProfile(applicant, profile))
        assertIs<Founded.Made>(alliances.found(founder, AllianceName("Fleet"), AllianceTag("FLT"), TEST_NOW))
        val current = assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, TEST_NOW))
        val askedAt = Instant.parse("2026-08-25T12:17:23.123456Z")
        val applied = assertIs<AllianceChange.Applied>(
            alliances.petition(applicant, current.alliance.alliance.id, askedAt, current.alliance.version),
        )
        val petitioning = assertIs<Affiliation.Petitioning>(applied.affiliation)

        val roster = assertIs<RosterRead.Present>(alliances.rosterOf(founder, askedAt))

        assertEquals(
            listOf(
                JoinRequest(
                    id = petitioning.petition.id,
                    profile = profile,
                    experience = ExperienceReading.Known(Experience(340)),
                    askedAt = askedAt,
                ),
            ),
            roster.pending,
        )
        assertNotEquals(applicant.value, petitioning.petition.id.value)
        val response = AllianceRosterResponse(ApiVersion.CURRENT, roster.members, roster.pending)
        val json = Protocol.json.encodeToString(AllianceRosterResponse.serializer(), response)
        assertFalse(json.contains(applicant.value))
    }

    @Test
    fun `a roster reads denormalized experience and last sync without decoding the snapshot`() = runTest {
        val lastUpdatedAt = Instant.parse("2026-08-25T11:17:23.123456Z")
        val fresh = freshColony(at = lastUpdatedAt)
        colonies.found(founder, fresh.copy(state = fresh.state.copy(experience = Experience(1_200))))
        assertIs<Founded.Made>(alliances.found(founder, AllianceName("Fleet"), AllianceTag("FLT"), TEST_NOW))
        database.connection.use { connection ->
            connection.prepareStatement("UPDATE colonies SET snapshot_json = ?::jsonb WHERE player_id = ?").use { statement ->
                statement.setString(1, """{"unreadable":true}""")
                statement.setString(2, founder.value)
                assertEquals(1, statement.executeUpdate())
            }
        }

        val member = assertIs<RosterRead.Present>(alliances.rosterOf(founder, TEST_NOW)).members.single()

        assertEquals(ExperienceReading.Known(Experience(1_200)), member.experience)
        assertEquals(lastUpdatedAt, member.lastSyncedAt)
    }

    @Test
    fun `a pending request from a legacy colony reads unknown experience`() = runTest {
        val applicant = PlayerId("legacy-applicant-account")
        database.givenPlayer(applicant)
        val fresh = freshColony()
        colonies.found(founder, fresh)
        colonies.found(applicant, fresh.copy(state = fresh.state.copy(experience = Experience(340))))
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Fleet"), AllianceTag("FLT"), TEST_NOW))
        val applied = assertIs<AllianceChange.Applied>(
            alliances.petition(applicant, made.alliance.alliance.id, TEST_NOW, made.alliance.version),
        )
        val petitioning = assertIs<Affiliation.Petitioning>(applied.affiliation)
        database.connection.use { connection ->
            connection.prepareStatement("UPDATE colonies SET experience = NULL WHERE player_id = ?").use { statement ->
                statement.setString(1, applicant.value)
                assertEquals(1, statement.executeUpdate())
            }
        }

        val roster = assertIs<RosterRead.Present>(alliances.rosterOf(founder, TEST_NOW))

        assertEquals(
            listOf(
                JoinRequest(
                    id = petitioning.petition.id,
                    profile = PlayerProfile(name = null, mark = null),
                    experience = ExperienceReading.Unknown,
                    askedAt = TEST_NOW,
                ),
            ),
            roster.pending,
        )
    }

    @Test
    fun `database roster order follows role then tenure then private id across repeated reads`() = runTest {
        val zulu = PlayerId("zulu")
        val bravo = PlayerId("bravo")
        val alpha = PlayerId("alpha")
        val admin = PlayerId("admin")
        colonies.found(founder, freshColony())
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Fleet"), AllianceTag("FLT"), TEST_NOW))
        for ((player, joinedAt) in listOf(
            zulu to TEST_NOW + 1.minutes,
            bravo to TEST_NOW + 2.minutes,
            alpha to TEST_NOW + 2.minutes,
            admin to TEST_NOW + 3.minutes,
        )) {
            database.givenPlayer(player)
            colonies.found(player, freshColony())
            val current = assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, joinedAt))
            val applied = assertIs<AllianceChange.Applied>(
                alliances.petition(player, made.alliance.alliance.id, joinedAt, current.alliance.version),
            )
            val petitioning = assertIs<Affiliation.Petitioning>(applied.affiliation)
            assertIs<AllianceChange.Applied>(
                alliances.approve(
                    founder, made.alliance.alliance.id, petitioning.petition.id,
                    JoinDecision.ADMITTED, joinedAt, petitioning.alliance.version,
                ),
            )
        }
        val adminAffiliation = assertIs<Affiliation.Enlisted>(alliances.allianceOf(admin, TEST_NOW))
        assertIs<AllianceChange.Applied>(
            alliances.setRole(
                founder, made.alliance.alliance.id, adminAffiliation.seat.id,
                AllianceRole.ADMIN, adminAffiliation.alliance.version,
            ),
        )
        val expected = listOf(founder, admin, zulu, alpha, bravo).map { player ->
            assertIs<Affiliation.Enlisted>(alliances.allianceOf(player, TEST_NOW)).seat.id
        }

        repeat(2) {
            assertEquals(expected, assertIs<RosterRead.Present>(alliances.rosterOf(founder, TEST_NOW)).members.map { it.id })
        }
    }

    @Test
    fun `an admin database roster includes the actual pending request`() = runTest {
        val admin = PlayerId("private-admin-account")
        val applicant = PlayerId("pending-applicant-account")
        for (player in listOf(admin, applicant)) {
            database.givenPlayer(player)
            colonies.found(player, freshColony())
        }
        colonies.found(founder, freshColony())
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Fleet"), AllianceTag("FLT"), TEST_NOW))
        val admission = assertIs<AllianceChange.Applied>(
            alliances.petition(admin, made.alliance.alliance.id, TEST_NOW, made.alliance.version),
        )
        val adminPetition = assertIs<Affiliation.Petitioning>(admission.affiliation)
        assertIs<AllianceChange.Applied>(
            alliances.approve(
                founder, made.alliance.alliance.id, adminPetition.petition.id,
                JoinDecision.ADMITTED, TEST_NOW, adminPetition.alliance.version,
            ),
        )
        val enlisted = assertIs<Affiliation.Enlisted>(alliances.allianceOf(admin, TEST_NOW))
        assertIs<AllianceChange.Applied>(
            alliances.setRole(founder, made.alliance.alliance.id, enlisted.seat.id, AllianceRole.ADMIN, enlisted.alliance.version),
        )
        val current = assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, TEST_NOW))
        val applied = assertIs<AllianceChange.Applied>(
            alliances.petition(applicant, made.alliance.alliance.id, TEST_NOW, current.alliance.version),
        )
        val petitioning = assertIs<Affiliation.Petitioning>(applied.affiliation)

        val roster = assertIs<RosterRead.Present>(alliances.rosterOf(admin, TEST_NOW))

        assertEquals(listOf(petitioning.petition.id), roster.pending?.map { it.id })
    }

    @Test
    fun `a member database roster hides an actual pending request with null`() = runTest {
        val member = PlayerId("private-member-account")
        val applicant = PlayerId("pending-applicant-account")
        for (player in listOf(member, applicant)) {
            database.givenPlayer(player)
            colonies.found(player, freshColony())
        }
        colonies.found(founder, freshColony())
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Fleet"), AllianceTag("FLT"), TEST_NOW))
        val admission = assertIs<AllianceChange.Applied>(
            alliances.petition(member, made.alliance.alliance.id, TEST_NOW, made.alliance.version),
        )
        val membershipPetition = assertIs<Affiliation.Petitioning>(admission.affiliation)
        assertIs<AllianceChange.Applied>(
            alliances.approve(
                founder, made.alliance.alliance.id, membershipPetition.petition.id,
                JoinDecision.ADMITTED, TEST_NOW, membershipPetition.alliance.version,
            ),
        )
        val current = assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, TEST_NOW))
        val applied = assertIs<AllianceChange.Applied>(
            alliances.petition(applicant, made.alliance.alliance.id, TEST_NOW, current.alliance.version),
        )
        assertIs<Affiliation.Petitioning>(applied.affiliation)

        val roster = assertIs<RosterRead.Present>(alliances.rosterOf(member, TEST_NOW))

        assertNull(roster.pending)
    }

    @Test
    fun `a founder account without a colony is refused a roster with no colony`() = runTest {
        assertIs<Founded.Made>(alliances.found(founder, AllianceName("Fleet"), AllianceTag("FLT"), TEST_NOW))

        val roster = alliances.rosterOf(founder, TEST_NOW)

        assertEquals(RosterRead.Refused(ApiError.NoColony), roster)
    }

    private companion object {

        @get:ClassRule
        @JvmStatic
        val postgres: SingleInstancePostgresRule = embeddedPostgres()
    }
}
