package dev.fardavide.oltre.server

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import dev.fardavide.oltre.protocol.Protocol
import dev.fardavide.oltre.protocol.SessionToken
import java.util.Base64
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

// **The app's own credential, which is the one nobody else ever verifies.** What matters here is
// almost entirely the refusals: a token this server signed is easy to get right, and every
// interesting property is about one it did not.
class SessionsTest {

    private val clock = MovableClock(TEST_NOW)
    private val sessions = Sessions(TEST_SIGNING_KEY, clock)
    private val davide = PlayerId("player-0001")

    @Test
    fun `a session names the player it was issued to`() {
        val issued = sessions.issue(davide)

        assertEquals(SessionVerdict.Valid(davide), sessions.read(issued.access, SessionKind.ACCESS))
        assertEquals(SessionVerdict.Valid(davide), sessions.read(issued.refresh, SessionKind.REFRESH))
    }

    @Test
    fun `the two halves are different tokens and run out at different times`() {
        val issued = sessions.issue(davide)

        assertNotEquals(issued.access, issued.refresh)
        assertEquals(TEST_NOW + 1.hours, issued.accessExpiresAt)
        assertEquals(TEST_NOW + 90.days, issued.refreshExpiresAt)
    }

    @Test
    fun `an access token past its hour is expired rather than invalid`() {
        // **The distinction is the whole reason `ApiError.SessionExpired` exists.** Expired is the
        // one failure the app fixes by itself — refresh, ask again, no screen — so an answer of
        // `Invalid` here would send a player to a sign-in they did not need.
        val issued = sessions.issue(davide)

        clock.advanceBy(61.minutes)

        assertEquals(SessionVerdict.Expired, sessions.read(issued.access, SessionKind.ACCESS))
    }

    @Test
    fun `a refresh token still works long after the access token it came with is gone`() {
        // Ninety days is how long somebody can ignore this game and still not sign in again, which
        // for a check-in game is the difference between a product and a chore.
        val issued = sessions.issue(davide)

        clock.advanceBy(89.days)

        assertEquals(SessionVerdict.Expired, sessions.read(issued.access, SessionKind.ACCESS))
        assertEquals(SessionVerdict.Valid(davide), sessions.read(issued.refresh, SessionKind.REFRESH))
    }

    @Test
    fun `a refresh token sent where an access token was wanted is invalid rather than expired`() {
        // **Not `Expired`**, and the difference is a loop: told to refresh, a client would refresh
        // and send the wrong token again, forever. Told it is invalid, it signs in and stops.
        val issued = sessions.issue(davide)

        assertIs<SessionVerdict.Invalid>(sessions.read(issued.refresh, SessionKind.ACCESS))
        assertIs<SessionVerdict.Invalid>(sessions.read(issued.access, SessionKind.REFRESH))
    }

    @Test
    fun `a token signed with a different key is refused`() {
        val elsewhere = Sessions(SessionSigningKey("a-different-key-of-at-least-32-bytes-long"), clock)
        val issued = elsewhere.issue(davide)

        assertIs<SessionVerdict.Invalid>(sessions.read(issued.access, SessionKind.ACCESS))
    }

    @Test
    fun `a forged token with an expiry in the past is invalid and never expired`() {
        // Order matters and this is the test that pins it: signature before claims. Answering
        // `Expired` to something nobody signed would tell a client to retry a token that can never
        // become good.
        val issued = sessions.issue(davide)
        val forged = SessionToken(issued.access.value.dropLast(4) + "AAAA")

        clock.advanceBy(2.hours)

        assertIs<SessionVerdict.Invalid>(sessions.read(forged, SessionKind.ACCESS))
    }

    @Test
    fun `a string that is not a token at all is refused rather than raised over`() {
        assertIs<SessionVerdict.Invalid>(sessions.read(SessionToken("nonsense"), SessionKind.ACCESS))
    }

    @Test
    fun `a signature this server made over something that is not a claims set is refused`() {
        // A JWS carries an arbitrary payload, so "we signed it" and "the body is a set of claims"
        // are two separate facts. `IdTokenVerifier` has the same arm and the same test.
        val signed = JWSObject(JWSHeader(JWSAlgorithm.HS256), Payload("this is not a claims set"))
        signed.sign(MACSigner(TEST_SIGNING_KEY.value.encodeToByteArray()))

        assertIs<SessionVerdict.Invalid>(sessions.read(SessionToken(signed.serialize()), SessionKind.ACCESS))
    }

