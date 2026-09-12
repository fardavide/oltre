package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AllianceNameMigrationTest {

    @Test
    fun `backfill changes only legacy normalized bytes and keeps display names`() {
        val legacy = AllianceNameRow(AllianceId("legacy"), AllianceName("Ｖａｎｇｕａｒｄ"), CanonicalAllianceName("ｖａｎｇｕａｒｄ"))
        val current = AllianceNameRow(AllianceId("current"), AllianceName("Fleet"), CanonicalAllianceName("fleet"))

        val migration = AllianceNameMigration.from(listOf(legacy, current))

        assertEquals(listOf(legacy.copy(canonical = CanonicalAllianceName("vanguard"))), migration.updates)
        assertEquals(CanonicalAllianceName("ｖａｎｇｕａｒｄ"), legacy.canonical)
    }

    @Test
    fun `a normalized backfill plans nothing when applied again`() {
        val row = AllianceNameRow(AllianceId("legacy"), AllianceName("Ｖａｎｇｕａｒｄ"), CanonicalAllianceName("ｖａｎｇｕａｒｄ"))
        val changed = AllianceNameMigration.from(listOf(row)).updates

        val repeated = AllianceNameMigration.from(changed)

        assertEquals(emptyList(), repeated.updates)
    }

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
