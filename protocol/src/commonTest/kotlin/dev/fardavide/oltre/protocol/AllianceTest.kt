package dev.fardavide.oltre.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// The mirror of `ErrorId` in `ApiErrorTest`, for `AllianceStanding`.
private enum class AllianceStandingId {
    UNAFFILIATED,
    PETITIONING,
    ENLISTED,
}

private fun idOf(standing: AllianceStanding): AllianceStandingId = when (standing) {
    AllianceStanding.Unaffiliated -> AllianceStandingId.UNAFFILIATED
    is AllianceStanding.Petitioning -> AllianceStandingId.PETITIONING
    is AllianceStanding.Enlisted -> AllianceStandingId.ENLISTED
}

private val SAMPLE_ALLIANCE: Alliance = Alliance(
    id = AllianceId("ferro-alto"),
    name = AllianceName("Ferro Alto"),
    tag = AllianceTag("FRA"),
    level = AllianceLevel(4),
    seats = AllianceSeats(taken = 9, cap = 16),
)

private val STANDING_SAMPLES: List<AllianceStanding> = listOf(
    AllianceStanding.Unaffiliated,
    AllianceStanding.Petitioning(alliance = SAMPLE_ALLIANCE),
    AllianceStanding.Enlisted(alliance = SAMPLE_ALLIANCE, role = AllianceRole.ADMIN),
)

// **The shape every later alliance slice speaks**, pure data with no table, no route and no `core`
// behind it — `#137`, slice 1 of the alliance epic. See `alliance-sheet.md` §1.1 for why nothing here
// is spatial and §1.3 for why every type below is argued to completeness rather than grown a field at
// a time.
class AllianceTest {

    // ── The identifiers ───────────────────────────────────────────────────────────────────────

    @Test
    fun `an identifier minted by the server cannot be blank`() {
        assertFailsWith<IllegalArgumentException> { AllianceId("") }
        assertFailsWith<IllegalArgumentException> { AllianceId("   ") }
        assertFailsWith<IllegalArgumentException> { AllianceMemberId("") }
        assertFailsWith<IllegalArgumentException> { JoinRequestId("") }
    }

