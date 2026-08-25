package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.Protocol
import dev.fardavide.oltre.protocol.SyncRequest
import dev.fardavide.oltre.protocol.SyncResponse
import dev.fardavide.oltre.protocol.VerbEnvelope
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.zonky.test.db.postgres.junit.SingleInstancePostgresRule
import org.junit.ClassRule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

// **The sentence this whole slice was for: a colony survives a restart.**
//
// Two servers, one after the other, sharing nothing but a database — a fresh connection pool, a
// fresh `PostgresColonyRepository`, a fresh Ktor application, and not one object in common. That is
// what a restart is on Cloud Run, which starts this process again every time it scales up from zero;
// and it is the one property no amount of testing the store on its own can demonstrate, because the
// store on its own is exactly the thing a restart throws away.
class ColonySurvivesRestartIntegrationTest {

    // Each test is its own pair of processes against its own empty database — the schema outlives
    // the truncate, which is the state a deployed server restarts into.
    @BeforeTest
    fun anEmptyColonyStore() {
        postgres.embeddedPostgres.postgresDatabase.applySchema()
        postgres.embeddedPostgres.postgresDatabase.emptyEveryTable()
    }

    @Test
    fun `a colony founded by one server process is played on by the next`() {
        val clock = MovableClock(TEST_NOW)
        lateinit var founded: SyncResponse

        // The first process founds a colony and starts a mine on it.
        serverProcess(clock) { client ->
            client.post("/v1/colony", sync())
            founded = client.post("/v1/sync", sync(MINE)).syncResponse()
            assertEquals(setOf(MINE.idempotencyKey), founded.applied)
        }

        // A week goes by with nothing running anywhere — no phone, no server, no timer. This is the
        // whole product: everything progresses while the app is closed, and after `#106` the thing
        // that has to still be there is the row rather than the file on the phone.
        clock.advanceBy(7.days)

        // A second process, which has never met the first.
        serverProcess(clock) { client ->
            val week = client.post("/v1/sync", sync()).syncResponse()

            assertEquals(founded.snapshot.state.galaxy.seed, week.snapshot.state.galaxy.seed)
            assertEquals(clock.now(), week.snapshot.lastUpdatedAt)
            // Not merely present — *advanced*. The mine the first process started finished while
            // nothing was running, which is `advance` being a pure function of the span rather than
            // of anything that was awake to watch it.
            assertTrue(week.snapshot.state.eventLog.any { it is Event.BuildCompleted })
        }
    }

    @Test
    fun `a verb the first process applied is not applied again by the second`() {
        // `applied_verbs` outliving the process is the half of persistence that is easy to forget,
        // and the failure it prevents is silent: the phone that lost the response on a train resends
        // the envelope, and a server that had forgotten the key would take the money twice.
        val clock = MovableClock(TEST_NOW)

        serverProcess(clock) { client ->
            client.post("/v1/colony", sync())
            client.post("/v1/sync", sync(MINE))
        }

        serverProcess(clock) { client ->
            val retried = client.post("/v1/sync", sync(MINE)).syncResponse()

            assertEquals(setOf(MINE.idempotencyKey), retried.applied)
            assertEquals(emptyList(), retried.rejected)
            assertEquals(1, retried.snapshot.state.eventLog.count { it is Event.BuildStarted })
        }
    }

    // ── The harness ───────────────────────────────────────────────────────────────────────────

    // One lifetime of the server, and everything inside it dies with it. The pool is built and
    // closed per process on purpose: a pool that outlived the block would be the one object the two
    // "processes" shared, and sharing it is exactly what this test is claiming not to do.
    private fun serverProcess(clock: Clock, block: suspend (HttpClient) -> Unit) = testApplication {
        connectionPool(postgres.embeddedPostgres.getJdbcUrl("postgres", "postgres")).use { pool ->
            pool.applySchema()
            application { oltre(PostgresColonyRepository(pool, clock), clock) }
            block(client)
        }
    }

    private fun sync(vararg envelopes: VerbEnvelope): SyncRequest =
        SyncRequest(ApiVersion.CURRENT, envelopes.toList())

    private suspend fun HttpClient.post(path: String, request: SyncRequest): String = post(path) {
        header(Protocol.PLAYER_HEADER, "davide")
        contentType(ContentType.Application.Json)
        setBody(Protocol.json.encodeToString(request))
    }.bodyAsText()

    private fun String.syncResponse(): SyncResponse = Protocol.json.decodeFromString(this)

    private companion object {

        @get:ClassRule
        @JvmStatic
        val postgres: SingleInstancePostgresRule = embeddedPostgres()

        val MINE = VerbEnvelope(
            verb = ClientVerb.StartUpgrade(BuildingType.METAL_MINE),
            clientInstant = TEST_NOW,
            idempotencyKey = IdempotencyKey("mine"),
        )
    }
}
