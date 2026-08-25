package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.protocol.IdempotencyKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// **A colony that lives exactly as long as the process does**, which is deliberate rather than
// provisional: `#109` is the slice that gives it three tables, and putting them in now would mean
// the replay and the store landed together with neither of them reviewed on its own.
//
// **The lock is not a formality.** Ktor serves requests concurrently, so two syncs for one player
// can be inside `found` or `write` at the same moment — and the compare-and-set this design needs
// (`ApiError.StaleColony` exists for losing it) is the same mutual exclusion one layer down. What
// makes it correct here and not enough in `#109` is that the map is one process: a second server
// instance shares no `Mutex`, which is why the real implementation's atomicity has to come from the
// database rather than from Kotlin.
internal class InMemoryColonyRepository : ColonyRepository {

    private val lock = Mutex()
    private val colonies = mutableMapOf<PlayerId, GameSnapshot>()

    // `applied_verbs` keyed the way the table is: the pair, not the key alone. Two players can mint
    // the same string — nothing about an idempotency key is globally unique, and the wire says so by
    // refusing to check anything but that one was minted — so a set of bare keys would let one
    // player's retry silently swallow another's verb.
    private val applied = mutableSetOf<Pair<PlayerId, IdempotencyKey>>()

    override suspend fun found(player: PlayerId, snapshot: GameSnapshot): Founding = lock.withLock {
        val existing = colonies[player]
        if (existing != null) return Founding.AlreadyThere(existing)
        colonies[player] = snapshot
        Founding.Founded(snapshot)
    }

    override suspend fun colonyOf(player: PlayerId): GameSnapshot? = lock.withLock { colonies[player] }

    override suspend fun appliedAmong(player: PlayerId, keys: Set<IdempotencyKey>): Set<IdempotencyKey> =
        lock.withLock { keys.filterTo(mutableSetOf()) { player to it in applied } }

    override suspend fun write(player: PlayerId, snapshot: GameSnapshot, applied: Set<IdempotencyKey>) {
        lock.withLock {
            colonies[player] = snapshot
            this.applied += applied.map { player to it }
        }
    }
}
