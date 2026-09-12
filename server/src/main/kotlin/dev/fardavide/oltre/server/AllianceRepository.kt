package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.Alliance
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ApiError
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

    data class AlreadyFounded(val alliance: StoredAlliance) : Founded
}

internal sealed interface Affiliation {

    data object Unaffiliated : Affiliation

    data class Enlisted(val alliance: StoredAlliance, val seat: Seat) : Affiliation
}

internal interface AllianceRepository {

    suspend fun found(player: PlayerId, name: AllianceName, tag: AllianceTag, now: Instant): Founded

    suspend fun allianceOf(player: PlayerId, now: Instant): Affiliation
}

internal fun interface AllianceIds {

    fun mint(): AllianceId

    companion object {

        val RANDOM = AllianceIds { AllianceId(UUID.randomUUID().toString()) }
    }
}
