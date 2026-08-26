package dev.fardavide.oltre.server

import com.nimbusds.jwt.JWTClaimsSet
import dev.fardavide.oltre.protocol.ApiError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// **The endpoint Apple already knows about and that did not exist.** Step 22 of the provisioning
// walkthrough entered `https://api.oltre.space/v1/auth/apple/notifications` into the App ID months
// before there was anything to answer it, purely because `api.oltre.space` is permanent — and nothing
// is sent today because nobody has signed in. It stops being harmless the moment `#113` ships.
//
// Two things make it worth being careful about, and they pull in opposite directions. It is a POST
// target **anyone can reach**, and one of the things it is asked to do is delete an account — so it
// has to verify Apple's signature before it acts on anything. And it is Apple's only way of telling
// this server that a player's Apple Account is gone, so refusing everything would be its own kind of
// wrong.

// Apple's own name for itself, which is a fact about Apple rather than configuration — see
// `IdentityProvider`. The tests stand on a generated keypair under `TEST_ISSUER`, per `Tokens.kt`:
// nothing here reaches Apple and nothing in CI ever should.
private val APPLE_SPEC = testSpec(provider = IdentityProvider.APPLE)

private const val APPLE_SUBJECT = "001234.fedcba9876543210.1200"

// The shape Apple posts: a JSON object with one field, holding a compact JWS. The `events` claim
// inside it is itself **a JSON string rather than an object**, which is the one detail of this format
// most likely to be got wrong by reading the field names alone.
private fun notificationClaims(
    issuer: String = TEST_ISSUER,
    audience: String = WEB_CLIENT,
    issuedAt: Instant = TEST_NOW,
    events: String? = """{"type":"account-delete","sub":"$APPLE_SUBJECT","event_time":1756123200000}""",
): JWTClaimsSet = JWTClaimsSet.Builder()
    .issuer(issuer)
    .audience(audience)
    .issueTime(Date(issuedAt.toEpochMilliseconds()))
    .jwtID("a-notification-id")
    .apply { events?.let { claim("events", it) } }
    .build()

private fun eventsOf(type: String, subject: String = APPLE_SUBJECT): String =
    """{"type":"$type","sub":"$subject","event_time":1756123200000}"""

private fun envelope(token: String): String = """{"payload":"$token"}"""

private fun verifier(key: ProviderKey, clock: MovableClock): AppleNotificationVerifier =
    AppleNotificationVerifier(
        spec = APPLE_SPEC,
        keys = JwksKeys(FakeJwksSource(jwksOf(key)), clock),
        clock = clock,
    )

class AppleNotificationVerifierTest {

    private val key = ProviderKey(keyId = "apple-key-1")
    private val clock = MovableClock(TEST_NOW)
    private val verifier = verifier(key, clock)

    @Test
    fun `a genuine notification names the event and the subject`() = runTest {
        val token = key.sign(notificationClaims()).value

        val verdict = verifier.verify(envelope(token))

        assertEquals(NotificationVerdict.Trusted(AppleEvent.ACCOUNT_DELETE, APPLE_SUBJECT), verdict)
    }

    @Test
    fun `every event Apple publishes is understood`() = runTest {
        AppleEvent.entries.forEach { event ->
            val token = key.sign(notificationClaims(events = eventsOf(event.wire))).value

            assertEquals(NotificationVerdict.Trusted(event, APPLE_SUBJECT), verifier.verify(envelope(token)))
        }
    }

    // **The whole reason this endpoint cannot simply act on what it is posted.** Anybody can reach
    // the URL, and the request that deletes an account is a POST with a JSON body in it.
    @Test
    fun `a notification signed by somebody else is refused`() = runTest {
        val impostor = ProviderKey(keyId = "apple-key-1")
        val token = impostor.sign(notificationClaims()).value

        assertTrue(verifier.verify(envelope(token)) is NotificationVerdict.Refused)
    }

    @Test
    fun `a notification for another audience is refused`() = runTest {
        val token = key.sign(notificationClaims(audience = "somebody.else.example")).value

        assertTrue(verifier.verify(envelope(token)) is NotificationVerdict.Refused)
    }

    @Test
    fun `a notification from another issuer is refused`() = runTest {
        val token = key.sign(notificationClaims(issuer = "https://impostor.example")).value

        assertTrue(verifier.verify(envelope(token)) is NotificationVerdict.Refused)
    }

