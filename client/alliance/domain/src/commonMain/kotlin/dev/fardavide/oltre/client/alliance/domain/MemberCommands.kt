package dev.fardavide.oltre.client.alliance.domain

import dev.fardavide.oltre.protocol.AllianceRole

// **What one commander may do to another, as a list derived from the pair of roles and never stored.**
//
// The member face and the arrow on the roster row are the same expression, which is the design's own
// requirement: a row presses if and only if this list is non-empty, so a row that invites a tap cannot
// open a face with nothing on it. Two flags on a row instead of one list would be two things to keep
// in step, and the pair that shipped — `removable` on the row, nothing for the role — is exactly how
// the app ended up with a rule (`canSetRole`) that no control ever reached.
//
// Every arm below is `AlliancePowers`' answer rather than a second copy of it. `MemberCommandsTest`
// pins that: whatever this returns, the shipped power for it already says yes.
sealed interface MemberCommand {

    // Promote or demote — one command, not two, because the direction is a property of the rank it
    // moves to and the button's label is the only thing that says which way it goes. A `Promote` and
    // a `Demote` member would be two names for one route (`setMemberRole`) and would let a caller
    // build the pair that does not exist: demoting a member, promoting an admin.
    data class SetRole(val role: AllianceRole) : MemberCommand

    // `Kick`, which is the word (Davide, 2026-09-18). The route is still `removeMember`; nothing a
    // player reads says Remove about a person any more.
    data object Kick : MemberCommand
}

// **The role command first and the kick second, which is the order they are drawn in**, so the face
// renders the list rather than re-deciding what goes above what.
//
// Note what is *not* a parameter: whether this row is the viewer's own. The roster wire carries no
// such marker and needs none — a row of the viewer's own rank is a row this table already refuses,
// for every rank. See `MemberCommandsTest`.
fun AllianceRole.commandsAgainst(target: AllianceRole): List<MemberCommand> = buildList {
    // The rank the target is *not*, which is the only move this face offers against them.
    //
    // **A founder is `null` here rather than a third arm, and the null is load-bearing.**
    // `canSetRole` permits `FOUNDER → FOUNDER → FOUNDER` — that is the wire's *hand the alliance on*
    // route, and it is real — so deriving the destination as "the rank they are not" would have this
    // face quietly offer a crown transfer as the founder's own row. That act needs a target's
    // acceptance and a rule for nobody accepting, none of which exists, and it is out of scope by
    // name. Refusing it here rather than leaning on `canSetRole` is what `MemberCommandsTest` pins.
    val moveTo = when (target) {
        AllianceRole.ADMIN -> AllianceRole.MEMBER
        AllianceRole.MEMBER -> AllianceRole.ADMIN
        AllianceRole.FOUNDER -> null
    }
    if (moveTo != null && canSetRole(target = target, role = moveTo)) add(MemberCommand.SetRole(moveTo))
    if (canRemove(target)) add(MemberCommand.Kick)
}

// Whether the row presses, and whether the arrow is drawn. One expression with the face above it, so
// the two cannot disagree.
fun AllianceRole.canCommand(target: AllianceRole): Boolean = commandsAgainst(target).isNotEmpty()
