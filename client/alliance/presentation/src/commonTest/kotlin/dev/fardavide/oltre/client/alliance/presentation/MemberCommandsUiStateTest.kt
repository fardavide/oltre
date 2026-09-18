package dev.fardavide.oltre.client.alliance.presentation

import dev.fardavide.oltre.client.alliance.domain.commandsAgainst
import dev.fardavide.oltre.client.alliance.ui.MemberCommandsStepUiState
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.core.Experience
import dev.fardavide.oltre.protocol.AllianceMember
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.CommanderName
import dev.fardavide.oltre.protocol.ExperienceReading
import dev.fardavide.oltre.protocol.PlayerProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// **What one commander may do to another, and what the face says about them.** Every rule here is a
// plain function over data — the face renders what it is handed — so this is a unit test.
class MemberCommandsUiStateTest {

    // ── Which cells open a face at all ───────────────────────────────────────────────────────

    @Test
    fun `a founder on a member is offered a promotion and a kick`() {
        val face = assertNotNull(faceOf(viewer = AllianceRole.FOUNDER, member = MEMBER))
        val step = assertIs<MemberCommandsStepUiState.Commands>(face.step)

        assertEquals(Strings.allianceMemberPromote(), step.role?.action)
        assertEquals(AllianceRole.ADMIN, step.role?.role)
        assertEquals(Strings.allianceMemberKickNamed(TextRes("Hard Vacuum")), step.kick)
    }

    @Test
    fun `a founder on an admin is offered a demotion and a kick`() {
        val face = assertNotNull(faceOf(viewer = AllianceRole.FOUNDER, member = ADMIN))
        val step = assertIs<MemberCommandsStepUiState.Commands>(face.step)

        assertEquals(Strings.allianceMemberDemote(), step.role?.action)
        assertEquals(AllianceRole.MEMBER, step.role?.role)
    }

    // An admin may kick members and nothing else, so there is no blue on this face at all and no
    // note under it — the absences are absences, and the face is simply shorter.
    @Test
    fun `an admin on a member is offered a kick and nothing else`() {
        val face = assertNotNull(faceOf(viewer = AllianceRole.ADMIN, member = MEMBER))
        val step = assertIs<MemberCommandsStepUiState.Commands>(face.step)

        assertNull(step.role)
        assertEquals(Strings.allianceMemberKickNamed(TextRes("Hard Vacuum")), step.kick)
    }

    // **No face, and no sentence explaining the emptiness.** An absence is answerable when something
    // says why, and nothing is absent from a member's view: they never saw an arrow.
    @Test
    fun `a member opens no face on anybody`() {
        listOf(FOUNDER, ADMIN, MEMBER).forEach { member ->
            assertNull(faceOf(viewer = AllianceRole.MEMBER, member = member), "member on ${member.role}")
        }
    }

    @Test
    fun `nobody opens a face on the founder`() {
        AllianceRole.entries.forEach { viewer ->
            assertNull(faceOf(viewer = viewer, member = FOUNDER), "$viewer on the founder")
        }
    }

    // The viewer's own row carries the viewer's own rank, and no rank commands its own — so *nobody
    // acts on themselves* needs no identifier on the wire. See `MemberCommandsTest`.
    @Test
    fun `nobody opens a face on a member of their own rank`() {
        AllianceRole.entries.forEach { role ->
            assertNull(faceOf(viewer = role, member = MEMBER.copy(role = role)), "$role on their own rank")
        }
    }

    // **The face and the roster row's arrow are one expression.** A row that presses always opens a
    // face with something on it, and a row that does not never claims otherwise.
    @Test
    fun `a face opens exactly where the powers permit a command`() {
        AllianceRole.entries.forEach { viewer ->
            AllianceRole.entries.forEach { role ->
                assertEquals(
                    expected = viewer.commandsAgainst(role).isNotEmpty(),
                    actual = faceOf(viewer = viewer, member = MEMBER.copy(role = role)) != null,
                    message = "$viewer on $role",
                )
            }
        }
    }

