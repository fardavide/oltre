package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.Alliance
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.JoinRequestId
import dev.fardavide.oltre.protocol.JoinDecision
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

    suspend fun found(player: PlayerId, name: AllianceName, tag: AllianceTag, now: Instant): Founded

    suspend fun allianceOf(player: PlayerId, now: Instant): Affiliation

    suspend fun alliance(id: AllianceId, now: Instant): AllianceLookup

    suspend fun petition(player: PlayerId, alliance: AllianceId, now: Instant, expected: AllianceVersion): AllianceChange

    suspend fun detach(player: PlayerId, alliance: AllianceId, expected: AllianceVersion): AllianceChange

    suspend fun approve(caller: PlayerId, alliance: AllianceId, request: JoinRequestId, decision: JoinDecision, now: Instant, expected: AllianceVersion): AllianceChange

    suspend fun setRole(caller: PlayerId, alliance: AllianceId, member: AllianceMemberId, role: AllianceRole, expected: AllianceVersion): AllianceChange

    suspend fun kick(caller: PlayerId, alliance: AllianceId, member: AllianceMemberId, expected: AllianceVersion): AllianceChange

    suspend fun rename(caller: PlayerId, alliance: AllianceId, name: AllianceName, tag: AllianceTag, expected: AllianceVersion): AllianceChange

    suspend fun disband(caller: PlayerId, alliance: AllianceId, expected: AllianceVersion): AllianceChange
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
