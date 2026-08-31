package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.PlayerProfile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// **The identity half of the store that dies with the process.** It is what every unit test in this
// module runs against and what `./gradlew :server:run` serves with no database, exactly as
// `InMemoryColonyRepository` is — and it is the pair with that class rather than a class of its own
// standing, because deletion has to reach both.
//
// **The cascade is written out here and is a foreign key over there**, which is the one place the
// two implementations genuinely differ. `schema.sql` puts `ON DELETE CASCADE` on `colonies` and on
// `applied_verbs`, so Postgres forgets a colony because the row it hung off is gone; a map has no
// such thing, so this asks the colony store to forget it. Both have to be true or the unit suite
// above them is standing on a lie — `PostgresPlayerRepositoryIntegrationTest` asks the same
// questions of the tables.
internal class InMemoryPlayerRepository(
    private val colonies: InMemoryColonyRepository,
    private val ids: PlayerIds = PlayerIds.RANDOM,
) : PlayerRepository {

    private val lock = Mutex()
    private val players = mutableMapOf<ProviderIdentity, PlayerId>()

    // **Keyed by `PlayerId` and not by `ProviderIdentity`**, which is the same shape the two columns
    // take over in Postgres and is load-bearing for the same reason: a deleted account signing in
    // again gets a *fresh* id, so a profile keyed on the subject would hand somebody back the name
    // and face of the colony they deleted.
    private val profiles = mutableMapOf<PlayerId, PlayerProfile>()

    override suspend fun resolve(identity: ProviderIdentity): PlayerId = lock.withLock {
        players.getOrPut(identity) { ids.mint() }
    }

    override suspend fun find(identity: ProviderIdentity): PlayerId? = lock.withLock { players[identity] }

    override suspend fun exists(player: PlayerId): Boolean = lock.withLock { player in players.values }

    override suspend fun forget(player: PlayerId): Boolean = lock.withLock {
        val identity = players.entries.firstOrNull { it.value == player }?.key ?: return@withLock false
        players.remove(identity)
        // The third thing the cascade takes, written out here for the reason the colony is: the
        // columns live *on* `players` in Postgres, so deleting the row takes the name and the mark
        // with it and there is nothing over there to write.
        profiles.remove(player)
        colonies.forget(player)
        true
    }

    override suspend fun profileOf(player: PlayerId): PlayerProfile? = lock.withLock {
        if (player !in players.values) return@withLock null
        // **Absent is `EMPTY` rather than null**, because a player who has never opened the editor
        // exists and has chosen nothing — which is exactly what two nulls say. Over in Postgres the
        // row is there with both columns null and the same value comes back.
        profiles[player] ?: PlayerProfile(name = null, mark = null)
    }

    override suspend fun setProfile(player: PlayerId, profile: PlayerProfile): Boolean = lock.withLock {
        if (player !in players.values) return@withLock false
        profiles[player] = profile
        true
    }
}
