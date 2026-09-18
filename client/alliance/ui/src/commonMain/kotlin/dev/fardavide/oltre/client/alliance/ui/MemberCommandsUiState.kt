package dev.fardavide.oltre.client.alliance.ui

import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.PlayerMark

// **The ninth face on the sheet: what one commander may do to another** — *The member commands*,
// returned 2026-09-18. It is reached by tapping a roster row, and the roster row stops carrying a
// control of its own.
//
// **There is no state of this face with nothing on it, and that is the whole shape of the type.**
// The face exists exactly where the viewer holds a command, so `memberCommandsUiState` answers null
// rather than building an empty one — which is what lets the arrow on the row and the contents of
// the face be one expression rather than two that have to agree.

// **A sheet, not an expansion in the flow.** The contribution confirm is in the flow because its
// question is *about* the pool figures behind it and a modal would take them off screen; this face's
// question is about a person and it carries that person onto itself, so nothing behind it is
// evidence. Two more reasons settle it: an expanding row would reflow the roster, and the only
// reflow this destination permits is the contribution ladder's; and a row low in a 612dp destination
// would open its commands below the fold.
data class MemberCommandsUiState(
    // What the tap hands back. The face draws and decides nothing — the shell turns this into
    // `setMemberRole` or `removeMember`.
    val id: AllianceMemberId,
    val member: MemberCardUiState,
    val step: MemberCommandsStepUiState,
    // Null when the server is reachable. **Held dims what acts and never what informs**, so the card
    // and the reading keep full strength while both commands drop and stop pressing.
    val held: MemberHeldUiState?,
)

// **The card is the heading.** The row that was tapped, redrawn at 44dp, and no title above it —
// a face that writes one commander's name three times is shouting.
data class MemberCardUiState(
    // Null is a commander who never chose one, which is nineteen in twenty. `IdentityMark` is what
    // substitutes the default, in the one place that substitution happens.
    val mark: PlayerMark?,
    val name: TextRes,
    // Null for a plain member, which is the roster row's own rule: the role rides the caption ramp
    // rather than a second badge, and a member draws nothing at all.
    //
    // **So the only word that can appear here is `ADMIN`, and there is no flag for which ink to use.**
    // The roster row has one — `FOUNDER` at full ink, `ADMIN` in secondary — and this face cannot:
    // nobody commands the founder, so no face ever opens on one. A `prominent` boolean here would be
    // a branch nothing could ever take, drawn in a colour no screenshot could ever record.
    val role: TextRes?,
    val level: TextRes,
)

sealed interface MemberCommandsStepUiState {

    // **`kick` is not nullable, and that is a property of the permission table rather than an
    // optimism.** Every cell that opens this face permits a kick: a founder against an admin gets
    // demote *and* kick, a founder against a member gets promote *and* kick, an admin against a
    // member gets kick alone. There is no cell that offers a role change without one, so a
    // role-command-only face is a state that cannot happen — and a nullable field for it would be a
    // branch nothing can ever take, which is the thing this module keeps refusing to ship.
    // `MemberCommandsUiStateTest` pins it against the shipped powers rather than against this
    // comment.
    data class Commands(
        // `lastSyncedAt`, which is drawn nowhere else in the app and is the one fact the roster wire
        // carries that the row does not. It belongs here because it is the fact the commands turn
        // on: a founder deciding whether to promote somebody or take their seat back is asking
        // whether they are still playing.
        val reading: TextRes,
        // The founder's extra. Null for an admin, whose only command is the kick below it.
        val role: RoleCommandUiState?,
        val kick: TextRes,
    ) : MemberCommandsStepUiState

    // **The last step, in the delete face's grammar**: the consequence first and in red, the
    // aftermath under it, and the filled danger as the second tap. Nothing about the role command is
    // reachable from here — a confirm step has one question.
    data class Confirm(
        val consequence: TextRes,
        val aftermath: TextRes,
        val kick: TextRes,
        val keep: TextRes,
    ) : MemberCommandsStepUiState
}

// **One tap, no confirm, and the note is why.** Promoting and demoting are reversible by the founder
// in one tap, so the consequence is stated *under the button before it is pressed* rather than in a
// second step — an unconfirmed consequence has to be stated before the tap or it is not stated at
// all. The confirmation is the face restating itself: the role word in the card changes and this
// command inverts under the same finger.
data class RoleCommandUiState(
    val action: TextRes,
    val note: TextRes,
    // The rank this moves them to. Carried rather than re-derived so the shell hands back what the
    // face offered, which is the roster row's own pattern with its id.
    val role: AllianceRole,
)

data class MemberHeldUiState(val lead: TextRes, val body: TextRes)
