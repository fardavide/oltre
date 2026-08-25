package dev.fardavide.oltre.server

import java.util.UUID

// **The `players` table, which `#109` wrote and nothing read.** Three questions, and each one is a
// slice of `#110`'s scope: who is this, are they still here, and forget them.
//
// It is a second interface beside `ColonyRepository` rather than three more methods on it, because
// the two answer about different things and only one of them has a version column. What they share
// is a store, which is why one class implements each of them on each side — see
// `PostgresPlayerRepository`, whose `forget` is a single `DELETE` because the foreign keys do the
// rest, and `InMemoryPlayerRepository`, whose has to reach the colony map by hand.
internal interface PlayerRepository {

    // **Find or create, and it is one operation rather than two on purpose.** Two devices signing in
    // at the same instant would otherwise both look, both miss and both insert — and a player with
    // two rows is a player with two colonies, which is the failure this whole slice exists to
    // prevent one layer down.
    suspend fun resolve(identity: ProviderIdentity): PlayerId

    // **Asked on every authenticated request, and that is what makes deletion mean deletion.** A
    // session token is a signed claim about a player, and a signature cannot be un-signed: without
    // this, an account deleted a minute ago keeps working until its access token runs out. One
    // primary-key lookup, at two to four requests per player per day.
    suspend fun exists(player: PlayerId): Boolean

    // **Actually delete, never soft-delete** — App Review 5.1.1(v), and `#106` §4. False is "there
    // was nobody there", which a caller treats as done rather than as a failure: deleting an account
    // twice is not an error, and a client that lost the response to the first attempt will send a
    // second.
    suspend fun forget(player: PlayerId): Boolean
}

// **Where a `players.id` comes from**, as a seam rather than a call to `UUID.randomUUID` in the
// middle of a store. It is the same move `IdempotencyKeys` is on the client: a class that reached
// for a random source of its own could not be asked what it did with the id it minted, and every
// test of "the same subject comes back to the same colony" would be reading an opaque string it had
// to take on trust.
internal fun interface PlayerIds {

    fun mint(): PlayerId

    companion object {

        // **A surrogate key and not the subject**, which `#109` deliberately did not build in
        // advance. Two properties earn it: a deleted account signing in again gets a *new* id, so a
        // colony cannot be resurrected by anybody who kept a copy of their Apple subject; and a
        // provider's identifier never reaches a session token, a log line or a URL.
        val RANDOM: PlayerIds = PlayerIds { PlayerId(UUID.randomUUID().toString()) }
    }
}
