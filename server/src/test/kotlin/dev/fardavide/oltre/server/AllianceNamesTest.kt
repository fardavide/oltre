package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceName
import kotlin.test.Test
import kotlin.test.assertEquals

class AllianceNamesTest {

    @Test
    fun `compatibility spellings share the ordinary name`() {
        for (spelling in listOf("Ｖａｎｇｕａｒｄ", "Ⅴanguard", "Vanguard")) {
            assertEquals(CanonicalAllianceName("vanguard"), AllianceRules.normalise(AllianceName(spelling)))
        }
    }
}