    // ── The name ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a name is what the founder typed and not an absence`() {
        assertFailsWith<IllegalArgumentException> { AllianceName("") }
        assertFailsWith<IllegalArgumentException> { AllianceName("   ") }
    }

    @Test
    fun `a name that was not trimmed before it was sent is refused`() {
        assertFailsWith<IllegalArgumentException> { AllianceName(" Ferro Alto") }
        assertFailsWith<IllegalArgumentException> { AllianceName("Ferro Alto ") }
    }

    @Test
    fun `a name longer than the contract accepts is refused`() {
        val longest = "a".repeat(AllianceName.MAX_LENGTH)
        assertEquals(longest, AllianceName(longest).value)
        assertFailsWith<IllegalArgumentException> { AllianceName("a".repeat(AllianceName.MAX_LENGTH + 1)) }
    }

    @Test
    fun `the bound is thirty-two and generous rather than measured`() {
        assertEquals(32, AllianceName.MAX_LENGTH)
    }

    @Test
    fun `a name that breaks the bound is refused on the way in as well as on the way out`() {
        val overlong = JsonPrimitive("a".repeat(AllianceName.MAX_LENGTH + 1))

        assertFailsWith<IllegalArgumentException> {
            Protocol.json.decodeFromJsonElement(AllianceName.serializer(), overlong)
        }
    }

    // ── The tag ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a tag outside three to four characters is refused`() {
        assertFailsWith<IllegalArgumentException> { AllianceTag("FR") }
        assertFailsWith<IllegalArgumentException> { AllianceTag("FRALT") }
        assertEquals("FRA", AllianceTag("FRA").value)
        assertEquals("FRAL", AllianceTag("FRAL").value)
    }

    @Test
    fun `the tag bound is three to four`() {
        assertEquals(3, AllianceTag.MIN_LENGTH)
        assertEquals(4, AllianceTag.MAX_LENGTH)
    }

    @Test
    fun `a tag is uppercase ascii letters and digits refused rather than folded`() {
        assertFailsWith<IllegalArgumentException> { AllianceTag("fra") }
        assertFailsWith<IllegalArgumentException> { AllianceTag("FR-A") }
        assertFailsWith<IllegalArgumentException> { AllianceTag("FR A") }
        // The whole reason the alphabet is written out rather than delegated to `isLetterOrDigit`.
        assertFailsWith<IllegalArgumentException> { AllianceTag("FRÄ") }
        assertEquals("FR7", AllianceTag("FR7").value)
    }

    @Test
    fun `a tag that breaks the bound is refused on the way in as well as on the way out`() {
        assertFailsWith<IllegalArgumentException> {
            Protocol.json.decodeFromJsonElement(AllianceTag.serializer(), JsonPrimitive("fra"))
        }
    }

    // ── The check both ends run ───────────────────────────────────────────────────────────────
    //
    // Davide, 2026-09-14: *"we should run the check on the server and the client as well. Please
    // make sure that we put this in a shared place so the client and the server share the same
    // logic."* These are that shared place, and the `init` guards above are the same rule said as a
    // raise — so a rule that moved in one and not the other would fail these.

    @Test
    fun `a name the contract accepts is refused for nothing`() {
        assertNull(AllianceName.refusalFor("Ferro Alto"))
    }

    @Test
    fun `a name is refused for the reason its guard raises on`() {
        assertEquals(AllianceNameRefusal.BLANK, AllianceName.refusalFor("   "))
        assertEquals(AllianceNameRefusal.UNTRIMMED, AllianceName.refusalFor(" Ferro Alto"))
        assertEquals(
            AllianceNameRefusal.TOO_LONG,
            AllianceName.refusalFor("a".repeat(AllianceName.MAX_LENGTH + 1)),
        )
    }

    @Test
    fun `a tag the contract accepts is refused for nothing`() {
        assertNull(AllianceTag.refusalFor("FRA"))
        assertNull(AllianceTag.refusalFor("FR7A"))
    }

    @Test
    fun `a tag is refused for the reason its guard raises on`() {
        assertEquals(AllianceTagRefusal.WRONG_LENGTH, AllianceTag.refusalFor("FR"))
        assertEquals(AllianceTagRefusal.WRONG_LENGTH, AllianceTag.refusalFor("FRALT"))
        assertEquals(AllianceTagRefusal.NOT_UPPERCASE, AllianceTag.refusalFor("fra"))
        assertEquals(AllianceTagRefusal.NOT_UPPERCASE, AllianceTag.refusalFor("FR-A"))
    }

    // **The length is read before the alphabet**, so a player who has typed two good letters is told
    // the tag is too short rather than that it holds a character it does not hold.
    @Test
    fun `a tag that is both too short and not uppercase is refused for its length`() {
        assertEquals(AllianceTagRefusal.WRONG_LENGTH, AllianceTag.refusalFor("f"))
    }

    // The check and the guard are one rule, and this is what says so: everything the check passes
    // constructs, and everything it refuses raises.
    @Test
    fun `every string the check passes is one the guard accepts`() {
        val candidates = listOf("", "  ", "F", "FR", "FRA", "FRAL", "FRALT", "fra", "FR-A", "FR7", " FRA")
        for (candidate in candidates) {
            if (AllianceTag.refusalFor(candidate) == null) {
                assertEquals(candidate, AllianceTag(candidate).value)
            } else {
                assertFailsWith<IllegalArgumentException> { AllianceTag(candidate) }
            }
        }
    }

    // ── The level and the seats ───────────────────────────────────────────────────────────────

    @Test
    fun `a level counts up from zero`() {
        assertFailsWith<IllegalArgumentException> { AllianceLevel(-1) }
        assertEquals(0, AllianceLevel(0).value)
    }

    @Test
    fun `seats taken cannot be negative or exceed the cap`() {
        assertFailsWith<IllegalArgumentException> { AllianceSeats(taken = -1, cap = 4) }
        assertFailsWith<IllegalArgumentException> { AllianceSeats(taken = 5, cap = 4) }
        assertEquals(4, AllianceSeats(taken = 4, cap = 4).taken)
    }

    // ── The role and the decision ─────────────────────────────────────────────────────────────

    // Decoded by every client, so it stays three forever unless a release pays for a fourth.
    @Test
    fun `the role wire names are pinned`() {
        assertEquals(listOf("FOUNDER", "ADMIN", "MEMBER"), AllianceRole.entries.map { it.name })
    }

    // Sent by a client and never decoded by one, which is the safe direction to add a fourth to.
    @Test
    fun `the join decision wire names are pinned`() {
        assertEquals(listOf("ADMITTED", "DECLINED"), JoinDecision.entries.map { it.name })
    }

    // ── The alliance ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `an alliance survives the round trip`() {
        val text = Protocol.json.encodeToString(Alliance.serializer(), SAMPLE_ALLIANCE)

        assertEquals(SAMPLE_ALLIANCE, Protocol.json.decodeFromString(Alliance.serializer(), text))
    }

    // ── The standing ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `every standing has a sample`() {
        assertEquals(AllianceStandingId.entries.toSet(), STANDING_SAMPLES.mapTo(mutableSetOf(), ::idOf))
    }

    @Test
    fun `every standing survives a round trip`() {
        STANDING_SAMPLES.forEach { standing ->
            val text = Protocol.json.encodeToString(AllianceStanding.serializer(), standing)
            assertEquals(standing, Protocol.json.decodeFromString(AllianceStanding.serializer(), text), text)
        }
    }

    @Test
    fun `the standing wire names are pinned`() {
        val encoded = STANDING_SAMPLES.map { standing ->
            val json = Protocol.json.encodeToJsonElement(AllianceStanding.serializer(), standing) as JsonObject
            (json["type"] as JsonPrimitive).content
        }
        assertEquals(listOf("Unaffiliated", "Petitioning", "Enlisted"), encoded)
    }

    // `Unaffiliated` states nothing else, so its own encoding has no field but the discriminator —
    // proof it is a `data object` rather than an empty `data class` masquerading as one.
    @Test
    fun `unaffiliated carries no field beyond the discriminator`() {
        val encoded = Protocol.json.encodeToJsonElement(
            AllianceStanding.serializer(),
            AllianceStanding.Unaffiliated,
        ) as JsonObject

        assertEquals(setOf("type"), encoded.keys)
    }

    // ── The response ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `an alliance response says which contract answered it`() {
        val response = AllianceResponse(apiVersion = ApiVersion.CURRENT, standing = STANDING_SAMPLES.last())
        val text = Protocol.json.encodeToString(AllianceResponse.serializer(), response)

        assertEquals(response, Protocol.json.decodeFromString(AllianceResponse.serializer(), text))
    }

    // ── The requests ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a create request survives the round trip`() {
        val request = CreateAllianceRequest(
            apiVersion = ApiVersion.CURRENT,
            name = AllianceName("Ferro Alto"),
            tag = AllianceTag("FRA"),
        )
        val text = Protocol.json.encodeToString(CreateAllianceRequest.serializer(), request)

        assertEquals(request, Protocol.json.decodeFromString(CreateAllianceRequest.serializer(), text))
    }

    // Carries both, per Davide's call that the tag moves with the name rather than staying fixed at
    // founding.
    @Test
    fun `a rename request carries both the name and the tag`() {
        val request = RenameAllianceRequest(
            apiVersion = ApiVersion.CURRENT,
            name = AllianceName("Ferro Alto Rinnovato"),
            tag = AllianceTag("FRAR"),
        )
        val text = Protocol.json.encodeToString(RenameAllianceRequest.serializer(), request)

        assertEquals(request, Protocol.json.decodeFromString(RenameAllianceRequest.serializer(), text))
    }

    @Test
    fun `a join request survives the round trip`() {
        val request = JoinAllianceRequest(apiVersion = ApiVersion.CURRENT, alliance = AllianceId("ferro-alto"))
        val text = Protocol.json.encodeToString(JoinAllianceRequest.serializer(), request)

        assertEquals(request, Protocol.json.decodeFromString(JoinAllianceRequest.serializer(), text))
    }

    @Test
    fun `an answer to a join request survives the round trip`() {
        val request = AnswerJoinRequest(
            apiVersion = ApiVersion.CURRENT,
            request = JoinRequestId("petition-1"),
            decision = JoinDecision.ADMITTED,
        )
        val text = Protocol.json.encodeToString(AnswerJoinRequest.serializer(), request)

        assertEquals(request, Protocol.json.decodeFromString(AnswerJoinRequest.serializer(), text))
    }

    @Test
    fun `a kick request survives the round trip`() {
        val request = KickMemberRequest(apiVersion = ApiVersion.CURRENT, member = AllianceMemberId("member-1"))
        val text = Protocol.json.encodeToString(KickMemberRequest.serializer(), request)

        assertEquals(request, Protocol.json.decodeFromString(KickMemberRequest.serializer(), text))
    }

    // The live example of a value that is well-formed and left to the server to refuse:
    // `role = FOUNDER` constructs happily, because the same enum is read back on `AllianceMember.role`
    // where `FOUNDER` is entirely legal.
    @Test
    fun `a set-role request accepts founder as a well-formed value the server may refuse`() {
        val request = SetMemberRoleRequest(
            apiVersion = ApiVersion.CURRENT,
            member = AllianceMemberId("member-1"),
            role = AllianceRole.FOUNDER,
        )
        val text = Protocol.json.encodeToString(SetMemberRoleRequest.serializer(), request)

        assertEquals(request, Protocol.json.decodeFromString(SetMemberRoleRequest.serializer(), text))
    }
}
