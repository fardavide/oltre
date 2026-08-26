package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.IdToken
import dev.fardavide.oltre.protocol.Protocol
import dev.fardavide.oltre.protocol.RefreshRequest
import dev.fardavide.oltre.protocol.SessionResponse
import dev.fardavide.oltre.protocol.SessionToken
import dev.fardavide.oltre.protocol.SignInRequest
import dev.fardavide.oltre.protocol.SyncRequest
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

// **What the four identity routes decide, judged without a server.** Everything here is a plain
// `…Test` because everything here is a decision: `OltreServerIntegrationTest` proves the paths, the
// status lines and the header spellings, which is what only a request can prove.
//
// The last section is the one `#110`'s Done-means is actually about — sign in twice and find the
// same colony, delete the account and find a fresh one — and it lives here rather than over the wire
// because it is a property of the store and the endpoints rather than of HTTP.
class AuthEndpointsTest {

    private val clock = MovableClock(TEST_NOW)
    private val colonies = InMemoryColonyRepository()
    private val players = InMemoryPlayerRepository(colonies, ids = sequentialPlayerIds())
    private val source = FakeJwksSource(jwksOf(providerKey))
    private val sessions = Sessions(TEST_SIGNING_KEY, clock)
    private val identity = Identity(
        verifier = IdTokenVerifier(
            specs = mapOf(
                IdentityProvider.GOOGLE to testSpec(IdentityProvider.GOOGLE),
                IdentityProvider.APPLE to testSpec(IdentityProvider.APPLE),
            ),
            keys = JwksKeys(source, clock),
            clock = clock,
        ),
        sessions = sessions,
        notifications = AppleNotificationVerifier(
            spec = testSpec(IdentityProvider.APPLE),
            keys = JwksKeys(source, clock),
            clock = clock,
        ),
    )
    private val authenticator = SessionAuthenticator(sessions, players)

    // ── Signing in ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a verified token is answered with a session this server signed`() = runTest {
        val answer = signInWith(token = providerKey.sign(idTokenClaims()))

