package dev.fardavide.oltre.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

// The three credentials on this wire refuse to be blank, and the sign-in pair round-trips through
// the one codec both ends share. Everything about *whether a token is any good* is `:server`'s and
// is tested there; what is here is the shape.
class AuthTest {

    @Test
    fun `a credential that is not there is refused rather than carried`() {
        // Each of the three is a thing somebody else minted, so an empty one is a bug at the edge
        // that produced it and never a state the far end has an answer for. `ApiVersion` makes the
        // opposite call one file over and says why.
        assertFailsWith<IllegalArgumentException> { IdToken("") }
        assertFailsWith<IllegalArgumentException> { SessionToken(" ") }
        assertFailsWith<IllegalArgumentException> { SignInNonce("") }
    }

    @Test
    fun `a sign-in round-trips through the codec both ends share`() {
        val request = SignInRequest(
            apiVersion = ApiVersion.CURRENT,
            idToken = IdToken("header.payload.signature"),
            nonce = SignInNonce("drawn-by-the-client"),
        )

        val encoded = Protocol.json.encodeToString(request)

        assertEquals(request, Protocol.json.decodeFromString<SignInRequest>(encoded))
    }

    @Test
    fun `a session round-trips with both expiries intact`() {
        val response = SessionResponse(
            apiVersion = ApiVersion.CURRENT,
            accessToken = SessionToken("access"),
            accessExpiresAt = Instant.parse("2026-08-25T13:00:00Z"),
            refreshToken = SessionToken("refresh"),
            refreshExpiresAt = Instant.parse("2026-11-23T12:00:00Z"),
        )

        val encoded = Protocol.json.encodeToString(response)

        assertEquals(response, Protocol.json.decodeFromString<SessionResponse>(encoded))
    }

    @Test
    fun `the bearer scheme is spelled once and carries its own separator`() {
        // Both ends build the header from this, so the space is part of the constant rather than a
        // thing each end remembers to add. A client that concatenated without one would send
        // `Bearereyj…` and read exactly like a player who never signed in — `PLAYER_HEADER`'s own
        // failure mode, one slice later.
        assertEquals("Bearer token", Protocol.BEARER_PREFIX + "token")
        assertEquals("Authorization", Protocol.AUTHORIZATION_HEADER)
    }
}
