package dev.fardavide.oltre.protocol

import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.SystemAddress
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

private val NOW: Instant = Instant.parse("2026-08-24T09:00:00Z")

private val SNAPSHOT: GameSnapshot = GameSnapshot(
    lastUpdatedAt = NOW,
    state = GameState.initial(GalaxySeed(4_711)),
)

private fun envelope(verb: ClientVerb, key: String): VerbEnvelope = VerbEnvelope(
    verb = verb,
    clientInstant = NOW,
    idempotencyKey = IdempotencyKey(key),
)

class SyncTest {

    @Test
    fun `a request carrying every verb survives a round trip`() {
        val request = SyncRequest(
            apiVersion = ApiVersion.CURRENT,
            envelopes = VERB_SAMPLES.mapIndexed { index, verb -> envelope(verb, "key-$index") },
        )
        val text = Protocol.json.encodeToString(SyncRequest.serializer(), request)
        assertEquals(request, Protocol.json.decodeFromString(SyncRequest.serializer(), text))
    }

    @Test
    fun `an empty request is a plain read`() {
        val request = SyncRequest(apiVersion = ApiVersion.CURRENT, envelopes = emptyList())
        val text = Protocol.json.encodeToString(SyncRequest.serializer(), request)
        assertEquals(request, Protocol.json.decodeFromString(SyncRequest.serializer(), text))
    }

    @Test
    fun `a response carrying a colony survives a round trip`() {
        val response = SyncResponse(
            apiVersion = ApiVersion.CURRENT,
            snapshot = SNAPSHOT,
            applied = setOf(IdempotencyKey("applied")),
            rejected = listOf(
                VerbRejection(
                    envelope = envelope(ClientVerb.StartUpgrade(BuildingType.NANITE_FACTORY), "refused"),
                    reason = RejectionReason.Refused(VerbRefusal.REQUIREMENTS_NOT_MET),
                ),
                VerbRejection(
                    envelope = envelope(
                        ClientVerb.StartSurvey(SystemAddress.of(SNAPSHOT.state.galaxy.home)),
                        "looked",
                    ),
                    reason = RejectionReason.NotQueueable,
                ),
            ),
        )
        val text = Protocol.json.encodeToString(SyncResponse.serializer(), response)
        assertEquals(response, Protocol.json.decodeFromString(SyncResponse.serializer(), text))
    }

    // The invariant that makes the two lists readable together: a client folds `applied` into
    // "these landed" and `rejected` into "these did not", so a key in both would put one row in two
    // places and there would be no honest way to draw it.
    // A mixed answer rather than a single clashing key, because the realistic shape of a broken
    // response is one row wrong among several right ones — and it is the shape that proves the
    // guard names the offender rather than the whole batch.
    @Test
    fun `a key cannot be applied and rejected at once`() {
        val clashing = IdempotencyKey("both")
        val innocent = IdempotencyKey("only rejected")
        assertFailsWith<IllegalArgumentException> {
            SyncResponse(
                apiVersion = ApiVersion.CURRENT,
                snapshot = SNAPSHOT,
                applied = setOf(clashing),
                rejected = listOf(clashing, innocent).map { key ->
                    VerbRejection(
                        envelope = VerbEnvelope(ClientVerb.ToggleFlightAlerts, NOW, key),
                        reason = RejectionReason.NotQueueable,
                    )
                },
            )
        }
    }

    @Test
    fun `two rejections cannot carry the same key`() {
        val key = IdempotencyKey("twice")
        assertFailsWith<IllegalArgumentException> {
            SyncResponse(
                apiVersion = ApiVersion.CURRENT,
                snapshot = SNAPSHOT,
                applied = emptySet(),
                rejected = List(2) {
                    VerbRejection(
                        envelope = VerbEnvelope(ClientVerb.ToggleFlightAlerts, NOW, key),
                        reason = RejectionReason.NotQueueable,
                    )
                },
            )
        }
    }

    // The same guard reached through the decoder rather than the constructor, which is the path
    // that actually matters: the client never *builds* a response — it reads one from a server it
    // has to take on trust. `#112` turns this into `ApiError.Malformed`.
    @Test
    fun `an incoherent response cannot be decoded`() {
        val coherent = SyncResponse(
            apiVersion = ApiVersion.CURRENT,
            snapshot = SNAPSHOT,
            applied = emptySet(),
            rejected = listOf(
                VerbRejection(
                    envelope = envelope(ClientVerb.ToggleFlightAlerts, "clash"),
                    reason = RejectionReason.NotQueueable,
                ),
            ),
        )
        val text = Protocol.json.encodeToString(SyncResponse.serializer(), coherent)
            .replace("\"applied\":[]", "\"applied\":[\"clash\"]")
        assertFailsWith<IllegalArgumentException> {
            Protocol.json.decodeFromString(SyncResponse.serializer(), text)
        }
    }

    // **A key the other end did not expect is fatal rather than dropped**, which is `GameSave`'s
    // own call and the reason `ApiVersion` means anything: an unknown field is a disagreement about
    // the contract, and silently ignoring it would let a mismatch look like a success.
    @Test
    fun `an unknown field is refused rather than ignored`() {
        val text = Protocol.json.encodeToString(
            SyncRequest.serializer(),
            SyncRequest(apiVersion = ApiVersion.CURRENT, envelopes = emptyList()),
        ).replaceFirst("{", "{\"invented\":1,")
        assertFailsWith<SerializationException> {
            Protocol.json.decodeFromString(SyncRequest.serializer(), text)
        }
    }

    // A key is minted at the edge for the reason the galaxy seed is — `core` reads no random source
    // — so the one thing this type can check is that the edge minted something.
    @Test
    fun `a blank idempotency key cannot be constructed`() {
        assertFailsWith<IllegalArgumentException> { IdempotencyKey(" ") }
    }

    @Test
    fun `an envelope encodes its verb under the discriminator`() {
        val text = Protocol.json.encodeToString(
            VerbEnvelope.serializer(),
            envelope(ClientVerb.ToggleFlightAlerts, "only"),
        )
        assertEquals(
            """{"verb":{"type":"ToggleFlightAlerts"},"clientInstant":"2026-08-24T09:00:00Z","idempotencyKey":"only"}""",
            text,
        )
    }
}
