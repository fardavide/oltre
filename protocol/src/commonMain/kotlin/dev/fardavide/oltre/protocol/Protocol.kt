package dev.fardavide.oltre.protocol

import kotlinx.serialization.json.Json

// **The wire, and nothing that knows what a network is.** This module holds the shape of what the
// client says to the server and what comes back: nine verbs became twelve, an envelope, a request,
// a response, a rejection taxonomy and a version. Routes, sockets, headers and Ktor are `#108`'s
// and `#112`'s; there is no I/O here and there is not meant to be.
//
// **Why it is not in `core`.** `core`'s charter is *model + rules*, and an auth envelope, an error
// taxonomy and an API version are none of those. `GameSave` is the near miss that makes the line
// worth drawing: the save format *is* in `core`, because it is a thing client and server must agree
// on byte for byte — but it is also a game rule, since a colony that cannot be decoded cannot be
// played. The wire is a game rule about nothing. Keeping it out is what preserves the sentence that
// has held since 0.0.6: *"`core` depends on nothing"*.
//
// **Why it is not on the client side.** Module rule 8 forbids `server` from reaching into
// `client/*`, and both ends have to read this. So it is a sibling of `core`, pointing at it.
//
// **`:protocol` states the shape; `core` states the rules.** A verb here carries whatever the
// player pointed at and checks almost nothing — an empty manifest, a window too short to come home
// in, a survey of a system already surveyed all construct happily. That is deliberate: every one of
// them has an answer in `core` already, and each answer is a *result* the player can be shown
// rather than an exception somebody has to catch. What is checked here is what `core` has no
// opinion about at all — that a version is a version and that a minted key was minted.
object Protocol {

    // The one codec both ends use. `encodeDefaults` for `GameSave`'s reason — a payload that does
    // not spell out its own version is a payload the other end has to guess at — and, deliberately,
    // **no `ignoreUnknownKeys`**, also `GameSave`'s call: an unknown key is a disagreement about the
    // contract, and `ApiVersion` is the field that exists to settle those. Silently dropping it
    // would let a mismatch look like a success.
    val json: Json = Json { encodeDefaults = true }

    // **Who is asking, until `#110` makes it trustworthy.** A header rather than a query parameter
    // or a body field because that is where a credential goes, and because this is the line the
    // session token replaces: what changes then is how the value is obtained, not where it is read
    // from.
    //
    // It is here rather than in `:server`, where `#108` first wrote it, because **a wire string
    // spelled out at both ends is a wire string that can differ at both ends** — and the failure is
    // silent in the worst way, since a header the server does not recognise reads exactly like a
    // player who is not signed in. That is trap 1's shape at one character instead of three verbs.
    // `PlayerId` stays `:server`'s, and the asymmetry is deliberate: the *name of the header* is
    // something both ends have to agree on, while *who a value in it belongs to* is the server's
    // conclusion rather than the client's claim.
    const val PLAYER_HEADER: String = "X-Oltre-Player"
}
