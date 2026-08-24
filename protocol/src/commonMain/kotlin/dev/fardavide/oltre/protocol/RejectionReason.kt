package dev.fardavide.oltre.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// **Why a queued verb did not survive the replay**, and there are exactly two answers because there
// are exactly two ways for one to fail. Anything else is an `ApiError` — the sync itself failing —
// or a success.
@Serializable
sealed interface RejectionReason {

    // `core` was asked and said no. The verb was replayed against the authoritative state advanced
    // to the claimed instant, and the result type refused.
    @Serializable
    @SerialName("Refused")
    data class Refused(val refusal: VerbRefusal) : RejectionReason

    // The verb is `OfflineRule.LOOK_DONT_ACT` and reached the outbox anyway. It should never happen
    // — the client refuses to queue one — and it is representable for the reason `advance` clamps a
    // span that cannot run backwards: the rule is enforced at one end and stated at both, so a
    // client with the rule wrong is answered rather than trusted.
    @Serializable
    @SerialName("NotQueueable")
    data object NotQueueable : RejectionReason
}

// **`core`'s refusals, flattened.** Every one of these is a member of one of the six result types in
// `core` that can say no; the seventh through twelfth verbs — the four alert controls and the two
// settings ladders — cannot refuse at all, so nothing here comes from them.
//
// Flat rather than one nested taxonomy per verb, and that is a deliberate loss of precision. The
// verb is on the envelope this reason is attached to, so nothing is unrecoverable; what the shape
// buys is that the client's *"which of my queued taps did not make it"* screen is one `when` rather
// than a two-level nest, and the server's mapping is one function per verb rather than a type per
// verb. The comments below say which verb each can come from, which is the part a nested taxonomy
// would have carried in the type.
@Serializable
enum class VerbRefusal {

    // `startUpgrade` — that facility is already climbing. Facilities upgrade in parallel; the only
    // queue rule is that one cannot be upgraded twice at once.
    ALREADY_UPGRADING,

    // `startResearch`, `startAdaptation` — the branch's slot is empire-wide and holds a job. Not
    // "already researching this": a busy slot refuses every technology on its branch, including the
    // ones the player is not looking at.
    SLOT_BUSY,

    // `startUpgrade`, `startResearch`, `startAdaptation`.
    REQUIREMENTS_NOT_MET,

    // `startUpgrade`, `startResearch`, `startAdaptation`, `buildShips`, `startSurvey`. The commonest
    // one by far, and the one an offline queue exists to catch: the stores at the instant the player
    // tapped are not the stores the server has.
    INSUFFICIENT_RESOURCES,

    // `buildShips` — an empty manifest. A refusal rather than a no-op, because a success here would
    // append a `ShipsOrdered` with nothing in it.
    NOTHING_TO_BUILD,

    // `buildShips` — a hull whose slice has not landed. `FleetBalance.shipCost` refuses to invent a
    // price, so the refusal is carried back rather than guessed at.
    NOT_FOR_SALE,

    // `startRun` — you cannot price a hold you cannot see.
    UNSURVEYED,

    // `startRun` — home, an empty slot, a world somebody holds, or deuterium. Note what is
    // deliberately *not* here: failing your tolerance bands. Hostility gates settling, not
    // gathering.
    NOT_A_VALID_TARGET,

    // `startRun` — the idle pool does not hold that manifest.
    NO_SUCH_SHIPS,

    // `startRun` — a manifest carrying a hull with no hold. Separate from `NO_SUCH_SHIPS` because
    // the pool is not what is wrong: a colony can own four scouts and still not be able to send one.
    NOT_A_GATHERING_HULL,

    // `startRun` — the window does not leave the minimum station on the surface after the round
    // trip.
    WINDOW_TOO_SHORT,

    // `startRun` — the world has nothing left of what was asked for. Rare by construction: a
    // stripped vein puts a whole unit back every twenty minutes.
    DEPLETED,

    // `startSurvey` — a probe is already on its way there.
    ALREADY_SURVEYING,

    // `startSurvey` — every world around that star is known and the map already reaches it.
    ALREADY_SURVEYED,

    // `startSurvey` — no idle scout. Its own refusal rather than folded into
    // `INSUFFICIENT_RESOURCES`, because the two are answered by different things: one is waited out,
    // the other is bought at the Shipyard.
    NO_IDLE_SCOUT,
}
