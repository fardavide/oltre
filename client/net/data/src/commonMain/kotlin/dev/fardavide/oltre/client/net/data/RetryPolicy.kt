package dev.fardavide.oltre.client.net.data

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// **How long to wait between asking again, stated as the waits themselves.**
//
// A list rather than `(attempts, first, factor, cap)` on the class, and the shape is what makes the
// awkward case honest: a single-attempt policy has *no* waits, so `RetryPolicy(emptyList())` is a
// complete description of it — where a policy holding a first delay and a factor it can never reach
// would be carrying two fields that mean nothing. It also makes the drain loop arithmetic-free:
// `waits[index]` is what to wait after the attempt at `index`, and running off the end **is** the
// last attempt.
data class RetryPolicy(val waits: List<Duration>) {

    val attempts: Int get() = waits.size + 1

    companion object {

        // **What a background sync does.** Three attempts, one second then three, so a dead server
        // is asked three times over four seconds rather than as fast as the loop can run — which
        // is the whole of "does not hammer".
        //
        // The numbers are invented here and are meant to move once there is a deployment to
        // measure; `#111` is the slice that can. What they are chosen around is `#106` §6's
        // scale-to-zero host: a first request after an idle spell can meet a cold start, and a
        // second attempt a second later is very nearly always the one that lands.
        val DEFAULT: RetryPolicy = exponential(attempts = 3, first = 1.seconds, factor = 3, cap = 30.seconds)

        // **What a tap does**, and it is a different policy rather than a shorter one. A player who
        // has tapped a facility is owed an answer now: the outbox has already taken the verb, so a
        // second and third attempt buys a colony that arrives four seconds later and nothing else,
        // while the screen has been waiting the whole time. A background sync has no such clock on
        // it, which is why it is the one that retries.
        val ONCE: RetryPolicy = RetryPolicy(emptyList())

        // Geometric, capped. **No jitter**, deliberately: jitter spreads a herd of clients that
        // failed together, and there is one device here — it would buy nothing and cost the one
        // property that makes a backoff testable, which is that the waits are known before the run.
        fun exponential(attempts: Int, first: Duration, factor: Int, cap: Duration): RetryPolicy {
            require(attempts >= 1) { "a policy that never asks is not a policy: was $attempts" }
            require(factor >= 1) { "a factor below one shortens the wait each time: was $factor" }
            require(first >= Duration.ZERO) { "a wait cannot run backwards: was $first" }
            return RetryPolicy(
                generateSequence(first.coerceAtMost(cap)) { previous -> (previous * factor).coerceAtMost(cap) }
                    .take(attempts - 1)
                    .toList(),
            )
        }
    }
}
