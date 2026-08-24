package dev.fardavide.oltre.protocol

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

// **Which contract is being spoken**, in the first line of the first slice rather than retrofitted
// once it hurts — see `#106` §8.
//
// The reason is this repository's release pipeline and not a general principle. Merging to `main`
// archives to TestFlight and cuts an Android release, so a client update ships when the merge
// happens and a server deploy happens whenever it happens; the two are never atomic and never can
// be. The server therefore has to keep answering **the build already on somebody's phone**, which
// only means something if the build says which build it is.
//
// One integer rather than a semantic triple, because the only question ever asked of it is *can
// this end answer that end*. A wire has no patch releases: a change either moves the shape or it
// does not.
@Serializable
@JvmInline
value class ApiVersion(val value: Int) : Comparable<ApiVersion> {

    // **No `require` here, unlike `IdempotencyKey` one file over, and the asymmetry is the point.**
    // A version outside the window already has a first-class answer designed for it — the server
    // replies `ApiError.UnsupportedApiVersion` and the client says *"update the app"* — so a
    // constructor that threw would pre-empt the very answer this type exists to give, and turn a
    // negotiation into a parse failure. A blank idempotency key has no such answer; it is malformed
    // input and nothing else, which is why that one *is* guarded.

    // Whether this build can answer that one. Both ends ask it, in opposite directions and with the
    // same answer: a server refuses a request it cannot serve with `ApiError.UnsupportedApiVersion`,
    // and a client that reads a response from beyond `CURRENT` knows it is the one that is behind.
    fun isServed(): Boolean = this in OLDEST_SERVED..CURRENT

    override fun compareTo(other: ApiVersion): Int = value.compareTo(other.value)

    companion object {

        // What this build speaks. Bumped when the shape of a request or a response changes in a way
        // the other end cannot ignore — a field removed, a field made required, a verb's payload
        // reshaped. Adding a verb is not one of those: an older client simply never sends it.
        val CURRENT: ApiVersion = ApiVersion(1)

        // The oldest build still worth answering. It moves **only** when the last install speaking
        // it is gone, and there is no way to know that from inside the repository — so raising this
        // is a decision with a date on it rather than a tidy-up, and it strands every phone that has
        // not opened the App Store since.
        //
        // Equal to `CURRENT` today because there is exactly one contract and nothing has shipped
        // against it yet.
        val OLDEST_SERVED: ApiVersion = ApiVersion(1)
    }
}
