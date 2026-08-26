package dev.fardavide.oltre.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// **When the sync itself did not happen.** The distinction this type exists to draw is between
// *"sign in again"*, *"update the app"* and *"you cannot afford that"* — three sentences with
// nothing in common, and a client that could not tell them apart would have to say the vaguest one
// to everybody.
//
// The third of those is **not here**: a refused verb comes back inside a *successful* `SyncResponse`
// as a `VerbRejection`, because the colony still moved and still has to be drawn. An `ApiError` is
// the case where nothing came back at all.
@Serializable
sealed interface ApiError {

    // Wire identifiers, pinned by `ApiErrorTest` for `ClientVerb`'s reason.

    // No session, or one this server will not accept. The sign-in screen, not a retry.
    @Serializable
    @SerialName("Unauthenticated")
    data object Unauthenticated : ApiError

    // A session that *was* valid. Its own member rather than a flag on `Unauthenticated`, because it
    // is the one case where the player did nothing wrong and the app can say so — and, once
    // refresh exists, the one that can be answered without a screen.
    @Serializable
    @SerialName("SessionExpired")
    data object SessionExpired : ApiError

    // The version negotiation failing, which is the whole reason `ApiVersion` is in the contract
    // from the first line. It carries the window rather than a bare refusal so the client can tell
    // *"update the app"* from *"this server is older than you are"* — the second is a deploy in
    // progress and worth retrying, the first is not.
    @Serializable
    @SerialName("UnsupportedApiVersion")
    data class UnsupportedApiVersion(
        val oldestServed: ApiVersion,
        val current: ApiVersion,
    ) : ApiError

    // Signed in, no colony adopted yet. Not a failure: it is what a first launch of the online build
    // meets before the one-time upload, and the difference between an upgrade and a fresh install is
    // only what happens next.
    @Serializable
    @SerialName("NoColony")
    data object NoColony : ApiError

    // The compare-and-set lost — another device wrote the colony between the read and the write. The
    // answer is always to sync again rather than to tell the player anything, which is why it is an
    // error and not a rejection: nothing the player queued was judged.
    @Serializable
    @SerialName("StaleColony")
    data object StaleColony : ApiError

    // **Asked too often, and the only member that says *when* rather than *what*.** It comes from
    // `/v1/auth/*` and from nowhere else: those routes are unauthenticated, publicly reachable and do
    // a signature check per request, so they are the one surface where a loop costs real money on a
    // host that bills per request.
    //
    // **It is here rather than left as a bare `429`**, which is what a 429 with no body would be. The
    // client's `unreadable` arm turns an unparseable `4xx` into `Malformed` — *"that did not make
    // sense"* — and then never asks again, which is the opposite of what a rate limit means. The
    // whole point of this taxonomy is that *"sign in again"*, *"update the app"* and *"in a moment"*
    // are different sentences, and this is the third one.
    //
    // **No `ApiVersion` bump comes with it**, on `ApiVersion.CURRENT`'s own rule: a version moves
    // when the other end *cannot ignore* the change. An older build never receives this, because the
    // only routes that can send it are ones no shipped client calls — `#112`'s client speaks
    // `/v1/colony` and `/v1/sync`, and the sign-in screen that speaks the rest is `#113`. It is the
    // mirror of *"adding a verb is not one of those"*.
    @Serializable
    @SerialName("TooManyRequests")
    data class TooManyRequests(val retryAfterSeconds: Int) : ApiError {

        init {
            // A negative wait would have the client ask again immediately, which is the one thing
            // this answer exists to stop. Refused rather than clamped: a server that computed one
            // has a bug, and clamping would leave nothing to find.
            require(retryAfterSeconds >= 0) { "a wait is a duration, not a debt: got $retryAfterSeconds" }
        }
    }

    // The request did not parse, or a field did not mean what it claimed. **`detail` is a
    // diagnostic and never player copy** — every word the game says is a `TextRes` built through
    // `Strings`, and a server cannot build one.
    @Serializable
    @SerialName("Malformed")
    data class Malformed(val detail: String) : ApiError

    // Everything the server could not name. `detail` is a diagnostic, exactly as above.
    @Serializable
    @SerialName("Internal")
    data class Internal(val detail: String) : ApiError
}
