package dev.fardavide.oltre.protocol

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Instant

// **The four things sign-in puts on the wire, and none of them is a password.** A player proves who
// they are to Apple or to Google, hands the resulting ID token to *this* server and to nothing else,
// and gets back a session this server minted itself. `#106` §4: verification is server-side, and the
// client never sends a provider token anywhere but here.
//
// Everything below is `:protocol`'s for the reason the sync pair is: the shape is something both
// ends have to agree on, and module rule 8 forbids `:server` from reaching into `client/*`. What is
// *not* here is anything about how a token was obtained — the ASAuthorization dance, the loopback
// OAuth flow on desktop, the provider SDKs — which is `#113`'s and is a platform's business rather
// than a contract's.

// The provider's ID token, verbatim, as the platform handed it over. A compact JWS the server
// verifies against the provider's JWKS; nothing on this side of the wire looks inside it.
//
// **Blank is refused here rather than at the far end**, exactly as `IdempotencyKey` is: an absent
// token is malformed input and has no designed answer, where a token that is present and wrong has
// one — `ApiError.Unauthenticated` — and that answer is the server's to give.
@Serializable
@JvmInline
value class IdToken(val value: String) {

    init {
        require(value.isNotBlank()) { "an id token is what the provider handed over, not an absence" }
    }
}

// **What the app itself issues, and the only credential that reaches the game's own routes.** Two
// of them come back from a sign-in and they are deliberately the same type: a client holds both,
// sends the access token on every request and the refresh token to exactly one endpoint, and the
// server tells them apart by a claim rather than by their shape. A type per kind would put a
// distinction on the wire that only the signing key can actually enforce.
@Serializable
@JvmInline
value class SessionToken(val value: String) {

    init {
        require(value.isNotBlank()) { "a session token is minted by the server, not an absence" }
    }
}

// **What stops a stolen ID token being replayed.** The client draws a nonce, asks the provider to
// mint a token carrying it, and sends both — so a token captured from somebody else's sign-in
// carries somebody else's nonce and is refused.
//
// It is the value the client expects to find *in the token*, which is not always the value it drew:
// Apple's native flow is given the SHA-256 of the raw nonce and puts that hash in the claim. Hashing
// is therefore the client's business and the comparison is the server's, and this field is what the
// two agree on.
@Serializable
@JvmInline
value class SignInNonce(val value: String) {

    init {
        require(value.isNotBlank()) { "a nonce that is not there cannot make a replay fail" }
    }
}

// **Who vouched for the player**, and it is deliberately *not* a field on `SignInRequest`: the
// provider is the path. What this type is for is the client side of that — the gate has two buttons,
// something has to carry which one was pressed from the finger to the method that spells the route,
// and both `:client:auth` and `:client:net:data` need to name it without depending on each other.
//
// It is here rather than in either of them for `PLAYER_HEADER`'s reason one level up: this is a fact
// about the contract — there are exactly two issuers this server will believe — rather than a
// preference either end is free to hold. `:server`'s `IdentityProvider` is the same closed set said
// again in that module's own words, and the two converging is a tidy-up worth doing the day
// something forces it rather than today.
//
// **No `@Serializable`, and that is the point.** Nothing here goes on the wire. The moment this
// gains a serial name it has become the body field that `SignInRequest` was shaped to avoid.
enum class AuthProvider {

    APPLE,
    GOOGLE,
}

// One sign-in. The provider is the **path** rather than a field — `/v1/auth/apple` against
// `/v1/auth/google` — because the two are verified against different issuers, different audiences
// and different key sets, and a field would make one route that has to branch on its own body.
@Serializable
data class SignInRequest(
    val apiVersion: ApiVersion,
    val idToken: IdToken,
    val nonce: SignInNonce,
)

// Trading a refresh token for a fresh pair. **This is what `ApiError.SessionExpired` is answered
// with**, and it is the whole reason that member is separate from `Unauthenticated`: an access token
// running out is a thing the app fixes by itself, without a screen and without the player noticing.
@Serializable
data class RefreshRequest(
    val apiVersion: ApiVersion,
    val refreshToken: SessionToken,
)

// What a sign-in and a refresh both answer.
//
// **The expiries are on the wire rather than left to be read out of the token**, and that is not
// redundancy: the client would otherwise have to decode a JWT to know when to refresh, which means
// trusting a body it is not the one that signed. Told plainly, it can refresh early and never has to
// parse a credential it only ever carries.
@Serializable
data class SessionResponse(
    val apiVersion: ApiVersion,
    val accessToken: SessionToken,
    val accessExpiresAt: Instant,
    val refreshToken: SessionToken,
    val refreshExpiresAt: Instant,
)
