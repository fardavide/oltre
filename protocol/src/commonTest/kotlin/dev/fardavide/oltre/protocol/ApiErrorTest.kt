package dev.fardavide.oltre.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

// The mirror of `VerbId` in `ClientVerbTest`, and it closes the same two holes for the same reason:
// an error the server can raise and the client cannot name is a failure the player is shown nothing
// about.
private enum class ErrorId {
    UNAUTHENTICATED,
    SESSION_EXPIRED,
    UNSUPPORTED_API_VERSION,
    NO_COLONY,
    STALE_COLONY,
    MALFORMED,
    INTERNAL,
}

private fun idOf(error: ApiError): ErrorId = when (error) {
    ApiError.Unauthenticated -> ErrorId.UNAUTHENTICATED
    ApiError.SessionExpired -> ErrorId.SESSION_EXPIRED
    is ApiError.UnsupportedApiVersion -> ErrorId.UNSUPPORTED_API_VERSION
    ApiError.NoColony -> ErrorId.NO_COLONY
    ApiError.StaleColony -> ErrorId.STALE_COLONY
    is ApiError.Malformed -> ErrorId.MALFORMED
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
    ApiError.Malformed("clientInstant is not an instant"),
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
                "Malformed",
                "Internal",
            ),
            encoded,
        )
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
