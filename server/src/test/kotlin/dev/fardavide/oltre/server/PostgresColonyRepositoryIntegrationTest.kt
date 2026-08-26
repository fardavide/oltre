package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GameSave
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.IdempotencyKey
import io.zonky.test.db.postgres.junit.SingleInstancePostgresRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.ClassRule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

// **The same four questions `InMemoryColonyRepositoryTest` asks the map, asked of three tables.**
// The pair is the point: the unit suite above this stands on a fake, and a fake that answers
// differently from the store it doubles makes every test above it a lie. So the cases are
// deliberately parallel, and where this file has more it is because SQL can be wrong in ways a
// `mutableMapOf` cannot — a column type, a conflict clause, a `WHERE` that matches nothing.
class PostgresColonyRepositoryIntegrationTest {

    private val database = postgres.embeddedPostgres.postgresDatabase
    private val clock = MovableClock(TEST_NOW)
    private val repository = PostgresColonyRepository(database, clock)
    private val davide = PlayerId("davide")
    private val someoneElse = PlayerId("someone-else")

    // The schema is applied here rather than once for the class, because one test drops it — and a
    // deployed server applies it on every start too, so this is the shape the real thing has.
    @BeforeTest
    fun anEmptyColonyStore() {
        database.applySchema()
        database.emptyEveryTable()
        // **`#110`'s one change to this suite.** `colonies.player_id` is a foreign key and `found`
        // no longer forges the row it points at — identity does, at sign-in — so the two players
        // this file uses have to exist before a colony can hang off either of them. That is the
        // shape a deployed server has too: nobody has a colony who has not signed in.
        database.givenPlayer(davide)
        database.givenPlayer(someoneElse)
    }

    // ── The schema ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the schema applies to an empty database and again to one that already has it`() {
        // The second half is the one that matters and the one nothing else would notice: Cloud Run
        // starts this process again on every scale-up from zero, so a DDL that failed the second
        // time would take the service down on its second cold start rather than on its first.
        database.dropEveryTable()

        database.applySchema()
        database.applySchema()

