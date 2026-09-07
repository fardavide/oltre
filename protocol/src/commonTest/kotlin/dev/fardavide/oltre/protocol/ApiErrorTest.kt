package dev.fardavide.oltre.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// The mirror of `VerbId` in `ClientVerbTest`, and it closes the same two holes for the same reason:
// an error the server can raise and the client cannot name is a failure the player is shown nothing
// about.
private enum class ErrorId {
    UNAUTHENTICATED,
    SESSION_EXPIRED,
    UNSUPPORTED_API_VERSION,
    NO_COLONY,
    STALE_COLONY,
    TOO_MANY_REQUESTS,
    MALFORMED,
    ALLIANCE_NAME_TAKEN,
    ALLIANCE_TAG_TAKEN,
    ALLIANCE_FULL,
    NO_SUCH_ALLIANCE,
    NOT_IN_AN_ALLIANCE,
    ALREADY_IN_AN_ALLIANCE,
    ALLIANCE_ROLE_TOO_LOW,
    STALE_ALLIANCE,
    INTERNAL,
}

private fun idOf(error: ApiError): ErrorId = when (error) {
    ApiError.Unauthenticated -> ErrorId.UNAUTHENTICATED
    ApiError.SessionExpired -> ErrorId.SESSION_EXPIRED
    is ApiError.UnsupportedApiVersion -> ErrorId.UNSUPPORTED_API_VERSION
    ApiError.NoColony -> ErrorId.NO_COLONY
    ApiError.StaleColony -> ErrorId.STALE_COLONY
    is ApiError.TooManyRequests -> ErrorId.TOO_MANY_REQUESTS
    is ApiError.Malformed -> ErrorId.MALFORMED
    ApiError.AllianceNameTaken -> ErrorId.ALLIANCE_NAME_TAKEN
    ApiError.AllianceTagTaken -> ErrorId.ALLIANCE_TAG_TAKEN
    ApiError.AllianceFull -> ErrorId.ALLIANCE_FULL
    ApiError.NoSuchAlliance -> ErrorId.NO_SUCH_ALLIANCE
    ApiError.NotInAnAlliance -> ErrorId.NOT_IN_AN_ALLIANCE
    ApiError.AlreadyInAnAlliance -> ErrorId.ALREADY_IN_AN_ALLIANCE
    ApiError.AllianceRoleTooLow -> ErrorId.ALLIANCE_ROLE_TOO_LOW
    ApiError.StaleAlliance -> ErrorId.STALE_ALLIANCE
    is ApiError.Internal -> ErrorId.INTERNAL
}

private val SAMPLES: List<ApiError> = listOf(
    ApiError.Unauthenticated,
    ApiError.SessionExpired,
    ApiError.UnsupportedApiVersion(
        oldestServed = ApiVersion.OLDEST_SERVED,
        current = ApiVersion.CURRENT,
    ),
    ApiError.NoColony,
    ApiError.StaleColony,
    ApiError.TooManyRequests(retryAfterSeconds = 12),
    ApiError.Malformed("clientInstant is not an instant"),
    ApiError.AllianceNameTaken,
    ApiError.AllianceTagTaken,
    ApiError.AllianceFull,
    ApiError.NoSuchAlliance,
    ApiError.NotInAnAlliance,
    ApiError.AlreadyInAnAlliance,
    ApiError.AllianceRoleTooLow,
    ApiError.StaleAlliance,
    ApiError.Internal("the store did not answer"),
)

class ApiErrorTest {

    @Test
    fun `every error has a sample`() {
        assertEquals(ErrorId.entries.toSet(), SAMPLES.mapTo(mutableSetOf(), ::idOf))
    }

    @Test
    fun `every error survives a round trip`() {
        SAMPLES.forEach { error ->
            val text = Protocol.json.encodeToString(ApiError.serializer(), error)
            assertEquals(error, Protocol.json.decodeFromString(ApiError.serializer(), text), text)
        }
    }

    @Test
    fun `the wire names are pinned`() {
        val encoded = SAMPLES.map { error ->
            val json = Protocol.json.encodeToJsonElement(ApiError.serializer(), error) as JsonObject
            (json["type"] as JsonPrimitive).content
        }
        assertEquals(
            listOf(
                "Unauthenticated",
                "SessionExpired",
                "UnsupportedApiVersion",
                "NoColony",
                "StaleColony",
                "TooManyRequests",
                "Malformed",
                "AllianceNameTaken",
                "AllianceTagTaken",
                "AllianceFull",
                "NoSuchAlliance",
                "NotInAnAlliance",
                "AlreadyInAnAlliance",
                "AllianceRoleTooLow",
                "StaleAlliance",
                "Internal",
            ),
            encoded,
        )
    }

    // **A wait the client cannot act on is worse than no number at all.** `TooManyRequests` exists so
    // a refused sign-in says *"in a moment"* rather than *"that did not make sense"*, and a negative
    // delay would have the client ask again immediately — the one behaviour the answer is trying to
    // stop. Guarded on construction rather than clamped, because a server that computed one has a
    // bug and hiding it would leave nothing to find.
    @Test
    fun `a wait cannot be negative`() {
        assertFailsWith<IllegalArgumentException> { ApiError.TooManyRequests(retryAfterSeconds = -1) }
    }

    // Zero is not the same mistake: a bucket that has just refilled is genuinely ready now, and the
    // client asking straight away is the right thing to do.
    @Test
    fun `a wait of no time at all is allowed`() {
        assertEquals(0, ApiError.TooManyRequests(retryAfterSeconds = 0).retryAfterSeconds)
    }

    // What the taxonomy is *for*: "sign in again" and "you cannot afford that" are different
    // sentences, and a client that could not tell them apart would have to say the vaguer one to
    // everybody. A refused verb is not an error at all — it comes back as data inside a successful
    // response, which is what `RejectionReason` is.
    @Test
    fun `a refused verb is not an api error`() {
        val response = Protocol.json.encodeToJsonElement(
            RejectionReason.serializer(),
            RejectionReason.Refused(VerbRefusal.INSUFFICIENT_RESOURCES),
        ) as JsonObject
        assertEquals("Refused", (response["type"] as JsonPrimitive).content)
    }

    @Test
    fun `a rejection reason survives a round trip`() {
        val reasons = listOf(
            RejectionReason.NotQueueable,
            *VerbRefusal.entries.map { RejectionReason.Refused(it) }.toTypedArray(),
        )
        reasons.forEach { reason ->
            val text = Protocol.json.encodeToString(RejectionReason.serializer(), reason)
            assertEquals(reason, Protocol.json.decodeFromString(RejectionReason.serializer(), text), text)
        }
    }
}
