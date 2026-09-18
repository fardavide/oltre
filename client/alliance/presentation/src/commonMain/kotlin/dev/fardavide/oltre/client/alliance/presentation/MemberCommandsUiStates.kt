package dev.fardavide.oltre.client.alliance.presentation

import dev.fardavide.oltre.client.alliance.domain.MemberCommand
import dev.fardavide.oltre.client.alliance.domain.commandsAgainst
import dev.fardavide.oltre.client.alliance.ui.MemberCardUiState
import dev.fardavide.oltre.client.alliance.ui.MemberCommandsStepUiState
import dev.fardavide.oltre.client.alliance.ui.MemberCommandsUiState
import dev.fardavide.oltre.client.alliance.ui.MemberHeldUiState
import dev.fardavide.oltre.client.alliance.ui.RoleCommandUiState
import dev.fardavide.oltre.client.design.format.toStalenessLabel
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.protocol.AllianceMember
import dev.fardavide.oltre.protocol.AllianceRole
import kotlin.time.Instant

// **The member face, or nothing at all.** Null is the answer for four of the six cells in the
// permission table, and it is the same answer the roster row's arrow gives — one expression, so the
// arrow and the face cannot disagree about whether a tap does anything.
//
// `now` is a parameter for `core`'s own reason: nothing here reads a clock. The shell re-derives this
// destination on every tick and hands the instant in, exactly as it hands in the colony's stores.
fun memberCommandsUiState(
    member: AllianceMember,
    viewer: AllianceRole,
    now: Instant,
    reachable: Boolean,
    // Which of the two steps the face is on. Screen state rather than the gateway's — it survives no
    // round trip and is nobody's business but this sheet's, which is `picked` on the contribution
    // ladder and for the same reason.
    confirming: Boolean,
): MemberCommandsUiState? {
    val commands = viewer.commandsAgainst(member.role)
    // **The guard, and the only one.** Everything below may assume a kick exists, because every cell
    // that reaches here permits one — see `MemberCommandsStepUiState.Commands.kick`.
    if (commands.isEmpty()) return null

    val name = member.profile.drawnName()
    return MemberCommandsUiState(
        id = member.id,
        member = MemberCardUiState(
            mark = member.profile.mark,
            name = name,
            role = when (member.role) {
                AllianceRole.ADMIN -> Strings.allianceRoleAdmin()
                // Unreachable — nobody commands a founder — and stated rather than folded into the
                // member arm so that a table which ever did permit it fails here instead of drawing
                // a founder's card as a plain member's.
                AllianceRole.FOUNDER -> null
                AllianceRole.MEMBER -> null
            },
            level = member.experience.badge(),
        ),
        step = if (confirming) {
            MemberCommandsStepUiState.Confirm(
                consequence = Strings.allianceMemberKickFirstFact(name),
                aftermath = Strings.allianceMemberKickSecondFact(),
                kick = Strings.allianceMemberKick(),
                keep = Strings.allianceMemberKeep(),
            )
        } else {
            MemberCommandsStepUiState.Commands(
                reading = Strings.allianceMemberLastSeen((now - member.lastSyncedAt).toStalenessLabel()),
                role = commands.filterIsInstance<MemberCommand.SetRole>().singleOrNull()?.asCommand(),
                kick = Strings.allianceMemberKickNamed(name),
            )
        },
        held = if (reachable) {
            null
        } else {
            MemberHeldUiState(lead = Strings.allianceMemberHeldLead(), body = Strings.allianceMemberHeldBody())
        },
    )
}

// **The label names the rank it moves them to, and so does the note.** The same button in the other
// cell of the table means the opposite direction, so the destination is the only thing that says
// which way it goes — which is why `MemberCommand.SetRole` carries a rank rather than a verb.
private fun MemberCommand.SetRole.asCommand(): RoleCommandUiState = when (role) {
    AllianceRole.ADMIN -> RoleCommandUiState(
        action = Strings.allianceMemberPromote(),
        note = Strings.allianceMemberPromoteNote(),
        role = role,
    )
    AllianceRole.MEMBER -> RoleCommandUiState(
        action = Strings.allianceMemberDemote(),
        note = Strings.allianceMemberDemoteNote(),
        role = role,
    )
    // `commandsAgainst` never builds one — handing the alliance on is out of scope by name and is
    // refused there rather than here. A `when` with no `else` is what makes that a compile-time fact
    // the day somebody adds a fourth rank.
    AllianceRole.FOUNDER -> error("the member face does not hand the alliance on")
}