    // ── Tokens this server would have signed, if it had written them like this ────────────────
    //
    // Each one below is signed with **the real key** and is wrong in exactly one other way, which is
    // the only way to reach these arms: a token that fails the signature never gets to them. They are
    // not hypothetical — a future change to `sign` that dropped a claim would produce exactly these,
    // and every one of them has to be a refusal rather than a crash or a pass.

    @Test
    fun `a genuine token whose header was rewritten to claim another algorithm is refused`() {
        // Algorithm substitution: keep the body and the signature, change what the header says was
        // used to make it. The algorithm is pinned here rather than read out of the token for
        // exactly this reason — a header is something the sender chose.
        val issued = sessions.issue(davide).access
        val (_, payload, signature) = issued.value.split('.')
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"HS512"}""".encodeToByteArray())

        assertIs<SessionVerdict.Invalid>(sessions.read(SessionToken("$header.$payload.$signature"), SessionKind.ACCESS))
    }

    @Test
    fun `a token signed with this key by something that is not this server is refused`() {
        // The signing key is a shared secret, so "signed with our key" and "minted by us" are not the
        // same claim. The issuer is what tells them apart.
        val forged = forge { it.issuer("somebody-else") }

        assertIs<SessionVerdict.Invalid>(sessions.read(forged, SessionKind.ACCESS))
    }

    @Test
    fun `a token whose kind is not even a string is refused rather than raised over`() {
        val forged = forge { it.claim("kind", 42) }

        assertIs<SessionVerdict.Invalid>(sessions.read(forged, SessionKind.ACCESS))
    }

    @Test
    fun `a token with no expiry is refused rather than accepted forever`() {
        val forged = forge { it.expirationTime(null) }

        assertIs<SessionVerdict.Invalid>(sessions.read(forged, SessionKind.ACCESS))
    }

    @Test
    fun `a token that names nobody is refused`() {
        val forged = forge { it.subject(null) }

        assertIs<SessionVerdict.Invalid>(sessions.read(forged, SessionKind.ACCESS))
    }

    @Test
    fun `a token whose subject is nothing but spaces is refused`() {
        val forged = forge { it.subject("   ") }

        assertIs<SessionVerdict.Invalid>(sessions.read(forged, SessionKind.ACCESS))
    }

    @Test
    fun `a signing key too short to be one is refused when it is built rather than at the first sign-in`() {
        // Nimbus refuses a key shorter than the digest outright, so without this guard the failure
        // is a stack trace on somebody's first sign-in instead of a server that will not boot.
        assertFailsWith<IllegalArgumentException> { SessionSigningKey("too-short") }
    }

    // ── The scheme ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a bearer header hands back the token inside it`() {
        assertEquals(SessionToken("abc"), bearerToken("${Protocol.BEARER_PREFIX}abc"))
    }

    @Test
    fun `the scheme is matched however the client capitalised it`() {
        // RFC 7235 says the scheme is case-insensitive and a client's HTTP stack is entitled to rely
        // on that. A server that demanded `Bearer` exactly would refuse a correct client.
        assertEquals(SessionToken("abc"), bearerToken("bearer abc"))
        assertEquals(SessionToken("abc"), bearerToken("BEARER abc"))
    }

    @Test
    fun `anything that is not a bearer credential is no credential at all`() {
        // All of these are `ApiError.Unauthenticated`, which is the same answer as a token that is
        // no good — a request cannot be told the difference in any way that would help it.
        assertNull(bearerToken(null))
        assertNull(bearerToken(""))
        assertNull(bearerToken("Bearer "))
        assertNull(bearerToken("Bearer    "))
        assertNull(bearerToken("Basic abc"))
        assertNull(bearerToken("abc"))
    }

    // A token signed with the real key and wrong in exactly the one way the caller names. It starts
    // from what `issue` would have produced, so every test above says only what it changed.
    private fun forge(change: (JWTClaimsSet.Builder) -> JWTClaimsSet.Builder): SessionToken {
        val claims = JWTClaimsSet.Builder()
            .issuer("oltre")
            .subject(davide.value)
            .claim("kind", "access")
            .expirationTime(Date((TEST_NOW + 1.hours).toEpochMilliseconds()))
        val jwt = SignedJWT(JWSHeader(JWSAlgorithm.HS256), change(claims).build())
        jwt.sign(MACSigner(TEST_SIGNING_KEY.value.encodeToByteArray()))
        return SessionToken(jwt.serialize())
    }
}
