package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.protocol.Alliance
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceProject
import dev.fardavide.oltre.protocol.AllianceLevel
import dev.fardavide.oltre.protocol.AllianceMember
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceSeats
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ExperienceReading
import dev.fardavide.oltre.protocol.JoinRequest
import dev.fardavide.oltre.protocol.JoinRequestId
import dev.fardavide.oltre.protocol.JoinDecision
import dev.fardavide.oltre.protocol.PlayerProfile
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.time.Instant

internal class InMemoryAllianceRepository(
    private val colonies: InMemoryColonyRepository,
    private val ids: AllianceIds = AllianceIds.RANDOM,
) : AllianceRepository {

    val playerProfiles = ConcurrentHashMap<PlayerId, PlayerProfile>()

    private val lock = Mutex()
    private val alliances = mutableMapOf<AllianceId, StoredAlliance>()
    private val seats = mutableMapOf<PlayerId, Seat>()
    private val petitions = mutableMapOf<PlayerId, Petition>()

    override suspend fun rosterOf(player: PlayerId, now: Instant): RosterRead = lock.withLock {
        val current = when (val affiliation = affiliationOf(player)) {
            Affiliation.Unaffiliated,
            is Affiliation.Petitioning,
            -> return@withLock RosterRead.Refused(ApiError.NotInAnAlliance)
            is Affiliation.Enlisted -> affiliation
        }
        val alliance = current.alliance.alliance.id
        if (colonies.colonyOf(player) == null) return@withLock RosterRead.Refused(ApiError.NoColony)
        succeed(alliance, now)
        val members = buildList {
            val ordered = seats.values.filter { it.alliance == alliance }.sortedWith(
                compareBy<Seat> {
                    when (it.role) {
                        AllianceRole.FOUNDER -> 0
                        AllianceRole.ADMIN -> 1
                        AllianceRole.MEMBER -> 2
                    }
                }.thenBy { it.joinedAt }.thenBy { it.player.value },
            )
            for (seat in ordered) {
                val colony = checkNotNull(colonies.colonyOf(seat.player)) { "an alliance member has no colony" }
                add(AllianceMember(
                    seat.id, playerProfiles.getValue(seat.player), seat.role,
                    ExperienceReading.Known(colony.snapshot.state.experience), colony.snapshot.lastUpdatedAt,
                ))
            }
        }
        val role = seats.getValue(player).role
        val pending = when (role) {
            AllianceRole.FOUNDER,
            AllianceRole.ADMIN,
            -> petitions.values.filter { it.alliance == alliance }
                .sortedWith(compareBy<Petition> { it.requestedAt }.thenBy { it.player.value }).map { petition ->
                    val experience = colonies.colonyOf(petition.player)?.snapshot?.state?.experience
                    JoinRequest(
                        petition.id, playerProfiles.getValue(petition.player),
                        experience?.let { ExperienceReading.Known(it) } ?: ExperienceReading.Unknown,
                        petition.requestedAt,
                    )
                }
            AllianceRole.MEMBER -> emptyList()
        }
        rosterFor(role, members, pending)
    }

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
                Alliance(
                    id,
                    name,
                    tag,
                    AllianceLevel(0),
                    AllianceSeats(1, AllianceBalance.seatCap(AllianceLevel(0), seatsBought = 0)),
                ),
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
        alliances[seat.alliance] = alliances.getValue(seat.alliance).reseated()
    }

    override suspend fun detach(player: PlayerId, alliance: AllianceId, expected: AllianceVersion): AllianceChange = lock.withLock {
        val stored = alliances[alliance] ?: return@withLock AllianceChange.Refused(ApiError.NoSuchAlliance)
        if (stored.version != expected) return@withLock AllianceChange.Stale
        when (val verdict = AllianceRules.detaching(affiliationOf(player), alliance)) {
            is DetachVerdict.Refused -> return@withLock AllianceChange.Refused(verdict.error)
            is DetachVerdict.Withdraw -> petitions.remove(player)
            is DetachVerdict.Leave -> seats.remove(player)
        }
        alliances[alliance] = stored.copy(version = stored.version.next()).reseated()
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
        alliances[alliance] = stored.copy(version = stored.version.next()).reseated()
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
        alliances[alliance] = stored.copy(version = stored.version.next()).reseated()
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

    override suspend fun treasuryOf(player: PlayerId, now: Instant): TreasuryRead = lock.withLock {
        val enlisted = when (val affiliation = affiliationOf(player)) {
            Affiliation.Unaffiliated,
            is Affiliation.Petitioning,
            -> return@withLock TreasuryRead.Refused(ApiError.NotInAnAlliance)
            is Affiliation.Enlisted -> affiliation
        }
        succeed(enlisted.alliance.alliance.id, now)
        val seat = seats.getValue(player)
        TreasuryRead.Present(alliances.getValue(seat.alliance), seat.contributed, seat.role)
    }

    override suspend fun buy(
        caller: PlayerId,
        project: AllianceProject,
        now: Instant,
        expected: AllianceVersion,
    ): TreasuryRead = lock.withLock {
        val seat = seats[caller] ?: return@withLock TreasuryRead.Refused(ApiError.NotInAnAlliance)
        val stored = alliances.getValue(seat.alliance)
        if (stored.version != expected) return@withLock TreasuryRead.Stale
        // **Founder or admin, and the check is `canSpend` rather than `founderAuthority`.**
        // `alliance-sheet.md` §5.3 gives admins the treasury and keeps only rename and disband with
        // the founder, which is the conservative split the ticket proposed and nobody overruled.
        when (seat.role) {
            AllianceRole.FOUNDER, AllianceRole.ADMIN -> Unit
            AllianceRole.MEMBER -> return@withLock TreasuryRead.Refused(ApiError.AllianceRoleTooLow)
        }
        val level = stored.alliance.level
        if (AllianceBalance.isExhausted(project, level, stored.seatsBought)) {
            return@withLock TreasuryRead.Refused(ApiError.AllianceTreasuryShort)
        }
        val cost = AllianceBalance.costOf(project, stored.seatsBought)
        if (!stored.pool.covers(cost)) return@withLock TreasuryRead.Refused(ApiError.AllianceTreasuryShort)

        val bought = stored.copy(
            version = stored.version.next(),
            pool = stored.pool.minus(cost),
            // **Monotonic, always.** Spending the pool adds to the level and never subtracts, or the
            // level would fall for doing the thing the level exists to encourage — `alliance-sheet
            // .md` §3, and the one property of this ladder that is not a placeholder.
            experience = stored.experience + AllianceBalance.projectAward(cost),
            seatsBought = when (project) {
                AllianceProject.CHARTER_EXPANSION -> stored.seatsBought + 1
            },
        )
        alliances[seat.alliance] = bought.reseated()
        TreasuryRead.Present(alliances.getValue(seat.alliance), seat.contributed, seat.role)
    }

    // **What the colony's writer calls when a contribution landed.** In the deployed store this is
    // one statement inside the colony's own transaction; here it is the same map under the same
    // lock, which is what makes the in-memory server a real dev loop rather than an approximation.
    suspend fun credit(credit: PoolCredit): Unit = lock.withLock {
        val stored = alliances[credit.alliance] ?: return@withLock
        alliances[credit.alliance] = stored.copy(
            version = stored.version.next(),
            pool = Resources.of(
                metal = stored.pool.metal + credit.amount.metal,
                crystal = stored.pool.crystal + credit.amount.crystal,
                deuterium = stored.pool.deuterium + credit.amount.deuterium,
            ),
            experience = stored.experience + credit.experience,
        ).reseated()
        val seat = seats.values.firstOrNull { it.id == credit.member } ?: return@withLock
        seats[seat.player] = seat.copy(contributed = seat.contributed + credit.experience)
    }

    // One statement of what a roster's seat line reads, so the six places that recount it cannot
    // disagree about the cap. Taken is counted from the map; the cap comes off the level the stored
    // experience buys and whatever the pool bought on top of it.
    private fun StoredAlliance.reseated(): StoredAlliance {
        val progress = AllianceBalance.progressOf(experience)
        val taken = seats.values.count { it.alliance == alliance.id }
        return copy(
            alliance = alliance.copy(
                level = progress.level,
                seats = AllianceSeats(
                    taken = taken,
                    cap = maxOf(AllianceBalance.seatCap(progress.level, seatsBought), taken),
                ),
            ),
        )
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