    // **Apple's notification tokens carry no expiry**, unlike its ID tokens — so freshness has to be
    // read off `iat` or not at all. A replay of a months-old `account-delete` is close to harmless on
    // its own, because the account it names is already gone; the bound is here so that a captured
    // notification does not stay usable forever against a subject that signs up again.
    @Test
    fun `a notification older than the freshness window is refused`() = runTest {
        val token = key.sign(notificationClaims(issuedAt = TEST_NOW - 2.days)).value

        assertTrue(verifier.verify(envelope(token)) is NotificationVerdict.Refused)
    }

    @Test
    fun `a notification from the future is refused`() = runTest {
        val token = key.sign(notificationClaims(issuedAt = TEST_NOW + 1.hours)).value

        assertTrue(verifier.verify(envelope(token)) is NotificationVerdict.Refused)
    }

    // Clocks disagree by seconds, and a notification refused for being four seconds early would be a
    // deletion this server never hears about.
    @Test
    fun `a notification a moment early is allowed`() = runTest {
        val token = key.sign(notificationClaims(issuedAt = TEST_NOW + 1.minutes)).value

        assertTrue(verifier.verify(envelope(token)) is NotificationVerdict.Trusted)
    }

    @Test
    fun `a notification with no issue time is refused`() = runTest {
        val claims = JWTClaimsSet.Builder()
            .issuer(TEST_ISSUER)
            .audience(WEB_CLIENT)
            .claim("events", eventsOf("account-delete"))
            .build()

        assertTrue(verifier.verify(envelope(key.sign(claims).value)) is NotificationVerdict.Refused)
    }

    @Test
    fun `a notification with no events claim is refused`() = runTest {
        val token = key.sign(notificationClaims(events = null)).value

        assertTrue(verifier.verify(envelope(token)) is NotificationVerdict.Refused)
    }

    @Test
    fun `an events claim that is not json is refused`() = runTest {
        val token = key.sign(notificationClaims(events = "not json at all")).value

        assertTrue(verifier.verify(envelope(token)) is NotificationVerdict.Refused)
    }

    // **An event type this build has never heard of is refused rather than ignored**, and the
    // direction matters: Apple adds types, and a server that silently answered "fine" to one it could
    // not read would leave nothing anywhere saying it had arrived. A refusal makes Apple retry and
    // puts a line in the log.
    @Test
    fun `an event type nobody publishes is refused`() = runTest {
        val token = key.sign(notificationClaims(events = eventsOf("something-new"))).value

        assertTrue(verifier.verify(envelope(token)) is NotificationVerdict.Refused)
    }

    @Test
    fun `an event naming nobody is refused`() = runTest {
        val token = key.sign(notificationClaims(events = eventsOf("account-delete", subject = ""))).value

        assertTrue(verifier.verify(envelope(token)) is NotificationVerdict.Refused)
    }

    // **Six shapes the envelope can be wrong, and none of them is exotic.** This is a public POST
    // target, so the body is whatever anybody felt like sending — and every one of these has to end
    // in a refusal rather than in an exception escaping the route as a bare 500. They are one test
    // because they are one rule: if the `payload` field is not a non-empty JSON string, there is
    // nothing here to verify.
    @Test
    fun `a body that is not an envelope holding a token is refused`() = runTest {
        val bodies = listOf(
            "not json",
            "[1, 2, 3]",
            "\"just a string\"",
            """{"notification":"something"}""",
            """{"payload":{"nested":"object"}}""",
            """{"payload":123}""",
            """{"payload":""}""",
        )

        bodies.forEach { body ->
            assertTrue(verifier.verify(body) is NotificationVerdict.Refused, body)
        }
    }

    // The same rule one level in, on the claim that is itself a JSON document. `type` and `sub` are
    // read out of a blob Apple encoded as a string, so both can be absent, both can be the wrong kind
    // of value, and neither may take the request down with it.
    @Test
    fun `an events claim that does not name a type and a subject is refused`() = runTest {
        val payloads = listOf(
            """{"sub":"$APPLE_SUBJECT"}""",
            """{"type":123,"sub":"$APPLE_SUBJECT"}""",
            """{"type":{"nested":"object"},"sub":"$APPLE_SUBJECT"}""",
            """{"type":"account-delete"}""",
            """{"type":"account-delete","sub":123}""",
            """{"type":"account-delete","sub":{"nested":"object"}}""",
            """["account-delete"]""",
        )

        payloads.forEach { events ->
            val token = key.sign(notificationClaims(events = events)).value
            assertTrue(verifier.verify(envelope(token)) is NotificationVerdict.Refused, events)
        }
    }
}

// **What the route does with a verdict**, which is where the one genuine design decision in this file
// lives: `account-delete` is not the only thing Apple sends and is the only thing that deletes.
class AppleNotificationEndpointTest {

