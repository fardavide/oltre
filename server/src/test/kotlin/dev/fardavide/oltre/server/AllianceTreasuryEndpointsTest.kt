package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceProject
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.BuyProjectRequest
import dev.fardavide.oltre.protocol.Protocol
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// **What the two treasury routes decide, with no socket anywhere near them** — the split
// `Endpoints.kt` already argues for: a decision belongs where the kind of test that judges it can
// reach it, and a route handler behind a test host would make every one of these an integration test
// of the wrong thing.
class AllianceTreasuryEndpointsTest {

    private val colonies = InMemoryColonyRepository()
    private val alliances = InMemoryAllianceRepository(colonies).also { colonies.pools = it }
    private val players = InMemoryPlayerRepository(colonies, alliances, ids = sequentialPlayerIds())
    private val authenticator = HeaderAuthenticator(players)
    private val clock = MovableClock(TEST_NOW)

    @Test
    fun `a caller with no session is refused before anything is read`() = runTest {
        val answer = readTreasury(alliances, authenticator, clock, Credentials(null, null))

        assertEquals(HttpStatusCode.Unauthorized, answer.status)
    }

    // Not in one, so there is no pool. A 404 rather than a 409: the thing being asked for does not
    // exist, which is a different sentence from *the pool is short*.
    @Test
    fun `a player in no alliance has no treasury to read`() = runTest {
        val davide = givenAPlayer()

        val answer = readTreasury(alliances, authenticator, clock, credentials(davide))

        assertEquals(HttpStatusCode.NotFound, answer.status)
        assertEquals(ApiError.NotInAnAlliance, answer.error())
    }

    @Test
    fun `a founder reads an empty pool and the one thing it could buy`() = runTest {
        val davide = givenAnAllianceFoundedBy()

        val answer = assertIs<Answer.Treasury>(readTreasury(alliances, authenticator, clock, credentials(davide)))

        assertEquals(Resources.of(), answer.response.pool)
        assertEquals(0, answer.response.contributed)
        assertEquals(listOf(AllianceProject.CHARTER_EXPANSION), answer.response.projects.map { it.project })
        // Nothing in the pool, so nothing is affordable — and the offer is still listed, because a
        // project you are saving for is a fact worth reading.
        assertEquals(listOf(false), answer.response.projects.map { it.affordable })
    }

    @Test
    fun `a pool that has been paid into reads back what is in it`() = runTest {
        val davide = givenAnAllianceFoundedBy()
        val paid = Resources.of(metal = 40_000, crystal = 20_000, deuterium = 10_000)
        creditThePool(davide, paid)

        val answer = assertIs<Answer.Treasury>(readTreasury(alliances, authenticator, clock, credentials(davide)))

        assertEquals(paid, answer.response.pool)
        assertEquals(AllianceBalance.award(paid), answer.response.contributed)
        assertEquals(listOf(true), answer.response.projects.map { it.affordable })
    }

    // **Not 402 and not 400.** The request was well formed and the caller was allowed; the pool is
    // short, which is a fact about the alliance's state — which is what 409 means.
    @Test
    fun `buying what the pool cannot cover is a conflict rather than a bad request`() = runTest {
        val davide = givenAnAllianceFoundedBy()

        val answer = buy(davide)

        assertEquals(HttpStatusCode.Conflict, answer.status)
        assertEquals(ApiError.AllianceTreasuryShort, answer.error())
    }

    @Test
    fun `a founder spends the pool and the roster widens on the same answer`() = runTest {
        val davide = givenAnAllianceFoundedBy()
        creditThePool(davide, Resources.of(metal = 400_000, crystal = 200_000, deuterium = 100_000))
        val before = assertIs<Answer.Treasury>(readTreasury(alliances, authenticator, clock, credentials(davide)))

        val answer = assertIs<Answer.Treasury>(buy(davide))

        assertEquals(1, answer.response.projects.single().timesBought)
        // Spending is monotonic on the level — see `alliance-sheet.md` §3 — so the gauge moves up.
        assertEquals(true, answer.response.progress.earned > before.response.progress.earned)
    }

