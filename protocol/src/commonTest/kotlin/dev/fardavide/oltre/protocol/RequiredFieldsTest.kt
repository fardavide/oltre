package dev.fardavide.oltre.protocol

import dev.fardavide.oltre.core.Experience
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

    // **A nullable field is still a required one**, which is the whole reason the profile pair is in
    // here beside the rest. `null` on this wire means *the player has not chosen*, and a key that
    // could be absent would add a second way to say it that means *this build does not say* — the
    // exact ambiguity `encodeDefaults` exists to remove.
    @Test
    fun `a profile missing either half is refused even when the half it states is null`() {
        assertEveryFieldRequired(
            PlayerProfile.serializer(),
            PlayerProfile(name = CommanderName("Ada"), mark = PlayerMark.Preset(MarkPreset.SEXTANT)),
        )
        assertEveryFieldRequired(PlayerProfile.serializer(), PlayerProfile(name = null, mark = null))
    }

    // A composition is three slots and all three are stated, always. A mark that arrived missing its
    // path would be a mark the drawing has to guess at, and the guess would be a different glyph.
    @Test
    fun `a mark missing any of its parts is refused`() {
        assertEveryFieldRequired(PlayerMark.serializer(), PlayerMark.Preset(MarkPreset.WAKE))
        assertEveryFieldRequired(
            PlayerMark.serializer(),
            PlayerMark.Composed(MarkBody.ORBIT, MarkPath.TRANSFER, MarkTerminus.RING),
        )
    }

    @Test
    fun `a profile request or response missing its version is refused`() {
        val profile = PlayerProfile(name = CommanderName("Ada"), mark = PlayerMark.Preset(MarkPreset.THRESHOLD))
        assertEveryFieldRequired(
            SetProfileRequest.serializer(),
            SetProfileRequest(apiVersion = ApiVersion.CURRENT, profile = profile),
        )
        assertEveryFieldRequired(
            ProfileResponse.serializer(),
            ProfileResponse(apiVersion = ApiVersion.CURRENT, profile = profile),
        )
    }

    // The four errors that carry a payload. The four that do not are `data object`s.
    @Test
    fun `an error missing its payload is refused`() {
        listOf(
            ApiError.UnsupportedApiVersion(
                oldestServed = ApiVersion.OLDEST_SERVED,
                current = ApiVersion.CURRENT,
            ),
            ApiError.TooManyRequests(retryAfterSeconds = 12),
            ApiError.Malformed("clientInstant is not an instant"),
            ApiError.Internal("the store did not answer"),
        ).forEach { assertEveryFieldRequired(ApiError.serializer(), it) }
    }

    // ── The alliance ──────────────────────────────────────────────────────────────────────────

    private val SAMPLE_ALLIANCE = Alliance(
        id = AllianceId("ferro-alto"),
        name = AllianceName("Ferro Alto"),
        tag = AllianceTag("FRA"),
        level = AllianceLevel(4),
        seats = AllianceSeats(taken = 9, cap = 16),
    )

    @Test
    fun `an alliance missing any of its five fields is refused`() {
        assertEveryFieldRequired(Alliance.serializer(), SAMPLE_ALLIANCE)
    }

    // `Unaffiliated` has nothing to drop but its discriminator, so only the two carrying standings
    // are checked here — the same shape as `ApiError`'s payload-less `data object`s above.
    @Test
    fun `a petitioning or enlisted standing missing its alliance is refused`() {
        assertEveryFieldRequired(AllianceStanding.serializer(), AllianceStanding.Petitioning(SAMPLE_ALLIANCE))
        assertEveryFieldRequired(
            AllianceStanding.serializer(),
            AllianceStanding.Enlisted(SAMPLE_ALLIANCE, AllianceRole.MEMBER),
        )
    }

    @Test
    fun `an alliance response missing its version or standing is refused`() {
        assertEveryFieldRequired(
            AllianceResponse.serializer(),
            AllianceResponse(apiVersion = ApiVersion.CURRENT, standing = AllianceStanding.Unaffiliated),
        )
    }

    @Test
    fun `a create request missing its name or its tag is refused`() {
        assertEveryFieldRequired(
            CreateAllianceRequest.serializer(),
            CreateAllianceRequest(
                apiVersion = ApiVersion.CURRENT,
                name = AllianceName("Ferro Alto"),
                tag = AllianceTag("FRA"),
            ),
        )
    }

    @Test
    fun `a rename request missing its name or its tag is refused`() {
        assertEveryFieldRequired(
            RenameAllianceRequest.serializer(),
            RenameAllianceRequest(
                apiVersion = ApiVersion.CURRENT,
                name = AllianceName("Ferro Alto"),
                tag = AllianceTag("FRA"),
            ),
        )
    }

    @Test
    fun `a join request missing the alliance it names is refused`() {
        assertEveryFieldRequired(
            JoinAllianceRequest.serializer(),
            JoinAllianceRequest(apiVersion = ApiVersion.CURRENT, alliance = AllianceId("ferro-alto")),
        )
    }

    @Test
    fun `an answer to a join request missing either half is refused`() {
        assertEveryFieldRequired(
            AnswerJoinRequest.serializer(),
            AnswerJoinRequest(
                apiVersion = ApiVersion.CURRENT,
                request = JoinRequestId("petition-1"),
                decision = JoinDecision.ADMITTED,
            ),
        )
    }

    @Test
    fun `a kick request missing the member it names is refused`() {
        assertEveryFieldRequired(
            KickMemberRequest.serializer(),
            KickMemberRequest(apiVersion = ApiVersion.CURRENT, member = AllianceMemberId("member-1")),
        )
    }

    @Test
    fun `a set-role request missing either half is refused`() {
        assertEveryFieldRequired(
            SetMemberRoleRequest.serializer(),
            SetMemberRoleRequest(
                apiVersion = ApiVersion.CURRENT,
                member = AllianceMemberId("member-1"),
                role = AllianceRole.ADMIN,
            ),
        )
    }

    // ── The search ────────────────────────────────────────────────────────────────────────────

    // **A nullable field is still a required one**, `nextCursor`'s reason for being here beside the
    // profile pair above: `null` means "that was the last page" and a key that could be absent would
    // add a second way to say it.
    @Test
    fun `a search response missing any field is refused whether or not the next cursor is null`() {
        val sample = AllianceSearchResponse(
            apiVersion = ApiVersion.CURRENT,
            query = "vanguard",
            results = emptyList(),
            nextCursor = AllianceSearchCursor("page-2"),
        )
        assertEveryFieldRequired(AllianceSearchResponse.serializer(), sample)
        assertEveryFieldRequired(AllianceSearchResponse.serializer(), sample.copy(nextCursor = null))
    }

    // ── The roster ────────────────────────────────────────────────────────────────────────────

    private val SAMPLE_MEMBER = AllianceMember(
        id = AllianceMemberId("member-1"),
        profile = PlayerProfile(name = CommanderName("Ada"), mark = PlayerMark.Preset(MarkPreset.SEXTANT)),
        role = AllianceRole.ADMIN,
        experience = Experience(1_200),
        lastSyncedAt = NOW,
    )

    private val SAMPLE_JOIN_REQUEST = JoinRequest(
        id = JoinRequestId("petition-1"),
        profile = PlayerProfile(name = null, mark = null),
        experience = Experience(340),
        askedAt = NOW,
    )

    @Test
    fun `a member missing any of its five fields is refused`() {
        assertEveryFieldRequired(AllianceMember.serializer(), SAMPLE_MEMBER)
    }

    @Test
    fun `a join request missing any of its four fields is refused`() {
        assertEveryFieldRequired(JoinRequest.serializer(), SAMPLE_JOIN_REQUEST)
    }

    // **`pending` is the roster's own nullable-and-required field**: `null` means "not yours to see"
    // and `[]` means "yours to see, and empty" — both required keys, and both checked here.
    @Test
    fun `a roster response missing any field is refused whether or not pending is null`() {
        val sample = AllianceRosterResponse(
            apiVersion = ApiVersion.CURRENT,
            members = listOf(SAMPLE_MEMBER),
            pending = listOf(SAMPLE_JOIN_REQUEST),
        )
        assertEveryFieldRequired(AllianceRosterResponse.serializer(), sample)
        assertEveryFieldRequired(AllianceRosterResponse.serializer(), sample.copy(pending = null))
    }
}
