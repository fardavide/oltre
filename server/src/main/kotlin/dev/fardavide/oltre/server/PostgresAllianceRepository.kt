package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.JoinRequestId
import dev.fardavide.oltre.protocol.JoinDecision
import dev.fardavide.oltre.protocol.ApiError
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
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
            when (val verdict = AllianceRules.founding(connection.selectAffiliation(player), name, tag, emptyList())) {
                is FoundingVerdict.Refused -> return@transaction Founded.Refused(verdict.error)
                is FoundingVerdict.Retry -> return@transaction Founded.AlreadyFounded(verdict.alliance, verdict.role)
                FoundingVerdict.Proceed -> Unit
            }
            val id = ids.mint()
            connection.lockPlayers(listOf(player))
            val affiliation = connection.selectAffiliation(player)
            when (val verdict = AllianceRules.founding(affiliation, name, tag, emptyList())) {
                is FoundingVerdict.Refused -> return@transaction Founded.Refused(verdict.error)
                is FoundingVerdict.Retry -> return@transaction Founded.AlreadyFounded(verdict.alliance, verdict.role)
                FoundingVerdict.Proceed -> Unit
            }
            connection.update(REAP_EMPTY) {
                setString(1, AllianceRules.normalise(name).value)
                setString(2, tag.value.lowercase())
            }
            val occupied = connection.occupied(name, tag)
            when (val verdict = AllianceRules.founding(affiliation, name, tag, occupied)) {
                is FoundingVerdict.Refused -> return@transaction Founded.Refused(verdict.error)
                is FoundingVerdict.Retry -> return@transaction Founded.AlreadyFounded(verdict.alliance, verdict.role)
                FoundingVerdict.Proceed -> Unit
            }
            val inserted = connection.update(INSERT_ALLIANCE) {
                setString(1, id.value)
                setString(2, name.value)
                setString(3, AllianceRules.normalise(name).value)
                setString(4, tag.value)
                setString(5, tag.value.lowercase())
                setObject(6, now.atUtc())
                setLong(7, 0)
                setLong(8, AllianceVersion.FIRST.value)
            }
            if (inserted == 0) {
                return@transaction when (val verdict = AllianceRules.founding(
                    connection.selectAffiliation(player), name, tag, connection.occupied(name, tag),
                )) {
                    is FoundingVerdict.Refused -> Founded.Refused(verdict.error)
                    is FoundingVerdict.Retry -> Founded.AlreadyFounded(verdict.alliance, verdict.role)
                    FoundingVerdict.Proceed -> error("an alliance insert conflicted but neither spelling is occupied")
                }
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
        dataSource.transaction { connection ->
            when (val current = connection.selectAffiliation(player, lockParent = true)) {
                Affiliation.Unaffiliated -> Unit
                is Affiliation.Enlisted -> connection.succeed(current.alliance.alliance.id, now)
                is Affiliation.Petitioning -> connection.succeed(current.alliance.alliance.id, now)
            }
            connection.selectAffiliation(player)
        }

    override suspend fun alliance(id: AllianceId, now: Instant): AllianceLookup = dataSource.transaction { connection ->
        when (connection.selectAlliance(id, lockParent = true)) {
            AllianceLookup.Absent -> AllianceLookup.Absent
            is AllianceLookup.Present -> {
                connection.succeed(id, now)
                connection.selectAlliance(id, lockParent = false)
            }
        }
    }

    override suspend fun petition(player: PlayerId, alliance: AllianceId, now: Instant, expected: AllianceVersion): AllianceChange =
        changeAlliance(player, alliance, expected) { connection, stored ->
            when (val verdict = AllianceRules.petitioning(connection.selectAffiliation(player), stored)) {
                is PetitionVerdict.Refused -> return@changeAlliance AllianceChange.Refused(verdict.error)
                is PetitionVerdict.Retry -> return@changeAlliance AllianceChange.Applied(verdict.affiliation)
                PetitionVerdict.Proceed -> Unit
            }
            connection.update(INSERT_PETITION) {
                setString(1, UUID.randomUUID().toString())
                setString(2, player.value)
                setString(3, alliance.value)
                setObject(4, now.atUtc())
            }
            connection.update(BUMP_VERSION) { setString(1, alliance.value) }
            AllianceChange.Applied(connection.selectAffiliation(player))
        }

    override suspend fun detach(player: PlayerId, alliance: AllianceId, expected: AllianceVersion): AllianceChange =
        changeAlliance(player, alliance, expected) { connection, _ ->
            when (val verdict = AllianceRules.detaching(connection.selectAffiliation(player), alliance)) {
                is DetachVerdict.Refused -> return@changeAlliance AllianceChange.Refused(verdict.error)
                is DetachVerdict.Withdraw -> connection.update("DELETE FROM alliance_requests WHERE id = ?") {
                    setString(1, verdict.petition.id.value)
                }
                is DetachVerdict.Leave -> connection.update("DELETE FROM alliance_members WHERE id = ?") {
                    setString(1, verdict.seat.id.value)
                }
            }
            connection.update(BUMP_VERSION) { setString(1, alliance.value) }
            AllianceChange.Applied(Affiliation.Unaffiliated)
        }

    override suspend fun approve(caller: PlayerId, alliance: AllianceId, request: JoinRequestId, decision: JoinDecision, now: Instant, expected: AllianceVersion): AllianceChange =
        changeAlliance(caller, alliance, expected, participants = { connection ->
            connection.requestsOf(alliance, request).map { it.player }
        }) { connection, stored ->
            val requests = connection.requestsOf(alliance, request)
            val seated = requests.map { connection.selectAffiliation(it.player) }.filterIsInstance<Affiliation.Enlisted>().map { it.seat.player }.toSet()
            val petition = when (val verdict = AllianceRules.approval(connection.selectAffiliation(caller), stored, requests, request, decision, seated)) {
                is ApprovalVerdict.Refused -> return@changeAlliance AllianceChange.Refused(verdict.error)
                is ApprovalVerdict.Decline -> verdict.petition
                is ApprovalVerdict.Admit -> {
                    val petition = verdict.petition
                    connection.update(INSERT_SEAT) {
                        setString(1, UUID.randomUUID().toString())
                        setString(2, petition.player.value)
                        setString(3, alliance.value)
                        setString(4, AllianceRole.MEMBER.name)
                        setObject(5, now.atUtc())
                        setLong(6, 0)
                    }
                    petition
                }
            }
            connection.update("DELETE FROM alliance_requests WHERE player_id = ?") { setString(1, petition.player.value) }
            connection.update(BUMP_VERSION) { setString(1, alliance.value) }
            AllianceChange.Applied(connection.selectAffiliation(caller))
        }

    override suspend fun setRole(caller: PlayerId, alliance: AllianceId, member: AllianceMemberId, role: AllianceRole, expected: AllianceVersion): AllianceChange =
        changeAlliance(caller, alliance, expected) { connection, stored ->
            val seats = connection.query(SELECT_SEATS, bind = { setString(1, alliance.value) }, read = { rows ->
                buildList { while (rows.next()) add(rows.seat(alliance, PlayerId(rows.getString("player_id")))) }
            })
            when (val verdict = AllianceRules.settingRole(connection.selectAffiliation(caller), stored, seats, member, role)) {
                is RoleVerdict.Refused -> return@changeAlliance AllianceChange.Refused(verdict.error)
                is RoleVerdict.Change -> for (seat in verdict.seats) {
                    connection.update(UPDATE_ROLE) { setString(1, seat.role.name); setString(2, seat.id.value) }
                }
            }
            connection.update(BUMP_VERSION) { setString(1, alliance.value) }
            AllianceChange.Applied(connection.selectAffiliation(caller))
        }

    override suspend fun kick(caller: PlayerId, alliance: AllianceId, member: AllianceMemberId, expected: AllianceVersion): AllianceChange =
        changeAlliance(caller, alliance, expected) { connection, stored ->
            val seats = connection.query(SELECT_SEATS, bind = { setString(1, alliance.value) }, read = { rows ->
                buildList { while (rows.next()) add(rows.seat(alliance, PlayerId(rows.getString("player_id")))) }
            })
            when (val verdict = AllianceRules.removing(connection.selectAffiliation(caller), stored, seats, member)) {
                is RemovalVerdict.Refused -> return@changeAlliance AllianceChange.Refused(verdict.error)
                is RemovalVerdict.Remove -> connection.update("DELETE FROM alliance_members WHERE id = ?") { setString(1, verdict.seat.id.value) }
            }
            connection.update(BUMP_VERSION) { setString(1, alliance.value) }
            AllianceChange.Applied(connection.selectAffiliation(caller))
        }

    override suspend fun rename(caller: PlayerId, alliance: AllianceId, name: AllianceName, tag: AllianceTag, expected: AllianceVersion): AllianceChange =
        changeAlliance(caller, alliance, expected) { connection, stored ->
            when (val authority = AllianceRules.renaming(connection.selectAffiliation(caller), stored, name, tag, connection.occupied(name, tag))) {
                AllianceAuthority.Granted -> Unit
                is AllianceAuthority.Refused -> return@changeAlliance AllianceChange.Refused(authority.error)
            }
            val beforeRename = connection.setSavepoint()
            try {
                connection.update("UPDATE alliances SET name = ?, normalised_name = ?, tag = ?, normalised_tag = ?, version = version + 1 WHERE id = ?") {
                    setString(1, name.value)
                    setString(2, AllianceRules.normalise(name).value)
                    setString(3, tag.value)
                    setString(4, tag.value.lowercase())
                    setString(5, alliance.value)
                }
            } catch (failure: SQLException) {
                if (failure.sqlState != "23505") throw failure
                connection.rollback(beforeRename)
                connection.releaseSavepoint(beforeRename)
                return@changeAlliance when (val authority = AllianceRules.renaming(connection.selectAffiliation(caller), stored, name, tag, connection.occupied(name, tag))) {
                    AllianceAuthority.Granted -> throw failure
                    is AllianceAuthority.Refused -> AllianceChange.Refused(authority.error)
                }
            }
            connection.releaseSavepoint(beforeRename)
            AllianceChange.Applied(connection.selectAffiliation(caller))
        }

    override suspend fun disband(caller: PlayerId, alliance: AllianceId, expected: AllianceVersion): AllianceChange =
        changeAlliance(caller, alliance, expected) { connection, _ ->
            when (val authority = AllianceRules.founderAuthority(connection.selectAffiliation(caller), alliance)) {
                AllianceAuthority.Granted -> Unit
                is AllianceAuthority.Refused -> return@changeAlliance AllianceChange.Refused(authority.error)
            }
            connection.update("DELETE FROM alliances WHERE id = ?") { setString(1, alliance.value) }
            AllianceChange.Applied(Affiliation.Unaffiliated)
        }

    private suspend fun changeAlliance(
        caller: PlayerId,
        alliance: AllianceId,
        expected: AllianceVersion,
        participants: (Connection) -> List<PlayerId> = { emptyList() },
        write: (Connection, StoredAlliance) -> AllianceChange,
    ): AllianceChange = dataSource.transaction { connection ->
        connection.lockPlayers(listOf(caller) + participants(connection))
        val stored = when (val lookup = connection.selectAlliance(alliance, lockParent = true)) {
            AllianceLookup.Absent -> return@transaction AllianceChange.Refused(ApiError.NoSuchAlliance)
            is AllianceLookup.Present -> lookup.alliance
        }
        if (stored.version != expected) return@transaction AllianceChange.Stale
        write(connection, stored)
    }
}

