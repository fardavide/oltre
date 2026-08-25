package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.protocol.IdempotencyKey
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

// A clock a test moves by hand, because a test that reads the wall clock is a test whose colony —
// and whose coverage number — is different on every run. The behaviour suite learned that the
// expensive way at 0.20; this module starts where that ended up.
internal class MovableClock(private var at: Instant) : Clock {

    override fun now(): Instant = at

    fun advanceBy(span: Duration) {
        at += span
    }
}

// The store that is there and cannot answer. Handwritten, per the repository's fakes-not-mocks
// convention, and it doubles exactly one thing — which is now a real network away.
internal class UnreachableColonyRepository : ColonyRepository {

    override suspend fun found(player: PlayerId, snapshot: GameSnapshot): Founding = error("no route to host")

    override suspend fun colonyOf(player: PlayerId): StoredColony? = error("no route to host")

    override suspend fun appliedAmong(player: PlayerId, keys: Set<IdempotencyKey>): Set<IdempotencyKey> =
        error("no route to host")

    override suspend fun write(
        player: PlayerId,
        snapshot: GameSnapshot,
        applied: Set<IdempotencyKey>,
        expected: ColonyVersion,
    ): WriteResult = error("no route to host")
}

// **The player's other device, syncing at the same moment.** The first `contentions` writes lose,
// and each loss is a *real* one rather than a flag: the other device's write lands on the store
// first, so the version this caller is asserting genuinely stops being current and the colony the
// retry reads is genuinely the one that won.
//
// `otherDeviceApplied` is what that write records against the colony, which is the only way to test
// the property the retry exists for — a key the winner has already spent must not be spent again by
// the loser's second attempt.
internal class ContendedColonyRepository(
    private val store: ColonyRepository,
    private var contentions: Int,
    private val otherDeviceApplied: Set<IdempotencyKey> = emptySet(),
) : ColonyRepository {

    var attempts: Int = 0
        private set

    override suspend fun found(player: PlayerId, snapshot: GameSnapshot): Founding = store.found(player, snapshot)

    override suspend fun colonyOf(player: PlayerId): StoredColony? = store.colonyOf(player)

    override suspend fun appliedAmong(player: PlayerId, keys: Set<IdempotencyKey>): Set<IdempotencyKey> =
        store.appliedAmong(player, keys)

    override suspend fun write(
        player: PlayerId,
        snapshot: GameSnapshot,
        applied: Set<IdempotencyKey>,
        expected: ColonyVersion,
    ): WriteResult {
        attempts++
        if (contentions <= 0) return store.write(player, snapshot, applied, expected)
        contentions--
        // The other device gets there first. It writes the colony as it stands — what matters is
        // that the version moves and the keys it spent are recorded — and this caller's assertion is
        // then out of date, which the store says for itself.
        val current = checkNotNull(store.colonyOf(player)) { "the other device has no colony to write" }
        store.write(player, current.snapshot, otherDeviceApplied, expected = current.version)
        return store.write(player, snapshot, applied, expected)
    }
}
