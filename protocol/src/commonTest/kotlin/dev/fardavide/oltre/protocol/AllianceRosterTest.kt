package dev.fardavide.oltre.protocol

import dev.fardavide.oltre.core.Experience
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private val NOW: Instant = Instant.parse("2026-09-05T09:00:00Z")

private val SAMPLE_MEMBER: AllianceMember = AllianceMember(
    id = AllianceMemberId("member-1"),
    profile = PlayerProfile(name = CommanderName("Ada di Notte"), mark = PlayerMark.Preset(MarkPreset.SEXTANT)),
    role = AllianceRole.ADMIN,
    experience = Experience(1_200),
    lastSyncedAt = NOW,
)

private val SAMPLE_JOIN_REQUEST: JoinRequest = JoinRequest(
    id = JoinRequestId("petition-1"),
    profile = PlayerProfile(name = null, mark = null),
    experience = Experience(340),
    askedAt = NOW,
)

// You can see who is in it — `alliance-sheet.md` §6. The denormalisation of experience and last-sync
// out of the snapshot is the roster slice's own; this file pins only the shape a member and a pending
// petition carry on the wire.
class AllianceRosterTest {

    @Test
    fun `a member survives the round trip`() {
        val text = Protocol.json.encodeToString(AllianceMember.serializer(), SAMPLE_MEMBER)

        assertEquals(SAMPLE_MEMBER, Protocol.json.decodeFromString(AllianceMember.serializer(), text))
    }

    // A default profile — nobody has chosen a name or a mark — reads exactly as `PlayerProfile`
    // already states it: both absences out loud rather than one inferred from the other.
    @Test
    fun `a member with no chosen profile states both absences out loud`() {
        val quiet = SAMPLE_MEMBER.copy(profile = PlayerProfile(name = null, mark = null))
        val encoded = Protocol.json.encodeToJsonElement(AllianceMember.serializer(), quiet) as JsonObject
        val profile = encoded["profile"] as JsonObject

        assertEquals(JsonNull, profile["name"])
        assertEquals(JsonNull, profile["mark"])
    }

    @Test
    fun `a join request survives the round trip`() {
        val text = Protocol.json.encodeToString(JoinRequest.serializer(), SAMPLE_JOIN_REQUEST)

        assertEquals(SAMPLE_JOIN_REQUEST, Protocol.json.decodeFromString(JoinRequest.serializer(), text))
    }

    // A plain member's read carries `null` for the pending list rather than an empty one — "this is
    // not yours to see" against "nobody is asking" are two different sentences.
    @Test
    fun `a roster response with no visibility into pending requests states null rather than empty`() {
        val response = AllianceRosterResponse(
            apiVersion = ApiVersion.CURRENT,
            members = listOf(SAMPLE_MEMBER),
            pending = null,
        )
        val encoded = Protocol.json.encodeToJsonElement(AllianceRosterResponse.serializer(), response) as JsonObject

        assertEquals(JsonNull, encoded["pending"])
        assertEquals(response, Protocol.json.decodeFromJsonElement(AllianceRosterResponse.serializer(), encoded))
    }

    @Test
    fun `a founder's roster response carries the pending list even when it is empty`() {
        val response = AllianceRosterResponse(
            apiVersion = ApiVersion.CURRENT,
            members = listOf(SAMPLE_MEMBER),
            pending = emptyList(),
        )
        val text = Protocol.json.encodeToString(AllianceRosterResponse.serializer(), response)

        assertEquals(response, Protocol.json.decodeFromString(AllianceRosterResponse.serializer(), text))
    }

    @Test
    fun `a roster response with pending requests survives the round trip`() {
        val response = AllianceRosterResponse(
            apiVersion = ApiVersion.CURRENT,
            members = listOf(SAMPLE_MEMBER),
            pending = listOf(SAMPLE_JOIN_REQUEST),
        )
        val text = Protocol.json.encodeToString(AllianceRosterResponse.serializer(), response)

        assertEquals(response, Protocol.json.decodeFromString(AllianceRosterResponse.serializer(), text))
    }
}
