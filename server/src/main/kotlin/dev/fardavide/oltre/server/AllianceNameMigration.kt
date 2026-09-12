package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceName

internal data class AllianceNameRow(
    val id: AllianceId,
    val name: AllianceName,
    val canonical: CanonicalAllianceName,
)

internal data class AllianceNameMigration(val updates: List<AllianceNameRow>) {

    companion object {

        fun from(rows: List<AllianceNameRow>): AllianceNameMigration {
            val normalised = rows.map { it.copy(canonical = AllianceRules.normalise(it.name)) }
            val collisions = normalised.groupBy { it.canonical }.values.filter { it.size > 1 }
            check(collisions.isEmpty()) {
                "alliance name normalisation collides for ids: " + collisions.joinToString("; ") { group -> group.joinToString { it.id.value } }
            }
            return AllianceNameMigration(rows.zip(normalised).filter { (before, after) -> before.canonical != after.canonical }.map { it.second })
        }
    }
}
