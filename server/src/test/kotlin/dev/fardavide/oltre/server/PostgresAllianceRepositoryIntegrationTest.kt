package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceSeats
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.JoinDecision
import dev.fardavide.oltre.protocol.JoinRequestId
import io.zonky.test.db.postgres.junit.SingleInstancePostgresRule
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.junit.ClassRule
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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

    // ── Search ──

    @Test
    fun `two server starts apply the name migration without deadlocking`() = runTest {
        database.connection.use { it.createStatement().use { statement -> statement.execute("DROP INDEX alliances_normalised_name") } }
        database.connection.use { blocker ->
            blocker.autoCommit = false
            blocker.createStatement().use { it.execute("LOCK TABLE alliance_requests IN ACCESS EXCLUSIVE MODE") }
            withContext(Dispatchers.IO) {
                val starts = List(2) { async { database.applySchema() } }
                try {
                    kotlinx.coroutines.withTimeout(10_000) {
                        while (checkNotNull(database.scalar("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock'")).toInt() < 2) {
                            kotlinx.coroutines.yield()
                        }
                    }
                } finally {
                    blocker.rollback()
                }
                starts.awaitAll()
            }
        }
    }

    @Test
    fun `a page counts three seats and publishes no commander name or account id`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, AllianceName("Fleet"), AllianceTag("FLT"), TEST_NOW))
        val people = listOf(founder, PlayerId("private-admin"), PlayerId("private-member"))
        for (player in people.drop(1)) {
            database.givenPlayer(player)
            val current = assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW))
            val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(repository.petition(player, made.alliance.alliance.id, TEST_NOW, current.alliance.version)).affiliation)
            repository.approve(founder, made.alliance.alliance.id, pending.petition.id, JoinDecision.ADMITTED, TEST_NOW, pending.alliance.version)
        }
        for (player in people) database.writeName(player, "Secret ${player.value}")

        val results = repository.search(CanonicalAllianceName("fleet"), null, 20)
        val response = dev.fardavide.oltre.protocol.AllianceSearchResponse(dev.fardavide.oltre.protocol.ApiVersion.CURRENT, "fleet", results.map { it.alliance }, null)
        val json = dev.fardavide.oltre.protocol.Protocol.json.encodeToString(response)

        assertEquals(AllianceSeats(3, 20), response.results.single().seats)
        for (player in people) {
            assertEquals(false, json.contains(player.value))
            assertEquals(false, json.contains("Secret ${player.value}"))
        }
        assertEquals(false, json.contains("treasury"))
        assertEquals(false, json.contains("members"))
    }

    @Test
    fun `Postgres and memory order supplementary characters after BMP characters across a cursor`() = runTest {
        val memory = InMemoryAllianceRepository(InMemoryColonyRepository())
        val names = listOf("Fleet \uD800\uDC00", "Fleet \uE000", "Fleet A")
        for ((index, name) in names.withIndex()) {
            val player = PlayerId("founder-$index")
            database.givenPlayer(player)
            repository.found(player, AllianceName(name), AllianceTag("F$index"), TEST_NOW)
            memory.found(player, AllianceName(name), AllianceTag("F$index"), TEST_NOW)
        }
        for (store in listOf(repository, memory)) {
            val first = store.search(CanonicalAllianceName("fleet"), null, 2)
            val second = store.search(CanonicalAllianceName("fleet"), AllianceSearchPosition.from(first.last()), 2)
            assertEquals(listOf("Fleet A", "Fleet \uE000", "Fleet \uD800\uDC00"), (first + second).map { it.alliance.name.value })
        }
    }

    @Test
    fun `a colliding backfill rolls back every name and identifies the rows to resolve`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, AllianceName("Ｖａｎｇｕａｒｄ"), AllianceTag("VNG"), TEST_NOW))
        database.connection.use { connection ->
            connection.prepareStatement("UPDATE alliances SET normalised_name = ? WHERE id = ?").use { statement ->
                statement.setString(1, "ｖａｎｇｕａｒｄ")
                statement.setString(2, made.alliance.alliance.id.value)
                statement.executeUpdate()
            }
        }
        val rival = PlayerId("rival")
        database.givenPlayer(rival)
        database.connection.use { connection ->
            connection.prepareStatement("INSERT INTO alliances (id, name, normalised_name, tag, normalised_tag, created_at, experience, version) VALUES ('ordinary', 'Vanguard', 'vanguard', 'RIV', 'riv', ?, 0, 1)").use { statement ->
                statement.setObject(1, TEST_NOW.atUtc())
                statement.executeUpdate()
            }
        }
        try {
            val failure = kotlin.test.assertFailsWith<IllegalStateException> { database.applySchema() }
            assertTrue(failure.message.orEmpty().contains(made.alliance.alliance.id.value))
            assertTrue(failure.message.orEmpty().contains("ordinary"))
            assertEquals("ｖａｎｇｕａｒｄ", database.scalar("SELECT normalised_name FROM alliances WHERE tag = 'VNG'"))
            assertEquals("vanguard", database.scalar("SELECT normalised_name FROM alliances WHERE id = 'ordinary'"))
        } finally {
            database.emptyEveryTable()
        }
    }

    @Test
    fun `the prefix index exists with C collation`() {
        database.connection.use { connection ->
            connection.prepareStatement("SELECT indexdef FROM pg_indexes WHERE tablename = 'alliances' AND indexname = 'alliances_normalised_name'").use { statement ->
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    assertTrue(rows.getString("indexdef").contains("COLLATE \"C\""))
                }
            }
        }
    }

    @Test
    fun `applying the schema backfills the old normalisation before uniqueness and search read it`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, AllianceName("Ｖａｎｇｕａｒｄ"), AllianceTag("VNG"), TEST_NOW))
        database.connection.use { connection ->
            connection.prepareStatement("UPDATE alliances SET normalised_name = ? WHERE id = ?").use { statement ->
                statement.setString(1, "ｖａｎｇｕａｒｄ")
                statement.setString(2, made.alliance.alliance.id.value)
                statement.executeUpdate()
            }
        }

        database.applySchema()

        assertEquals(listOf(made.alliance), repository.search(CanonicalAllianceName("vanguard"), null, 20))
        val rival = PlayerId("rival")
        database.givenPlayer(rival)
        assertEquals(Founded.Refused(ApiError.AllianceNameTaken), repository.found(rival, AllianceName("Vanguard"), AllianceTag("RIV"), TEST_NOW))
        database.applySchema()
        assertEquals(listOf(made.alliance), repository.search(CanonicalAllianceName("vanguard"), null, 20))
    }

    @Test
    fun `search matches wildcard characters as literal name prefixes`() = runTest {
        val names = listOf("Fleet % A", "Fleet _ B", "Fleet \\ C", "Fleet normal")
        val made = names.mapIndexed { index, name ->
            val player = PlayerId("founder-$index")
            database.givenPlayer(player)
            assertIs<Founded.Made>(repository.found(player, AllianceName(name), AllianceTag("F$index"), TEST_NOW)).alliance
        }

        for ((index, prefix) in listOf("fleet %", "fleet _", "fleet \\").withIndex()) {
            assertEquals(listOf(made[index]), repository.search(CanonicalAllianceName(prefix), null, 20))
        }
    }

    @Test
    fun `a keyset walk follows experience then C name order without repeating a row`() = runTest {
        val made = (25 downTo 1).map { number ->
            val player = PlayerId("founder-$number")
            database.givenPlayer(player)
            val stored = assertIs<Founded.Made>(repository.found(player, AllianceName("Fleet ${number.toString().padStart(2, '0')}"), AllianceTag("F$number"), TEST_NOW)).alliance
            database.connection.use { connection ->
                connection.prepareStatement("UPDATE alliances SET experience = ? WHERE id = ?").use { statement ->
                    statement.setLong(1, (number % 3).toLong())
                    statement.setString(2, stored.alliance.id.value)
                    statement.executeUpdate()
                }
            }
            stored.copy(experience = (number % 3).toLong())
        }.sortedBy { AllianceSearchPosition.from(it) }
        val query = CanonicalAllianceName("fleet")

        val first = repository.search(query, null, 20)
        val second = repository.search(query, AllianceSearchPosition.from(first.last()), 20)

        assertEquals(20, first.size)
        assertEquals(made, first + second)
    }

    @Test
    fun `search finds the same compatibility spelling that founding refuses`() = runTest {
        val rival = PlayerId("rival")
        database.givenPlayer(rival)
        val made = assertIs<Founded.Made>(repository.found(founder, AllianceName("Ｓｔｒａße  Fleet"), AllianceTag("VNG"), TEST_NOW))
        assertEquals(Founded.Refused(ApiError.AllianceNameTaken), repository.found(rival, AllianceName("STRASSE\tFLEET"), AllianceTag("RIV"), TEST_NOW))

        val results = repository.search(AllianceRules.normalise(AllianceName("strasse f")), null, 20)

        assertEquals(listOf(made.alliance), results)
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
    fun `petitioning persists its request and advances the parent version once`() = runTest {
        val petitioner = PlayerId("petitioner")
        database.givenPlayer(petitioner)
        val made = assertIs<Founded.Made>(repository.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))

        val changed = assertIs<AllianceChange.Applied>(repository.petition(petitioner, made.alliance.alliance.id, TEST_NOW, made.alliance.version))

        val pending = assertIs<Affiliation.Petitioning>(changed.affiliation)
        assertTrue(pending.petition.id.value.isNotBlank())
        assertNotEquals(petitioner.value, pending.petition.id.value)
        assertNotEquals(made.alliance.alliance.id.value, pending.petition.id.value)
        assertEquals(petitioner, pending.petition.player)
        assertEquals(made.alliance.alliance.id, pending.petition.alliance)
        assertEquals(TEST_NOW, pending.petition.requestedAt)
        assertEquals(made.alliance.version.next(), pending.alliance.version)
        val reopened = PostgresAllianceRepository(database)
        assertEquals(pending, reopened.allianceOf(petitioner, TEST_NOW))
        val enlisted = assertIs<Affiliation.Enlisted>(reopened.allianceOf(founder, TEST_NOW))
        assertEquals(made.alliance.version.next(), enlisted.alliance.version)
    }

    @Test
    fun `admitting a pending player persists their member seat and removes their petition`() = runTest {
        val applicant = PlayerId("applicant")
        database.givenPlayer(applicant)
        val made = assertIs<Founded.Made>(repository.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val petitioned = assertIs<AllianceChange.Applied>(repository.petition(applicant, made.alliance.alliance.id, TEST_NOW, made.alliance.version))
        val pending = assertIs<Affiliation.Petitioning>(petitioned.affiliation)

        val changed = assertIs<AllianceChange.Applied>(repository.approve(
            founder,
            made.alliance.alliance.id,
            pending.petition.id,
            JoinDecision.ADMITTED,
            TEST_NOW,
            pending.alliance.version,
        ))

        val caller = assertIs<Affiliation.Enlisted>(changed.affiliation)
        assertEquals(founder, caller.seat.player)
        assertEquals(AllianceRole.FOUNDER, caller.seat.role)
        assertEquals(pending.alliance.version.next(), caller.alliance.version)
        val reopened = PostgresAllianceRepository(database)
        val admitted = assertIs<Affiliation.Enlisted>(reopened.allianceOf(applicant, TEST_NOW))
        assertEquals(applicant, admitted.seat.player)
        assertEquals(AllianceRole.MEMBER, admitted.seat.role)
        assertEquals(made.alliance.alliance.id, admitted.seat.alliance)
        assertEquals(AllianceSeats(2, 20), admitted.alliance.alliance.seats)
        assertEquals(pending.alliance.version.next(), admitted.alliance.version)
        assertEquals(caller, reopened.allianceOf(founder, TEST_NOW))
        assertEquals(2, database.rowsIn("alliance_members"))
        assertEquals(0, database.rowsIn("alliance_requests"))
    }

    @Test
    fun `admitting a petitioner who already holds a seat refuses and preserves their request and seat`() = runTest {
        val applicant = PlayerId("applicant")
        val rival = PlayerId("rival")
        database.givenPlayer(applicant)
        database.givenPlayer(rival)
        val made = assertIs<Founded.Made>(repository.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val other = assertIs<Founded.Made>(repository.found(rival, AllianceName("Horizon"), AllianceTag("HRZ"), TEST_NOW))
        val petitioned = assertIs<AllianceChange.Applied>(repository.petition(applicant, made.alliance.alliance.id, TEST_NOW, made.alliance.version))
        val pending = assertIs<Affiliation.Petitioning>(petitioned.affiliation)
        database.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO alliance_members (id, player_id, alliance_id, role, joined_at, contributed) VALUES (?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, "applicant-seat")
                statement.setString(2, applicant.value)
                statement.setString(3, other.alliance.alliance.id.value)
                statement.setString(4, AllianceRole.MEMBER.name)
                statement.setObject(5, TEST_NOW.atUtc())
                statement.setLong(6, 0)
                statement.executeUpdate()
            }
        }
        val seated = assertIs<Affiliation.Enlisted>(repository.allianceOf(applicant, TEST_NOW))

        val refused = assertIs<AllianceChange.Refused>(repository.approve(
            founder,
            made.alliance.alliance.id,
            pending.petition.id,
            JoinDecision.ADMITTED,
            TEST_NOW,
            pending.alliance.version,
        ))

        assertEquals(ApiError.AlreadyInAnAlliance, refused.error)
        val reopened = PostgresAllianceRepository(database)
        assertEquals(seated, reopened.allianceOf(applicant, TEST_NOW))
        val target = assertIs<AllianceLookup.Present>(reopened.alliance(made.alliance.alliance.id, TEST_NOW))
        assertEquals(pending.alliance, target.alliance)
        database.connection.use { connection ->
            connection.prepareStatement(
                "SELECT id, alliance_id, requested_at FROM alliance_requests WHERE player_id = ?",
            ).use { statement ->
                statement.setString(1, applicant.value)
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    assertEquals(pending.petition.id.value, rows.getString("id"))
                    assertEquals(pending.petition.alliance.value, rows.getString("alliance_id"))
                    assertEquals(TEST_NOW.atUtc(), rows.getObject("requested_at", java.time.OffsetDateTime::class.java))
                    assertEquals(false, rows.next())
                }
            }
        }
        assertEquals(1, database.rowsIn("alliance_requests"))
        assertEquals(3, database.rowsIn("alliance_members"))
    }

    @Test
    fun `competing admissions fill the final seat once and preserve the losing petition`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        for (number in 1..18) {
            val player = PlayerId("member-$number")
            database.givenPlayer(player)
            val current = assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW))
            val changed = assertIs<AllianceChange.Applied>(repository.petition(player, made.alliance.alliance.id, TEST_NOW, current.alliance.version))
            val pending = assertIs<Affiliation.Petitioning>(changed.affiliation)
            assertIs<AllianceChange.Applied>(repository.approve(founder, made.alliance.alliance.id, pending.petition.id, JoinDecision.ADMITTED, TEST_NOW, pending.alliance.version))
        }
        val requests = mutableListOf<Petition>()
        for (player in listOf(PlayerId("first-applicant"), PlayerId("second-applicant"))) {
            database.givenPlayer(player)
            val current = assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW))
            val changed = assertIs<AllianceChange.Applied>(repository.petition(player, made.alliance.alliance.id, TEST_NOW, current.alliance.version))
            requests += assertIs<Affiliation.Petitioning>(changed.affiliation).petition
        }
        val before = assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW))
        assertEquals(AllianceSeats(19, 20), before.alliance.alliance.seats)
        val atCall = CyclicBarrier(2)

        val results = withContext(Dispatchers.IO) {
            requests.map { request ->
                async {
                    atCall.await(10, TimeUnit.SECONDS)
                    request to repository.approve(founder, made.alliance.alliance.id, request.id, JoinDecision.ADMITTED, TEST_NOW, before.alliance.version)
                }
            }.awaitAll()
        }

        assertEquals(1, results.count { it.second is AllianceChange.Applied })
        assertEquals(1, results.count { it.second == AllianceChange.Stale })
        val winner = results.single { it.second is AllianceChange.Applied }.first
        val loser = results.single { it.second == AllianceChange.Stale }.first
        val reopened = PostgresAllianceRepository(database)
        val full = assertIs<Affiliation.Enlisted>(reopened.allianceOf(founder, TEST_NOW))
        assertEquals(AllianceSeats(20, 20), full.alliance.alliance.seats)
        assertEquals(before.alliance.version.next(), full.alliance.version)
        assertEquals(AllianceRole.MEMBER, assertIs<Affiliation.Enlisted>(reopened.allianceOf(winner.player, TEST_NOW)).seat.role)
        val pending = assertIs<Affiliation.Petitioning>(reopened.allianceOf(loser.player, TEST_NOW))
        assertEquals(loser, pending.petition)

        val retried = reopened.approve(founder, made.alliance.alliance.id, loser.id, JoinDecision.ADMITTED, TEST_NOW, full.alliance.version)

        assertEquals(AllianceChange.Refused(ApiError.AllianceFull), retried)
        assertEquals(pending, reopened.allianceOf(loser.player, TEST_NOW))
        assertEquals(full, reopened.allianceOf(founder, TEST_NOW))
        assertEquals(20, database.rowsIn("alliance_members"))
        assertEquals(1, database.rowsIn("alliance_requests"))
    }

    @Test
    fun `changing an admitted member to admin persists their existing seat and advances the version once`() = runTest {
        val applicant = PlayerId("applicant")
        database.givenPlayer(applicant)
        val made = assertIs<Founded.Made>(repository.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val petitioned = assertIs<AllianceChange.Applied>(repository.petition(applicant, made.alliance.alliance.id, TEST_NOW, made.alliance.version))
        val pending = assertIs<Affiliation.Petitioning>(petitioned.affiliation)
        assertIs<AllianceChange.Applied>(repository.approve(
            founder,
            made.alliance.alliance.id,
            pending.petition.id,
            JoinDecision.ADMITTED,
            TEST_NOW,
            pending.alliance.version,
        ))
        val admitted = assertIs<Affiliation.Enlisted>(repository.allianceOf(applicant, TEST_NOW))

        val changed = assertIs<AllianceChange.Applied>(repository.setRole(
            founder,
            admitted.alliance.alliance.id,
            admitted.seat.id,
            AllianceRole.ADMIN,
            admitted.alliance.version,
        ))

        val caller = assertIs<Affiliation.Enlisted>(changed.affiliation)
        val reopened = PostgresAllianceRepository(database)
        val promoted = assertIs<Affiliation.Enlisted>(reopened.allianceOf(applicant, TEST_NOW))
        assertEquals(AllianceRole.ADMIN, promoted.seat.role)
        assertEquals(admitted.seat.id, promoted.seat.id)
        assertEquals(admitted.seat.player, promoted.seat.player)
        assertEquals(admitted.seat.alliance, promoted.seat.alliance)
        assertEquals(admitted.seat.joinedAt, promoted.seat.joinedAt)
        assertEquals(admitted.seat.contributed, promoted.seat.contributed)
        assertEquals(AllianceSeats(2, 20), promoted.alliance.alliance.seats)
        assertEquals(admitted.alliance.version.next(), promoted.alliance.version)
        assertEquals(promoted.alliance.version, caller.alliance.version)
        assertEquals(caller, reopened.allianceOf(founder, TEST_NOW))
        assertEquals(2, database.rowsIn("alliance_members"))
    }

    @Test
    fun `removing an admitted member persists their departure and advances the version once`() = runTest {
        val applicant = PlayerId("applicant")
        database.givenPlayer(applicant)
        val made = assertIs<Founded.Made>(repository.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val petitioned = assertIs<AllianceChange.Applied>(repository.petition(applicant, made.alliance.alliance.id, TEST_NOW, made.alliance.version))
        val pending = assertIs<Affiliation.Petitioning>(petitioned.affiliation)
        assertIs<AllianceChange.Applied>(repository.approve(
            founder,
            made.alliance.alliance.id,
            pending.petition.id,
            JoinDecision.ADMITTED,
            TEST_NOW,
            pending.alliance.version,
        ))
        val admitted = assertIs<Affiliation.Enlisted>(repository.allianceOf(applicant, TEST_NOW))

        val changed = assertIs<AllianceChange.Applied>(repository.kick(
            founder,
            admitted.alliance.alliance.id,
            admitted.seat.id,
            admitted.alliance.version,
        ))

        val caller = assertIs<Affiliation.Enlisted>(changed.affiliation)
        val reopened = PostgresAllianceRepository(database)
        assertEquals(Affiliation.Unaffiliated, reopened.allianceOf(applicant, TEST_NOW))
        val remaining = assertIs<Affiliation.Enlisted>(reopened.allianceOf(founder, TEST_NOW))
        assertEquals(founder, remaining.seat.player)
        assertEquals(AllianceRole.FOUNDER, remaining.seat.role)
        assertEquals(made.alliance.alliance.id, remaining.alliance.alliance.id)
        assertEquals(AllianceSeats(1, 20), remaining.alliance.alliance.seats)
        assertEquals(admitted.alliance.version.next(), remaining.alliance.version)
        assertEquals(caller, remaining)
        assertEquals(1, database.rowsIn("alliance_members"))
    }

    @Test
    fun `renaming persists the name and tag together and advances the version once`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val original = assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW))
        val name = AllianceName("Horizon")
        val tag = AllianceTag("HRZ")

        val changed = assertIs<AllianceChange.Applied>(repository.rename(
            founder,
            made.alliance.alliance.id,
            name,
            tag,
            made.alliance.version,
        ))

        val caller = assertIs<Affiliation.Enlisted>(changed.affiliation)
        val reopened = PostgresAllianceRepository(database)
        val renamed = assertIs<Affiliation.Enlisted>(reopened.allianceOf(founder, TEST_NOW))
        assertEquals(name, renamed.alliance.alliance.name)
        assertEquals(tag, renamed.alliance.alliance.tag)
        assertEquals(made.alliance.alliance.id, renamed.alliance.alliance.id)
        assertEquals(original.seat, renamed.seat)
        assertEquals(AllianceRole.FOUNDER, renamed.seat.role)
        assertEquals(made.alliance.alliance.seats, renamed.alliance.alliance.seats)
        assertEquals(made.alliance.version.next(), renamed.alliance.version)
        assertEquals(caller, renamed)
    }

    @Test
    fun `simultaneous renames to the same name refuse one change and preserve the losing alliance`() = runTest {
        val rival = PlayerId("rival")
        database.givenPlayer(rival)
        repository.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW)
        repository.found(rival, AllianceName("Horizon"), AllianceTag("HRZ"), TEST_NOW)
        val first = assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW))
        val second = assertIs<Affiliation.Enlisted>(repository.allianceOf(rival, TEST_NOW))
        val atWrite = CyclicBarrier(2)
        val coordinated = object : DataSource by database {
            override fun getConnection(): Connection {
                val connection = database.connection
                return object : Connection by connection {
                    override fun prepareStatement(sql: String): PreparedStatement {
                        val statement = connection.prepareStatement(sql)
                        return if (sql.trimStart().startsWith("UPDATE alliances SET name")) {
                            object : PreparedStatement by statement {
                                override fun executeUpdate(): Int {
                                    atWrite.await(10, TimeUnit.SECONDS)
                                    return statement.executeUpdate()
                                }
                            }
                        } else {
                            statement
                        }
                    }
                }
            }
        }
        val contended = PostgresAllianceRepository(coordinated)
        val name = AllianceName("Unity")

        val results = withContext(Dispatchers.IO) {
            listOf(first to AllianceTag("NVG"), second to AllianceTag("NHR"))
                .map { request: Pair<Affiliation.Enlisted, AllianceTag> ->
                    async {
                        val original = request.first
                        original to contended.rename(
                            original.seat.player,
                            original.alliance.alliance.id,
                            name,
                            request.second,
                            original.alliance.version,
                        )
                    }
                }.awaitAll()
        }

        val changes = results.map { attempt: Pair<Affiliation.Enlisted, AllianceChange> -> attempt.second }
        val applied = changes.filterIsInstance<AllianceChange.Applied>().single()
        assertEquals(ApiError.AllianceNameTaken, changes.filterIsInstance<AllianceChange.Refused>().single().error)
        val winner = assertIs<Affiliation.Enlisted>(applied.affiliation)
        assertEquals(name, winner.alliance.alliance.name)
        val loser = results.single { attempt: Pair<Affiliation.Enlisted, AllianceChange> ->
            attempt.second is AllianceChange.Refused
        }.first
        val reopened = PostgresAllianceRepository(database)
        assertEquals(loser, reopened.allianceOf(loser.seat.player, TEST_NOW))
        assertEquals(winner, reopened.allianceOf(winner.seat.player, TEST_NOW))
        assertEquals(2, database.rowsIn("alliances"))
        assertEquals(2, database.rowsIn("alliance_members"))
    }

    @Test
    fun `disbanding removes the alliance with every member seat and pending petition`() = runTest {
        val member = PlayerId("member")
        val petitioner = PlayerId("petitioner")
        database.givenPlayer(member)
        database.givenPlayer(petitioner)
        val made = assertIs<Founded.Made>(repository.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val requested = assertIs<AllianceChange.Applied>(repository.petition(member, made.alliance.alliance.id, TEST_NOW, made.alliance.version))
        val membershipRequest = assertIs<Affiliation.Petitioning>(requested.affiliation)
        val approved = assertIs<AllianceChange.Applied>(repository.approve(
            founder,
            made.alliance.alliance.id,
            membershipRequest.petition.id,
            JoinDecision.ADMITTED,
            TEST_NOW,
            membershipRequest.alliance.version,
        ))
        val caller = assertIs<Affiliation.Enlisted>(approved.affiliation)
        val petitioned = assertIs<AllianceChange.Applied>(repository.petition(petitioner, made.alliance.alliance.id, TEST_NOW, caller.alliance.version))
        val pending = assertIs<Affiliation.Petitioning>(petitioned.affiliation)

        val changed = assertIs<AllianceChange.Applied>(repository.disband(
            founder,
            made.alliance.alliance.id,
            pending.alliance.version,
        ))

        assertEquals(Affiliation.Unaffiliated, changed.affiliation)
        val reopened = PostgresAllianceRepository(database)
        assertEquals(Affiliation.Unaffiliated, reopened.allianceOf(founder, TEST_NOW))
        assertEquals(Affiliation.Unaffiliated, reopened.allianceOf(member, TEST_NOW))
        assertEquals(Affiliation.Unaffiliated, reopened.allianceOf(petitioner, TEST_NOW))
        assertEquals(0, database.rowsIn("alliances"))
        assertEquals(0, database.rowsIn("alliance_members"))
        assertEquals(0, database.rowsIn("alliance_requests"))
    }

    @Test
    fun `every mutation refuses an absent alliance without creating alliance rows`() = runTest {
        val missing = AllianceId("missing")
        val request = JoinRequestId("missing-request")
        val member = AllianceMemberId("missing-member")
        val mutations: List<suspend () -> AllianceChange> = listOf(
            { repository.petition(founder, missing, TEST_NOW, AllianceVersion.FIRST) },
            { repository.detach(founder, missing, AllianceVersion.FIRST) },
            { repository.approve(founder, missing, request, JoinDecision.ADMITTED, TEST_NOW, AllianceVersion.FIRST) },
            { repository.setRole(founder, missing, member, AllianceRole.ADMIN, AllianceVersion.FIRST) },
            { repository.kick(founder, missing, member, AllianceVersion.FIRST) },
            { repository.rename(founder, missing, AllianceName("Vanguard"), AllianceTag("VNG"), AllianceVersion.FIRST) },
            { repository.disband(founder, missing, AllianceVersion.FIRST) },
        )

        for (mutate in mutations) {
            assertEquals(AllianceChange.Refused(ApiError.NoSuchAlliance), mutate())
            assertEquals(0, database.rowsIn("alliances"))
            assertEquals(0, database.rowsIn("alliance_members"))
            assertEquals(0, database.rowsIn("alliance_requests"))
        }
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

    @Test
    fun `simultaneous founders contending for a name create one alliance and receive one refusal`() = runTest {
        val rival = PlayerId("rival")
        database.givenPlayer(rival)
        val afterRead = CyclicBarrier(2)
        val contended = PostgresAllianceRepository(database, AllianceIds {
            afterRead.await(10, TimeUnit.SECONDS)
            AllianceId(UUID.randomUUID().toString())
        })

        val results = withContext(Dispatchers.IO) {
            listOf(founder to AllianceTag("VNG"), rival to AllianceTag("RIV"))
                .map { (player, tag) -> async { contended.found(player, AllianceName("Vanguard"), tag, TEST_NOW) } }
                .awaitAll()
        }

        assertEquals(1, results.filterIsInstance<Founded.Made>().size)
        assertEquals(ApiError.AllianceNameTaken, results.filterIsInstance<Founded.Refused>().single().error)
        assertEquals(1, database.rowsIn("alliances"))
        assertEquals(1, database.rowsIn("alliance_members"))
    }

    @Test
    fun `one player founding different alliances simultaneously creates one alliance and receives one refusal`() = runTest {
        val afterRead = CyclicBarrier(2)
        val contended = PostgresAllianceRepository(database, AllianceIds {
            afterRead.await(10, TimeUnit.SECONDS)
            AllianceId(UUID.randomUUID().toString())
        })

        val results = withContext(Dispatchers.IO) {
            listOf(AllianceName("Vanguard") to AllianceTag("VNG"), AllianceName("Horizon") to AllianceTag("HRZ"))
                .map { (name, tag) -> async { contended.found(founder, name, tag, TEST_NOW) } }
                .awaitAll()
        }

        assertEquals(1, results.filterIsInstance<Founded.Made>().size)
        assertEquals(ApiError.AlreadyInAnAlliance, results.filterIsInstance<Founded.Refused>().single().error)
        assertEquals(1, database.rowsIn("alliances"))
        assertEquals(1, database.rowsIn("alliance_members"))
    }

    @Test
    fun `one player petitioning different alliances simultaneously persists one request and receives one refusal`() = runTest {
        val rival = PlayerId("rival")
        database.givenPlayer(rival)
        val first = assertIs<Founded.Made>(repository.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val second = assertIs<Founded.Made>(repository.found(rival, AllianceName("Horizon"), AllianceTag("HRZ"), TEST_NOW))
        val alliances = listOf(first.alliance.alliance.id, second.alliance.alliance.id)

        repeat(10) { index: Int ->
            val applicant = PlayerId("applicant-$index")
            database.givenPlayer(applicant)
            val parents = alliances.map { id: AllianceId ->
                assertIs<AllianceLookup.Present>(repository.alliance(id, TEST_NOW)).alliance
            }
            val atCall = CyclicBarrier(2)

            val results = withContext(Dispatchers.IO) {
                parents.map { parent: StoredAlliance ->
                    async {
                        atCall.await(10, TimeUnit.SECONDS)
                        repository.petition(applicant, parent.alliance.id, TEST_NOW, parent.version)
                    }
                }.awaitAll()
            }

            val applied = results.filterIsInstance<AllianceChange.Applied>().single()
            val pending = assertIs<Affiliation.Petitioning>(applied.affiliation)
            assertEquals(ApiError.AlreadyInAnAlliance, results.filterIsInstance<AllianceChange.Refused>().single().error)
            assertEquals(applicant, pending.petition.player)
            assertEquals(pending, PostgresAllianceRepository(database).allianceOf(applicant, TEST_NOW))
            assertEquals(index + 1, database.rowsIn("alliance_requests"))
        }

        assertEquals(2, database.rowsIn("alliances"))
        assertEquals(2, database.rowsIn("alliance_members"))
    }

    @Test
    fun `deleting the founder leaves the alliance and promotes an active admin on the next read`() = runTest {
        val admin = PlayerId("admin")
        val member = PlayerId("member")
        database.givenPlayer(admin)
        database.givenPlayer(member)
        val made = assertIs<Founded.Made>(repository.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val colonies = PostgresColonyRepository(database, MovableClock(TEST_NOW))
        colonies.found(founder, freshColony())
        colonies.found(admin, freshColony())
        colonies.found(member, freshColony())
        database.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO alliance_members (id, player_id, alliance_id, role, joined_at, contributed) VALUES (?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                for ((player, role) in listOf(admin to AllianceRole.ADMIN, member to AllianceRole.MEMBER)) {
                    statement.setString(1, "${player.value}-seat")
                    statement.setString(2, player.value)
                    statement.setString(3, made.alliance.alliance.id.value)
                    statement.setString(4, role.name)
                    statement.setObject(5, TEST_NOW.atUtc())
                    statement.setLong(6, 0)
                    statement.executeUpdate()
                }
            }
        }

        PostgresPlayerRepository(database, MovableClock(TEST_NOW)).forget(founder)
        val standing = assertIs<Affiliation.Enlisted>(repository.allianceOf(admin, TEST_NOW))

        assertEquals(AllianceRole.FOUNDER, standing.seat.role)
        assertEquals(AllianceSeats(2, 20), standing.alliance.alliance.seats)
        assertEquals(made.alliance.version.next(), standing.alliance.version)
        assertEquals(2, database.rowsIn("colonies"))
        assertEquals(2, database.rowsIn("alliance_members"))
        assertEquals(1, database.rowsIn("alliances"))
        assertEquals(standing, repository.allianceOf(admin, TEST_NOW))
    }

    @Test
    fun `looking up the parent alliance persists founder succession and advances the version once`() = runTest {
        val admin = PlayerId("admin")
        val member = PlayerId("member")
        database.givenPlayer(admin)
        database.givenPlayer(member)
        val made = assertIs<Founded.Made>(repository.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val colonies = PostgresColonyRepository(database, MovableClock(TEST_NOW))
        colonies.found(founder, freshColony())
        colonies.found(admin, freshColony())
        colonies.found(member, freshColony())
        database.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO alliance_members (id, player_id, alliance_id, role, joined_at, contributed) VALUES (?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                for ((player, role) in listOf(admin to AllianceRole.ADMIN, member to AllianceRole.MEMBER)) {
                    statement.setString(1, "${player.value}-seat")
                    statement.setString(2, player.value)
                    statement.setString(3, made.alliance.alliance.id.value)
                    statement.setString(4, role.name)
                    statement.setObject(5, TEST_NOW.atUtc())
                    statement.setLong(6, 0)
                    statement.executeUpdate()
                }
            }
        }
        PostgresPlayerRepository(database, MovableClock(TEST_NOW)).forget(founder)

        val lookedUp = assertIs<AllianceLookup.Present>(repository.alliance(made.alliance.alliance.id, TEST_NOW))

        assertEquals(made.alliance.version.next(), lookedUp.alliance.version)
        assertEquals(AllianceSeats(2, 20), lookedUp.alliance.alliance.seats)
        val reopened = PostgresAllianceRepository(database)
        assertEquals(lookedUp, reopened.alliance(made.alliance.alliance.id, TEST_NOW))
        val promoted = assertIs<Affiliation.Enlisted>(reopened.allianceOf(admin, TEST_NOW))
        assertEquals(AllianceRole.FOUNDER, promoted.seat.role)
        assertEquals(lookedUp.alliance, promoted.alliance)
        assertEquals(promoted, reopened.allianceOf(admin, TEST_NOW))
        assertEquals(2, database.rowsIn("alliance_members"))
        assertEquals(1, database.rowsIn("alliances"))
    }

    @Test
    fun `reading a pending applicant persists founder succession and advances the parent version once`() = runTest {
        val admin = PlayerId("admin")
        val applicant = PlayerId("applicant")
        database.givenPlayer(admin)
        database.givenPlayer(applicant)
        val made = assertIs<Founded.Made>(repository.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val colonies = PostgresColonyRepository(database, MovableClock(TEST_NOW))
        colonies.found(founder, freshColony())
        colonies.found(admin, freshColony())
        database.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO alliance_members (id, player_id, alliance_id, role, joined_at, contributed) VALUES (?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, "admin-seat")
                statement.setString(2, admin.value)
                statement.setString(3, made.alliance.alliance.id.value)
                statement.setString(4, AllianceRole.ADMIN.name)
                statement.setObject(5, TEST_NOW.atUtc())
                statement.setLong(6, 0)
                statement.executeUpdate()
            }
        }
        val petitioned = assertIs<AllianceChange.Applied>(repository.petition(applicant, made.alliance.alliance.id, TEST_NOW, made.alliance.version))
        val pending = assertIs<Affiliation.Petitioning>(petitioned.affiliation)
        PostgresPlayerRepository(database, MovableClock(TEST_NOW)).forget(founder)

        val refreshed = assertIs<Affiliation.Petitioning>(repository.allianceOf(applicant, TEST_NOW))

        assertEquals(pending.alliance.version.next(), refreshed.alliance.version)
        assertEquals(pending.petition, refreshed.petition)
        assertEquals(AllianceSeats(1, 20), refreshed.alliance.alliance.seats)
        val reopened = PostgresAllianceRepository(database)
        assertEquals(refreshed, reopened.allianceOf(applicant, TEST_NOW))
        val promoted = assertIs<Affiliation.Enlisted>(reopened.allianceOf(admin, TEST_NOW))
        assertEquals(AllianceRole.FOUNDER, promoted.seat.role)
        assertEquals(refreshed.alliance, promoted.alliance)
        assertEquals(1, database.rowsIn("alliance_members"))
        assertEquals(1, database.rowsIn("alliance_requests"))
        assertEquals(1, database.rowsIn("alliances"))
    }

    @Test
    fun `founding reaps an empty alliance holding the requested name and tag`() = runTest {
        val rival = PlayerId("rival")
        database.givenPlayer(rival)
        val old = assertIs<Founded.Made>(repository.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        PostgresPlayerRepository(database, MovableClock(TEST_NOW)).forget(founder)

        val replacement = assertIs<Founded.Made>(repository.found(rival, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))

        kotlin.test.assertNotEquals(old.alliance.alliance.id, replacement.alliance.alliance.id)
        assertEquals(1, database.rowsIn("alliances"))
        assertEquals(1, database.rowsIn("alliance_members"))
    }

    private companion object {

        @get:ClassRule
        @JvmStatic
        val postgres: SingleInstancePostgresRule = embeddedPostgres()
    }
}
