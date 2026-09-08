package dev.fardavide.oltre.protocol

import dev.fardavide.oltre.core.Experience
import kotlinx.serialization.Serializable
import kotlin.time.Instant

// `GET /v1/alliance/roster` — you can see who is in it, `alliance-sheet.md` §6. **Its own route and
// not a field on `AllianceResponse`**: a roster on the alliance read could never grow a column after
// the alliance's routes ship, and a roster is the part of this feature most likely to want one. It
// also means a screen that only needs a name and a tag does not carry every member's profile down the
// wire.

// A seat on the roster: who they are, what they carry, and when they were last seen.
//
// **It reuses `PlayerProfile` rather than restating a name and a mark.** The type already means "what
// a player chose, with `null` meaning has not chosen", the drawing already substitutes
// `Strings.playerDefaultName()` where the answer is null, and a second pair of fields would be a
// second place for that substitution to be forgotten.
//
// **It carries `Experience` and not a level, and this is not laziness.** `core` already has
// `ExperienceBalance.levelFor(earned: Experience): PlayerLevel`, which is what the player strip calls
// for the viewer's own badge. If the server sent a computed level instead, a viewer on an older build
// could see themselves at one level here and another in the strip, because the two numbers would have
// come from two ladders. Carrying the raw figure means one build computes every badge on a roster with
// one ladder, so no two rows — including the viewer's own — can contradict each other.
//
// **`lastSyncedAt` is here because `alliance-sheet.md` §5.3 needs the concept and the roster is the
// only place a player can read it.** Succession turns on "has synced within N days", and N is
// unanswered — so the wire carries the instant now, while the door is still open, and lets the screen
// say "last seen three weeks ago" without anybody having to pick N first.
@Serializable
data class AllianceMember(
    val id: AllianceMemberId,
    val profile: PlayerProfile,
    val role: AllianceRole,
    val experience: Experience,
    val lastSyncedAt: Instant,
)

// A pending petition, as a founder or an admin reads it.
//
// **`askedAt` orders the pending list and nothing else.** `alliance-sheet.md` §1.4 is categorical —
// there is no push and nothing may be time-critical — and §5.2 says a request "waits until it is
// answered or withdrawn." Nobody builds a countdown off this field.
@Serializable
data class JoinRequest(
    val id: JoinRequestId,
    val profile: PlayerProfile,
    val experience: Experience,
    val askedAt: Instant,
)

// **`pending` is nullable, and the nullability is load-bearing.** Only a founder or an admin may
// answer a join request (`ApiError.AllianceRoleTooLow` guards the route that does), so a plain
// member's read carries `null` — *this is not yours to see* — rather than an empty list, which would
// read as *nobody is asking*. `null` and `[]` are both required keys on this wire, per
// `encodeDefaults`, and mean two different things here.
@Serializable
data class AllianceRosterResponse(
    val apiVersion: ApiVersion,
    val members: List<AllianceMember>,
    val pending: List<JoinRequest>?,
)
