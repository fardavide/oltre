package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.Alliance
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceLevel
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceSeats
import dev.fardavide.oltre.protocol.AllianceTag
import kotlin.test.Test
import kotlin.test.assertEquals

class AllianceRowTest {

    @Test
    fun `a stored alliance row carries its version and authoritative seat count`() {
        val id = AllianceId("vanguard")
        val name = AllianceName("Vanguard")
        val tag = AllianceTag("VNG")

        val stored = allianceFrom(id, name, tag, AllianceVersion(3), TEST_NOW, experience = 95_000, taken = 7)

        assertEquals(StoredAlliance(Alliance(id, name, tag, AllianceLevel(0), AllianceSeats(7, 20)), AllianceVersion(3), TEST_NOW, 95_000), stored)
    }
}
