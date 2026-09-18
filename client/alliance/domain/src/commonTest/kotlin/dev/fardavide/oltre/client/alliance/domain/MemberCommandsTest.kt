package dev.fardavide.oltre.client.alliance.domain

import dev.fardavide.oltre.protocol.AllianceRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberCommandsTest {

    @Test
    fun `a founder promotes a member and may kick them`() {
        assertEquals(
            expected = listOf(MemberCommand.SetRole(AllianceRole.ADMIN), MemberCommand.Kick),
            actual = AllianceRole.FOUNDER.commandsAgainst(AllianceRole.MEMBER),
        )
    }

    @Test
    fun `a founder demotes an admin and may kick them`() {
        assertEquals(
            expected = listOf(MemberCommand.SetRole(AllianceRole.MEMBER), MemberCommand.Kick),
            actual = AllianceRole.FOUNDER.commandsAgainst(AllianceRole.ADMIN),
        )
    }

    @Test
    fun `an admin may only kick members`() {
        assertEquals(
            expected = listOf(MemberCommand.Kick),
            actual = AllianceRole.ADMIN.commandsAgainst(AllianceRole.MEMBER),
        )
        assertEquals(emptyList(), AllianceRole.ADMIN.commandsAgainst(AllianceRole.ADMIN))
    }

    @Test
    fun `a member commands nobody`() {
        AllianceRole.entries.forEach { target ->
            assertEquals(
                expected = emptyList(),
                actual = AllianceRole.MEMBER.commandsAgainst(target),
                message = "member against $target",
            )
        }
    }

    @Test
    fun `nobody commands the founder`() {
        AllianceRole.entries.forEach { viewer ->
            assertEquals(
                expected = emptyList(),
                actual = viewer.commandsAgainst(AllianceRole.FOUNDER),
                message = "$viewer against the founder",
            )
        }
    }

    // **The viewer's own row needs no identifier, and this is the proof.** The roster wire carries no
    // "this one is you" marker, and it does not need one: a row of the viewer's own role is a row the
    // pair already refuses, whichever role the viewer holds. A founder's own row is founder-on-founder,
    // an admin's is admin-on-admin, a member commands nobody at all — so *nobody acts on themselves*
    // falls out of the table rather than being enforced beside it.
    @Test
    fun `nobody commands a member of their own rank - which is what makes self inert`() {
        AllianceRole.entries.forEach { role ->
            assertEquals(
                expected = emptyList(),
                actual = role.commandsAgainst(role),
                message = "$role against their own rank",
            )
        }
    }

    // The design's own requirement: the arrow on the row and the face behind it are one expression, so
    // a row that presses always opens a face with something on it.
    @Test
    fun `a row presses if and only if it has commands`() {
        AllianceRole.entries.forEach { viewer ->
            AllianceRole.entries.forEach { target ->
                assertEquals(
                    expected = viewer.commandsAgainst(target).isNotEmpty(),
                    actual = viewer.canCommand(target),
                    message = "$viewer against $target",
                )
            }
        }
    }

    // Derived from the shipped powers rather than restating them, so the two can never disagree.
    @Test
    fun `every command is one the powers already permit`() {
        AllianceRole.entries.forEach { viewer ->
            AllianceRole.entries.forEach { target ->
                viewer.commandsAgainst(target).forEach { command ->
                    val permitted = when (command) {
                        is MemberCommand.SetRole -> viewer.canSetRole(target = target, role = command.role)
                        MemberCommand.Kick -> viewer.canRemove(target)
                    }
                    assertTrue(permitted, "$viewer against $target: $command is not permitted")
                }
            }
        }
    }
}
