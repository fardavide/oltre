package dev.fardavide.oltre.client.alliance.domain

import dev.fardavide.oltre.protocol.AllianceRole
import kotlin.test.Test
import kotlin.test.assertEquals

class AlliancePowersTest {

    @Test
    fun `only founders can set roles and existing founders cannot be demoted directly`() {
        val permittedChanges = setOf(
            AllianceRole.FOUNDER to AllianceRole.FOUNDER,
            AllianceRole.ADMIN to AllianceRole.FOUNDER,
            AllianceRole.ADMIN to AllianceRole.ADMIN,
            AllianceRole.ADMIN to AllianceRole.MEMBER,
            AllianceRole.MEMBER to AllianceRole.FOUNDER,
            AllianceRole.MEMBER to AllianceRole.ADMIN,
            AllianceRole.MEMBER to AllianceRole.MEMBER,
        )

        AllianceRole.entries.forEach { caller ->
            AllianceRole.entries.forEach { target ->
                AllianceRole.entries.forEach { role ->
                    assertEquals(
                        expected = caller == AllianceRole.FOUNDER && (target to role) in permittedChanges,
                        actual = caller.canSetRole(target = target, role = role),
                        message = "$caller setting $target to $role",
                    )
                }
            }
        }
    }

    @Test
    fun `only founders can rename an alliance`() {
        assertEquals(true, AllianceRole.FOUNDER.canRename())
        assertEquals(false, AllianceRole.ADMIN.canRename())
        assertEquals(false, AllianceRole.MEMBER.canRename())
    }

    @Test
    fun `member removal respects caller and target roles`() {
        assertEquals(false, AllianceRole.FOUNDER.canRemove(AllianceRole.FOUNDER))
        assertEquals(true, AllianceRole.FOUNDER.canRemove(AllianceRole.ADMIN))
        assertEquals(true, AllianceRole.FOUNDER.canRemove(AllianceRole.MEMBER))
        assertEquals(false, AllianceRole.ADMIN.canRemove(AllianceRole.FOUNDER))
        assertEquals(false, AllianceRole.ADMIN.canRemove(AllianceRole.ADMIN))
        assertEquals(true, AllianceRole.ADMIN.canRemove(AllianceRole.MEMBER))
        assertEquals(false, AllianceRole.MEMBER.canRemove(AllianceRole.FOUNDER))
        assertEquals(false, AllianceRole.MEMBER.canRemove(AllianceRole.ADMIN))
        assertEquals(false, AllianceRole.MEMBER.canRemove(AllianceRole.MEMBER))
    }

    @Test
    fun `admins and members can leave while founders cannot`() {
        assertEquals(false, AllianceRole.FOUNDER.canLeave())
        assertEquals(true, AllianceRole.ADMIN.canLeave())
        assertEquals(true, AllianceRole.MEMBER.canLeave())
    }

    @Test
    fun `only founders can disband an alliance`() {
        assertEquals(true, AllianceRole.FOUNDER.canDisband())
        assertEquals(false, AllianceRole.ADMIN.canDisband())
        assertEquals(false, AllianceRole.MEMBER.canDisband())
    }

    @Test
    fun `founders and admins can answer join requests while members cannot`() {
        assertEquals(true, AllianceRole.FOUNDER.canAnswerJoinRequests())
        assertEquals(true, AllianceRole.ADMIN.canAnswerJoinRequests())
        assertEquals(false, AllianceRole.MEMBER.canAnswerJoinRequests())
    }
}