private fun Connection.succeed(alliance: AllianceId, now: Instant) {
    val seats = query(SELECT_SEATS, bind = { setString(1, alliance.value) }, read = { rows ->
        buildList { while (rows.next()) add(rows.seat(alliance, PlayerId(rows.getString("player_id")))) }
    })
    val active = query(SELECT_SYNCS, bind = { setString(1, alliance.value) }, read = { rows -> buildSet {
        while (rows.next()) {
            val syncedAt = Instant.parse(rows.getObject("last_updated_at", OffsetDateTime::class.java).toInstant().toString())
            if (AllianceRules.isActive(syncedAt, now)) add(PlayerId(rows.getString("player_id")))
        }
    } })
    when (val choice = AllianceRules.successor(seats, active)) {
        SuccessionChoice.Unchanged -> Unit
        is SuccessionChoice.Promote -> {
            for (seat in AllianceRules.transferLeadership(seats, choice.seat.id)) {
                update(UPDATE_ROLE) { setString(1, seat.role.name); setString(2, seat.id.value) }
            }
            update(BUMP_VERSION) { setString(1, alliance.value) }
        }
    }
}

private fun Connection.requestsOf(alliance: AllianceId, request: JoinRequestId): List<Petition> = query(
    "SELECT id, player_id, requested_at FROM alliance_requests WHERE alliance_id = ? AND id = ?",
    bind = { setString(1, alliance.value); setString(2, request.value) },
    read = { rows -> buildList {
        while (rows.next()) add(Petition(
            JoinRequestId(rows.getString("id")), PlayerId(rows.getString("player_id")), alliance,
            Instant.parse(rows.getObject("requested_at", OffsetDateTime::class.java).toInstant().toString()),
        ))
    } },
)

