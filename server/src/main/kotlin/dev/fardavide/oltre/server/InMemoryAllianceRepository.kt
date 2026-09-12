package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.Alliance
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceLevel
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceSeats
import dev.fardavide.oltre.protocol.AllianceTag
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

    override suspend fun found(player: PlayerId, name: AllianceName, tag: AllianceTag, now: Instant): Founded =
        lock.withLock {
            seats[player]?.let { return@withLock Founded.AlreadyFounded(alliances.getValue(it.alliance)) }
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
        val seat = seats.getValue(player)
        Affiliation.Enlisted(alliances.getValue(seat.alliance), seat)
    }
}
