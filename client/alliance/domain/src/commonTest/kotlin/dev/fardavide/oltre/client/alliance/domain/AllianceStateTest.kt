package dev.fardavide.oltre.client.alliance.domain

import kotlin.test.Test
import kotlin.test.assertNotEquals

class AllianceStateTest {

    @Test
    fun `an unread standing is distinct from being unaffiliated`() {
        assertNotEquals<AllianceState>(AllianceState.Unread, AllianceState.Unaffiliated)
    }
}
