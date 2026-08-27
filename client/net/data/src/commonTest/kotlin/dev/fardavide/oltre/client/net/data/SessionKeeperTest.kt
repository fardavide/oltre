package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.SessionResponse
import dev.fardavide.oltre.protocol.SessionToken
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val KEEPER_SIGNED_IN_AT: Instant = Instant.parse("2026-08-26T09:00:00Z")

private fun session(
    access: String = "access.1",
    refresh: String = "refresh.1",
    accessExpiresAt: Instant = KEEPER_SIGNED_IN_AT + 1.hours,
    refreshExpiresAt: Instant = KEEPER_SIGNED_IN_AT + 90.days,
): SessionResponse = SessionResponse(
    apiVersion = ApiVersion.CURRENT,
    accessToken = SessionToken(access),
    accessExpiresAt = accessExpiresAt,
    refreshToken = SessionToken(refresh),
    refreshExpiresAt = refreshExpiresAt,
)

// A clock a test moves by hand, for the reason every other seam in this repository is a parameter:
// what this class decides is entirely a question about *when*, and one that read the wall clock
// could not be asked any of the questions below.
private class KeeperClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
}

private class KeeperScenario(
    stored: SessionResponse? = session(),
    val api: FakeOltreApi = FakeOltreApi(session = session(access = "access.2", refresh = "refresh.2")),
    val clock: KeeperClock = KeeperClock(KEEPER_SIGNED_IN_AT),
) {
    val store = FakeSessionStore(stored)
    val keeper = SessionKeeper(api = api, store = store, clock = clock)
}

class SessionKeeperTest {

    @Test
    fun `a device nobody has signed in on has no session to send`() = runTest {
        assertEquals(Credential.Gone, KeeperScenario(stored = null).keeper.current())
    }

    @Test
    fun `an access token still good is sent as it is`() = runTest {
        // given — half an hour into a one-hour token
        val scenario = KeeperScenario()
        scenario.clock.instant = KEEPER_SIGNED_IN_AT + 30.minutes

        // when
        val current = scenario.keeper.current()

        // then — and nothing was asked of the server
        assertEquals(Credential.Held(SessionToken("access.1")), current)
        assertEquals(emptyList(), scenario.api.signIns())
    }

    // **Refreshed before it expires rather than after**, and the margin is the whole point: a token
    // checked at the instant it is sent can still expire in flight on a slow connection, and the
    // cost of being a minute early is one request every fifty-nine minutes.
    @Test
    fun `an access token about to expire is refreshed before it is used`() = runTest {
        // given — inside the margin, not yet past the expiry
        val scenario = KeeperScenario()
        scenario.clock.instant = KEEPER_SIGNED_IN_AT + 1.hours - 30.seconds

        // when
        val current = scenario.keeper.current()

        // then
        assertEquals(Credential.Held(SessionToken("access.2")), current)
    }

    @Test
    fun `a refreshed pair is kept so the next launch does not sign in again`() = runTest {
        // given
        val scenario = KeeperScenario()
        scenario.clock.instant = KEEPER_SIGNED_IN_AT + 2.hours

        // when
        scenario.keeper.current()

        // then
        assertEquals(SessionToken("refresh.2"), scenario.store.read()?.refreshToken)
    }

    // **Ninety days without opening the game ends at the gate**, and it ends there without a round
    // trip: the refresh token's own expiry is on the wire, so a client can tell that asking is
    // pointless rather than learning it from a 401.
    @Test
    fun `a refresh token ninety days old is not even offered`() = runTest {
        // given
        val scenario = KeeperScenario()
        scenario.clock.instant = KEEPER_SIGNED_IN_AT + 91.days

        // when
        val current = scenario.keeper.current()

        // then
        assertEquals(Credential.Gone, current)
        assertEquals(emptyList(), scenario.api.signIns())
    }

    @Test
    fun `a session that has run out entirely is forgotten rather than left to fail again`() = runTest {
        // given
        val scenario = KeeperScenario()
        scenario.clock.instant = KEEPER_SIGNED_IN_AT + 91.days

        // when
        scenario.keeper.current()

        // then
        assertNull(scenario.store.read())
    }

