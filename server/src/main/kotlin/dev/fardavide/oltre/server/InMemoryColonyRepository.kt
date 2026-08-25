package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.protocol.IdempotencyKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// **A colony that lives exactly as long as the process does.** It is no longer the only store — see
// `PostgresColonyRepository` — and it stays for two reasons rather than as a leftover: it is what
// every unit test in this module runs against, and it is what `./gradlew :server:run` serves when no
// `DATABASE_URL` is set, so the dev loop needs no database.
//
// **The lock is not a formality.** Ktor serves requests concurrently, so two syncs for one player
// can be inside `found` or `write` at the same moment. What makes it correct here and not enough in
// Postgres is that the map is one process: a second server instance shares no `Mutex`, which is why
// the real implementation's atomicity comes from the database row rather than from Kotlin — and why
// both of them answer the same `WriteResult`.
internal class InMemoryColonyRepository : ColonyRepository {

    private val lock = Mutex()
    private val colonies = mutableMapOf<PlayerId, StoredColony>()

    // `applied_verbs` keyed the way the table is: the pair, not the key alone. Two players can mint
    // the same string — nothing about an idempotency key is globally unique, and the wire says so by
    // refusing to check anything but that one was minted — so a set of bare keys would let one
    // player's retry silently swallow another's verb.
    private val applied = mutableSetOf<Pair<PlayerId, IdempotencyKey>>()

    override suspend fun found(player: PlayerId, snapshot: GameSnapshot): Founding = lock.withLock {
        val existing = colonies[player]
        if (existing != null) return Founding.AlreadyThere(existing)
        val founded = StoredColony(snapshot, ColonyVersion.FIRST)
        colonies[player] = founded
        Founding.Founded(founded)
    }

    override suspend fun colonyOf(player: PlayerId): StoredColony? = lock.withLock { colonies[player] }

    override suspend fun appliedAmong(player: PlayerId, keys: Set<IdempotencyKey>): Set<IdempotencyKey> =
        lock.withLock { keys.filterTo(mutableSetOf()) { player to it in applied } }

    override suspend fun write(
        player: PlayerId,
        snapshot: GameSnapshot,
        applied: Set<IdempotencyKey>,
        expected: ColonyVersion,
    ): WriteResult = lock.withLock {
        // **A colony that is not there loses exactly as a colony at another version does**, and the
        // two are one comparison rather than two branches on purpose: `UPDATE … WHERE player_id = ?
        // AND version = ?` touches no row in either case and cannot tell them apart either. A store
        // that answered them differently would be a store the unit tests could not stand in for.
        if (colonies[player]?.version != expected) return@withLock WriteResult.STALE
        colonies[player] = StoredColony(snapshot, expected.next())
        this.applied += applied.map { player to it }
        WriteResult.WRITTEN
    }
}
