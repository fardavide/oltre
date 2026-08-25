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

// The store `#109` will one day fail to reach. Handwritten, per the repository's fakes-not-mocks
// convention, and it doubles exactly one thing: a `ColonyRepository` that is there and cannot answer.
internal class UnreachableColonyRepository : ColonyRepository {

    override suspend fun found(player: PlayerId, snapshot: GameSnapshot): Founding = error("no route to host")

    override suspend fun colonyOf(player: PlayerId): GameSnapshot? = error("no route to host")

    override suspend fun appliedAmong(player: PlayerId, keys: Set<IdempotencyKey>): Set<IdempotencyKey> =
        error("no route to host")

    override suspend fun write(player: PlayerId, snapshot: GameSnapshot, applied: Set<IdempotencyKey>) =
        error("no route to host")
}
