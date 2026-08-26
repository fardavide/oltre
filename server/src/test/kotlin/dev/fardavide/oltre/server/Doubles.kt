package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.protocol.IdempotencyKey
import java.util.concurrent.atomic.AtomicInteger
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

// **Player ids a test can read.** The real mint is a UUID, which is right for a surrogate key and
// useless in an assertion: a test that wants to say *"the same subject came back to the same
// player"* has to be able to name one. Counting from one also makes "a deleted account signs in
// again and gets a **new** id" a thing a reader can see rather than take on trust.
//
// **Counted atomically, and that is a fix rather than a flourish.** `PostgresPlayerRepositoryIntegration
// Test` resolves the same identity from two threads at once — which is the point of that test — and a
// plain `var` lets both read the same number and mint `player-1` twice. The insert then conflicts on
// `players_pkey` rather than on `(provider, subject)`, which is the one conflict `DO NOTHING` does not
// cover, and the test fails having proved nothing about the upsert it was written for. It is a defect
// in this double and not in the store: the real mint is `UUID.randomUUID()`, which two threads cannot
// collide on. Found by #111, on a run where the race happened to be lost.
internal fun sequentialPlayerIds(): PlayerIds {
    val minted = AtomicInteger()
    return PlayerIds { PlayerId("player-${minted.incrementAndGet()}") }
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

// The same store, on the identity side. It exists for one property: sign-in touches a database a
// network away, so `answering`'s `catch` has to turn that into `ApiError.Internal` rather than let
// it escape the route — and the only way to be sure is to make the store fail.
internal class UnreachablePlayerRepository : PlayerRepository {

    override suspend fun resolve(identity: ProviderIdentity): PlayerId = error("no route to host")

    override suspend fun find(identity: ProviderIdentity): PlayerId? = error("no route to host")

    override suspend fun exists(player: PlayerId): Boolean = error("no route to host")

    override suspend fun forget(player: PlayerId): Boolean = error("no route to host")
}

// **A store that fails and does not say why**, which is neither a hypothetical nor a nicety. The
// `catch` on both route files reads `e.message`, and a `NullPointerException`, a
// `ConcurrentModificationException` or a driver's own internal error routinely carries none — so
// without the elvis the diagnostic in `ApiError.Internal` would be the string `"null"`, which is the
// one thing worse than nothing for whoever is reading the log. It doubles both sides because both
// `served` and `answering` make the same call.
internal class SpeechlessRepository : ColonyRepository, PlayerRepository {

    override suspend fun found(player: PlayerId, snapshot: GameSnapshot): Founding = throw NullPointerException()

    override suspend fun colonyOf(player: PlayerId): StoredColony? = throw NullPointerException()

    override suspend fun appliedAmong(player: PlayerId, keys: Set<IdempotencyKey>): Set<IdempotencyKey> =
        throw NullPointerException()

    override suspend fun write(
        player: PlayerId,
        snapshot: GameSnapshot,
        applied: Set<IdempotencyKey>,
        expected: ColonyVersion,
    ): WriteResult = throw NullPointerException()

    override suspend fun resolve(identity: ProviderIdentity): PlayerId = throw NullPointerException()

    override suspend fun find(identity: ProviderIdentity): PlayerId? = throw NullPointerException()

    override suspend fun exists(player: PlayerId): Boolean = throw NullPointerException()

    override suspend fun forget(player: PlayerId): Boolean = throw NullPointerException()
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
