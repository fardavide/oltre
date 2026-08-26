package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.ApiError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// **The guard on the only routes anybody can reach without a session.** Step 45 of
// `identity-provisioning.md` asks for it in the same breath as the startup self-check, and the reason
// is arithmetic rather than caution: `/v1/auth/*` is unauthenticated, publicly reachable and does an
// RS256 verification per request, on a host that bills per request. Everything else behind `/v1`
// costs a signature check *after* a bearer token has already been read, so a caller who cannot sign
// in cannot reach it at all.
//
// Nothing here knows what a socket is, which is the split `#108` made on the routes and `#109` made
// on the store: `OltreServer.kt` reads a header and calls `admit`, and every rule below is judged by
// a plain `…Test` with a clock a test moves by hand.

// What the limiter concluded. **`Refused` carries the wait rather than a boolean**, because the whole
// value of the answer is the number: `ApiError.TooManyRequests` puts it on the wire and a client told
// only "no" has to guess, which in practice means asking again immediately.
internal sealed interface RateVerdict {

    data object Allowed : RateVerdict

    data class Refused(val retryAfter: Duration) : RateVerdict {

        // **Rounded up, and that is the whole of why it is not `inWholeSeconds`.** A wait of nine
        // hundred milliseconds truncates to zero, and both `Retry-After: 0` and
        // `TooManyRequests(0)` are instructions to ask again immediately — the loop this class exists
        // to break, arriving through the answer that breaks it.
        val retryAfterSeconds: Int
            get() = ((retryAfter.inWholeMilliseconds + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()

        // **What a refusal is on the wire, decided here rather than in the routing file.** It is a
        // decision and not plumbing — which status, which member of the taxonomy, and that the number
        // in the body is the same one the header carries — and `OltreServer.kt` is the one file in
        // this module a unit test cannot reach. `#108` moved the route rules out for this reason and
        // `#109` moved the store's; this is the same move, three lines wide.
        fun answer(): Answer.Failed =
            Answer.Failed(HttpStatusCode.TooManyRequests, ApiError.TooManyRequests(retryAfterSeconds))
    }
}

private const val MILLIS_PER_SECOND = 1_000

// **Twenty a minute, per caller, with all twenty available at once.** A real sign-in is one request
// and a refresh is one an hour per device, so this is roughly a thousand times what a player does and
// still four orders of magnitude below what a loop does. The burst is the whole allowance rather than
// a trickle because the honest failure here is a phone retrying a flaky sign-in three times in five
// seconds, and that must not look like an attack.
private const val PERMITS = 20
private val WINDOW: Duration = 1.minutes

// **The map is the attack surface this class adds**, so it has a ceiling. A caller rotating its
// address mints an entry per request, and an unbounded map on a 512 MiB instance is a way to take the
// server down through the thing built to keep it up. Ten thousand instants is well under a megabyte
// and is more distinct callers than this game will have players by three orders of magnitude.
private const val MAX_KEYS = 10_000

// **A generic cell rate algorithm, which is a token bucket that holds one instant instead of a
// counter and a timestamp.** `arriveBy` is the earliest moment at which the next request would be
// exactly on quota; a caller who has spent nothing has one in the past, and a caller who has spent
// the whole burst has one a full window ahead.
//
// **Per instance, and it says so rather than pretending otherwise.** Cloud Run runs as many instances
// as it likes and they share nothing, so N instances allow N times this. That is the right trade at
// this size — the alternative is a round trip to a shared store on the one path that must stay cheap
// — and it still bounds what a single connection can provoke, which is the thing that costs money.
// The real ceiling on spend is the budget alert, and this is what stops it being reached by accident.
internal class RateLimiter(
    private val clock: Clock,
    private val permits: Int = PERMITS,
    private val window: Duration = WINDOW,
    private val maxKeys: Int = MAX_KEYS,
) {

    init {
        require(permits > 0) { "a limiter that permits nothing is a closed door" }
        require(window > Duration.ZERO) { "a window is a span of time" }
        require(maxKeys > 0) { "a limiter that can remember nobody limits nobody" }
    }

    // One permit every this often, sustained. The burst is `permits` of them, which is `window`.
    private val emissionInterval: Duration = window / permits

    // Coarse on purpose, exactly as `JwksKeys`'s is: contention is one caller's own requests, the
    // critical section is a map lookup and an addition, and one map guarded once is easier to be
    // right about than a map of locks.
    private val lock = Mutex()
    private val arriveBy = mutableMapOf<String, Instant>()

    suspend fun admit(key: String): RateVerdict = lock.withLock {
        val now = clock.now()
        if (key !in arriveBy) makeRoomFor(now)

        // A caller whose instant is in the past has been quiet longer than the whole burst, so they
        // start again from now rather than banking the difference. Without the `maxOf` a week away
        // would buy a week of requests.
        val current = maxOf(arriveBy[key] ?: now, now)
        val allowedFrom = current + emissionInterval - window
        if (now < allowedFrom) return@withLock RateVerdict.Refused(allowedFrom - now)

        arriveBy[key] = current + emissionInterval
        RateVerdict.Allowed
    }

    // How many callers are being tracked. Exposed for the test that the ceiling is real, which is the
    // one property here that is about the map rather than about a verdict — `FakeJwksSource.fetches`
    // exists for the same reason and is the same shape.
    suspend fun tracked(): Int = lock.withLock { arriveBy.size }

    private fun makeRoomFor(now: Instant) {
        if (arriveBy.size < maxKeys) return

        // Anybody whose instant has passed owes nothing, so remembering them buys nothing either.
        // This is the whole of the eviction in every normal minute: callers go quiet and are dropped.
        arriveBy.values.removeAll { it <= now }
        if (arriveBy.size < maxKeys) return

        // Every slot is taken by a caller still in debt, which is what an actual flood looks like.
        // **The one closest to recovery is dropped**, and the direction matters: evicting the worst
        // offender would hand a fresh burst to whoever is trying hardest, which is the opposite of
        // what this is for. The cost is that a caller near the end of their wait may get a few
        // permits early, which is a rounding error against a flood.
        //
        // `minBy` and not `minByOrNull`: this line is only reached when the map is at its ceiling and
        // the ceiling is at least one, so the empty case is a state that cannot occur — and a safe
        // call for it would be an arm no test could ever reach.
        arriveBy.remove(arriveBy.minBy { it.value }.key)
    }
}

// **Who a request is from, for the purpose of counting it — and it is the *last* hop rather than the
// first.** Everything in `X-Forwarded-For` before the final entry is whatever the caller wrote; the
// last is the address Google's front end observed for itself. A limiter keyed on a forgeable string
// is not a limiter.
//
// The two ways of being wrong are deliberately not symmetric. Read the last hop and a trusted proxy
// in front would pool every caller into one bucket — too strict, immediately visible, and nobody is
// let through who should not be. Read the first and a caller rotating a forged header defeats the
// whole class, silently, which is the failure nobody finds until the bill arrives.
//
// The socket's own address is the fallback and is what the dev loop uses, where there is no proxy and
// no header at all.
internal fun clientKey(forwardedFor: String?, remoteHost: String): String {
    val hops = forwardedFor?.split(',') ?: return remoteHost
    return hops.map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: remoteHost
}