        assertEquals(listOf("applied_verbs", "colonies", "players"), database.tableNames())
    }

    // ── Founding ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `founding a colony stores it under the player who founded it`() = runTest {
        val colony = freshColony()

        val founding = repository.found(davide, colony)

        assertEquals(Founding.Founded(StoredColony(colony, ColonyVersion.FIRST)), founding)
        assertEquals(colony, repository.colonyOf(davide)?.snapshot)
        assertNull(repository.colonyOf(someoneElse))
    }

    @Test
    fun `founding twice hands back the colony already there rather than a second galaxy`() = runTest {
        val first = freshColony(seed = 1)
        repository.found(davide, first)

        val founding = repository.found(davide, freshColony(seed = 2))

        assertEquals(Founding.AlreadyThere(StoredColony(first, ColonyVersion.FIRST)), founding)
        assertEquals(first, repository.colonyOf(davide)?.snapshot)
    }

    @Test
    fun `founding writes no player row of its own`() = runTest {
        // This asserted the opposite until `#110`, and the inversion is the slice. `found` forged a
        // player row from the header value because there was nothing else to hang the foreign key
        // off; now `PostgresPlayerRepository.resolve` writes it at sign-in, which is the only moment
        // anybody has actually said who they are. What guarantees the row is there by the time this
        // runs is the authenticator — see `PostgresPlayerRepositoryIntegrationTest`.
        repository.found(davide, freshColony())

        assertEquals(
            listOf("header" to "davide", "header" to "someone-else"),
            database.playerIdentities(),
        )
    }

    // ── The colony itself ─────────────────────────────────────────────────────────────────────

    @Test
    fun `a colony that has been played round-trips through snapshot_json unchanged`() = runTest {
        // Not a fresh colony — a fresh one is mostly defaults, and a codec that dropped a field
        // would still look right. This one has stocks, a facility, a build in flight and an event
        // log, which is the shape a colony has by the time losing it would matter.
        val played = replay(
            colony = establishedColony(),
            envelopes = listOf(envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW)),
            alreadyApplied = emptySet(),
            serverNow = TEST_NOW + 6.hours,
        ).snapshot
        val founded = repository.found(davide, freshColony()).colony
        repository.write(davide, played, applied = emptySet(), expected = founded.version)

        assertEquals(played, repository.colonyOf(davide)?.snapshot)
    }

    @Test
    fun `the colony is stored as a document Postgres can read into rather than as text`() = runTest {
        // `jsonb` and not `text`, which `#106` §6's "the same code modulo a driver" would have
        // ruled out — Davide's call on 2026-08-25 was to use Postgres properly and correct the
        // claim. This is the assertion that says the column really is one: a `->>` on a `text`
        // column is an error, not an answer.
        repository.found(davide, freshColony())

        assertEquals(
            GameSave.SCHEMA_VERSION.toString(),
            database.scalar("SELECT snapshot_json ->> 'schemaVersion' FROM colonies"),
        )
    }

    @Test
    fun `writing replaces the colony and moves it on to the next version`() = runTest {
        val founded = repository.found(davide, freshColony()).colony
        val advanced = freshColony(at = TEST_NOW + 2.hours)

        val written = repository.write(davide, advanced, applied = emptySet(), expected = founded.version)

        assertEquals(WriteResult.WRITTEN, written)
        assertEquals(StoredColony(advanced, founded.version.next()), repository.colonyOf(davide))
    }

    // ── The compare-and-set ───────────────────────────────────────────────────────────────────

    @Test
    fun `two writers hold one colony, one wins and the loser is told rather than obeyed`() = runTest {
        // **The reason this slice exists.** Both devices read the colony at the same version; the
        // second write asserts a version the row has moved past, updates no row, and says so. What
        // it must never do is succeed, because succeeding means the winner's work is gone and
        // nothing anywhere knows.
        val read = repository.found(davide, freshColony(seed = 1)).colony
        val winner = freshColony(at = TEST_NOW + 1.hours, seed = 2)
        val loser = freshColony(at = TEST_NOW + 9.hours, seed = 3)

        val first = repository.write(davide, winner, applied = setOf(IdempotencyKey("won")), expected = read.version)
        val second = repository.write(davide, loser, applied = setOf(IdempotencyKey("lost")), expected = read.version)

        assertEquals(WriteResult.WRITTEN, first)
        assertEquals(WriteResult.STALE, second)
        assertEquals(winner, repository.colonyOf(davide)?.snapshot)
        // And the loser's key did not land either — the colony and its keys are one transaction, so
        // a verb the player paid for and did not get is not a state this store can reach.
        assertEquals(setOf(IdempotencyKey("won")), repository.appliedAmong(davide, ALL_THREE_KEYS))
    }

    @Test
    fun `two writers racing on one row still leave exactly one winner`() = runTest {
        // The test above is sequential and proves the `WHERE version = ?`. This one proves the part
        // the clause alone does not: that the *database* is doing the exclusion. Under READ
        // COMMITTED the second `UPDATE` blocks on the first one's row lock and then re-evaluates its
        // `WHERE` against the committed row, so it finds a version that has moved and matches
        // nothing. Which of the two wins is a race; that exactly one does is not.
        val read = repository.found(davide, freshColony()).colony

        val outcomes = withContext(Dispatchers.IO) {
            (1..2).map { attempt ->
                async {
                    repository.write(
                        davide,
                        freshColony(at = TEST_NOW + attempt.hours),
                        applied = setOf(IdempotencyKey("racer-$attempt")),
                        expected = read.version,
                    )
                }
            }.awaitAll()
        }

        assertEquals(listOf(WriteResult.WRITTEN, WriteResult.STALE), outcomes.sorted())
        assertEquals(read.version.next(), repository.colonyOf(davide)?.version)
    }

    @Test
    fun `a write against a colony nobody founded is stale rather than a colony out of nowhere`() = runTest {
        val written = repository.write(davide, freshColony(), applied = emptySet(), expected = ColonyVersion.FIRST)

        assertEquals(WriteResult.STALE, written)
        assertNull(repository.colonyOf(davide))
    }

    // ── Applied verbs ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a key that was applied is still refused after the colony has moved on`() = runTest {
        val mine = IdempotencyKey("mine")
        val founded = repository.found(davide, freshColony()).colony
        repository.write(davide, freshColony(), applied = setOf(mine), expected = founded.version)

        repository.write(davide, freshColony(at = TEST_NOW + 3.days), applied = emptySet(), expected = founded.version.next())

        assertEquals(setOf(mine), repository.appliedAmong(davide, setOf(mine)))
    }

    @Test
    fun `only the keys asked about come back`() = runTest {
        val founded = repository.found(davide, freshColony()).colony
        repository.write(davide, freshColony(), applied = setOf(IdempotencyKey("won")), expected = founded.version)

        assertEquals(setOf(IdempotencyKey("won")), repository.appliedAmong(davide, ALL_THREE_KEYS))
    }

    @Test
    fun `asking about no keys at all asks the database nothing`() = runTest {
        // The empty outbox is the ordinary sync — the app was opened and nothing was tapped — so
        // this is the common path rather than a degenerate one.
        repository.found(davide, freshColony())

        assertEquals(emptySet(), repository.appliedAmong(davide, emptySet()))
    }

    @Test
    fun `one player's applied keys are not another's`() = runTest {
        // The primary key is the pair and not the key alone, because nothing about an idempotency
        // key is globally unique. Keyed on the string alone, one player's retry would swallow
        // another player's verb.
        val shared = IdempotencyKey("shared-string")
        val mine = repository.found(davide, freshColony()).colony
        repository.found(someoneElse, freshColony())
        repository.write(davide, freshColony(), applied = setOf(shared), expected = mine.version)

        assertEquals(emptySet(), repository.appliedAmong(someoneElse, setOf(shared)))
    }

    @Test
    fun `a key applied twice is recorded once rather than failing the write`() = runTest {
        // `replay` reports a key it found already spent as applied, and the write that follows
        // carries it — so the second insert of the same key is the normal case, not an error.
        val mine = IdempotencyKey("mine")
        val founded = repository.found(davide, freshColony()).colony
        repository.write(davide, freshColony(), applied = setOf(mine), expected = founded.version)

        val again = repository.write(
            davide,
            freshColony(at = TEST_NOW + 1.hours),
            applied = setOf(mine),
            expected = founded.version.next(),
        )

        assertEquals(WriteResult.WRITTEN, again)
        assertEquals(setOf(mine), repository.appliedAmong(davide, setOf(mine)))
    }

    // ── Pruning ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `pruning forgets keys past the retention window and keeps the ones inside it`() = runTest {
        val old = IdempotencyKey("old")
        val recent = IdempotencyKey("recent")
        val founded = repository.found(davide, freshColony()).colony
        repository.write(davide, freshColony(), applied = setOf(old), expected = founded.version)
        clock.advanceBy(APPLIED_RETENTION + 1.days)
        repository.write(davide, freshColony(), applied = setOf(recent), expected = founded.version.next())

        val removed = repository.prune(before = clock.now() - APPLIED_RETENTION)

        assertEquals(1, removed)
        assertEquals(setOf(recent), repository.appliedAmong(davide, setOf(old, recent)))
    }

    @Test
    fun `pruning a table with nothing old enough in it removes nothing`() = runTest {
        val founded = repository.found(davide, freshColony()).colony
        repository.write(davide, freshColony(), applied = setOf(IdempotencyKey("recent")), expected = founded.version)

        assertEquals(0, repository.prune(before = clock.now() - APPLIED_RETENTION))
    }

    // ── The transaction ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a transaction that fails part way through leaves nothing behind`() = runTest {
        // The claim `PostgresDatabase.kt` makes about its own `catch`, and the one thing in this
        // slice with no other way to be believed: `write` records the colony and then its spent keys,
        // so a failure between the two would be a colony that had been paid for with verbs the store
        // has forgotten. Provoked here rather than waited for, because the only natural cause is a
        // connection dying mid-statement.
        assertFailsWith<IllegalStateException> {
            database.transaction { connection ->
                connection.update(
                    "INSERT INTO players (id, provider, subject, created_at) VALUES (?, ?, ?, now())",
                ) {
                    setString(1, "half-written")
                    setString(2, "header")
                    setString(3, "half-written")
                }
                error("the connection went away")
            }
        }

        // The two the fixture put there, and not the third.
        assertEquals(listOf("header" to "davide", "header" to "someone-else"), database.playerIdentities())
    }

    // ── The pool ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a colony reached through the connection pool is the colony`() = runTest {
        // The pool is what a deployed server actually talks through, and it is the one piece of
        // this slice that no other test touches: everything above holds Zonky's own `DataSource`.
        val pool = connectionPool(postgres.embeddedPostgres.getJdbcUrl("postgres", "postgres"))
        pool.use {
            it.applySchema()
            val pooled = PostgresColonyRepository(it, clock)
            val founded = pooled.found(davide, freshColony()).colony

            assertTrue(founded.snapshot.state.galaxy.home in founded.snapshot.state.galaxy.surveyed)
            assertEquals(founded, pooled.colonyOf(davide))
        }
    }

    // **The form every provider actually prints, against a real driver.** `DatabaseUrlTest` says what
    // `postgresql://user@host/db` is converted *into*; only this can say that the answer is a URL the
    // PostgreSQL driver takes and connects with. The two halves are the point: a conversion that is
    // self-consistent and wrong would pass the unit file completely, and the symptom would be a
    // revision that never boots after a four-minute deploy.
    @Test
    fun `a libpq url of the shape a console prints connects`() = runTest {
        val port = postgres.embeddedPostgres.port

        val pool = connectionPool("postgresql://postgres@localhost:$port/postgres")
        pool.use {
            it.applySchema()
            val pooled = PostgresColonyRepository(it, clock)

            assertEquals(pooled.found(davide, freshColony()).colony, pooled.colonyOf(davide))
        }
    }

    private companion object {

        @get:ClassRule
        @JvmStatic
        val postgres: SingleInstancePostgresRule = embeddedPostgres()

        val ALL_THREE_KEYS = setOf(IdempotencyKey("won"), IdempotencyKey("lost"), IdempotencyKey("never-sent"))
    }
}