    // `alliance-sheet.md` §5.3 gives admins the treasury and keeps only rename and disband with the
    // founder. A plain member is neither.
    @Test
    fun `a plain member may not spend the pool`() = runTest {
        val founder = givenAnAllianceFoundedBy()
        val member = givenAMemberOf(founder)
        creditThePool(founder, Resources.of(metal = 400_000, crystal = 200_000, deuterium = 100_000))

        val answer = buy(member)

        assertEquals(HttpStatusCode.Forbidden, answer.status)
        assertEquals(ApiError.AllianceRoleTooLow, answer.error())
    }

    @Test
    fun `an admin may spend the pool`() = runTest {
        val founder = givenAnAllianceFoundedBy()
        val member = givenAMemberOf(founder)
        val seat = assertIs<Affiliation.Enlisted>(alliances.allianceOf(member, TEST_NOW)).seat
        val alliance = assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, TEST_NOW))
        alliances.setRole(founder, alliance.alliance.alliance.id, seat.id, AllianceRole.ADMIN, alliance.alliance.version)
        creditThePool(founder, Resources.of(metal = 400_000, crystal = 200_000, deuterium = 100_000))

        assertIs<Answer.Treasury>(buy(member))
    }

    @Test
    fun `a body this build cannot read is a bad request rather than a crash`() = runTest {
        val davide = givenAnAllianceFoundedBy()

        val answer = buyAllianceProject(alliances, authenticator, clock, credentials(davide), "{ not json")

        assertEquals(HttpStatusCode.BadRequest, answer.status)
        assertIs<ApiError.Malformed>(answer.error())
    }

    // The store that is there and cannot answer — `answering`'s one `catch`, which is what stops Ktor
    // replying with a bare 500 the client reads as `Unreachable` and retries forever.
    @Test
    fun `a store a network away that fails is a 500 with something in it`() = runTest {
        val davide = givenAPlayer()

        val answer = readTreasury(UnreachableAllianceRepository(), authenticator, clock, credentials(davide))

        assertEquals(HttpStatusCode.InternalServerError, answer.status)
        assertIs<ApiError.Internal>(answer.error())
    }

    // **A project that can no longer move its own number is absent from the catalogue**, and buying
    // it anyway is refused rather than taking the pool for nothing — which is the dead control
    // wearing a price tag.
    @Test
    fun `a roster already at its ceiling cannot buy more seats`() = runTest {
        val davide = givenAnAllianceFoundedBy()
        creditThePool(davide, Resources.of(metal = 400_000_000, crystal = 200_000_000, deuterium = 100_000_000))
        // Bought until the cap is reached, which is what a very rich alliance does.
        repeat(20) { buy(davide) }

        val answer = assertIs<Answer.Treasury>(readTreasury(alliances, authenticator, clock, credentials(davide)))

        assertEquals(emptyList(), answer.response.projects)
        assertEquals(ApiError.AllianceTreasuryShort, buy(davide).error())
    }

    // **The version is read here rather than sent by the client**, so a purchase that loses the
    // compare-and-set is retried against the row that won. Losing every attempt is `StaleAlliance`,
    // and the client's answer to that is to read again.
    @Test
    fun `a purchase that keeps losing the race is a stale alliance rather than a wrong answer`() = runTest {
        val davide = givenAnAllianceFoundedBy()
        creditThePool(davide, Resources.of(metal = 400_000, crystal = 200_000, deuterium = 100_000))

        val answer = buyAllianceProject(
            ContendedAllianceRepository(alliances),
            authenticator,
            clock,
            credentials(davide),
            Protocol.json.encodeToString(
                BuyProjectRequest.serializer(),
                BuyProjectRequest(ApiVersion.CURRENT, AllianceProject.CHARTER_EXPANSION),
            ),
        )

        assertEquals(HttpStatusCode.Conflict, answer.status)
        assertEquals(ApiError.StaleAlliance, answer.error())
    }

    // An alliance disbanded between the read and the tap. A 404 rather than a 409, on the same
    // division as `NotInAnAlliance`: the thing being spent does not exist, which is a different
    // sentence from *the pool is short*.
    @Test
    fun `an alliance that has gone is not found rather than conflicted`() = runTest {
        val davide = givenAPlayer()

        val answer = readTreasury(VanishedAllianceRepository(), authenticator, clock, credentials(davide)).status

        assertEquals(HttpStatusCode.NotFound, answer)
    }

    @Test
    fun `buying with no session is refused before anything is read`() = runTest {
        val answer = buyAllianceProject(alliances, authenticator, clock, Credentials(null, null), "{}")

        assertEquals(HttpStatusCode.Unauthorized, answer.status)
    }

    // **A row that is stale every time it is read never gets to assert a version**, so the loop runs
    // out rather than buying against a price nobody saw — and the answer is the same one a lost
    // compare-and-set gives.
    @Test
    fun `a treasury that is stale on every read gives up rather than guessing`() = runTest {
        val davide = givenAPlayer()
        val unsettled = UnsettledAllianceRepository()

        val answer = buyAllianceProject(
            unsettled,
            authenticator,
            clock,
            credentials(davide),
            Protocol.json.encodeToString(
                BuyProjectRequest.serializer(),
                BuyProjectRequest(ApiVersion.CURRENT, AllianceProject.CHARTER_EXPANSION),
            ),
        )

        assertEquals(HttpStatusCode.Conflict, answer.status)
        assertEquals(ApiError.StaleAlliance, answer.error())
        assertEquals(WRITE_ATTEMPTS, unsettled.reads)
    }

    @Test
    fun `a caller with no seat buys nothing`() = runTest {
        val davide = givenAPlayer()

        val answer = buy(davide)

        assertEquals(HttpStatusCode.NotFound, answer.status)
        assertEquals(ApiError.NotInAnAlliance, answer.error())
    }

    // **A credit for an alliance that is no longer there lands nowhere**, which is what a disband
    // racing a sync looks like. It is a no-op rather than a failure: the colony's own write already
    // won or lost on its own terms.
    @Test
    fun `crediting an alliance that has gone changes nothing`() = runTest {
        val davide = givenAnAllianceFoundedBy()
        val seat = assertIs<Affiliation.Enlisted>(alliances.allianceOf(davide, TEST_NOW)).seat

        alliances.credit(
            PoolCredit(
                alliance = dev.fardavide.oltre.protocol.AllianceId("gone"),
                member = seat.id,
                amount = Resources.of(metal = 10),
                experience = 10,
            ),
        )

        val answer = assertIs<Answer.Treasury>(readTreasury(alliances, authenticator, clock, credentials(davide)))
        assertEquals(Resources.of(), answer.response.pool)
    }

    // **The in-memory store's own half of the cross-row write.** In Postgres the colony and the pool
    // are one transaction; here they are two locks taken in one order, and this is what says the
    // credit actually lands when the compare-and-set wins.
    @Test
    fun `a colony write carrying a credit moves the pool with it`() = runTest {
        val davide = givenAnAllianceFoundedBy()
        val seat = assertIs<Affiliation.Enlisted>(alliances.allianceOf(davide, TEST_NOW)).seat
        val colony = checkNotNull(colonies.colonyOf(davide))
        val paid = Resources.of(metal = 4_210, crystal = 960)

        val written = colonies.write(
            player = davide,
            snapshot = colony.snapshot,
            applied = emptySet(),
            expected = colony.version,
            credit = PoolCredit(seat.alliance, seat.id, paid, AllianceBalance.award(paid)),
        )

        assertEquals(WriteResult.WRITTEN, written)
        val read = assertIs<TreasuryRead.Present>(alliances.treasuryOf(davide, TEST_NOW))
        assertEquals(paid, read.alliance.pool)
        assertEquals(AllianceBalance.award(paid), read.contributed)
    }

    // **A colony store with no alliance store beside it takes the credit and drops it**, which is
    // the shape every test that is not about an alliance constructs. It must not raise: a unit test
    // of the sync pair has no pool and is not asking for one.
    @Test
    fun `a credit with no pool store wired anywhere is dropped rather than raised`() = runTest {
        val alone = InMemoryColonyRepository()
        val davide = PlayerId("nobody")
        val colony = alone.found(davide, freshColony()).colony

        val written = alone.write(
            player = davide,
            snapshot = colony.snapshot,
            applied = emptySet(),
            expected = colony.version,
            credit = PoolCredit(
                alliance = dev.fardavide.oltre.protocol.AllianceId("ferro"),
                member = dev.fardavide.oltre.protocol.AllianceMemberId("seat"),
                amount = Resources.of(metal = 10),
                experience = 10,
            ),
        )

        assertEquals(WriteResult.WRITTEN, written)
    }

    // A write with nothing to contribute takes no second lock at all, which is the common case: most
    // syncs never touch an alliance.
    @Test
    fun `a colony write with no credit leaves the pool alone`() = runTest {
        val davide = givenAnAllianceFoundedBy()
        val colony = checkNotNull(colonies.colonyOf(davide))

        colonies.write(davide, colony.snapshot, emptySet(), colony.version, credit = null)

        val read = assertIs<TreasuryRead.Present>(alliances.treasuryOf(davide, TEST_NOW))
        assertEquals(Resources.of(), read.alliance.pool)
    }

    // ── The harness ──────────────────────────────────────────────────────────────────────────

    private suspend fun buy(player: PlayerId): Answer = buyAllianceProject(
        alliances,
        authenticator,
        clock,
        credentials(player),
        Protocol.json.encodeToString(
            BuyProjectRequest.serializer(),
            BuyProjectRequest(ApiVersion.CURRENT, AllianceProject.CHARTER_EXPANSION),
        ),
    )

    private suspend fun givenAPlayer(subject: String = "davide"): PlayerId {
        val player = players.resolve(ProviderIdentity(ProviderName.HEADER, subject))
        colonies.found(player, freshColony())
        return player
    }

    private suspend fun givenAnAllianceFoundedBy(): PlayerId {
        val davide = givenAPlayer()
        alliances.found(davide, AllianceName("Ferro Alto"), AllianceTag("FRA"), TEST_NOW)
        return davide
    }

    private suspend fun givenAMemberOf(founder: PlayerId): PlayerId {
        val member = givenAPlayer(subject = "somebody-else")
        val alliance = assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, TEST_NOW)).alliance
        val petitioned = assertIs<AllianceChange.Applied>(
            alliances.petition(member, alliance.alliance.id, TEST_NOW, alliance.version),
        )
        val petition = assertIs<Affiliation.Petitioning>(petitioned.affiliation)
        alliances.approve(
            founder,
            alliance.alliance.id,
            petition.petition.id,
            dev.fardavide.oltre.protocol.JoinDecision.ADMITTED,
            TEST_NOW,
            petition.alliance.version,
        )
        return member
    }

    private suspend fun creditThePool(player: PlayerId, paid: Resources) {
        val seat = assertIs<Affiliation.Enlisted>(alliances.allianceOf(player, TEST_NOW)).seat
        alliances.credit(PoolCredit(seat.alliance, seat.id, paid, AllianceBalance.award(paid)))
    }

    private fun credentials(player: PlayerId): Credentials =
        Credentials(authorization = null, playerHeader = if (player.value == "player-1") "davide" else "somebody-else")

    private fun Answer.error(): ApiError = assertIs<Answer.Failed>(this).error
}
