package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.Alliance
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceLevel
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceSeats
import dev.fardavide.oltre.protocol.AllianceTag
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource
import kotlin.time.Instant

internal class PostgresAllianceRepository(
    private val dataSource: DataSource,
    private val ids: AllianceIds = AllianceIds.RANDOM,
) : AllianceRepository {

    override suspend fun found(player: PlayerId, name: AllianceName, tag: AllianceTag, now: Instant): Founded =
        dataSource.transaction { connection ->
            val id = ids.mint()
            connection.update(INSERT_ALLIANCE) {
                setString(1, id.value)
                setString(2, name.value)
                setString(3, AllianceRules.normalise(name).value)
                setString(4, tag.value)
                setString(5, tag.value.lowercase())
                setObject(6, now.atUtc())
                setLong(7, 0)
                setLong(8, AllianceVersion.FIRST.value)
            }
            connection.update(INSERT_SEAT) {
                setString(1, UUID.randomUUID().toString())
                setString(2, player.value)
                setString(3, id.value)
                setString(4, AllianceRole.FOUNDER.name)
                setObject(5, now.atUtc())
                setLong(6, 0)
            }
            val enlisted = connection.selectAffiliation(player)
            check(enlisted is Affiliation.Enlisted) { "a founder seat was inserted but could not be read" }
            Founded.Made(enlisted.alliance)
        }

    override suspend fun allianceOf(player: PlayerId, now: Instant): Affiliation =
        dataSource.transaction { connection -> connection.selectAffiliation(player) }
}

private fun Connection.selectAffiliation(player: PlayerId): Affiliation = query(
    SELECT_AFFILIATION,
    bind = { setString(1, player.value) },
    read = { rows ->
        if (rows.next()) {
            val alliance = rows.storedAlliance()
            Affiliation.Enlisted(
                alliance,
                Seat(
                    AllianceMemberId(rows.getString("member_id")),
                    player,
                    alliance.alliance.id,
                    AllianceRole.valueOf(rows.getString("role")),
                    Instant.parse(rows.getObject("joined_at", OffsetDateTime::class.java).toInstant().toString()),
                    rows.getLong("contributed"),
                ),
            )
        } else {
            Affiliation.Unaffiliated
        }
    },
)

private fun ResultSet.storedAlliance(): StoredAlliance = StoredAlliance(
    Alliance(
        AllianceId(getString("id")),
        AllianceName(getString("name")),
        AllianceTag(getString("tag")),
        AllianceLevel(0),
        AllianceSeats(getInt("seat_count"), AllianceRules.SEAT_CAP),
    ),
    AllianceVersion(getLong("version")),
    Instant.parse(getObject("created_at", OffsetDateTime::class.java).toInstant().toString()),
    getLong("experience"),
)

private const val SELECT_AFFILIATION = """
    SELECT a.*, m.id AS member_id, m.role, m.joined_at, m.contributed,
           (SELECT count(*) FROM alliance_members WHERE alliance_id = a.id) AS seat_count
    FROM alliances a JOIN alliance_members m ON m.alliance_id = a.id
    WHERE m.player_id = ?
"""

private const val INSERT_ALLIANCE = """
    INSERT INTO alliances (id, name, normalised_name, tag, normalised_tag, created_at, experience, version)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
"""

private const val INSERT_SEAT = """
    INSERT INTO alliance_members (id, player_id, alliance_id, role, joined_at, contributed)
    VALUES (?, ?, ?, ?, ?, ?)
"""