    private val key = ProviderKey(keyId = "apple-key-1")
    private val clock = MovableClock(TEST_NOW)
    private val colonies = InMemoryColonyRepository()
    private val players = InMemoryPlayerRepository(colonies, sequentialPlayerIds())
    private val identity = Identity(
        verifier = IdTokenVerifier(mapOf(IdentityProvider.APPLE to APPLE_SPEC), JwksKeys(FakeJwksSource(jwksOf(key)), clock), clock),
        sessions = Sessions(TEST_SIGNING_KEY, clock),
        notifications = verifier(key, clock),
    )

    private suspend fun colonised(): PlayerId {
        val player = players.resolve(ProviderIdentity(IdentityProvider.APPLE.providerName, APPLE_SUBJECT))
        colonies.found(player, freshColony())
        return player
    }

    private suspend fun notify(event: AppleEvent): Answer =
        appleNotification(identity, players, envelope(key.sign(notificationClaims(events = eventsOf(event.wire))).value))

    // **Apple's account was deleted, so ours goes too.** It is the one event that means the subject
    // will never come back: nothing can ever sign in as it again, so a colony left behind is a colony
    // nobody can reach and a provider subject nobody has a reason to still hold.
    @Test
    fun `an account delete forgets the player and the colony`() = runTest {
        val player = colonised()

        assertEquals(Answer.Noted, notify(AppleEvent.ACCOUNT_DELETE))

        assertFalse(players.exists(player))
        assertNull(colonies.colonyOf(player))
    }

    // **The call this endpoint exists to get right, and the obvious reading is the wrong one.**
    // `consent-revoked` is the player turning Sign in with Apple off for this app — an unlink, not a
    // deletion. Signing in again re-consents and hands back **the same subject**, so a colony deleted
    // here would be a year of play destroyed by a settings toggle that Apple lets anybody undo.
    @Test
    fun `a revoked consent leaves the colony alone`() = runTest {
        val player = colonised()

        assertEquals(Answer.Noted, notify(AppleEvent.CONSENT_REVOKED))

        assertTrue(players.exists(player))
        assertNotNull(colonies.colonyOf(player))
    }

    // Nothing here stores an email — `#106` §4 chose the subject precisely so there would be less to
    // hold — so these two are acknowledged and are about nothing this server knows.
    @Test
    fun `an email change is acknowledged and changes nothing`() = runTest {
        val player = colonised()

        assertEquals(Answer.Noted, notify(AppleEvent.EMAIL_DISABLED))
        assertEquals(Answer.Noted, notify(AppleEvent.EMAIL_ENABLED))

        assertTrue(players.exists(player))
    }

    // Apple retries anything that is not a 2xx, so a notification about somebody who never signed in
    // here has to be an answer rather than a 404 — otherwise it comes back for days.
    @Test
    fun `a delete for somebody who never signed in is still accepted`() = runTest {
        assertEquals(Answer.Noted, notify(AppleEvent.ACCOUNT_DELETE))
    }

    // **A 401 and not a 400.** Apple retries a failure, and a forged notification should not be
    // retried by anybody — but the answer also has to be one nothing reads as "keep sending". The
    // reason lives in the verdict for a log to carry; the body says one sentence, exactly as a
    // refused sign-in does.
    @Test
    fun `a forged notification is refused and touches nothing`() = runTest {
        val player = colonised()
        val impostor = ProviderKey(keyId = "apple-key-1")
        val forged = envelope(impostor.sign(notificationClaims()).value)

        val answer = appleNotification(identity, players, forged)

        assertEquals(Answer.Failed(HttpStatusCode.Unauthorized, ApiError.Unauthenticated), answer)
        assertTrue(players.exists(player))
    }

    // Not a dead control, for `signIn`'s reason: a server with no session key still has the URL Apple
    // was given, and `503` with a diagnostic says which variable is missing.
    @Test
    fun `with no identity configured the endpoint says so`() = runTest {
        val answer = appleNotification(null, players, envelope(key.sign(notificationClaims()).value))

        assertEquals(HttpStatusCode.ServiceUnavailable, answer.status)
    }

    // The store is a network away, so a failure there must become an `ApiError` rather than escape
    // the route — `answering`'s one `catch`, which every function in `AuthEndpoints.kt` is inside.
    @Test
    fun `a store that cannot answer becomes an internal error`() = runTest {
        val answer = appleNotification(
            identity,
            SpeechlessRepository(),
            envelope(key.sign(notificationClaims()).value),
        )

        assertEquals(HttpStatusCode.InternalServerError, answer.status)
    }
}