        val session = answer.session(HttpStatusCode.OK)
        assertEquals(ApiVersion.CURRENT, session.apiVersion)
        assertEquals(TEST_NOW + 1.hours, session.accessExpiresAt)
        assertEquals(TEST_NOW + 90.days, session.refreshExpiresAt)
        assertEquals(SessionVerdict.Valid(PlayerId("player-1")), sessions.read(session.accessToken, SessionKind.ACCESS))
    }

    @Test
    fun `the same provider subject comes back to the same player however often they sign in`() = runTest {
        // The property the `players` unique index exists for. An upgrading player and a fresh
        // install are one code path; what differs is only whether a colony comes back afterwards.
        val first = signInWith(token = providerKey.sign(idTokenClaims())).session()
        clock.advanceBy(1.hours)

        val again = signInWith(token = providerKey.sign(idTokenClaims(expiresAt = clock.now() + 1.hours))).session()

        assertNotEquals(first.accessToken, again.accessToken, "a second sign-in reuses the first token")
        assertEquals(playerIn(first), playerIn(again))
    }

    @Test
    fun `a different subject is a different player`() = runTest {
        val mine = signInWith(token = providerKey.sign(idTokenClaims())).session()

        val theirs = signInWith(token = providerKey.sign(idTokenClaims(subject = "somebody-else"))).session()

        assertNotEquals(playerIn(mine), playerIn(theirs))
    }

    @Test
    fun `the same subject at two providers is two people`() = runTest {
        // Nothing about a subject is globally unique — Apple and Google can both mint `1234` and
        // mean two different people — which is why the table's index is on the pair.
        val google = signInWith(IdentityProvider.GOOGLE, providerKey.sign(idTokenClaims())).session()

        val apple = signInWith(IdentityProvider.APPLE, providerKey.sign(idTokenClaims())).session()

        assertNotEquals(playerIn(google), playerIn(apple))
    }

    @Test
    fun `a token this server will not verify is one sentence and never six`() = runTest {
        // Telling a client *which* check failed tells anybody holding a stolen token which check to
        // work on, and there is nothing a player could do differently about any of them.
        val expired = providerKey.sign(idTokenClaims(expiresAt = TEST_NOW - 1.hours))
        val wrongAudience = providerKey.sign(idTokenClaims(audience = "somebody.else"))
        val gibberish = IdToken("not.a.token")

        listOf(expired, wrongAudience, gibberish).forEach { token ->
            val answer = assertIs<Answer.Failed>(signInWith(token = token))
            assertEquals(HttpStatusCode.Unauthorized, answer.status)
            assertEquals(ApiError.Unauthenticated, answer.error)
        }
    }

    @Test
    fun `a sign-in nobody can serve says which variable is missing rather than 404`() = runTest {
        // **Not a dead control.** A server with no session key still has a sign-in URL, because
        // `#111` is the slice that sets the variables and the day it has not is a day somebody is
        // looking at a deploy wondering why nothing happens.
        val answer = assertIs<Answer.Failed>(
            signInWith(identity = null, token = providerKey.sign(idTokenClaims())),
        )

        assertEquals(HttpStatusCode.ServiceUnavailable, answer.status)
        assertIs<ApiError.Internal>(answer.error)
    }

    @Test
    fun `a sign-in body this build cannot read is malformed rather than unauthenticated`() = runTest {
        val answer = assertIs<Answer.Failed>(
            signInWith(IdentityProvider.GOOGLE, body = """{"apiVersion":1,"idToken":""}"""),
        )

        assertEquals(HttpStatusCode.BadRequest, answer.status)
        assertIs<ApiError.Malformed>(answer.error)
    }

    @Test
    fun `a sign-in from a build this server does not serve carries the window`() = runTest {
        val body = Protocol.json.encodeToString(
            SignInRequest(ApiVersion(99), providerKey.sign(idTokenClaims()), TEST_NONCE),
        )

        val answer = assertIs<Answer.Failed>(signInWith(IdentityProvider.GOOGLE, body = body))

        assertEquals(HttpStatusCode.UpgradeRequired, answer.status)
        assertEquals(
            ApiError.UnsupportedApiVersion(ApiVersion.OLDEST_SERVED, ApiVersion.CURRENT),
            answer.error,
        )
    }

    @Test
    fun `a store that cannot answer becomes a 500 rather than escaping the route`() = runTest {
        // Sign-in touches a store a network away and a provider further away than that, so it needs
        // `served()`'s one `catch` as much as the sync routes do.
        val answer = assertIs<Answer.Failed>(
            signInWith(players = UnreachablePlayerRepository(), token = providerKey.sign(idTokenClaims())),
        )

        assertEquals(HttpStatusCode.InternalServerError, answer.status)
        assertIs<ApiError.Internal>(answer.error)
    }

    @Test
    fun `a failure with nothing to say still comes back naming itself`() = runTest {
        // A driver's own `NullPointerException` carries no message, and `ApiError.Internal("null")`
        // is worse than nothing for whoever reads the log. `served` makes the same call and
        // `EndpointsTest` pins it there.
        val answer = assertIs<Answer.Failed>(
            signInWith(players = SpeechlessRepository(), token = providerKey.sign(idTokenClaims())),
        )

        assertEquals("NullPointerException", assertIs<ApiError.Internal>(answer.error).detail)
    }

    // ── Refreshing ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a refresh token is traded for a fresh pair long after the access token has gone`() = runTest {
        val signedIn = signInWith(token = providerKey.sign(idTokenClaims())).session()

        clock.advanceBy(2.hours)
        val refreshed = refreshWith(signedIn.refreshToken).session(HttpStatusCode.OK)

        assertEquals(playerIn(signedIn), playerIn(refreshed))
        assertEquals(clock.now() + 1.hours, refreshed.accessExpiresAt)
    }

    @Test
    fun `the refresh token slides forward too so that checking in keeps the session alive`() = runTest {
        // The whole of what ninety days is for: a player who opens the game twice a day never signs
        // in twice. What keeps it safe is the player-row check below, not the lifetime.
        val signedIn = signInWith(token = providerKey.sign(idTokenClaims())).session()

        clock.advanceBy(80.days)
        val refreshed = refreshWith(signedIn.refreshToken).session()

        assertEquals(clock.now() + 90.days, refreshed.refreshExpiresAt)
    }

    @Test
    fun `a refresh token that has itself run out is the sign-in screen and not another refresh`() = runTest {
        // **`Unauthenticated` and not `SessionExpired`**, which looks inconsistent and is the point:
        // a client told to *refresh* about the credential it refreshes *with* would loop forever.
        val signedIn = signInWith(token = providerKey.sign(idTokenClaims())).session()

        clock.advanceBy(91.days)
        val answer = assertIs<Answer.Failed>(refreshWith(signedIn.refreshToken))

        assertEquals(HttpStatusCode.Unauthorized, answer.status)
        assertEquals(ApiError.Unauthenticated, answer.error)
    }

    @Test
    fun `an access token sent to the refresh endpoint is refused`() = runTest {
        val signedIn = signInWith(token = providerKey.sign(idTokenClaims())).session()

        assertIs<Answer.Failed>(refreshWith(signedIn.accessToken))
    }

    @Test
    fun `a refresh nobody can serve says so rather than 404`() = runTest {
        val answer = assertIs<Answer.Failed>(refreshWith(SessionToken("anything"), identity = null))

        assertEquals(HttpStatusCode.ServiceUnavailable, answer.status)
    }

    @Test
    fun `a refresh body this build cannot read is malformed`() = runTest {
        val answer = assertIs<Answer.Failed>(refreshRawWith("""{"apiVersion":1}"""))

        assertEquals(HttpStatusCode.BadRequest, answer.status)
        assertIs<ApiError.Malformed>(answer.error)
    }

    // ── Deleting ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `deleting an account answers with nothing at all`() = runTest {
        val signedIn = signInWith(token = providerKey.sign(idTokenClaims())).session()

        val answer = deleteWith(bearer(signedIn.accessToken))

        assertEquals(Answer.Deleted, answer)
        assertEquals(HttpStatusCode.NoContent, answer.status)
    }

    @Test
    fun `deleting without a credential is refused rather than quietly doing nothing`() = runTest {
        val answer = assertIs<Answer.Failed>(deleteWith(Credentials(authorization = null, playerHeader = null)))

        assertEquals(HttpStatusCode.Unauthorized, answer.status)
        assertEquals(ApiError.Unauthenticated, answer.error)
    }

    @Test
    fun `a token that outlived the account it names stops working immediately`() = runTest {
        // **A signature cannot be un-signed**, so without the player-row check on every request an
        // account deleted a minute ago would keep working until its access token ran out. This is
        // the line that turns "deleted" into "now".
        val signedIn = signInWith(token = providerKey.sign(idTokenClaims())).session()
        deleteWith(bearer(signedIn.accessToken))

        val caller = authenticator.identify(bearer(signedIn.accessToken))

        assertEquals(Caller.Refused(ApiError.Unauthenticated), caller)
    }

    @Test
    fun `a refresh token that outlived the account it names cannot bring it back`() = runTest {
        val signedIn = signInWith(token = providerKey.sign(idTokenClaims())).session()
        deleteWith(bearer(signedIn.accessToken))

        assertIs<Answer.Failed>(refreshWith(signedIn.refreshToken))
    }

    // ── Which 401 ─────────────────────────────────────────────────────────────────────────────
    //
    // The one thing `SessionAuthenticator` exists to decide, and the reason this slice did not take
    // `ktor-server-auth`: its challenge is a bare `401` with no body, and these are two different
    // sentences — *"one moment"* against *"sign in again"*.

    @Test
    fun `an access token past its hour is the one 401 the app can answer by itself`() = runTest {
        val signedIn = signInWith(token = providerKey.sign(idTokenClaims())).session()

        clock.advanceBy(2.hours)

        assertEquals(
            Caller.Refused(ApiError.SessionExpired),
            authenticator.identify(bearer(signedIn.accessToken)),
        )
    }

    @Test
    fun `a token nobody here signed is the other one`() = runTest {
        // Forged, wrong kind, or signed by something else — all one sentence to a player, and none
        // of them `SessionExpired`, because refreshing would not help and asking would be a loop.
        assertEquals(
            Caller.Refused(ApiError.Unauthenticated),
            authenticator.identify(bearer(SessionToken("not.a.token"))),
        )
    }

    // ── The account, end to end ───────────────────────────────────────────────────────────────

    @Test
    fun `a second sign-in finds the colony the first one founded`() = runTest {
        val first = signInWith(token = providerKey.sign(idTokenClaims())).session()
        val founded = foundWith(first.accessToken).colony()

        clock.advanceBy(1.hours)
        val again = signInWith(token = providerKey.sign(idTokenClaims(expiresAt = clock.now() + 1.hours))).session()
        val synced = syncWith(again.accessToken).colony()

        assertEquals(founded.snapshot.state.galaxy.seed, synced.snapshot.state.galaxy.seed)
    }

    @Test
    fun `a different subject opens a different colony`() = runTest {
        val mine = signInWith(token = providerKey.sign(idTokenClaims())).session()
        val theirs = signInWith(token = providerKey.sign(idTokenClaims(subject = "somebody-else"))).session()

        val ours = foundWith(mine.accessToken).colony()
        val yours = foundWith(theirs.accessToken).colony()

        assertNotEquals(ours.snapshot.state.galaxy.seed, yours.snapshot.state.galaxy.seed)
    }

    @Test
    fun `deleting an account takes the colony and the spent keys with it`() = runTest {
        val signedIn = signInWith(token = providerKey.sign(idTokenClaims())).session()
        foundWith(signedIn.accessToken)
        val player = playerIn(signedIn)
        val key = dev.fardavide.oltre.protocol.IdempotencyKey("spent")
        colonies.write(player, freshColony(), applied = setOf(key), expected = ColonyVersion.FIRST)

        deleteWith(bearer(signedIn.accessToken))

        assertNull(colonies.colonyOf(player))
        assertEquals(emptySet(), colonies.appliedAmong(player, setOf(key)))
    }

    @Test
    fun `signing in again after a deletion founds a fresh colony rather than resurrecting the old one`() = runTest {
        // **The property App Review's rule is actually about.** A surrogate `players.id` is what
        // buys it: the same Apple subject comes back to a *new* id, which has nothing hanging off
        // it — so a colony cannot be recovered by anybody who kept a copy of their subject.
        val before = signInWith(token = providerKey.sign(idTokenClaims())).session()
        val old = foundWith(before.accessToken).colony()
        deleteWith(bearer(before.accessToken))

        clock.advanceBy(1.hours)
        val after = signInWith(token = providerKey.sign(idTokenClaims(expiresAt = clock.now() + 1.hours))).session()

        assertNotEquals(playerIn(before), playerIn(after))
        assertIs<Answer.Failed>(syncWith(after.accessToken)).let { assertEquals(ApiError.NoColony, it.error) }
        val fresh = foundWith(after.accessToken).colony()
        assertNotEquals(old.snapshot.state.galaxy.seed, fresh.snapshot.state.galaxy.seed)
    }

    // ── The harness ───────────────────────────────────────────────────────────────────────────

    private suspend fun signInWith(
        provider: IdentityProvider = IdentityProvider.GOOGLE,
        token: IdToken = providerKey.sign(idTokenClaims()),
        identity: Identity? = this.identity,
        players: PlayerRepository = this.players,
        body: String = Protocol.json.encodeToString(SignInRequest(ApiVersion.CURRENT, token, TEST_NONCE)),
    ): Answer = signIn(identity, players, provider, body)

    private suspend fun refreshWith(token: SessionToken, identity: Identity? = this.identity): Answer =
        refreshSession(
            identity,
            players,
            Protocol.json.encodeToString(RefreshRequest(ApiVersion.CURRENT, token)),
        )

    private suspend fun refreshRawWith(body: String): Answer = refreshSession(identity, players, body)

    private suspend fun deleteWith(credentials: Credentials): Answer =
        deleteAccount(authenticator, players, credentials)

    private suspend fun foundWith(token: SessionToken): Answer =
        foundColony(colonies, authenticator, clock, bearer(token), emptySync())

    private suspend fun syncWith(token: SessionToken): Answer =
        syncColony(colonies, authenticator, clock, bearer(token), emptySync())

    private fun emptySync(): String =
        Protocol.json.encodeToString(SyncRequest(ApiVersion.CURRENT, emptyList()))

    private fun bearer(token: SessionToken): Credentials =
        Credentials(authorization = Protocol.BEARER_PREFIX + token.value, playerHeader = null)

    private fun Answer.session(status: HttpStatusCode? = null): SessionResponse {
        val answer = assertIs<Answer.Session>(this)
        status?.let { assertEquals(it, answer.status) }
        return answer.response
    }

    private fun Answer.colony() = assertIs<Answer.Colony>(this).response

    // Read off the **refresh** token, which is the one still readable after the clock has moved: an
    // access token is an hour long by design, and several of these tests are about what happens
    // hours or months later.
    private fun playerIn(session: SessionResponse): PlayerId =
        assertIs<SessionVerdict.Valid>(sessions.read(session.refreshToken, SessionKind.REFRESH)).player

    private companion object {

        val providerKey = ProviderKey("the-published-key")
    }
}
