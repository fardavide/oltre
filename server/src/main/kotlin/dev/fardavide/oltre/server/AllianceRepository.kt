package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.Alliance
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceMember
import dev.fardavide.oltre.protocol.JoinRequest
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.AllianceProject
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.JoinRequestId
import dev.fardavide.oltre.protocol.JoinDecision
import dev.fardavide.oltre.core.Resources
import java.util.UUID
import kotlin.time.Instant

@JvmInline
internal value class AllianceVersion(val value: Long) {

    fun next(): AllianceVersion = AllianceVersion(value + 1)

    companion object {

        val FIRST = AllianceVersion(1)
    }
}

internal data class StoredAlliance(
    val alliance: Alliance,
    val version: AllianceVersion,
    val createdAt: Instant,
    val experience: Long,
    // The treasury, in whole units — see `schema.sql`. Carried on the stored row rather than fetched
    // by the treasury route on its own, because the level and the seat cap are already derived from
    // the same row and a second read could disagree with the first.
    val pool: Resources = Resources.of(),
    val seatsBought: Int = 0,
)

internal data class Seat(
    val id: AllianceMemberId,
    val player: PlayerId,
    val alliance: AllianceId,
    val role: AllianceRole,
    val joinedAt: Instant,
    val contributed: Long,
)

internal sealed interface Founded {

    data class Refused(val error: ApiError) : Founded

    data class Made(val alliance: StoredAlliance) : Founded

    data class AlreadyFounded(val alliance: StoredAlliance, val role: AllianceRole) : Founded
}

internal data class Petition(
    val id: JoinRequestId,
    val player: PlayerId,
    val alliance: AllianceId,
    val requestedAt: Instant,
)

internal sealed interface AllianceChange {

    data object Stale : AllianceChange

    data class Refused(val error: ApiError) : AllianceChange

    data class Applied(val affiliation: Affiliation) : AllianceChange
}

internal sealed interface Affiliation {

    data object Unaffiliated : Affiliation

    data class Enlisted(val alliance: StoredAlliance, val seat: Seat) : Affiliation

    data class Petitioning(val alliance: StoredAlliance, val petition: Petition) : Affiliation
}

internal interface AllianceRepository {

    suspend fun rosterOf(player: PlayerId, now: Instant): RosterRead

    suspend fun search(query: CanonicalAllianceName, cursor: AllianceSearchPosition?, limit: Int): List<StoredAlliance>

    // **Founding costs the founder's colony `price`, charged in the same transaction as the insert.**
    // The store reads the colony under a row lock, advances it to `now`, asks `core` whether it
    // covers the price and writes it back beside the new alliance — so there is no instant at which
    // an alliance exists and has not been paid for, and none at which a colony has paid for an
    // alliance that does not exist.
    //
    // **`price` is a parameter rather than a constant read in here**, which is what keeps the balance
    // out of the store: `AllianceBalance.FOUNDING_PRICE` is the one production ever passes, and the
    // only other caller is a test saying what it wants to be true of the money.
    //
    // Two refusals belong to the price and neither is a new member: `ApiError.NoColony` for a player
    // with nothing to charge, and `ApiError.AllianceFoundingUnaffordable` for one who cannot cover it.
    suspend fun found(
        player: PlayerId,
        name: AllianceName,
        tag: AllianceTag,
        now: Instant,
        price: Resources,
    ): Founded

    suspend fun allianceOf(player: PlayerId, now: Instant): Affiliation

    suspend fun alliance(id: AllianceId, now: Instant): AllianceLookup

    suspend fun petition(player: PlayerId, alliance: AllianceId, now: Instant, expected: AllianceVersion): AllianceChange

    suspend fun detach(player: PlayerId, alliance: AllianceId, expected: AllianceVersion): AllianceChange

    suspend fun approve(caller: PlayerId, alliance: AllianceId, request: JoinRequestId, decision: JoinDecision, now: Instant, expected: AllianceVersion): AllianceChange

    suspend fun setRole(caller: PlayerId, alliance: AllianceId, member: AllianceMemberId, role: AllianceRole, expected: AllianceVersion): AllianceChange

    suspend fun kick(caller: PlayerId, alliance: AllianceId, member: AllianceMemberId, expected: AllianceVersion): AllianceChange

    suspend fun rename(caller: PlayerId, alliance: AllianceId, name: AllianceName, tag: AllianceTag, expected: AllianceVersion): AllianceChange

    suspend fun disband(caller: PlayerId, alliance: AllianceId, expected: AllianceVersion): AllianceChange

    // What the pool holds and what it could buy. Read-only, and it answers a seat too — the caller's
    // own paid-in total, which is a standing rather than a stock and is the column the succession
    // rule reads.
    suspend fun treasuryOf(player: PlayerId, now: Instant): TreasuryRead

    // Spends the pool on a project. Founder or admin — `alliance-sheet.md` §5.3 gives admins the
    // treasury — and a compare-and-set on the alliance row, because two admins buying the same
    // project in the same second must not both succeed at the price one of them read.
    suspend fun buy(
        caller: PlayerId,
        project: AllianceProject,
        now: Instant,
        expected: AllianceVersion,
    ): TreasuryRead
}

internal sealed interface TreasuryRead {

    data class Refused(val error: ApiError) : TreasuryRead

    // **Stale is its own answer rather than an `ApiError.StaleAlliance`**, on `AllianceChange`'s own
    // shape one interface up: losing a compare-and-set is the caller being told to read and try
    // again, and only the route decides whether it has tries left.
    data object Stale : TreasuryRead

    data class Present(
        val alliance: StoredAlliance,
        val contributed: Long,
        val role: AllianceRole,
    ) : TreasuryRead
}

internal sealed interface RosterRead {

    data class Refused(val error: ApiError) : RosterRead

    data class Present(val members: List<AllianceMember>, val pending: List<JoinRequest>?) : RosterRead
}

internal sealed interface AllianceLookup {

    data object Absent : AllianceLookup

    data class Present(val alliance: StoredAlliance) : AllianceLookup
}

internal fun interface AllianceIds {

    fun mint(): AllianceId

    companion object {

        val RANDOM = AllianceIds { AllianceId(UUID.randomUUID().toString()) }
    }
}
