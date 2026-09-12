package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceName
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AllianceNameMigrationTest {

    @Test
    fun `compatibility collisions name both alliances before any update is planned`() {
        val rows = listOf(
            AllianceNameRow(AllianceId("full-width"), AllianceName("Ｖａｎｇｕａｒｄ"), CanonicalAllianceName("ｖａｎｇｕａｒｄ")),
            AllianceNameRow(AllianceId("ordinary"), AllianceName("Vanguard"), CanonicalAllianceName("vanguard")),
        )

        val failure = assertFailsWith<IllegalStateException> { AllianceNameMigration.from(rows) }

        assertTrue(failure.message.orEmpty().contains("full-width"))
        assertTrue(failure.message.orEmpty().contains("ordinary"))
    }
}