    // ── The card ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a plain member's card draws no role word and an admin's does`() {
        assertNull(assertNotNull(faceOf(viewer = AllianceRole.FOUNDER, member = MEMBER)).member.role)
        assertEquals(
            expected = Strings.allianceRoleAdmin(),
            actual = assertNotNull(faceOf(viewer = AllianceRole.FOUNDER, member = ADMIN)).member.role,
        )
    }

    @Test
    fun `a commander with no chosen name is drawn as the default`() {
        val anonymous = MEMBER.copy(profile = PlayerProfile(name = null, mark = null))
        val face = assertNotNull(faceOf(viewer = AllianceRole.FOUNDER, member = anonymous))

        assertEquals(Strings.playerDefaultName(), face.member.name)
    }

    // ── The reading ──────────────────────────────────────────────────────────────────────────

    // Always drawn, never conditional on a threshold: a fact that vanishes when it is small is a
    // fact nobody trusts when it is large.
    @Test
    fun `the reading is how long ago they last opened the game`() {
        val stale = assertNotNull(faceOf(viewer = AllianceRole.FOUNDER, member = MEMBER, ago = 3.days + 4.hours))
        val fresh = assertNotNull(faceOf(viewer = AllianceRole.FOUNDER, member = MEMBER, ago = 18.minutes))

        assertEquals(
            "Last opened the game 3d 04h ago.",
            English.resolve(assertIs<MemberCommandsStepUiState.Commands>(stale.step).reading),
        )
        assertEquals(
            "Last opened the game 18m ago.",
            English.resolve(assertIs<MemberCommandsStepUiState.Commands>(fresh.step).reading),
        )
    }

    // ── The last step ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the last step names the commander and states what does not come back`() {
        val face = assertNotNull(faceOf(viewer = AllianceRole.FOUNDER, member = MEMBER, confirming = true))
        val step = assertIs<MemberCommandsStepUiState.Confirm>(face.step)

        assertEquals(Strings.allianceMemberKickFirstFact(TextRes("Hard Vacuum")), step.consequence)
        assertEquals(Strings.allianceMemberKickSecondFact(), step.aftermath)
        assertEquals(Strings.allianceMemberKick(), step.kick)
        assertEquals(Strings.allianceMemberKeep(), step.keep)
    }

    // A confirm step has one question: nothing about the promotion is reachable from it.
    @Test
    fun `the last step offers no role command even to a founder`() {
        val face = assertNotNull(faceOf(viewer = AllianceRole.FOUNDER, member = MEMBER, confirming = true))

        assertIs<MemberCommandsStepUiState.Confirm>(face.step)
    }

    // **An admin has a last step too.** Kick is the one command they hold, and it is the one that
    // confirms — a face that skipped the second tap for them would make the cheaper role the more
    // dangerous one.
    @Test
    fun `an admin reaches the last step as well`() {
        val face = assertNotNull(faceOf(viewer = AllianceRole.ADMIN, member = MEMBER, confirming = true))

        assertIs<MemberCommandsStepUiState.Confirm>(face.step)
    }

    // ── Held ─────────────────────────────────────────────────────────────────────────────────

    // Held dims what acts and never what informs: the card and the reading stay, both commands stop.
    @Test
    fun `with no network the face still draws and says why nothing presses`() {
        val face = assertNotNull(faceOf(viewer = AllianceRole.FOUNDER, member = MEMBER, reachable = false))

        assertEquals(Strings.allianceMemberHeldLead(), face.held?.lead)
        assertEquals(Strings.allianceMemberHeldBody(), face.held?.body)
        assertIs<MemberCommandsStepUiState.Commands>(face.step)
    }

    @Test
    fun `with a network there is nothing to explain`() {
        assertNull(assertNotNull(faceOf(viewer = AllianceRole.FOUNDER, member = MEMBER)).held)
    }

    private fun faceOf(
        viewer: AllianceRole,
        member: AllianceMember,
        ago: kotlin.time.Duration = 3.days + 4.hours,
        reachable: Boolean = true,
        confirming: Boolean = false,
    ) = memberCommandsUiState(
        member = member.copy(lastSyncedAt = NOW - ago),
        viewer = viewer,
        now = NOW,
        reachable = reachable,
        confirming = confirming,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-18T09:00:00Z")

        val FOUNDER = AllianceMember(
            id = AllianceMemberId("seat-1"),
            profile = PlayerProfile(name = CommanderName("Dead Reckoning"), mark = null),
            role = AllianceRole.FOUNDER,
            experience = ExperienceReading.Known(Experience(54_300)),
            lastSyncedAt = NOW,
        )

        val ADMIN = FOUNDER.copy(
            id = AllianceMemberId("seat-2"),
            profile = PlayerProfile(name = CommanderName("Slow Burn"), mark = null),
            role = AllianceRole.ADMIN,
        )

        val MEMBER = FOUNDER.copy(
            id = AllianceMemberId("seat-3"),
            profile = PlayerProfile(name = CommanderName("Hard Vacuum"), mark = null),
            role = AllianceRole.MEMBER,
        )
    }
}
