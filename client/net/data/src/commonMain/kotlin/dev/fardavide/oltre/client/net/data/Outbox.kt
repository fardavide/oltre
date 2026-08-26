package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.OfflineRule
import dev.fardavide.oltre.protocol.Protocol
import dev.fardavide.oltre.protocol.VerbEnvelope
import dev.fardavide.oltre.protocol.offlineRule
import kotlinx.serialization.SerializationException

// **What a verb offered to the queue did**, and it is two answers because `#106` §3 draws exactly
// one line. An enum rather than a `Boolean` for `WriteResult`'s reason on the other side of the
// wire: a `when` with no `else` at the call site cannot forget one.
enum class QueueResult {

    // Written to the file before this function returned. A process killed on the next line still
    // has it.
    QUEUED,

    // Not written, and it never will be. The verb is `OfflineRule.LOOK_DONT_ACT`: its outcome
    // depends on a world somebody else may now hold, so replaying it later is a promise this game
    // will not be able to keep the day an AI empire or another player can take a coordinate.
    NOT_QUEUEABLE,
}

// **What pressing the amber ghost did**, and it is two answers because the queue can move under the
// finger. An enum rather than a `Boolean` for `QueueResult`'s reason: a caller that has to tell the
// player which of these happened cannot forget one.
enum class WithdrawResult {

    // It was still outstanding and it is gone. Nothing was ever sent, so nothing was countermanded.
    WITHDRAWN,

    // **The queue flushed between the tap that held it and the tap that would withdraw it.** Not an
    // error and not a failure — the verb the player wanted is on its way to happening, which is what
    // they asked for in the first place. It is its own answer because the card has already changed
    // shape underneath them, and a screen that said *"withdrawn"* here would be lying about the one
    // thing the held state exists to be honest about.
    ALREADY_SENT,
}

// **Everything the player did that the server has not answered yet**, in the order they did it, on
// disk. *"A queued verb that evaporates when the app is killed is worse than one that was
// refused"* — so every mutation here is a write, and nothing is held in memory between calls. That
// is not a performance decision worth revisiting: the queue is what a player tapped in the seconds
// before a tunnel, it is never more than a handful of envelopes, and a cache is one more thing that
// can disagree with the file.
//
// **Order is load-bearing and the list says so.** Two upgrades on one facility, or a purchase and
// the dispatch it pays for, are only replayable in the sequence they happened — `Replay.kt` walks
// the list in order for exactly this reason.
class Outbox(private val file: OutboxFile) {

    suspend fun queued(): List<VerbEnvelope> {
        val text = file.read() ?: return emptyList()
        return try {
            Protocol.json.decodeFromString<List<VerbEnvelope>>(text)
        } catch (_: SerializationException) {
            // **An unreadable outbox is an empty outbox, and this one is worth more than the
            // shrug it looks like.** `GameStore.load` makes the same call about a corrupt colony
            // and it costs nothing there — the save is rewritten on the next event. Here it costs
            // taps the player actually made, and there is no honest way to recover them: a queue
            // that is half-parsed is a queue whose order is unknown, and replaying an unknown
            // order is worse than replaying nothing.
            //
            // What arrives here is corruption, a truncated write, or a file from a build that
            // reshaped the envelope — `Protocol.json` has no `ignoreUnknownKeys`, deliberately, so
            // a newer file is unreadable rather than misread. Against that the alternative is
            // refusing to start.
            emptyList()
        }
    }

    // **Mints nothing and decides one thing.** The envelope arrives already made, because the same
    // envelope is also what a live verb rides on and minting it twice would defeat the key.
    //
    // The queue-or-refuse split is read off `ClientVerb.offlineRule` and is **never re-derived
    // here**. That is the whole reason `:protocol` states it: it is a `when` with no `else` in the
    // main source set, so a thirteenth verb cannot compile without somebody deciding what it does
    // on a train — where a table copied into the outbox would silently queue the new one by
    // omission, and the tell would be a player finding a world they had dispatched to already
    // worked.
    suspend fun queue(envelope: VerbEnvelope): QueueResult {
        if (envelope.verb.offlineRule == OfflineRule.LOOK_DONT_ACT) return QueueResult.NOT_QUEUEABLE
        write(queued() + envelope)
        return QueueResult.QUEUED
    }

    // **What the server judged leaves; what it did not stays.** Both lists go in — applied and
    // rejected alike — because a rejected verb has been judged as surely as an accepted one and
    // replaying it would get the same answer forever. Surfacing it is `ColonySync`'s job and it
    // happens before this is called; dropping it here is not swallowing it.
    //
    // Anything the response did not mention survives, which is what makes a partial answer safe.
    suspend fun answered(keys: Set<IdempotencyKey>) {
        val outstanding = queued()
        val remaining = outstanding.filterNot { it.idempotencyKey in keys }
        // **Nothing left means nothing written**, and the guard is here rather than at the call site
        // because this is the only place that can tell. The common case it covers is not the empty
        // one: a galaxy-touching verb is sent live and never queued, so every dispatch made with
        // signal answers a key the file has never held — and without this, each one would delete a
        // file that is not there.
        if (remaining.size != outstanding.size) write(remaining)
    }

    // **The one way out of the held state, and it is a deletion rather than a message.** A held verb
    // has not been sent — that is the whole of what held means — so taking it back costs nobody an
    // apology and needs no server to agree.
    //
    // **It is deliberately not undo.** Undo would have to reach something that already happened; this
    // can only reach something that has not. The two are told apart by whether the key is still in
    // the file, and the answer says which, because between the tap that held a verb and the tap that
    // would withdraw it the queue may have drained — see `WithdrawResult.ALREADY_SENT`.
    suspend fun withdraw(key: IdempotencyKey): WithdrawResult {
        val outstanding = queued()
        val remaining = outstanding.filterNot { it.idempotencyKey == key }
        // Nothing matched means nothing written, which is `answered`'s guard for `answered`'s
        // reason: a stale tap must not cost a disk write.
        if (remaining.size == outstanding.size) return WithdrawResult.ALREADY_SENT
        write(remaining)
        return WithdrawResult.WITHDRAWN
    }

    private suspend fun write(envelopes: List<VerbEnvelope>) {
        if (envelopes.isEmpty()) {
            file.clear()
        } else {
            file.write(Protocol.json.encodeToString(envelopes))
        }
    }
}
