package dev.fardavide.oltre.client.auth.data

import dev.fardavide.oltre.protocol.AuthProvider
import dev.fardavide.oltre.protocol.IdToken
import dev.fardavide.oltre.protocol.SignInNonce

// **What asking a platform to sign somebody in produced**, and it is three answers because the gate
// has three things to say. They are deliberately `ApiResult`'s three shapes one layer out: a thing
// that worked, a thing that was refused, and a thing that nobody answered — because the screen above
// draws the same three sentences whether the silence came from Apple or from `api.oltre.space`.
sealed interface SignInAttempt {

    // The provider vouched for the player. **The nonce travels back with the token** because it is not
    // always the one that was drawn: Apple is handed the SHA-256 of the raw nonce and puts *that* in
    // the claim, so what the server has to be told is what it will find in the token. Which of the two
    // it is is the platform's business and never the caller's.
    data class Signed(val idToken: IdToken, val nonce: SignInNonce) : SignInAttempt

    // **One member for a refusal and for a cancellation**, and that is a decision rather than
    // laziness: the platforms frequently cannot tell them apart — a dismissed Apple sheet and a
    // declined consent both arrive as `ASAuthorizationError.canceled` on some paths — and the design
    // writes one sentence for both because *an accusation is worse than a fact*.
    data object Refused : SignInAttempt

    // Nothing answered: no signal, or the provider's own service is down. Split from `Refused` for
    // `ApiResult`'s reason — the instruction is *wait*, and telling somebody on a train that Apple
    // refused them would be a lie they could act on wrongly.
    data object Unreachable : SignInAttempt
}

// **The platform's half of the gate**, and the only thing in the app that opens a window somebody
// else drew.
//
// A `fun interface` so a test hands in a lambda and the shell hands in whatever the platform has.
// **It never throws**, and that is the contract rather than an aspiration: this sits in front of the
// screen that gates the whole game, so an exception escaping here is not a degraded app, it is an app
// that cannot be opened. Every `actual` below catches everything it can and answers `Refused` or
// `Unreachable`; `SignInAttempt` has no fourth member because there is no fourth thing the gate could
// say.
fun interface ProviderSignIn {

    suspend fun signIn(provider: AuthProvider): SignInAttempt
}

// **Which buttons this build can actually complete**, and it is a platform fact rather than a
// preference.
//
// **The dead-control rule is what makes this a value rather than a comment.** A provider whose flow
// this platform cannot finish must not be drawn: a button that opens a browser that never comes back
// is the worst failure a gate has available, because the player cannot even describe it. So the gate
// draws this set and nothing else, and a platform that gains a provider gains a button by changing
// one line.
//
// What each platform answers, and why, is in its own `actual`.
expect fun signInProviders(): Set<AuthProvider>

// The real thing, for the composition root. Every collaborator a platform needs it reaches for
// itself, exactly as `defaultShakeDetector` and `defaultTiltSource` do.
expect fun defaultProviderSignIn(): ProviderSignIn