private fun Connection.selectAlliance(alliance: AllianceId, lockParent: Boolean): AllianceLookup = query(
    SELECT_ALLIANCE + if (lockParent) " FOR UPDATE OF a" else "",
    bind = { setString(1, alliance.value) },
    read = { rows -> if (rows.next()) AllianceLookup.Present(rows.storedAlliance()) else AllianceLookup.Absent },
)

private fun Connection.lockPlayers(players: List<PlayerId>) {
    for (player in players.distinct().sortedBy { it.value }) {
        query("SELECT id FROM players WHERE id = ? FOR UPDATE", bind = { setString(1, player.value) }, read = { rows ->
            while (rows.next()) Unit
        })
    }
}

private fun Connection.occupied(name: AllianceName, tag: AllianceTag): List<StoredAlliance> = query(
    SELECT_OCCUPIED,
    bind = {
        setString(1, AllianceRules.normalise(name).value)
        setString(2, tag.value.lowercase())
    },
    read = { rows -> buildList { while (rows.next()) add(rows.storedAlliance()) } },
)

private fun Connection.selectAffiliation(player: PlayerId, lockParent: Boolean = false): Affiliation = query(
    SELECT_AFFILIATION + if (lockParent) " FOR UPDATE OF a" else "",
    bind = { setString(1, player.value) },
    read = { rows ->
        if (rows.next()) {
            val alliance = rows.storedAlliance()
            Affiliation.Enlisted(
                alliance,
                rows.seat(alliance.alliance.id, player),
            )
        } else {
            query(
                SELECT_PETITION + if (lockParent) " FOR UPDATE OF a" else "",
                bind = { setString(1, player.value) },
                read = { pending ->
                    if (pending.next()) {
                        val alliance = pending.storedAlliance()
                        Affiliation.Petitioning(alliance, Petition(
                            JoinRequestId(pending.getString("request_id")), player, alliance.alliance.id,
                            Instant.parse(pending.getObject("requested_at", OffsetDateTime::class.java).toInstant().toString()),
                        ))
                    } else {
                        Affiliation.Unaffiliated
                    }
                },
            )
        }
    },
)

