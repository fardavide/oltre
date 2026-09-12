package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceName
import kotlin.test.Test
import kotlin.test.assertEquals

class AllianceNamesTest {

    @Test
    fun `normalisation folds case and all Unicode whitespace and is idempotent`() {
        for (spelling in listOf("  STRASSE\t  FLEET  ", "Straße\u00a0Fleet", "Straße\u3000Fleet", "Straße\u0085Fleet")) {
            val normalised = CanonicalAllianceName.normalised(spelling)
            assertEquals(CanonicalAllianceName("strasse fleet"), normalised)
            assertEquals(normalised, CanonicalAllianceName.normalised(normalised.value))
        }
        assertEquals(CanonicalAllianceName("i fleet"), CanonicalAllianceName.normalised("I FLEET"))
    }

    @Test
    fun `SQL prefix escaping preserves literal wildcard and escape characters`() {
        assertEquals("fleet \\%\\_\\\\%", CanonicalAllianceName("fleet %_\\").likePrefix)
    }

    @Test
    fun `compatibility spellings share the ordinary name`() {
        for (spelling in listOf("Ｖａｎｇｕａｒｄ", "Ⅴanguard", "Vanguard")) {
            assertEquals(CanonicalAllianceName("vanguard"), AllianceRules.normalise(AllianceName(spelling)))
        }
    }
}
