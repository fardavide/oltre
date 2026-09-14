package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceProject
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.IdempotencyKey
import io.zonky.test.db.postgres.junit.SingleInstancePostgresRule
import kotlinx.coroutines.test.runTest
import org.junit.ClassRule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// **The cross-row write, against the real thing.** This is the first write in the game that touches
// two rows, and every property that makes it safe is a property of SQL rather than of Kotlin: the
// colony keeps its compare-and-set, the pool takes an in-database increment, and both are in one
// transaction with `applied_verbs`.
//
// A unit test standing on `InMemoryColonyRepository` cannot judge any of that — a map has no
// transactions — which is exactly the split `#106` §8 asks for.
class TreasuryIntegrationTest {

    private val database = postgres.embeddedPostgres.postgresDatabase
    private val clock = MovableClock(TEST_NOW)
    private val colonies = PostgresColonyRepository(database, clock)
    private val alliances = PostgresAllianceRepository(database)
    private val davide = PlayerId("davide")

    @BeforeTest
    fun anEmptyStore() {
        database.applySchema()
        database.emptyEveryTable()
        database.givenPlayer(davide)
    }

    @Test
    fun `a contribution debits the colony and credits the pool in one write`() = runTest {
        val seat = anAllianceWithDavideInIt()
        val colony = colonies.found(davide, establishedColony()).colony
        val paid = Resources.of(metal = 4_210, crystal = 960, deuterium = 120)

        val written = colonies.write(
            player = davide,
            snapshot = contributed(colony.snapshot, paid),
            applied = setOf(IdempotencyKey("first")),
            expected = colony.version,
            credit = PoolCredit(seat.alliance, seat.id, paid, AllianceBalance.award(paid)),
        )

        assertEquals(WriteResult.WRITTEN, written)
        assertEquals("4210", database.scalar("SELECT pool_metal FROM alliances"))
        assertEquals("960", database.scalar("SELECT pool_crystal FROM alliances"))
        assertEquals("120", database.scalar("SELECT pool_deuterium FROM alliances"))
        // 1 : 2 : 3, banked on the alliance and on the seat alike — the second is the column the
        // succession rule reads when it looks for the best active contributor, and it has been zero
        // for everybody since the day it shipped.
        assertEquals("6490", database.scalar("SELECT experience FROM alliances"))
        assertEquals("6490", database.scalar("SELECT contributed FROM alliance_members"))
    }

    // **A lost compare-and-set contributes nothing**, which is the whole reason the credit is inside
    // this transaction rather than beside it: the resources never left the colony, so they must not
    // arrive in the pool.
    @Test
    fun `a stale write credits nothing at all`() = runTest {
        val seat = anAllianceWithDavideInIt()
        val colony = colonies.found(davide, establishedColony()).colony
        val paid = Resources.of(metal = 4_210)
        // Somebody else's device wins the race, moving the version this caller is about to assert.
        colonies.write(davide, colony.snapshot, emptySet(), colony.version)

        val written = colonies.write(
            player = davide,
            snapshot = contributed(colony.snapshot, paid),
            applied = setOf(IdempotencyKey("first")),
            expected = colony.version,
            credit = PoolCredit(seat.alliance, seat.id, paid, AllianceBalance.award(paid)),
        )

        assertEquals(WriteResult.STALE, written)
        assertEquals("0", database.scalar("SELECT pool_metal FROM alliances"))
        assertEquals("0", database.scalar("SELECT experience FROM alliances"))
    }

    // **The idempotency half, and it matters more than the atomicity half.** A verb whose response
    // was lost gets resent; the credit must be computed from what was applied *now*, which is
    // `Replayed.contributed`'s job — so a replay that finds the key already spent credits nothing.
    @Test
    fun `a replayed envelope credits the pool once`() = runTest {
        val seat = anAllianceWithDavideInIt()
        var colony = colonies.found(davide, establishedColony()).colony
        val envelope = envelope(ClientVerb.Contribute(Resources.of(metal = 4_210)), TEST_NOW, key = "same-key")

        repeat(2) {
            colony = checkNotNull(colonies.colonyOf(davide))
            val replayed = replay(
                colony = colony.snapshot,
                envelopes = listOf(envelope),
                alreadyApplied = colonies.appliedAmong(davide, setOf(envelope.idempotencyKey)),
                serverNow = TEST_NOW,
                alliance = seat.alliance,
            )
            colonies.write(
                player = davide,
                snapshot = replayed.snapshot,
                applied = replayed.applied,
                expected = colony.version,
                credit = if (replayed.contributed == Resources.of()) {
                    null
                } else {
                    PoolCredit(
                        seat.alliance,
                        seat.id,
                        replayed.contributed,
                        AllianceBalance.award(replayed.contributed),
                    )
                },
            )
        }

        assertEquals("4210", database.scalar("SELECT pool_metal FROM alliances"))
        assertEquals(1, database.rowsIn("applied_verbs"))
    }

