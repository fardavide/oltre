package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.Alliance
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceLevel
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceSeats
import dev.fardavide.oltre.protocol.AllianceTag
import kotlin.time.Instant

// Row meaning is independent of JDBC. The balance slice replaces level zero and the temporary cap.
internal fun allianceFrom(
    id: AllianceId,
    name: AllianceName,
    tag: AllianceTag,
    version: AllianceVersion,
    createdAt: Instant,
    experience: Long,
    taken: Int,
): StoredAlliance = StoredAlliance(
    Alliance(id, name, tag, AllianceLevel(0), AllianceSeats(taken, AllianceRules.SEAT_CAP)),
    version,
    createdAt,
    experience,
)
