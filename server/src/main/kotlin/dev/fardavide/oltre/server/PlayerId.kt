package dev.fardavide.oltre.server

// **Who the colony belongs to, and for now nothing more than a string somebody sent.** `#110` is
// what makes this trustworthy: a verified Apple or Google subject, exchanged for a session token the
// server minted itself. Until then a request names its player in a header, which is enough to build
// and test everything above it and is not enough to deploy.
//
// A type rather than a `String` all the way down, deliberately and for the reason the model uses one
// everywhere else: the day this is minted from a JWT claim, the compiler has to be able to find
// every place that reads it. It is `:server`'s rather than `:protocol`'s because the client never
// says who it is — the wire carries a session, and who that session belongs to is the server's
// conclusion rather than the client's claim.
@JvmInline
value class PlayerId(val value: String) {

    init {
        require(value.isNotBlank()) { "a player id is a subject, not an absence" }
    }
}