    // What the whole slice is for, read back through the type a screen would draw: the pool, the
    // caller's own standing, and the one thing it could buy.
    @Test
    fun `the treasury reads back what was paid in`() = runTest {
        val seat = anAllianceWithDavideInIt()
        val colony = colonies.found(davide, establishedColony()).colony
        val paid = Resources.of(metal = 40_000, crystal = 20_000, deuterium = 10_000)
        colonies.write(
            davide,
            contributed(colony.snapshot, paid),
            setOf(IdempotencyKey("first")),
            colony.version,
            PoolCredit(seat.alliance, seat.id, paid, AllianceBalance.award(paid)),
        )

        val read = assertIs<TreasuryRead.Present>(alliances.treasuryOf(davide, TEST_NOW))

        assertEquals(paid, read.alliance.pool)
        assertEquals(AllianceBalance.award(paid), read.contributed)
    }

    // **The project, and the effect §8 asks to be visible**: the pool goes down, the level goes up,
    // and the roster's cap goes up with it on the very same read.
    @Test
    fun `buying a charter spends the pool and widens the roster`() = runTest {
        val seat = anAllianceWithDavideInIt()
        val colony = colonies.found(davide, establishedColony()).colony
        val paid = Resources.of(metal = 400_000, crystal = 200_000, deuterium = 100_000)
        colonies.write(
            davide,
            contributed(colony.snapshot, paid),
            setOf(IdempotencyKey("first")),
            colony.version,
            PoolCredit(seat.alliance, seat.id, paid, AllianceBalance.award(paid)),
        )
        val before = assertIs<TreasuryRead.Present>(alliances.treasuryOf(davide, TEST_NOW))
        val cost = AllianceBalance.costOf(AllianceProject.CHARTER_EXPANSION, before.alliance.seatsBought)

        val bought = assertIs<TreasuryRead.Present>(
            alliances.buy(davide, AllianceProject.CHARTER_EXPANSION, TEST_NOW, before.alliance.version),
        )

        assertEquals(paid.minus(cost), bought.alliance.pool)
        assertEquals(
            before.alliance.alliance.seats.cap + AllianceBalance.SEATS_PER_CHARTER,
            bought.alliance.alliance.seats.cap,
        )
        // **Monotonic**: spending the pool adds to the level and never subtracts, or the level would
        // fall for doing the thing the level exists to encourage.
        assertEquals(
            before.alliance.experience + AllianceBalance.projectAward(cost),
            bought.alliance.experience,
        )
    }

    @Test
    fun `a pool that cannot cover the project buys nothing`() = runTest {
        val seat = anAllianceWithDavideInIt()
        val before = assertIs<TreasuryRead.Present>(alliances.treasuryOf(davide, TEST_NOW))

        val refused = alliances.buy(davide, AllianceProject.CHARTER_EXPANSION, TEST_NOW, before.alliance.version)

        assertEquals(
            TreasuryRead.Refused(dev.fardavide.oltre.protocol.ApiError.AllianceTreasuryShort),
            refused,
        )
        assertEquals("0", database.scalar("SELECT pool_metal FROM alliances"))
        assertEquals(seat.alliance.value, database.scalar("SELECT id FROM alliances"))
    }

    private suspend fun anAllianceWithDavideInIt(): Seat {
        alliances.found(davide, AllianceName("Ferro Alto"), AllianceTag("FRA"), TEST_NOW)
        return assertIs<Affiliation.Enlisted>(alliances.allianceOf(davide, TEST_NOW)).seat
    }

    private fun contributed(colony: dev.fardavide.oltre.core.GameSnapshot, paid: Resources) = colony.copy(
        state = assertIs<dev.fardavide.oltre.core.ContributeResult.Started>(
            dev.fardavide.oltre.core.contribute(colony.state, paid, TEST_NOW),
        ).state,
    )

    private companion object {

        @get:ClassRule
        @JvmStatic
        val postgres: SingleInstancePostgresRule = embeddedPostgres()
    }
}
