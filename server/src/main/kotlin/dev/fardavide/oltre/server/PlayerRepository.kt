package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.PlayerProfile
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

    // **Find, and pointedly not create** — the sibling of `resolve` and the reason the two are
    // separate methods rather than a flag. `#111` added it for Apple's server-to-server
    // notifications, where the caller is Apple rather than a player: a notification about somebody
    // who never signed in here has to answer *"nobody"*, and `resolve` would answer it by minting a
    // row for a subject that has never held a colony and is being deleted.
    suspend fun find(identity: ProviderIdentity): PlayerId?

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

    // **What the player chose to be called and to wear.** Two more questions about a *player* rather
    // than about a colony, which is why they are here and not on `ColonyRepository` — see
    // `profile-sheet.md` §3, and note that a profile is deliberately not a `ClientVerb`: there is
    // nothing in `core` to replay it against.
    //
    // Null is **no such player**, not "has not chosen" — an unfilled profile is a `PlayerProfile`
    // whose two halves are both null, and the distinction matters because the caller answers one
    // with `Unauthenticated` and the other with a strip that still says `Dead Reckoning`. In practice
    // only a deletion racing this request produces the null, since `Authenticator` has already asked
    // `exists`; it is modelled anyway because a repository that answered an invented profile for a
    // player who is gone would be one nothing could tell had gone wrong.
    suspend fun profileOf(player: PlayerId): PlayerProfile?

    // **Replaces rather than merges**, which is `SetProfileRequest`'s own call one layer out: with a
    // merge, `null` would have to mean *leave it alone* and *clear it* at once, and clearing is the
    // only way out of a name a player regrets. False is "there was nobody there", exactly as
    // `forget`'s is.
    suspend fun setProfile(player: PlayerId, profile: PlayerProfile): Boolean
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
