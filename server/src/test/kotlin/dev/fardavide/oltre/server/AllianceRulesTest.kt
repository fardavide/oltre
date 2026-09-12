package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceRole
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.nanoseconds

class AllianceRulesTest {

    @Test
    fun `a sync exactly thirty days ago is active and an older sync is inactive`() {
        assertTrue(AllianceRules.isActive(TEST_NOW - 30.days, TEST_NOW))
        assertFalse(AllianceRules.isActive(TEST_NOW - 30.days - 1.nanoseconds, TEST_NOW))
    }

    @Test
    fun `an admin may remove members but not admins or the founder`() {
        assertTrue(AllianceRules.canRemove(AllianceRole.ADMIN, AllianceRole.MEMBER))
        assertFalse(AllianceRules.canRemove(AllianceRole.ADMIN, AllianceRole.ADMIN))
        assertFalse(AllianceRules.canRemove(AllianceRole.ADMIN, AllianceRole.FOUNDER))
    }

    @Test
    fun `the founder may remove admins and members but may not remove a founder`() {
        assertTrue(AllianceRules.canRemove(AllianceRole.FOUNDER, AllianceRole.ADMIN))
        assertTrue(AllianceRules.canRemove(AllianceRole.FOUNDER, AllianceRole.MEMBER))
        assertFalse(AllianceRules.canRemove(AllianceRole.FOUNDER, AllianceRole.FOUNDER))
    }
}
