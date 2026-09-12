package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.Alliance
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceLevel
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceSeats
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.JoinRequestId
import dev.fardavide.oltre.protocol.JoinDecision
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.time.Instant

internal class InMemoryAllianceRepository(
    private val colonies: InMemoryColonyRepository,
    private val ids: AllianceIds = AllianceIds.RANDOM,
) : AllianceRepository {

    private val lock = Mutex()
    private val alliances = mutableMapOf<AllianceId, StoredAlliance>()
    private val seats = mutableMapOf<PlayerId, Seat>()
    private val petitions = mutableMapOf<PlayerId, Petition>()

    override suspend fun search(query: CanonicalAllianceName, cursor: AllianceSearchPosition?, limit: Int): List<StoredAlliance> = lock.withLock {
        alliances.values
            .filter { AllianceRules.normalise(it.alliance.name).value.startsWith(query.value) }
            .sortedBy { AllianceSearchPosition.from(it) }
            .filter { cursor == null || AllianceSearchPosition.from(it) > cursor }
            .take(limit)
    }

    override suspend fun found(player: PlayerId, name: AllianceName, tag: AllianceTag, now: Instant): Founded =
        lock.withLock {
            val affiliation = affiliationOf(player)
            when (val verdict = AllianceRules.founding(affiliation, name, tag, emptyList())) {
                is FoundingVerdict.Refused -> return@withLock Founded.Refused(verdict.error)
                is FoundingVerdict.Retry -> return@withLock Founded.AlreadyFounded(verdict.alliance, verdict.role)
                FoundingVerdict.Proceed -> Unit
            }
            val empty = alliances.values.filter {
                it.alliance.seats.taken == 0 &&
                    (AllianceRules.normalise(it.alliance.name) == AllianceRules.normalise(name) || it.alliance.tag == tag)
            }
            for (alliance in empty) {
                alliances.remove(alliance.alliance.id)
                petitions.entries.removeAll { it.value.alliance == alliance.alliance.id }
            }
            when (val verdict = AllianceRules.founding(affiliation, name, tag, alliances.values.toList())) {
                is FoundingVerdict.Refused -> return@withLock Founded.Refused(verdict.error)
                is FoundingVerdict.Retry -> return@withLock Founded.AlreadyFounded(verdict.alliance, verdict.role)
                FoundingVerdict.Proceed -> Unit
            }
            val id = ids.mint()
            val stored = StoredAlliance(
                Alliance(id, name, tag, AllianceLevel(0), AllianceSeats(1, AllianceRules.SEAT_CAP)),
                AllianceVersion.FIRST,
                now,
                0,
            )
            alliances[id] = stored
            seats[player] = Seat(
                AllianceMemberId(UUID.randomUUID().toString()), player, id, AllianceRole.FOUNDER, now, 0,
            )
            Founded.Made(stored)
        }

    override suspend fun allianceOf(player: PlayerId, now: Instant): Affiliation = lock.withLock {
        when (val current = affiliationOf(player)) {
            Affiliation.Unaffiliated -> Unit
            is Affiliation.Enlisted -> succeed(current.alliance.alliance.id, now)
            is Affiliation.Petitioning -> succeed(current.alliance.alliance.id, now)
        }
        affiliationOf(player)
    }

    override suspend fun alliance(id: AllianceId, now: Instant): AllianceLookup = lock.withLock {
        succeed(id, now)
        alliances[id]?.let { AllianceLookup.Present(it) } ?: AllianceLookup.Absent
    }

    override suspend fun petition(player: PlayerId, alliance: AllianceId, now: Instant, expected: AllianceVersion): AllianceChange = lock.withLock {
        val stored = alliances[alliance] ?: return@withLock AllianceChange.Refused(ApiError.NoSuchAlliance)
        if (stored.version != expected) return@withLock AllianceChange.Stale
        val current = affiliationOf(player)
        when (val verdict = AllianceRules.petitioning(current, stored)) {
            is PetitionVerdict.Refused -> return@withLock AllianceChange.Refused(verdict.error)
            is PetitionVerdict.Retry -> return@withLock AllianceChange.Applied(verdict.affiliation)
            PetitionVerdict.Proceed -> Unit
        }
        val updated = stored.copy(version = stored.version.next())
        val petition = Petition(JoinRequestId(UUID.randomUUID().toString()), player, alliance, now)
        alliances[alliance] = updated
        petitions[player] = petition
        AllianceChange.Applied(Affiliation.Petitioning(updated, petition))
    }

    suspend fun forget(player: PlayerId): Unit = lock.withLock {
        petitions.remove(player)
        val seat = seats.remove(player) ?: return@withLock
        val stored = alliances.getValue(seat.alliance)
        alliances[seat.alliance] = stored.copy(alliance = stored.alliance.copy(
            seats = AllianceSeats(seats.values.count { it.alliance == seat.alliance }, AllianceRules.SEAT_CAP),
        ))
    }

    override suspend fun detach(player: PlayerId, alliance: AllianceId, expected: AllianceVersion): AllianceChange = lock.withLock {
        val stored = alliances[alliance] ?: return@withLock AllianceChange.Refused(ApiError.NoSuchAlliance)
        if (stored.version != expected) return@withLock AllianceChange.Stale
        when (val verdict = AllianceRules.detaching(affiliationOf(player), alliance)) {
            is DetachVerdict.Refused -> return@withLock AllianceChange.Refused(verdict.error)
            is DetachVerdict.Withdraw -> petitions.remove(player)
            is DetachVerdict.Leave -> seats.remove(player)
        }
        alliances[alliance] = stored.copy(version = stored.version.next(), alliance = stored.alliance.copy(
            seats = AllianceSeats(seats.values.count { it.alliance == alliance }, AllianceRules.SEAT_CAP),
        ))
        AllianceChange.Applied(Affiliation.Unaffiliated)
    }

    override suspend fun approve(caller: PlayerId, alliance: AllianceId, request: JoinRequestId, decision: JoinDecision, now: Instant, expected: AllianceVersion): AllianceChange = lock.withLock {
        val stored = alliances[alliance] ?: return@withLock AllianceChange.Refused(ApiError.NoSuchAlliance)
        if (stored.version != expected) return@withLock AllianceChange.Stale
        val petition = when (val verdict = AllianceRules.approval(affiliationOf(caller), stored, petitions.values.filter { it.alliance == alliance }, request, decision, seats.keys)) {
            is ApprovalVerdict.Refused -> return@withLock AllianceChange.Refused(verdict.error)
            is ApprovalVerdict.Decline -> verdict.petition
            is ApprovalVerdict.Admit -> {
                val petition = verdict.petition
                seats[petition.player] = Seat(AllianceMemberId(UUID.randomUUID().toString()), petition.player, alliance, AllianceRole.MEMBER, now, 0)
                petition
            }
        }
        petitions.remove(petition.player)
        alliances[alliance] = stored.copy(version = stored.version.next(), alliance = stored.alliance.copy(seats = AllianceSeats(seats.values.count { it.alliance == alliance }, AllianceRules.SEAT_CAP)))
        AllianceChange.Applied(affiliationOf(caller))
    }

    override suspend fun setRole(caller: PlayerId, alliance: AllianceId, member: AllianceMemberId, role: AllianceRole, expected: AllianceVersion): AllianceChange = lock.withLock {
        val stored = alliances[alliance] ?: return@withLock AllianceChange.Refused(ApiError.NoSuchAlliance)
        if (stored.version != expected) return@withLock AllianceChange.Stale
        when (val verdict = AllianceRules.settingRole(affiliationOf(caller), stored, seats.values.filter { it.alliance == alliance }, member, role)) {
            is RoleVerdict.Refused -> return@withLock AllianceChange.Refused(verdict.error)
            is RoleVerdict.Change -> for (seat in verdict.seats) seats[seat.player] = seat
        }
        alliances[alliance] = stored.copy(version = stored.version.next())
        AllianceChange.Applied(affiliationOf(caller))
    }

    override suspend fun kick(caller: PlayerId, alliance: AllianceId, member: AllianceMemberId, expected: AllianceVersion): AllianceChange = lock.withLock {
        val stored = alliances[alliance] ?: return@withLock AllianceChange.Refused(ApiError.NoSuchAlliance)
        if (stored.version != expected) return@withLock AllianceChange.Stale
        when (val verdict = AllianceRules.removing(affiliationOf(caller), stored, seats.values.filter { it.alliance == alliance }, member)) {
            is RemovalVerdict.Refused -> return@withLock AllianceChange.Refused(verdict.error)
            is RemovalVerdict.Remove -> seats.remove(verdict.seat.player)
        }
        alliances[alliance] = stored.copy(version = stored.version.next(), alliance = stored.alliance.copy(
            seats = AllianceSeats(seats.values.count { it.alliance == alliance }, AllianceRules.SEAT_CAP),
        ))
        AllianceChange.Applied(affiliationOf(caller))
    }

    override suspend fun rename(caller: PlayerId, alliance: AllianceId, name: AllianceName, tag: AllianceTag, expected: AllianceVersion): AllianceChange = lock.withLock {
        val stored = alliances[alliance] ?: return@withLock AllianceChange.Refused(ApiError.NoSuchAlliance)
        if (stored.version != expected) return@withLock AllianceChange.Stale
        when (val authority = AllianceRules.renaming(affiliationOf(caller), stored, name, tag, alliances.values.toList())) {
            AllianceAuthority.Granted -> Unit
            is AllianceAuthority.Refused -> return@withLock AllianceChange.Refused(authority.error)
        }
        alliances[alliance] = stored.copy(version = stored.version.next(), alliance = stored.alliance.copy(name = name, tag = tag))
        AllianceChange.Applied(affiliationOf(caller))
    }

    override suspend fun disband(caller: PlayerId, alliance: AllianceId, expected: AllianceVersion): AllianceChange = lock.withLock {
        val stored = alliances[alliance] ?: return@withLock AllianceChange.Refused(ApiError.NoSuchAlliance)
        if (stored.version != expected) return@withLock AllianceChange.Stale
        when (val authority = AllianceRules.founderAuthority(affiliationOf(caller), alliance)) {
            AllianceAuthority.Granted -> Unit
            is AllianceAuthority.Refused -> return@withLock AllianceChange.Refused(authority.error)
        }
        alliances.remove(alliance)
        seats.entries.removeAll { it.value.alliance == alliance }
        petitions.entries.removeAll { it.value.alliance == alliance }
        AllianceChange.Applied(Affiliation.Unaffiliated)
    }

    private fun affiliationOf(player: PlayerId): Affiliation =
        seats[player]?.let { Affiliation.Enlisted(alliances.getValue(it.alliance), it) }
            ?: petitions[player]?.let { Affiliation.Petitioning(alliances.getValue(it.alliance), it) }
            ?: Affiliation.Unaffiliated

    private suspend fun succeed(alliance: AllianceId, now: Instant) {
        val roster = seats.values.filter { it.alliance == alliance }
        val active = buildSet {
            for (seat in roster) {
                val colony = colonies.colonyOf(seat.player)
                if (colony != null && AllianceRules.isActive(colony.snapshot.lastUpdatedAt, now)) add(seat.player)
            }
        }
        when (val choice = AllianceRules.successor(roster, active)) {
            SuccessionChoice.Unchanged -> Unit
            is SuccessionChoice.Promote -> {
                for (seat in AllianceRules.transferLeadership(roster, choice.seat.id)) seats[seat.player] = seat
                val stored = alliances.getValue(alliance)
                alliances[alliance] = stored.copy(version = stored.version.next())
            }
        }
    }
}