    // The refresh token was still in date and the server refused it anyway — an account deleted on
    // another device is the case that matters. There is nothing left to try.
    @Test
    fun `a refresh the server refuses ends the session`() = runTest {
        // given
        val scenario = KeeperScenario()
        scenario.api.error = ApiError.Unauthenticated
        scenario.clock.instant = KEEPER_SIGNED_IN_AT + 2.hours

        // when
        val current = scenario.keeper.current()

        // then
        assertEquals(Credential.Gone, current)
        assertNull(scenario.store.read())
    }

    // **A refresh nobody answered is not a signed-out player**, and the distinction is the whole
    // reason `ApiResult` splits `Refused` from `Unreachable`. Dropping the session here would send
    // somebody to a sign-in screen they cannot use, on a train, and lose the credential that would
    // have worked when the signal came back.
    //
    // **It is answered as its own member and not as an absence**, which is #113's correction and the
    // reason this test now asserts twice. Keeping the session on disk was never enough on its own:
    // the caller read *no token* as *signed out* and cleared it two frames later, so the promise this
    // test's first assertion makes was undone by the code that consumed it. `Credential.Unreachable`
    // is what carries the reason far enough to be acted on.
    @Test
    fun `a refresh that never reached anybody keeps the session for later`() = runTest {
        // given
        val scenario = KeeperScenario()
        scenario.api.offline = true
        scenario.clock.instant = KEEPER_SIGNED_IN_AT + 2.hours

        // when
        val current = scenario.keeper.current()

        // then
        assertEquals(Credential.Unreachable, current)
        assertEquals(SessionToken("refresh.1"), scenario.store.read()?.refreshToken)
    }

    // What a sign-in does with what it got. Written before it is read, so a process killed on the
    // next line has the session rather than making the player sign in twice.
    @Test
    fun `a session from a sign-in is kept and used`() = runTest {
        // given
        val scenario = KeeperScenario(stored = null)

        // when
        scenario.keeper.adopt(session(access = "fresh.access", refresh = "fresh.refresh"))

        // then
        assertEquals(Credential.Held(SessionToken("fresh.access")), scenario.keeper.current())
        assertEquals(SessionToken("fresh.refresh"), scenario.store.read()?.refreshToken)
    }

    @Test
    fun `forgetting a session leaves nothing on the device`() = runTest {
        // given
        val scenario = KeeperScenario()

        // when
        scenario.keeper.forget()

        // then
        assertNull(scenario.store.read())
        assertEquals(Credential.Gone, scenario.keeper.current())
    }

    // **The forced renewal, and it is a different question from `current`.** `current` asks *"is
    // this token still in date"*; this one is asked after the server has said `SessionExpired`, which
    // means the answer to that question was wrong — a clock that disagrees with the server's by more
    // than the margin, most likely. Refreshing on the token's own arithmetic would return the same
    // dead token forever.
    @Test
    fun `a session the server called expired is renewed even though the clock disagrees`() = runTest {
        // given — by this device's reckoning the token has fifty-nine minutes left
        val scenario = KeeperScenario()
        scenario.clock.instant = KEEPER_SIGNED_IN_AT + 1.minutes

        // when
        val renewed = scenario.keeper.renew()

        // then
        assertEquals(Credential.Held(SessionToken("access.2")), renewed)
    }

    // **A forced renewal nobody answered is a train too**, and it needs saying separately because
    // this is the arm reached *after* the server has spoken once: the app has signal enough to have
    // been told `SessionExpired` and then loses it. The session is intact and the answer is the
    // offline one, exactly as it is for `current`.
    @Test
    fun `a forced renewal that never reached anybody keeps the session for later`() = runTest {
        // given
        val scenario = KeeperScenario()
        scenario.api.offline = true

        // when
        val renewed = scenario.keeper.renew()

        // then
        assertEquals(Credential.Unreachable, renewed)
        assertEquals(SessionToken("refresh.1"), scenario.store.read()?.refreshToken)
    }
}
