package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.SyncResponse
import dev.fardavide.oltre.protocol.VerbEnvelope
import kotlin.jvm.JvmInline

// **Who is asking.** Until `#110` it is whatever the client puts in `Protocol.PLAYER_HEADER` and
// nothing has verified it; after `#110` it is a session token the server minted, and the only thing
// that changes here is where the string comes from. A type rather than a `String` all the way down
// for the reason the model uses one everywhere else — the day it is minted rather than typed, the
// compiler has to be able to find every place that carries it.
@JvmInline
value class PlayerHandle(val value: String) {

    init {
        require(value.isNotBlank()) { "a player handle names somebody and cannot be blank" }
    }
}

// **What came back, and there are three answers because there are three different things to do.**
//
// The split that matters is between the second and the third. `Refused` is the server having
// answered — it read the request, formed an opinion, and `ApiError` is that opinion; the outbox
// cannot help and retrying the same request will get the same reply. `Unreachable` is nothing
// having answered at all, which is the case the outbox exists for: the verbs are still queued, the
// colony is still the one last seen, and the honest thing is to try again later.
//
// `ApiError` deliberately has no member for *"no connection"*, and this is why: it is the taxonomy
// of what a **server** says, and a server that says nothing has said nothing.
sealed interface ApiResult<out T> {

    data class Answered<out T>(val value: T) : ApiResult<T>

    data class Refused(val error: ApiError) : ApiResult<Nothing>

    data object Unreachable : ApiResult<Nothing>
}

// **The whole of what the client can ask**, which is two questions because the server has two
// routes and everything a player can do to a colony is already one of twelve verbs on one envelope.
//
// It is an interface rather than the Ktor class itself so that the suite has something to hand the
// shell that is not a socket — `#106` §8, and the reason `:client:net:data-testing` lands in this
// slice rather than in `#113`: the whole behaviour and screenshot suite runs on the desktop target,
// and it must not try to reach production.
//
// **Neither method takes a `SyncRequest`.** The client's API version is a fact about this build
// rather than a choice a caller makes, so the implementation states it and a caller cannot get it
// wrong. What a caller supplies is the only thing it knows: which envelopes are outstanding.
interface OltreApi {

    // Found this player's colony, and mint its galaxy while doing it. Idempotent at the far end — a
    // second call after a lost response gets the colony that is already there rather than a second
    // one — which is why it needs no envelope and no key of its own.
    suspend fun foundColony(player: PlayerHandle): ApiResult<SyncResponse>

    // Everything queued since the last sync goes up in the order the player tapped it; the
    // authoritative colony comes back, and what became of each verb comes back with it. An empty
    // list is the normal case rather than a degenerate one — it is what opening the app sends.
    suspend fun sync(player: PlayerHandle, envelopes: List<VerbEnvelope>): ApiResult<SyncResponse>
}
