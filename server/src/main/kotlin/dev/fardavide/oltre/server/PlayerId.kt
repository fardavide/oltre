package dev.fardavide.oltre.server

// **Who the colony belongs to, and since `#110` it is a conclusion rather than a claim.** The value
// is `players.id` — a surrogate key minted when somebody first signs in — reached by verifying an
// Apple or Google subject against the provider's own key set and then by reading a session token
// this server signed itself. What a request carries is the token; who it belongs to is worked out
// here, which is why this type never crosses the wire.
//
// **It is not the provider's subject, and that is a decision rather than an indirection.** Two
// properties pay for the extra column: a deleted account signing in again gets a *new* id, so
// nobody can resurrect a colony by keeping a copy of their Apple subject; and a provider's
// identifier never reaches a session token, a log line or a URL. See `PlayerIds`.
//
// A type rather than a `String` all the way down, for the reason the model uses one everywhere else:
// three different strings end up meaning "a player" — the id, the subject, and the value in
// `X-Oltre-Player` — and the compiler has to be able to tell which is which. It is `:server`'s rather
// than `:protocol`'s because the client never says who it is.
@JvmInline
value class PlayerId(val value: String) {

    init {
        require(value.isNotBlank()) { "a player id is a subject, not an absence" }
    }
}