private fun ResultSet.seat(alliance: AllianceId, player: PlayerId): Seat = Seat(
    AllianceMemberId(getString("member_id")),
    player,
    alliance,
    AllianceRole.valueOf(getString("role")),
    Instant.parse(getObject("joined_at", OffsetDateTime::class.java).toInstant().toString()),
    getLong("contributed"),
)

private fun ResultSet.storedAlliance(): StoredAlliance = allianceFrom(
    AllianceId(getString("id")),
    AllianceName(getString("name")),
    AllianceTag(getString("tag")),
    AllianceVersion(getLong("version")),
    Instant.parse(getObject("created_at", OffsetDateTime::class.java).toInstant().toString()),
    getLong("experience"),
    getInt("seat_count"),
)

private const val SELECT_AFFILIATION = """
    SELECT a.*, m.id AS member_id, m.role, m.joined_at, m.contributed,
           (SELECT count(*) FROM alliance_members WHERE alliance_id = a.id) AS seat_count
    FROM alliances a JOIN alliance_members m ON m.alliance_id = a.id
    WHERE m.player_id = ?
"""

private const val SELECT_ALLIANCE = """
    SELECT a.*, (SELECT count(*) FROM alliance_members WHERE alliance_id = a.id) AS seat_count
    FROM alliances a WHERE id = ?
"""

private const val SELECT_PETITION = """
    SELECT a.*, r.id AS request_id, r.requested_at,
           (SELECT count(*) FROM alliance_members WHERE alliance_id = a.id) AS seat_count
    FROM alliances a JOIN alliance_requests r ON r.alliance_id = a.id WHERE r.player_id = ?
"""

private const val INSERT_PETITION = """
    INSERT INTO alliance_requests (id, player_id, alliance_id, requested_at) VALUES (?, ?, ?, ?)
"""

private const val INSERT_ALLIANCE = """
    INSERT INTO alliances (id, name, normalised_name, tag, normalised_tag, created_at, experience, version)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT DO NOTHING
"""

private const val SELECT_OCCUPIED = """
    SELECT a.*, (SELECT count(*) FROM alliance_members WHERE alliance_id = a.id) AS seat_count
    FROM alliances a WHERE normalised_name = ? OR normalised_tag = ?
"""

private const val INSERT_SEAT = """
    INSERT INTO alliance_members (id, player_id, alliance_id, role, joined_at, contributed)
    VALUES (?, ?, ?, ?, ?, ?)
"""

private const val SELECT_SEATS = """
    SELECT id AS member_id, player_id, role, joined_at, contributed FROM alliance_members WHERE alliance_id = ?
"""

private const val SELECT_SYNCS = """
    SELECT m.player_id, c.last_updated_at FROM alliance_members m JOIN colonies c ON c.player_id = m.player_id
    WHERE m.alliance_id = ?
"""

private const val UPDATE_ROLE = "UPDATE alliance_members SET role = ? WHERE id = ?"

private const val BUMP_VERSION = "UPDATE alliances SET version = version + 1 WHERE id = ?"

private const val REAP_EMPTY = """
    DELETE FROM alliances a WHERE (normalised_name = ? OR normalised_tag = ?)
    AND NOT EXISTS (SELECT 1 FROM alliance_members m WHERE m.alliance_id = a.id)
"""
