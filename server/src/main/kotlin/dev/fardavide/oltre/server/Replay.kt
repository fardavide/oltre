package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.OfflineRule
import dev.fardavide.oltre.protocol.RejectionReason
import dev.fardavide.oltre.protocol.VerbEnvelope
import dev.fardavide.oltre.protocol.VerbRejection
import dev.fardavide.oltre.protocol.offlineRule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// The colony the sync hands back, and what became of what was sent. Exactly the three fields of a
// `SyncResponse` minus the version — which is the route's to state, because it is a fact about the
// contract being spoken and not about the colony.
internal data class Replayed(
    val snapshot: GameSnapshot,
    val applied: Set<IdempotencyKey>,
    val rejected: List<VerbRejection>,
)

// **How recently a galaxy-touching verb has to have been tapped for the server to act on it.**
//
// `#106` §3 is the rule — `startRun` and `startSurvey` are look-don't-act, because the day a world
// can be held by somebody else a retroactively-validated dispatch to one taken ten minutes ago is
// unresolvable. What that rule needs and does not supply is a way to tell a verb *sent live* from a
// verb *queued offline*, and the only evidence the server has is the instant the envelope claims.
// A client that queued one did so because it had no connection, so the gap between the claim and
// the sync is the offline window it waited out; a client that sent one live is a network round trip
// behind, which is seconds.
//
// **Five minutes, invented here and confirmed by Davide on 2026-08-25.** It is chosen to be far
// longer than any request and far shorter than any absence — it covers a slow connection, a request
// that was retried, and a phone whose clock is a little behind, and it covers nothing that was
// queued. Nothing in the game depends on the exact value: widening it makes the server act on a
// slightly older dispatch, narrowing it makes a slow connection look like a queue.
//
// It is measured against the **clamped** instant rather than the claim, which is the part that is
// not arbitrary — see `replay`.
internal val FRESH_WINDOW: Duration = 5.minutes

// **The whole of the engine's decision-making, and it reads no clock and no store.** `serverNow`
// and the keys already spent are arguments for `core`'s own reason: a function that took them for
// itself could not be tested, and this is the one place in the slice where being wrong is expensive
// and silent.
//
// The steps, in the order `#108` sets them out:
//
//  1. Load the authoritative colony and the instant it is accurate as of — the caller's, above.
//  2. Per envelope, in the order the player tapped: check the key, clamp the claim, advance to it,
//     apply the verb, and **keep the result only if `core` accepted it**.
//  3. Anything `core` refused goes back as data, with the reason.
//  4. Advance to `serverNow` and hand the colony back.
//
// **A refusal keeps nothing at all, including the advance it was judged against.** That is not a
// shortcut: the advance is pure time passing and the final one at step 4 covers the same span, so
// composability says the two land on the identical colony — and *keeping nothing* is what makes
// step 2's sentence literally true rather than nearly true.
internal fun replay(
    colony: GameSnapshot,
    envelopes: List<VerbEnvelope>,
    alreadyApplied: Set<IdempotencyKey>,
    serverNow: Instant,
): Replayed {
    // A colony stamped in the future is met where it is rather than refused — the debug menu writes
    // one at the instant it was skipped to, and a server clock can step backwards on its own.
    // `advance` cannot run backwards, and losing a colony to either would be absurd.
    val now = maxOf(serverNow, colony.lastUpdatedAt)

    var state = colony.state
    var lastAcceptedAt = colony.lastUpdatedAt
    // Insertion-ordered, so a response is byte-identical for two identical requests. Nothing reads
    // the order — `SyncResponse.applied` is a set — but a diagnostic that changes shape run to run
    // is a diagnostic nobody can compare.
    val applied = LinkedHashSet<IdempotencyKey>()
    val rejected = mutableListOf<VerbRejection>()
    val answered = mutableSetOf<IdempotencyKey>()

    for (envelope in envelopes) {
        val key = envelope.idempotencyKey
        // **A key repeated inside one request is answered once.** Nothing on the wire stops a client
        // sending the same envelope twice in one list, and `SyncResponse`'s own guards refuse to be
        // built with a key rejected twice — so without this a client bug would take the sync down
        // instead of getting an answer to it.
        if (!answered.add(key)) continue
        // Step 2's first half, and it is **before** the verb is replayed rather than after: a verb
        // whose response was lost on a flaky train connection gets retried, and applying it a second
        // time is a double-spend. The colony that comes back already contains it, so reporting the
        // key as applied is the truth rather than a placation.
        if (key in alreadyApplied) {
            applied += key
            continue
        }

        val at = clamped(envelope.clientInstant, notBefore = lastAcceptedAt, notAfter = now)
        // **The look-don't-act rule, read off the verb rather than re-derived.** `offlineRule` is a
        // `when` with no `else` in `:protocol`, so a thirteenth verb cannot reach this line without
        // somebody having decided what it does on a train.
        //
        // Measured against the *clamped* instant and not the claim, which is the part that is not
        // arbitrary: the question is whether the verb is about to be applied to a world that has
        // moved on, and the clamped instant is where it would be applied. A colony synced a moment
        // ago pulls a stale claim up to its own instant, and a verb applied there is not
        // retroactive at all.
        if (envelope.verb.offlineRule == OfflineRule.LOOK_DONT_ACT && now - at > FRESH_WINDOW) {
            rejected += VerbRejection(envelope, RejectionReason.NotQueueable)
            continue
        }

        // Advance first, then apply *at that instant* — `GameSession.acting`'s order, and
        // load-bearing for its reason: applying a verb to a colony that has not accrued the time
        // yet spends resources it does not have.
        when (val outcome = applyVerb(envelope.verb, advance(state, from = lastAcceptedAt, to = at), at)) {
            is VerbOutcome.Accepted -> {
                state = outcome.state
                lastAcceptedAt = at
                applied += key
            }

            is VerbOutcome.Refused -> {
                rejected += VerbRejection(envelope, RejectionReason.Refused(outcome.refusal))
            }
        }
    }

    return Replayed(
        // `copy` rather than a fresh `GameSnapshot`, so the envelope's other two fields ride across
        // untouched rather than being re-derived. `debugUsed` is one-way and a colony whose clock
        // was moved by hand three syncs ago is still one whose clock was moved by hand; the schema
        // version is whatever the migration ladder left when the save was adopted.
        snapshot = colony.copy(
            lastUpdatedAt = now,
            state = advance(state, from = lastAcceptedAt, to = now),
        ),
        applied = applied,
        rejected = rejected,
    )
}

// **`clientInstant` is a claim rather than a fact**, and the honest thing is to state the residual
// rather than pretend it away — `#106` §3. A modified client can claim it acted at the *start* of
// its offline window instead of the end, which buys at most one window's head start on one job; in
// a single-player game against scripted AI that is not worth defending against, and the clamp is
// what stops it being unbounded. Revisit when there is a second player to take something from.
//
// Written as two `coerce` calls rather than as `coerceIn`, which throws when the bounds cross. They
// cannot cross — `notAfter` is `max(serverNow, lastUpdatedAt)` and `notBefore` starts at
// `lastUpdatedAt` and only ever moves to an instant this function already clamped — so the
// difference never shows. It is written this way because *the failure modes differ if it ever does*:
// a range check raises where an answer was wanted, and this hands back a bound.
private fun clamped(claim: Instant, notBefore: Instant, notAfter: Instant): Instant =
    claim.coerceAtLeast(notBefore).coerceAtMost(notAfter)
