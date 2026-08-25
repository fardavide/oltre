package dev.fardavide.oltre.protocol

import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

// **Nothing on this wire has a default, and this is what says so.**
//
// It is `GameSave`'s rule one layer out. A save that does not spell out its own schema version is a
// save no future build can migrate, so the codec sets `encodeDefaults` and the version field is
// written even when it is the default. The same reasoning forbids a *reader* from filling a gap:
// a request arriving without its `apiVersion` is a request from something that does not agree about
// the contract, and guessing at what it meant is how a mismatch comes to look like a success.
//
// The mechanism is the generated deserialization constructor, which checks a bitmask of the fields
// it saw and raises for the ones it did not. That check is invisible in the source — it is the one
// piece of behaviour in this module nobody wrote — which is exactly why it is worth a test rather
// than a comment.
private fun <T> assertEveryFieldRequired(serializer: KSerializer<T>, value: T) {
    val whole = Protocol.json.encodeToJsonElement(serializer, value) as JsonObject
    // `type` is the polymorphic discriminator rather than a field: dropping it is a different
    // failure — "which member is this" — and `ClientVerbTest` pins those names already.
    val fields = whole.keys - "type"
    assertTrue(fields.isNotEmpty(), "nothing to drop from $whole")
    fields.forEach { field ->
        assertFailsWith<SerializationException>("dropping $field was accepted: $whole") {
            Protocol.json.decodeFromJsonElement(serializer, JsonObject(whole - field))
        }
    }
}

private val NOW: Instant = Instant.parse("2026-08-24T09:00:00Z")

private val ENVELOPE: VerbEnvelope = VerbEnvelope(
    verb = ClientVerb.ToggleFlightAlerts,
    clientInstant = NOW,
    idempotencyKey = IdempotencyKey("only"),
)

class RequiredFieldsTest {

    // Every verb that carries a subject at all. The three that carry none are `data object`s with
    // nothing to drop, which is the whole of why they are objects.
    @Test
    fun `a verb missing its subject is refused`() {
        val carrying = VERB_SAMPLES.filter { it != ClientVerb.ToggleFlightAlerts }
        assertTrue(carrying.size == VERB_SAMPLES.size - 1)
        carrying.forEach { assertEveryFieldRequired(ClientVerb.serializer(), it) }
    }

    @Test
    fun `an envelope missing any of its three parts is refused`() {
        assertEveryFieldRequired(VerbEnvelope.serializer(), ENVELOPE)
    }

    @Test
    fun `a request missing its version is refused`() {
        assertEveryFieldRequired(
            SyncRequest.serializer(),
            SyncRequest(apiVersion = ApiVersion.CURRENT, envelopes = listOf(ENVELOPE)),
        )
    }

    @Test
    fun `a response missing any part of its answer is refused`() {
        assertEveryFieldRequired(
            SyncResponse.serializer(),
            SyncResponse(
                apiVersion = ApiVersion.CURRENT,
                snapshot = GameSnapshot(
                    lastUpdatedAt = NOW,
                    state = GameState.initial(GalaxySeed(4_711)),
                ),
                applied = setOf(IdempotencyKey("applied")),
                rejected = listOf(
                    VerbRejection(envelope = ENVELOPE, reason = RejectionReason.NotQueueable),
                ),
            ),
        )
    }

    @Test
    fun `a rejection missing its envelope or its reason is refused`() {
        assertEveryFieldRequired(
            VerbRejection.serializer(),
            VerbRejection(
                envelope = ENVELOPE,
                reason = RejectionReason.Refused(VerbRefusal.DEPLETED),
            ),
        )
    }

    @Test
    fun `a refusal missing the reason it refused is rejected`() {
        assertEveryFieldRequired(
            RejectionReason.serializer(),
            RejectionReason.Refused(VerbRefusal.NO_IDLE_SCOUT),
        )
    }

    @Test
    fun `a sign-in missing its token or its nonce is refused`() {
        assertEveryFieldRequired(
            SignInRequest.serializer(),
            SignInRequest(
                apiVersion = ApiVersion.CURRENT,
                idToken = IdToken("header.payload.signature"),
                nonce = SignInNonce("drawn-by-the-client"),
            ),
        )
    }

    @Test
    fun `a refresh missing its token is refused`() {
        assertEveryFieldRequired(
            RefreshRequest.serializer(),
            RefreshRequest(apiVersion = ApiVersion.CURRENT, refreshToken = SessionToken("refresh")),
        )
    }

    @Test
    fun `a session missing either token or either expiry is refused`() {
        // The two expiries are the ones worth pinning: a client that read them as absent would
        // either refresh on every request or never, and neither failure says anything out loud.
        assertEveryFieldRequired(
            SessionResponse.serializer(),
            SessionResponse(
                apiVersion = ApiVersion.CURRENT,
                accessToken = SessionToken("access"),
                accessExpiresAt = NOW,
                refreshToken = SessionToken("refresh"),
                refreshExpiresAt = NOW,
            ),
        )
    }

    // The three errors that carry a payload. The four that do not are `data object`s.
    @Test
    fun `an error missing its payload is refused`() {
        listOf(
            ApiError.UnsupportedApiVersion(
                oldestServed = ApiVersion.OLDEST_SERVED,
                current = ApiVersion.CURRENT,
            ),
            ApiError.Malformed("clientInstant is not an instant"),
            ApiError.Internal("the store did not answer"),
        ).forEach { assertEveryFieldRequired(ApiError.serializer(), it) }
    }
}
